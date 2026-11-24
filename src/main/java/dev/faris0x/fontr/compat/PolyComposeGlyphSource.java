package dev.faris0x.fontr.compat;

import dev.faris0x.fontr.config.FontrConfig;
import dev.faris0x.fontr.text.FontrFont;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * PolyCompose has a second text path for "Minecraft-style" text. It does not go
 * through {@code FontManager}; it asks {@code McGlyphs.source} for a bitmap and
 * metrics for each codepoint. OneClient installs a source backed by the active
 * resource pack's {@code ascii.png}, which for HD packs is a 256x256 sheet that
 * renders as tiny blocks in chat/HUD.
 *
 * <p>This class builds a tiny Minecraft-grid atlas from the Fontr TTF and
 * installs it as that source, so Compose chat/HUD text uses Fontr too.</p>
 */
public final class PolyComposeGlyphSource {
    private static final Logger LOGGER = LogManager.getLogger("Fontr");

    private static final int CELL = 8;
    private static final int SUPERSAMPLE = 8;
    private static final int PIXELS = CELL * SUPERSAMPLE; // 64
    private static final int GRID = 48;
    private static final int ATLAS = PIXELS * GRID; // 3072
    private static final int MAX_CP = GRID * GRID; // 2304

    private static Object sourceProxy;
    private static Object polyBitmap;
    private static byte[] pixels;
    private static Constructor<?> glyphConstructor;
    private static Class<?> sourceClass;

    private static Font font;
    private static FontRenderContext frc;
    private static final int[] advances = new int[MAX_CP];
    private static final Map<Integer, Object> glyphs = new HashMap<Integer, Object>();
    private static boolean ready;
    private static boolean failed;
    private static byte[] installedTtf;
    private static boolean installedAntialias;

    private PolyComposeGlyphSource() {
    }

    /** Build the atlas from the TTF and install it as McGlyphs.source. */
    public static synchronized void install(byte[] ttf, boolean antialias) {
        if (!FontrConfig.applyToPolyCompose || failed) {
            return;
        }
        try {
            Class<?> mcGlyphs = Class.forName("org.polyfrost.compose.mc.McGlyphs");
            Class<?> function1 = Class.forName("kotlin.jvm.functions.Function1");
            Class<?> mcGlyph = Class.forName("org.polyfrost.compose.mc.McGlyph");
            Class<?> bitmap = Class.forName("org.polyfrost.compose.render.PolyBitmap");

            if (!ready || installedTtf != ttf || installedAntialias != antialias) {
                glyphs.clear();
                java.util.Arrays.fill(advances, 0);
                initialise(ttf, antialias, mcGlyph, bitmap);
                installedTtf = ttf;
                installedAntialias = antialias;
            }

            if (sourceProxy == null) {
                sourceProxy = Proxy.newProxyInstance(
                    function1.getClassLoader() != null ? function1.getClassLoader()
                        : PolyComposeGlyphSource.class.getClassLoader(),
                    new Class<?>[]{function1},
                    new SourceHandler());
            }
            mcGlyphs.getMethod("setSource", function1).invoke(null, sourceProxy);
            sourceClass = mcGlyphs;
            log("Installed Fontr glyph source for PolyCompose Minecraft text");
        } catch (ClassNotFoundException e) {
            failed = true;
        } catch (Throwable t) {
            failed = true;
            log("Could not install PolyCompose glyph source: " + t);
        }
    }

    private static void initialise(byte[] ttf, boolean antialias, Class<?> mcGlyph, Class<?> bitmapClass)
        throws Exception {
        font = FontrFont.deriveVisualSize(
            Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(ttf)), (float) CELL);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = probe.createGraphics();
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics();
        frc = graphics.getFontRenderContext();
        graphics.dispose();

        BufferedImage empty = new BufferedImage(ATLAS, ATLAS, BufferedImage.TYPE_INT_ARGB);
        Method fromAwt = bitmapClass.getMethod("fromAwt", BufferedImage.class);
        polyBitmap = fromAwt.invoke(null, empty);
        pixels = (byte[]) bitmapClass.getMethod("getPixels").invoke(polyBitmap);

        glyphConstructor = mcGlyph.getConstructor(
            bitmapClass, float.class, float.class, float.class, float.class,
            float.class, float.class, float.class, float.class, float.class);

        // Sensible default metrics before a glyph is rasterised.
        int space = Math.max(2, Math.round(metrics.charWidth(' ')));
        advances[' '] = space;
        ready = true;
    }

    /** Re-install if another mod/resource reload replaced the source. */
    public static void ensureInstalled() {
        if (!ready || sourceProxy == null || sourceClass == null || !FontrConfig.applyToPolyCompose) {
            return;
        }
        try {
            Object current = sourceClass.getMethod("getSource").invoke(null);
            if (current != sourceProxy) {
                sourceClass.getMethod("setSource",
                    Class.forName("kotlin.jvm.functions.Function1")).invoke(null, sourceProxy);
                log("Re-asserted Fontr PolyCompose glyph source");
            }
        } catch (Throwable t) {
            failed = true;
        }
    }

    private static synchronized Object glyph(int cp) {
        if (!ready || cp < 0 || cp >= MAX_CP) {
            return null;
        }
        Object cached = glyphs.get(cp);
        if (cached != null) {
            return cached;
        }
        try {
            String text = new String(Character.toChars(cp));
            if (!font.canDisplay(cp)) {
                return null;
            }

            int advance = advances[cp];
            if (advance == 0) {
                GlyphVector vector = font.layoutGlyphVector(frc, text.toCharArray(), 0, text.length(),
                    Font.LAYOUT_LEFT_TO_RIGHT);
                double width = vector.getGlyphPosition(vector.getNumGlyphs()).getX();
                advance = Math.max(1, Math.min(CELL, (int) Math.round(width)));
                advances[cp] = advance;
            }

            int cellX = (cp % GRID) * PIXELS;
            int cellY = (cp / GRID) * PIXELS;
            rasterise(text, cellX, cellY);

            float u0 = (float) cellX / ATLAS;
            float v0 = (float) cellY / ATLAS;
            float u1 = (float) (cellX + advance * SUPERSAMPLE) / ATLAS;
            float v1 = (float) (cellY + PIXELS) / ATLAS;

            Object glyph = glyphConstructor.newInstance(polyBitmap,
                u0, v0, u1, v1,
                0.0F, (float) advance, (float) CELL,
                (float) advance + 1.0F, 1.0F);
            glyphs.put(cp, glyph);
            return glyph;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void rasterise(String text, int cellX, int cellY) {
        BufferedImage cell = new BufferedImage(PIXELS, PIXELS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = cell.createGraphics();
        graphics.setComposite(java.awt.AlphaComposite.Src);
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            FontrConfig.pixelSnap ? RenderingHints.VALUE_ANTIALIAS_OFF : RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            FontrConfig.pixelSnap ? RenderingHints.VALUE_TEXT_ANTIALIAS_OFF : RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(font);
        GlyphVector vector = font.layoutGlyphVector(frc, text.toCharArray(), 0, text.length(),
            Font.LAYOUT_LEFT_TO_RIGHT);
        // Draw at 4x resolution with the baseline on row 7 of the logical 8px cell.
        graphics.scale(SUPERSAMPLE, SUPERSAMPLE);
        graphics.drawGlyphVector(vector, 0, CELL - 1);
        graphics.dispose();

        int[] argb = cell.getRGB(0, 0, PIXELS, PIXELS, null, 0, PIXELS);
        for (int row = 0; row < PIXELS; row++) {
            for (int col = 0; col < PIXELS; col++) {
                int color = argb[row * PIXELS + col];
                int at = ((cellY + row) * ATLAS + cellX + col) * 4;
                pixels[at] = (byte) (color >> 16);
                pixels[at + 1] = (byte) (color >> 8);
                pixels[at + 2] = (byte) color;
                pixels[at + 3] = (byte) (color >>> 24);
            }
        }
    }

    private static final class SourceHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("invoke".equals(name)) {
                return glyph((Integer) args[0]);
            }
            if ("toString".equals(name)) {
                return "FontrGlyphSource";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            return null;
        }
    }

    private static void log(String message) {
        if (FontrConfig.debug) {
            LOGGER.info("[Fontr] " + message);
        }
    }
}
