package dev.faris0x.fontr.config;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.File;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

/**
 * Native OneConfig settings page.
 *
 * <p>OneConfig reflects the annotated static fields and generates the UI. The
 * same fields are read by the render engine; changing one invalidates the
 * font/atlas cache on the next frame.</p>
 */
public final class FontrConfig extends Config {
    public static final FontrConfig INSTANCE = new FontrConfig();

    private static final String CATEGORY_GENERAL = "General";
    private static final String CATEGORY_FONT = "Font";
    private static final String CATEGORY_TEXT = "Text";
    private static final String CATEGORY_FALLBACK = "Fallback";
    private static final String CATEGORY_COMPAT = "Compatibility";
    private static final String CATEGORY_DEBUG = "Debug";

    @Switch(
        title = "Enabled",
        description = "Replace the vanilla bitmap font with the configured TTF/OTF font.",
        category = CATEGORY_GENERAL
    )
    public static boolean enabled = true;

    @File(
        title = "Font file",
        description = "Path to a .ttf or .otf file. Relative paths are resolved under .minecraft/config/.",
        types = {"ttf", "otf"},
        filterName = "Font files",
        category = CATEGORY_FONT
    )
    public static String fontPath = "Fontr/ArabicPixelSmall.ttf";

    @Slider(
        title = "Font size",
        description = "Glyph size in game pixels. Pixel fonts usually look best at an integer size.",
        min = 4,
        max = 64,
        step = 1,
        category = CATEGORY_FONT
    )
    public static int fontSize = 8;

    @Switch(
        title = "Antialiasing",
        description = "Smooth glyph edges. Turn this off for pixel fonts.",
        category = CATEGORY_FONT
    )
    public static boolean antialias = false;

    @Switch(
        title = "Pixel snap",
        description = "Round glyph positions to whole pixels and use nearest-neighbour texture filtering.",
        category = CATEGORY_FONT
    )
    public static boolean pixelSnap = true;

    @Switch(
        title = "Arabic shaping",
        description = "Shape Arabic letters into their joining forms with ICU before layout. Required for fonts without GSUB.",
        category = CATEGORY_TEXT
    )
    public static boolean arabicShaping = true;

    @Switch(
        title = "Drop shadow",
        description = "Use Minecraft's normal drop-shadow pass for Fontr text.",
        category = CATEGORY_TEXT
    )
    public static boolean dropShadow = true;

    @Dropdown(
        title = "Fallback",
        description = "Font used for codepoints missing from the main font.",
        options = {"None", "System SansSerif", "System Monospaced"},
        category = CATEGORY_FALLBACK
    )
    public static String fallback = "System SansSerif";

    @Switch(
        title = "Allow resource pack font override",
        description = "If the active resource pack provides a bitmap font, let it win and render that text with vanilla instead of Fontr.",
        category = CATEGORY_COMPAT
    )
    public static boolean allowResourcePackFontOverride = false;

    @Switch(
        title = "Debug logging",
        description = "Log font loading, shaping and resource-pack override decisions.",
        category = CATEGORY_DEBUG
    )
    public static boolean debug = false;

    private FontrConfig() {
        super("fontr.json", "/assets/fontr/icon.png", "Fontr", Config.Category.VISUALS);
    }

    public void register() {
        preload();
    }

    public static String fontPathOrDefault() {
        if (fontPath == null || fontPath.trim().isEmpty()) {
            return "Fontr/ArabicPixelSmall.ttf";
        }
        return fontPath.trim();
    }

    public static String fallbackFontName() {
        if ("System Monospaced".equalsIgnoreCase(fallback)) {
            return java.awt.Font.MONOSPACED;
        }
        if ("None".equalsIgnoreCase(fallback)) {
            return null;
        }
        return java.awt.Font.SANS_SERIF;
    }
}
