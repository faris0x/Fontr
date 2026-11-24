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

    @File(
        title = "Bold font (optional)",
        description = "Leave empty to auto-detect a bold face next to the regular font.",
        types = {"ttf", "otf"},
        filterName = "Font files",
        category = CATEGORY_FONT
    )
    public static String boldFont = "";

    @File(
        title = "Italic font (optional)",
        description = "Leave empty to auto-detect an italic face next to the regular font.",
        types = {"ttf", "otf"},
        filterName = "Font files",
        category = CATEGORY_FONT
    )
    public static String italicFont = "";

    @File(
        title = "Bold-italic font (optional)",
        description = "Leave empty to auto-detect a bold-italic face next to the regular font.",
        types = {"ttf", "otf"},
        filterName = "Font files",
        category = CATEGORY_FONT
    )
    public static String boldItalicFont = "";

    @Dropdown(
        title = "Sheet resolution",
        description = "Resolution of the generated ASCII sheet. 4096 is 32x supersampling (~85 MB VRAM); 8192 is 64x (~340 MB VRAM).",
        options = {"4096 (recommended)", "8192 (maximum)"},
        category = CATEGORY_FONT
    )
    public static String resolution = "4096 (recommended)";

    @Slider(
        title = "Glyph scale",
        description = "Scales the fitted glyph size. 1.0 is the largest size that fits the 8px line without clipping; higher values may clip." ,
        min = 0.8F,
        max = 1.3F,
        step = 0.05F,
        category = CATEGORY_FONT
    )
    public static float fontScale = 1.0F;

    @Slider(
        title = "Letter spacing",
        description = "Extra pixels between glyphs. Negative values tighten text; 0 uses the font's natural advance.",
        min = -2.0F,
        max = 2.0F,
        step = 0.25F,
        category = CATEGORY_FONT
    )
    public static float letterSpacing = 0.0F;

    @Slider(
        title = "Embolden",
        description = "Strokes glyph outlines to thicken thin fonts at 8px. 0 disables.",
        min = 0.0F,
        max = 0.5F,
        step = 0.05F,
        category = CATEGORY_FONT
    )
    public static float embolden = 0.0F;

    @Slider(
        title = "Script scale",
        description = "Scales unicode blocks (Arabic, CJK, ...) relative to their fitted size. 1.0 is the default fit.",
        min = 0.8F,
        max = 2.0F,
        step = 0.05F,
        category = CATEGORY_FONT
    )
    public static float scriptScale = 1.0F;

    @Slider(
        title = "Script offset",
        description = "Moves unicode blocks (Arabic/CJK/...) up or down inside the line, in pixels.",
        min = -3.0F,
        max = 3.0F,
        step = 0.25F,
        category = CATEGORY_FONT
    )
    public static float scriptOffset = 0.0F;

    @Slider(
        title = "Script height",
        description = "Stretches unicode blocks vertically around their baseline.",
        min = 0.8F,
        max = 1.5F,
        step = 0.05F,
        category = CATEGORY_FONT
    )
    public static float scriptHeight = 1.0F;

    @Switch(
        title = "Antialiasing",
        description = "Smooth glyph edges in the generated sheet. Turn off for pixel fonts.",
        category = CATEGORY_FONT
    )
    public static boolean antialias = true;

    @Dropdown(
        title = "Styled sheet resolution",
        description = "Resolution for the bold/italic sheets. 2048 keeps four sheets within a reasonable VRAM budget.",
        options = {"2048 (recommended)", "4096 (maximum)"},
        category = CATEGORY_FONT
    )
    public static String styledResolution = "2048 (recommended)";

    @Switch(
        title = "Use real bold/italic faces",
        description = "Generate bold/italic sheets from the font's own faces instead of vanilla's double-draw.",
        category = CATEGORY_FONT
    )
    public static boolean trueStyles = true;

    @Dropdown(
        title = "Fallback",
        description = "Font used for codepoints missing from the main font.",
        options = {"None", "System SansSerif", "System Monospaced"},
        category = CATEGORY_FONT
    )
    public static String fallback = "System SansSerif";

    @Switch(
        title = "Mipmaps",
        description = "Generate a mip chain for font textures. Smoother while scrolling; turn off to compare sharpness.",
        category = CATEGORY_FONT
    )
    public static boolean mipmaps = true;

    @Switch(
        title = "Arabic shaping + RTL",
        description = "Joins Arabic letters and reorders right-to-left runs before Minecraft draws them.",
        category = CATEGORY_COMPAT
    )
    public static boolean arabicShaping = true;

    @Switch(
        title = "Real styles in Argentum text",
        description = "Use real bold/italic sheets in Argentum's batched chat and nametags (requires faces).",
        category = CATEGORY_COMPAT
    )
    public static boolean argentumStyles = true;

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
        FontrSettings.boldFont = boldFont == null ? "" : boldFont.trim();
        FontrSettings.italicFont = italicFont == null ? "" : italicFont.trim();
        FontrSettings.boldItalicFont = boldItalicFont == null ? "" : boldItalicFont.trim();
        FontrSettings.resolution = resolution != null && resolution.startsWith("8192") ? 8192 : 4096;
        FontrSettings.styledResolution = styledResolution != null && styledResolution.startsWith("4096")
            ? 4096 : 2048;
        FontrSettings.trueStyles = trueStyles;
        FontrSettings.argentumStyles = argentumStyles;
        FontrSettings.fontSize = 8;
        FontrSettings.fontScale = fontScale;
        FontrSettings.letterSpacing = letterSpacing;
        FontrSettings.embolden = embolden;
        FontrSettings.scriptScale = scriptScale;
        FontrSettings.scriptOffset = scriptOffset;
        FontrSettings.scriptHeight = scriptHeight;
        FontrSettings.antialias = antialias;
        FontrSettings.mipmaps = mipmaps;
        FontrSettings.arabicShaping = arabicShaping;
        FontrSettings.fallback = fallback;
        FontrSettings.applyToPolyCompose = applyToPolyCompose;
        FontrSettings.debug = debug;
    }
}
