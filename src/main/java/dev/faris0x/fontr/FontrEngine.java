package dev.faris0x.fontr;

import dev.faris0x.fontr.compat.PolyComposeFontBridge;
import dev.faris0x.fontr.config.FontrConfig;
import dev.faris0x.fontr.text.FontrAtlas;
import dev.faris0x.fontr.text.FontrFont;
import dev.faris0x.fontr.text.FontrLayout;
import dev.faris0x.fontr.text.FontrRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.pack.ResourcePack;
import net.minecraft.client.resource.pack.ResourcePacks;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Runtime facade used by the TextRenderer mixin. Measure/layout paths are
 * THREAD-SAFE and never touch OpenGL; rasterisation happens only in render().
 */
public final class FontrEngine {
    public static final FontrEngine INSTANCE = new FontrEngine();
    private static final Logger LOGGER = LogManager.getLogger("Fontr");

    private static final int MAX_LAYOUTS = 4096;
    private static final long RESOURCE_PACK_CACHE_MS = 1000L;

    private final Map<String, FontrLayout> layouts = Collections.synchronizedMap(
        new LinkedHashMap<String, FontrLayout>(256, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, FontrLayout> eldest) {
                return size() > MAX_LAYOUTS;
            }
        });

    private volatile FontrFont font;
    private volatile FontrAtlas atlas;
    private final Set<String> loggedLocations = new HashSet<String>();
    private boolean atlasDumped;
    private String loadedSettings = "";

    private boolean resourcePackOverride;
    private volatile long resourcePackCheckedAt;
    private volatile boolean lastPackResult;

    private FontrEngine() {
    }

    public void log(String message) {
        if (FontrConfig.debug) {
            LOGGER.info("[Fontr] " + message);
        }
    }

    /**
     * Loads the font eagerly so the PolyCompose/Skia registry is populated before
     * the first OneConfig Compose frame is drawn.
     */
    public void warmUp() {
        reloadIfNeeded();
    }

    public boolean shouldHandle(Identifier fontLocation) {
        if (!FontrConfig.enabled) {
            return false;
        }
        if (fontLocation == null || !fontLocation.toString().endsWith("textures/font/ascii.png")) {
            if (FontrConfig.debug) {
                String location = String.valueOf(fontLocation);
                synchronized (loggedLocations) {
                    if (loggedLocations.size() < 8 && loggedLocations.add(location)) {
                        log("Ignoring non-ascii TextRenderer " + location);
                    }
                }
            }
            return false;
        }

        reloadIfNeeded();
        if (font == null) {
            return false;
        }

        if (FontrConfig.allowResourcePackFontOverride) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.isOnSameThread()) {
                if (hasResourcePackFont()) {
                    if (!resourcePackOverride) {
                        resourcePackOverride = true;
                        log("Resource pack provides a font; deferring to vanilla until the toggle is changed.");
                    }
                    return false;
                }
            } else if (lastPackResult) {
                return false;
            }
        }
        resourcePackOverride = false;
        return true;
    }

    public Integer render(String text, float x, float y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (shadow && !FontrConfig.dropShadow) {
            return 0;
        }

        PolyComposeFontBridge.tick();

        FontrLayout layout = layout(text);
        if (layout == null) {
            return null;
        }

        try {
            if (atlas == null) {
                atlas = new FontrAtlas(font);
            }
            FontrRenderer.draw(layout, atlas, x, y, color, shadow, FontrConfig.pixelSnap);
            if (FontrConfig.debug && !atlasDumped) {
                atlasDumped = true;
                atlas.dump(FabricLoader.getInstance().getConfigDir().resolve("Fontr"));
                log("First render: text=" + text + " glyphs=" + layout.visual.length
                    + " advance=" + layout.advance + " color=" + Integer.toHexString(color));
            }
            return layout.advance;
        } catch (Throwable t) {
            log("Render failed: " + t);
            return null;
        }
    }

    public Integer measure(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        FontrLayout layout = layout(text);
        return layout == null ? null : layout.advance;
    }

    public Integer measureChar(char c) {
        return measure(String.valueOf(c));
    }

    public String trim(String text, int width, boolean reverse) {
        FontrLayout layout = layout(text);
        return layout == null ? null : layout.trimTo(width, reverse);
    }

    public Integer indexAtWidth(String text, int width) {
        FontrLayout layout = layout(text);
        return layout == null ? null : layout.indexAtWidth(width);
    }

    private FontrLayout layout(String text) {
        if (text == null) {
            return null;
        }
        reloadIfNeeded();
        if (font == null) {
            return null;
        }
        FontrLayout cached = layouts.get(text);
        if (cached != null) {
            return cached;
        }
        try {
            FontrLayout layout = FontrLayout.build(text, font);
            layouts.put(text, layout);
            return layout;
        } catch (Throwable t) {
            log("Layout failed: " + t);
            return null;
        }
    }

    private synchronized void reloadIfNeeded() {
        String settings = settingsKey();
        if (settings.equals(loadedSettings) && font != null) {
            return;
        }
        if (settings.equals(loadedSettings) && font == null) {
            return;
        }

        loadedSettings = settings;
        layouts.clear();
        atlas = null; // old GL textures are left to the driver on a live reload
        atlasDumped = false;
        FontrRenderer.resetDebug();

        try {
            Path path = resolveFontPath(FontrConfig.fontPathOrDefault());
            if (!Files.isRegularFile(path)) {
                font = null;
                log("Font file not found: " + path);
                return;
            }
            font = FontrFont.load(path, FontrConfig.fontSize, FontrConfig.antialias,
                                  FontrConfig.pixelSnap, FontrConfig.arabicShaping,
                                  FontrConfig.fallbackFontName());
            log("Loaded font " + path + " at " + FontrConfig.fontSize + "px");
            if (FontrConfig.applyToPolyCompose) {
                PolyComposeFontBridge.install(path, FontrConfig.pixelSnap);
            }
        } catch (Throwable t) {
            font = null;
            log("Could not load font: " + t);
        }
    }

    private String settingsKey() {
        return FontrConfig.fontPathOrDefault() + '|' + FontrConfig.fontSize + '|'
            + FontrConfig.antialias + '|' + FontrConfig.pixelSnap + '|'
            + FontrConfig.fontScale + '|'
            + FontrConfig.arabicShaping + '|' + FontrConfig.fallback + '|'
            + FontrConfig.allowResourcePackFontOverride + '|'
            + FontrConfig.applyToPolyCompose;
    }

    private static Path resolveFontPath(String value) {
        Path path = Paths.get(value);
        if (path.isAbsolute()) {
            return path;
        }
        String normalized = value.replace('\\', '/');
        FabricLoader loader = FabricLoader.getInstance();
        if (normalized.startsWith("config/")) {
            return loader.getGameDir().resolve(normalized);
        }
        return loader.getConfigDir().resolve(normalized);
    }

    private boolean hasResourcePackFont() {
        long now = System.currentTimeMillis();
        if (now - resourcePackCheckedAt < RESOURCE_PACK_CACHE_MS) {
            return lastPackResult;
        }
        resourcePackCheckedAt = now;
        lastPackResult = false;

        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return false;
            }
            ResourcePacks packs = minecraft.getResourcePacks();
            if (packs != null) {
                for (ResourcePacks.UnopenedPack unopened : packs.getSelectedPacks()) {
                    if (!isCustomPackName(unopened.getName())) {
                        continue;
                    }
                    ResourcePack pack;
                    try {
                        pack = unopened.open();
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (pack != null && providesFont(pack)) {
                        lastPackResult = true;
                        return true;
                    }
                }
                ResourcePack serverPack = packs.getServerResourcePack();
                if (serverPack != null && providesFont(serverPack)) {
                    lastPackResult = true;
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // A resource manager reload can race this check; fail open.
        }
        return lastPackResult;
    }

    private static boolean isCustomPackName(String name) {
        if (name == null) {
            return true;
        }
        return !"Default".equalsIgnoreCase(name) && !"Programmer Art".equalsIgnoreCase(name);
    }

    private static boolean providesFont(ResourcePack pack) {
        Identifier ascii = new Identifier("textures/font/ascii.png");
        if (!pack.hasResource(ascii)) {
            return false;
        }
        // Only defer to a pack font that vanilla can actually render. HD packs
        // ship 256x256 (or larger) ascii sheets and rely on MCPatcher/Cera;
        // letting those win produces the classic wall of squares.
        try (java.io.InputStream in = pack.getResource(ascii)) {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(in);
            return image != null && image.getWidth() == 128 && image.getHeight() == 128;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
