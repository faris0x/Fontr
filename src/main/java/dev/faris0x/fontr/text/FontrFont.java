package dev.faris0x.fontr.text;

import com.ibm.icu.text.ArabicShaping;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * The loaded TTF/OTF plus its metrics and shaping context.
 */
public final class FontrFont {
    public final Font base;
    public final Font fallback;
    public final int size;
    public final boolean antialias;
    public final boolean pixelSnap;
    public final boolean arabicShaping;
    public final int baseline;
    public final int descent;

    private final FontRenderContext frc;

    private FontrFont(Font base, Font fallback, int size, boolean antialias,
                      boolean pixelSnap, boolean arabicShaping, FontRenderContext frc,
                      int baseline, int descent) {
        this.base = base;
        this.fallback = fallback;
        this.size = size;
        this.antialias = antialias;
        this.pixelSnap = pixelSnap;
        this.arabicShaping = arabicShaping;
        this.frc = frc;
        this.baseline = baseline;
        this.descent = descent;
    }

    public static FontrFont load(Path path, int size, boolean antialias, boolean pixelSnap,
                                 boolean arabicShaping, String fallbackName) throws Exception {
        Font created = Font.createFont(Font.TRUETYPE_FONT, path.toFile());
        Font base = deriveVisualSize(created, (float) size);
        Font fallback = null;
        if (fallbackName != null) {
            fallback = deriveVisualSize(new Font(fallbackName, Font.PLAIN, size), (float) size);
        }

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = probe.createGraphics();
        setHints(graphics, antialias, pixelSnap);
        graphics.setFont(base);
        FontMetrics metrics = graphics.getFontMetrics();
        FontRenderContext frc = graphics.getFontRenderContext();
        // Minecraft's line is `size` pixels tall. Put the baseline one pixel
        // above the bottom instead of using the font's em ascent (some fonts
        // have ascent == em and would push glyphs out of the line).
        int baseline = Math.max(1, size - 1);
        int descent = Math.round(metrics.getDescent());
        graphics.dispose();

        return new FontrFont(base, fallback, size, antialias, pixelSnap,
                             arabicShaping, frc, baseline, descent);
    }

    /**
     * Pixel fonts often have a cap height far below their em size (this one is
     * ~58%). Deriving at the raw pixel size then produces 4px blobs in a 9px
     * line. Scale the point size so a capital H occupies ~80% of the requested
     * line height while keeping the same baseline/line spacing.
     */
    public static Font deriveVisualSize(Font created, float lineHeight) {
        float pointSize = lineHeight;
        try {
            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = probe.createGraphics();
            Font probeFont = created.deriveFont(Font.PLAIN, 100.0F);
            graphics.setFont(probeFont);
            FontRenderContext frc = graphics.getFontRenderContext();
            GlyphVector vector = probeFont.createGlyphVector(frc, "H");
            java.awt.Rectangle bounds = vector.getPixelBounds(frc, 0, 0);
            graphics.dispose();
            if (bounds.height > 0) {
                float capRatio = bounds.height / 100.0F;
                pointSize = (lineHeight * 0.80F) / capRatio;
            }
        } catch (Throwable ignored) {
        }
        // Never shrink below the requested size, and avoid absurd scaling.
        pointSize = Math.max(lineHeight, Math.min(lineHeight * 2.5F, pointSize));
        return created.deriveFont(Font.PLAIN, pointSize);
    }

    private static void setHints(Graphics2D graphics, boolean antialias, boolean pixelSnap) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            antialias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            antialias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
            pixelSnap ? RenderingHints.VALUE_FRACTIONALMETRICS_OFF : RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
            pixelSnap ? RenderingHints.VALUE_RENDER_SPEED : RenderingHints.VALUE_RENDER_QUALITY);
    }

    public FontRenderContext frc() {
        return frc;
    }

    public Font forStyle(int style) {
        return base.deriveFont(style);
    }

    public Font fallbackForStyle(int style) {
        return fallback == null ? null : fallback.deriveFont(style);
    }

    public boolean primaryCanDisplay(char c) {
        return base.canDisplay(c);
    }

    public Font fontFor(char c, int style) {
        if (primaryCanDisplay(c)) {
            return forStyle(style);
        }
        Font fb = fallbackForStyle(style);
        if (fb != null && fb.canDisplay(c)) {
            return fb;
        }
        return forStyle(style);
    }

    public GlyphVector layout(Font font, char[] text, int start, int end) {
        return font.layoutGlyphVector(frc, text, start, end, Font.LAYOUT_LEFT_TO_RIGHT);
    }

    public ShapedText shape(String text) {
        if (!arabicShaping || !containsArabic(text)) {
            int[] map = new int[text.length()];
            for (int i = 0; i < map.length; i++) {
                map[i] = i;
            }
            return new ShapedText(text, map);
        }

        try {
            ArabicShaping shaper = new ArabicShaping(ArabicShaping.LETTERS_SHAPE);
            String shaped = shaper.shape(text);
            int[] map = new int[shaped.length()];
            int original = 0;
            for (int i = 0; i < shaped.length(); i++) {
                if (original >= text.length()) {
                    original = text.length() - 1;
                }
                map[i] = original;
                if (original < text.length()) {
                    char current = text.charAt(original);
                    if (current == '\u0644' && original + 1 < text.length()
                        && isAlef(text.charAt(original + 1))) {
                        original += 2;
                    } else {
                        original++;
                    }
                }
            }
            return new ShapedText(shaped, map);
        } catch (Throwable t) {
            int[] map = new int[text.length()];
            for (int i = 0; i < map.length; i++) {
                map[i] = i;
            }
            return new ShapedText(text, map);
        }
    }

    private static boolean containsArabic(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0600' && c <= '\u06FF') {
                return true;
            }
            if (c >= '\u0750' && c <= '\u077F') {
                return true;
            }
            if (c >= '\u08A0' && c <= '\u08FF') {
                return true;
            }
        }
        return false;
    }

    private static boolean isAlef(char c) {
        return c == '\u0627' || c == '\u0622' || c == '\u0623' || c == '\u0625' || c == '\u0671';
    }

    public static final class ShapedText {
        public final String text;
        /** shaped index -> original stripped index */
        public final int[] map;

        ShapedText(String text, int[] map) {
            this.text = text;
            this.map = map;
        }
    }
}
