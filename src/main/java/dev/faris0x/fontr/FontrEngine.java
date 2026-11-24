// SPDX-License-Identifier: GPL-3.0-only

package dev.faris0x.fontr;

import dev.faris0x.fontr.compat.FontrPack;
import dev.faris0x.fontr.compat.PolyComposeFontBridge;
import dev.faris0x.fontr.text.FontrFaces;
import dev.faris0x.fontr.text.FontrFont;
import dev.faris0x.fontr.text.FontrSheets;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resource.Identifier;
import net.ornithemc.osl.core.api.util.NamespacedIdentifier;
import net.ornithemc.osl.core.api.util.NamespacedIdentifiers;
import net.ornithemc.osl.core.api.util.function.IOSupplier;
import net.ornithemc.osl.resource.loader.api.resource.ResourceType;
import net.ornithemc.osl.resource.loader.api.resource.pack.ResourcePack;
import net.ornithemc.osl.resource.loader.api.resource.repository.ResourcePackRepository;
import net.ornithemc.osl.resource.loader.api.resource.repository.ResourcePackSummary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Runtime facade for the resource-level font override.
 *
 * <p>Fontr replaces the resources every 1.8.9 renderer already reads
 * ({@code textures/font/ascii.png}, {@code textures/font/unicode_page_XX.png}
 * and {@code font/glyph_sizes.bin}) with sheets rasterised from the selected
 * TTF. Delivery happens through an OSL resource pack, so Minecraft, Argentum's
 * batcher, PolyCompose's MC font and Cera all consume the same glyphs.</p>
 */
public final class FontrEngine {
    public static final FontrEngine INSTANCE = new FontrEngine();

    private static final Logger LOGGER = LogManager.getLogger("Fontr");
    private static final String ASCII = "textures/font/ascii.png";
    private static final String ASCII_ALT = "font/ascii.png";
    private static final String GLYPH_SIZES = "font/glyph_sizes.bin";
    private static final String GLYPH_SIZES_ALT = "textures/font/glyph_sizes.bin";
    private static final String UNICODE = "textures/font/unicode_page_";
    private static final String UNICODE_ALT = "font/unicode_page_";
    private static final String STYLED_BOLD = "textures/font/fontr_ascii_bold.png";
    private static final String STYLED_ITALIC = "textures/font/fontr_ascii_italic.png";
    private static final String STYLED_BOLD_ITALIC = "textures/font/fontr_ascii_bold_italic.png";
    private static final int REASSERT_TICKS = 200;

    private final Object lock = new Object();

    private volatile String loadedKey = "";
    private volatile FontrSheets sheets;
    private volatile Path loadedPath;
    private volatile List<ResourcePack> basePacks;
    private volatile Runnable settingsPoller;
    private volatile boolean pendingReload;
    private volatile boolean served;
    private volatile boolean loggedServe;
    private volatile boolean loggedArgentumAdjust;
    private volatile int tickCounter;

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
            // Do not force a reload during startup before the game has read a font.
            if (served) {
                pendingReload = true;
            }
        }
        if (FontrSettings.applyToPolyCompose && !PolyComposeFontBridge.isInstalled()
            && loadedPath != null) {
            PolyComposeFontBridge.install(loadedPath);
        }
        if ((tickCounter % REASSERT_TICKS) == 0 && loadedPath != null) {
            PolyComposeFontBridge.reassert();
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

    // ------------------------------------------------------------------
    // Resource pack feed
    // ------------------------------------------------------------------

    /** Returns the generated bytes for a Minecraft asset, or null. */
    public byte[] resourceBytes(NamespacedIdentifier id) {
        if (!FontrSettings.enabled || !"minecraft".equals(id.namespace())) {
            return null;
        }
        String path = id.identifier();
        boolean ascii = ASCII.equals(path) || ASCII_ALT.equals(path);
        boolean sizes = GLYPH_SIZES.equals(path) || GLYPH_SIZES_ALT.equals(path);
        String prefix = path.startsWith(UNICODE) ? UNICODE
            : path.startsWith(UNICODE_ALT) ? UNICODE_ALT : null;
        FontrFont.Style styled = styleForPath(path);
        if (!ascii && !sizes && prefix == null && styled == null) {
            return null;
        }

        reloadIfNeeded();
        FontrSheets current = sheets;
        if (current == null) {
            return null;
        }
        try {
            served = true;
            if (!loggedServe && FontrSettings.debug) {
                loggedServe = true;
                LOGGER.info("[Fontr] Serving generated font resources (" + path + ")");
            }
            if (styled != null) {
                return current.hasStyle(styled) ? current.asciiPng(styled) : null;
            }
            if (ascii) {
                return current.regularPng();
            }
            if (sizes) {
                return current.glyphSizes();
            }
            int block = parseBlock(path.substring(prefix.length()));
            return block < 0 ? null : current.unicodePng(block);
        } catch (Throwable t) {
            LOGGER.warn("[Fontr] Could not build font resource " + path + ": " + t);
            return null;
        }
    }

    private static FontrFont.Style styleForPath(String path) {
        if (STYLED_BOLD.equals(path)) {
            return FontrFont.Style.BOLD;
        }
        if (STYLED_ITALIC.equals(path)) {
            return FontrFont.Style.ITALIC;
        }
        if (STYLED_BOLD_ITALIC.equals(path)) {
            return FontrFont.Style.BOLD_ITALIC;
        }
        return null;
    }

    /** True when the exact bold/italic face combination was generated. */
    public boolean styleAvailable(boolean bold, boolean italic) {
        FontrSheets current = sheets;
        if (current == null || !FontrSettings.enabled || (!bold && !italic)) {
            return false;
        }
        FontrFont.Style style = bold && italic ? FontrFont.Style.BOLD_ITALIC
            : bold ? FontrFont.Style.BOLD : FontrFont.Style.ITALIC;
        return current.hasStyle(style);
    }

    /** Identifier of the styled ASCII sheet, or null when that style is unavailable. */
    public Identifier styledSheet(boolean bold, boolean italic) {
        if (!styleAvailable(bold, italic)) {
            return null;
        }
        String path = bold && italic ? STYLED_BOLD_ITALIC : bold ? STYLED_BOLD : STYLED_ITALIC;
        return new Identifier("minecraft", path);
    }

    public boolean argentumStylesEnabled() {
        return FontrSettings.argentumStyles && FontrSettings.trueStyles;
    }

    /**
     * Argentum adds one pixel to every scanned advance. Fontr's sheets already
     * include vanilla's one-pixel gap, so remove the extra one.
     */
    public void adjustArgentumWidths(float[] widths, int[] characterWidths) {
        if (!FontrSettings.enabled || sheets == null || widths == null) {
            return;
        }
        for (int i = 0; i < widths.length && i < 256; i++) {
            if (i == 32) {
                continue;
            }
            if (widths[i] > 1.0F) {
                widths[i] -= 1.0F;
            }
        }
        widths[32] = 4.0F;
        if (characterWidths != null) {
            for (int i = 0; i < characterWidths.length && i < 256; i++) {
                characterWidths[i] = Math.round(widths[i]);
            }
        }
        if (!loggedArgentumAdjust) {
            loggedArgentumAdjust = true;
            log("Adjusted Argentum advances for the one-pixel gap");
        }
    }

    /** Used by the pack for resource listings. Only emits the fixed sheets. */
    public void findResources(String namespace, String path,
                              BiConsumer<NamespacedIdentifier, byte[]> consumer) {
        if (!"minecraft".equals(namespace) || !FontrSettings.enabled) {
            return;
        }
        String prefix = path == null ? "" : path;
        if (ASCII.startsWith(prefix) && !prefix.isEmpty()) {
            emit(ASCII, consumer);
        }
        if (GLYPH_SIZES.startsWith(prefix) && !prefix.isEmpty()) {
            emit(GLYPH_SIZES, consumer);
        }
    }

    private void emit(String path, BiConsumer<NamespacedIdentifier, byte[]> consumer) {
        byte[] bytes = resourceBytes(NamespacedIdentifiers.from("minecraft", path));
        if (bytes != null) {
            consumer.accept(NamespacedIdentifiers.from("minecraft", path), bytes);
        }
    }

    private static int parseBlock(String rest) {
        if (rest.endsWith(".png")) {
            rest = rest.substring(0, rest.length() - 4);
        }
        try {
            return Integer.parseInt(rest, 16);
        } catch (Throwable t) {
            return -1;
        }
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

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
            closeBasePacks();
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
                Path boldOverride = resolveOptional(FontrSettings.boldFont);
                Path italicOverride = resolveOptional(FontrSettings.italicFont);
                Path boldItalicOverride = resolveOptional(FontrSettings.boldItalicFont);
                FontrFaces faces = FontrFaces.resolve(path, boldOverride, italicOverride,
                    boldItalicOverride);
                FontrFont font = FontrFont.load(path, faces.bold, faces.italic, faces.boldItalic,
                    FontrSettings.fontSize, FontrSettings.fontScale, FontrSettings.antialias,
                    FontrSettings.fontName());
                Path cacheDir = FabricLoader.getInstance().getConfigDir()
                    .resolve("Fontr").resolve("cache").resolve(cacheKey(path));
                FontrSheets created = new FontrSheets(font, FontrSettings.resolution,
                    FontrSettings.styledResolution, FontrSettings.trueStyles, cacheDir,
                    this::openBase, FontrSettings.letterSpacing, FontrSettings.embolden,
                    FontrSettings.scriptScale, FontrSettings.scriptOffset,
                    FontrSettings.scriptHeight, FontrSettings.debug);
                sheets = created;
                loadedPath = path;
                String styles = FontrSettings.trueStyles
                    ? (faces.bold != null ? " (real bold)" : " (synthetic bold)")
                        + (faces.italic != null ? " (real italic)" : " (synthetic italic)")
                        + (faces.boldItalic != null ? " (real bold-italic)" : " (synthetic bold-italic)")
                    : " (styles off)";
                LOGGER.info("[Fontr] Loaded " + path.getFileName() + " at "
                    + FontrSettings.resolution + "px sheets" + styles);
                if (FontrSettings.applyToPolyCompose) {
                    PolyComposeFontBridge.install(path, faces.bold, faces.italic, faces.boldItalic);
                }
                invalidateArgentumStyles();
            } catch (Throwable t) {
                LOGGER.warn("[Fontr] Could not load font " + path + ": " + t);
            }
        }
    }

    // ------------------------------------------------------------------
    // Base pack access (for glyphs the selected font lacks)
    // ------------------------------------------------------------------

    private InputStream openBase(String path) {
        NamespacedIdentifier id = NamespacedIdentifiers.from("minecraft", path);
        for (ResourcePack pack : basePacks()) {
            try {
                if (!pack.hasResource(ResourceType.CLIENT_ASSETS, id)) {
                    continue;
                }
                IOSupplier<InputStream> supplier = pack.getResource(ResourceType.CLIENT_ASSETS, id);
                if (supplier != null) {
                    return supplier.get();
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private List<ResourcePack> basePacks() {
        List<ResourcePack> current = basePacks;
        if (current != null) {
            return current;
        }
        synchronized (lock) {
            if (basePacks != null) {
                return basePacks;
            }
            List<ResourcePack> opened = new ArrayList<ResourcePack>();
            try {
                ResourcePackRepository repository = ResourcePackRepository.client();
                if (repository != null) {
                    for (ResourcePackSummary summary : repository.getSelectedPacks()) {
                        if (FontrPack.ID.equals(summary.getId())) {
                            continue;
                        }
                        try {
                            opened.add(summary.open());
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable t) {
                log("Could not open base resource packs: " + t);
            }
            basePacks = opened;
            return opened;
        }
    }

    private void closeBasePacks() {
        List<ResourcePack> current = basePacks;
        basePacks = null;
        if (current == null) {
            return;
        }
        for (ResourcePack pack : current) {
            try {
                pack.close();
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    // Paths and cache
    // ------------------------------------------------------------------

    private static Path resolveOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            Path path = resolveFontPath(value.trim());
            return Files.isRegularFile(path) ? path : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private void invalidateArgentumStyles() {
        if (!FabricLoader.getInstance().isModLoaded("argentum")) {
            return;
        }
        try {
            dev.faris0x.fontr.compat.ArgentumStyles.invalidate();
        } catch (Throwable ignored) {
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
            pruneCache(key);
            return key;
        } catch (Throwable t) {
            return "default";
        }
    }

    private void pruneCache(String keep) {
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
