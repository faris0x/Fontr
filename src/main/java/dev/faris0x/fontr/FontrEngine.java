package dev.faris0x.fontr;

import dev.faris0x.fontr.compat.FontrResource;
import dev.faris0x.fontr.compat.PolyComposeFontBridge;
import dev.faris0x.fontr.text.FontrFont;
import dev.faris0x.fontr.text.FontrSheets;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Runtime facade for the resource-level font override.
 *
 * <p>Fontr does not hook any text renderer. It replaces the resources every
 * 1.8.9 renderer already reads ({@code font/ascii.png},
 * {@code font/unicode_page_XX.png} and {@code font/glyph_sizes.bin}) with sheets
 * rasterised from the selected TTF. Vanilla, Argentum's batcher, Cera and
 * PolyCompose therefore all consume the same glyphs.</p>
 */
public final class FontrEngine {
    public static final FontrEngine INSTANCE = new FontrEngine();

    private static final Logger LOGGER = LogManager.getLogger("Fontr");
    private static final String FONT_ROOT = "minecraft:textures/font/";
    private static final String GLYPH_SIZES = "minecraft:textures/font/glyph_sizes.bin";
    private static final String ASCII = "minecraft:textures/font/ascii.png";
    private static final String UNICODE = "minecraft:textures/font/unicode_page_";

    private static final ThreadLocal<Boolean> INTERNAL_READ =
        ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final Object lock = new Object();

    private volatile String loadedKey = "";
    private volatile FontrSheets sheets;
    private volatile java.nio.file.Path loadedPath;
    private volatile Runnable settingsPoller;
    private volatile ResourceManager lastManager;
    private volatile boolean pendingReload;
    private volatile int tickCounter;
    private volatile boolean loggedResourceHit;

    private FontrEngine() {
    }

    public void init() {
        reloadIfNeeded();
        LOGGER.info("[Fontr] Resource font engine ready");
    }

    /** Supplies a hook that copies OneConfig values into {@link FontrSettings}. */
    public void setSettingsPoller(Runnable poller) {
        this.settingsPoller = poller;
    }

    public void log(String message) {
        if (FontrSettings.debug) {
            LOGGER.info("[Fontr] " + message);
        }
    }

    /** Called from the client tick mixin. */
    public void tick() {
        if ((++tickCounter & 15) != 0) {
            return;
        }
        Runnable poller = settingsPoller;
        if (poller != null) {
            try {
                poller.run();
            } catch (Throwable t) {
                settingsPoller = null;
                LOGGER.warn("[Fontr] Settings poller failed: " + t);
            }
        }
        String key = FontrSettings.sheetKey();
        if (!key.equals(loadedKey) || (sheets == null && FontrSettings.enabled)) {
            reloadIfNeeded();
            pendingReload = true;
        }
        if (FontrSettings.applyToPolyCompose && !PolyComposeFontBridge.isInstalled()
            && loadedPath != null) {
            PolyComposeFontBridge.install(loadedPath);
        }
        if (pendingReload) {
            pendingReload = false;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                try {
                    minecraft.reloadResources();
                    log("Reloaded resources for new font settings");
                } catch (Throwable t) {
                    LOGGER.warn("[Fontr] Resource reload failed: " + t);
                }
            }
        }
    }

    /**
     * Called from the resource-manager mixins.
     *
     * @return the replacement resource, or null to let the vanilla one load
     */
    public Resource resourceFor(Identifier location, ResourceManager manager) {
        if (!FontrSettings.enabled || Boolean.TRUE.equals(INTERNAL_READ.get())) {
            return null;
        }
        String name = location.toString();
        if (!(name.equals(ASCII) || name.equals(GLYPH_SIZES) || name.startsWith(UNICODE))) {
            return null;
        }
        lastManager = manager;
        reloadIfNeeded();
        FontrSheets current = sheets;
        if (current == null) {
            return null;
        }
        try {
            if (!loggedResourceHit && FontrSettings.debug) {
                loggedResourceHit = true;
                LOGGER.info("[Fontr] Serving generated font resources (" + name + ")");
            }
            if (name.equals(ASCII)) {
                return FontrResource.of(location, current.asciiPng());
            }
            if (name.equals(GLYPH_SIZES)) {
                return FontrResource.of(location, current.glyphSizes());
            }
            int block = parseBlock(name);
            if (block < 0) {
                return null;
            }
            byte[] png = current.unicodePng(block);
            return png == null ? null : FontrResource.of(location, png);
        } catch (Throwable t) {
            LOGGER.warn("[Fontr] Could not build font resource " + name + ": " + t);
            return null;
        }
    }

    private static int parseBlock(String name) {
        String rest = name.substring(UNICODE.length());
        if (rest.endsWith(".png")) {
            rest = rest.substring(0, rest.length() - 4);
        }
        try {
            return Integer.parseInt(rest, 16);
        } catch (Throwable t) {
            return -1;
        }
    }

    private void reloadIfNeeded() {
        String key = FontrSettings.sheetKey();
        synchronized (lock) {
            if (key.equals(loadedKey) && (sheets != null || !FontrSettings.enabled)) {
                return;
            }
            loadedKey = key;
            pendingReload = false;
            sheets = null;
            loadedPath = null;
            if (!FontrSettings.enabled) {
                LOGGER.info("[Fontr] Disabled");
                return;
            }

            Path path;
            try {
                path = resolveFontPath(FontrSettings.fontPath);
            } catch (Throwable t) {
                LOGGER.warn("[Fontr] Invalid font path: " + t);
                return;
            }
            if (!Files.isRegularFile(path)) {
                LOGGER.warn("[Fontr] Font file not found: " + path);
                return;
            }

            try {
                FontrFont font = FontrFont.load(path, FontrSettings.fontSize,
                    FontrSettings.fontScale, FontrSettings.antialias, FontrSettings.fontName());
                Path cacheDir = FabricLoader.getInstance().getConfigDir()
                    .resolve("Fontr").resolve("cache").resolve(cacheKey(path));
                FontrSheets created = new FontrSheets(font, FontrSettings.resolution, cacheDir,
                    this::openBase, FontrSettings.debug);
                sheets = created;
                loadedPath = path;
                LOGGER.info("[Fontr] Loaded " + path.getFileName() + " at "
                    + FontrSettings.resolution + "px sheets");
                if (FontrSettings.applyToPolyCompose) {
                    PolyComposeFontBridge.install(path);
                }
            } catch (Throwable t) {
                LOGGER.warn("[Fontr] Could not load font " + path + ": " + t);
            }
        }
    }

    /** Reads from the resource packs underneath Fontr (never our own sheet). */
    private InputStream openBase(String path) {
        ResourceManager manager = lastManager;
        if (manager == null) {
            return null;
        }
        String[] parts = path.split("/", 2);
        if (parts.length != 2) {
            return null;
        }
        try {
            INTERNAL_READ.set(Boolean.TRUE);
            Resource resource = manager.getResource(new Identifier(parts[0], parts[1]));
            return resource == null ? null : resource.asStream();
        } catch (Throwable t) {
            return null;
        } finally {
            INTERNAL_READ.set(Boolean.FALSE);
        }
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

    private String cacheKey(Path font) {
        try {
            String identity = font.toAbsolutePath() + "|" + Files.getLastModifiedTime(font).toMillis()
                + "|" + Files.size(font) + "|" + FontrSettings.sheetKey();
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                builder.append(String.format(Locale.ROOT, "%02x", hash[i]));
            }
            String key = builder.toString();
            pruneCache(font, key);
            return key;
        } catch (Throwable t) {
            return "default";
        }
    }

    private void pruneCache(Path font, String keep) {
        try {
            Path root = FabricLoader.getInstance().getConfigDir().resolve("Fontr").resolve("cache");
            if (!Files.isDirectory(root)) {
                return;
            }
            try (Stream<Path> children = Files.list(root)) {
                children.filter(Files::isDirectory).forEach(child -> {
                    if (!child.getFileName().toString().equals(keep)) {
                        deleteTree(child);
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static void deleteTree(Path path) {
        try (Stream<Path> children = Files.list(path)) {
            children.forEach(FontrEngine::deleteTree);
        } catch (Throwable ignored) {
        }
        try {
            Files.deleteIfExists(path);
        } catch (Throwable ignored) {
        }
    }
}
