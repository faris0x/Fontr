package dev.faris0x.fontr.config;

import dev.faris0x.fontr.FontrSettings;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.File;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

/**
 * OneConfig page, shown in the OneClient mods manager under Visuals.
 *
 * <p>This is the only class that references OneConfig. Values are copied into
 * {@link FontrSettings} so the engine still runs when OneConfig is absent.</p>
 */
public final class FontrConfig extends Config {
    public static final FontrConfig INSTANCE = new FontrConfig();

    private static final String CATEGORY_GENERAL = "General";
    private static final String CATEGORY_FONT = "Font";
    private static final String CATEGORY_COMPAT = "Compatibility";
    private static final String CATEGORY_DEBUG = "Debug";

    @Switch(
        title = "Enabled",
        description = "Replace the game's bitmap font with the selected TTF/OTF.",
        category = CATEGORY_GENERAL
    )
    public static boolean enabled = true;

    @File(
        title = "Font file",
        description = "Path to a .ttf or .otf file. Relative paths resolve under .minecraft/config/.",
        types = {"ttf", "otf"},
        filterName = "Font files",
        category = CATEGORY_FONT
    )
    public static String fontPath = "Fontr/font.ttf";

    @Dropdown(
        title = "Sheet resolution",
        description = "Resolution of the generated ASCII sheet. 4096 is 32x supersampling; 8192 uses ~256 MB of VRAM.",
        options = {"4096 (recommended)", "8192 (maximum)"},
        category = CATEGORY_FONT
    )
    public static String resolution = "4096 (recommended)";

    @Slider(
        title = "Line height",
        description = "Minecraft's text line height in pixels. Leave at 8 to match the vanilla font.",
        min = 8,
        max = 16,
        step = 1,
        category = CATEGORY_FONT
    )
    public static int fontSize = 8;

    @Slider(
        title = "Glyph scale",
        description = "Adjusts cap height within the 8px line. 1.0 matches Mojangles' 7px caps.",
        min = 0.7F,
        max = 1.2F,
        step = 0.05F,
        category = CATEGORY_FONT
    )
    public static float fontScale = 0.95F;

    @Switch(
        title = "Antialiasing",
        description = "Smooth glyph edges in the generated sheet. Turn off for pixel fonts.",
        category = CATEGORY_FONT
    )
    public static boolean antialias = true;

    @Dropdown(
        title = "Fallback",
        description = "Font used for codepoints missing from the main font.",
        options = {"None", "System SansSerif", "System Monospaced"},
        category = CATEGORY_FONT
    )
    public static String fallback = "System SansSerif";

    @Switch(
        title = "Apply to OneConfig UI",
        description = "Also register the font with OneConfig's PolyCompose renderer, so the mod menu and HUD match.",
        category = CATEGORY_COMPAT
    )
    public static boolean applyToPolyCompose = true;

    @Switch(
        title = "Debug logging",
        description = "Log sheet generation, cache keys and resource overrides.",
        category = CATEGORY_DEBUG
    )
    public static boolean debug = false;

    private FontrConfig() {
        super("fontr.json", "/assets/fontr/icon.png", "Fontr", Config.Category.VISUALS);
    }

    public void register() {
        preload();
    }

    /** Copies the page's values into the config-independent settings object. */
    public static void refresh() {
        FontrSettings.enabled = enabled;
        FontrSettings.fontPath = fontPath == null || fontPath.trim().isEmpty()
            ? "Fontr/font.ttf" : fontPath.trim();
        FontrSettings.resolution = resolution != null && resolution.startsWith("8192") ? 8192 : 4096;
        FontrSettings.fontSize = Math.max(8, fontSize);
        FontrSettings.fontScale = fontScale;
        FontrSettings.antialias = antialias;
        FontrSettings.fallback = fallback;
        FontrSettings.applyToPolyCompose = applyToPolyCompose;
        FontrSettings.debug = debug;
    }
}
