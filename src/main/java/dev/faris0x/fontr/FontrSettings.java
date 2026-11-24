package dev.faris0x.fontr;

/**
 * Config-independent view of Fontr's settings.
 *
 * <p>OneConfig writes into {@code FontrConfig}; that class copies its values
 * here. Keeping the values in a class that does not reference OneConfig lets the
 * font engine run on clients without OneConfig installed.</p>
 */
public final class FontrSettings {
    public static volatile boolean enabled = true;
    public static volatile String fontPath = "Fontr/font.ttf";
    public static volatile String boldFont = "";
    public static volatile String italicFont = "";
    public static volatile String boldItalicFont = "";
    public static volatile int resolution = 4096;
    public static volatile int styledResolution = 2048;
    public static volatile int fontSize = 8;
    public static volatile float fontScale = 1.0F;
    public static volatile float letterSpacing = 0.0F;
    public static volatile float embolden = 0.0F;
    public static volatile float scriptScale = 1.0F;
    public static volatile boolean antialias = true;
    public static volatile boolean mipmaps = true;
    public static volatile boolean trueStyles = true;
    public static volatile boolean argentumStyles = true;
    public static volatile boolean arabicShaping = true;
    public static volatile String fallback = "System SansSerif";
    public static volatile boolean applyToPolyCompose = true;
    public static volatile boolean debug = false;

    /** Bump when the sheet builder changes so old caches are ignored. */
    private static final int SHEET_VERSION = 5;

    private FontrSettings() {
    }

    /** Everything that changes the generated sheets. */
    public static String sheetKey() {
        return SHEET_VERSION + "|" + fontPath + '|' + boldFont + '|' + italicFont + '|'
            + boldItalicFont + '|' + resolution + '|' + styledResolution + '|'
            + fontSize + '|' + fontScale + '|' + letterSpacing + '|' + embolden + '|' + scriptScale + '|'
            + antialias + '|' + trueStyles + '|' + fallback;
    }

    public static String fontName() {
        if ("System Monospaced".equalsIgnoreCase(fallback)) {
            return java.awt.Font.MONOSPACED;
        }
        if ("None".equalsIgnoreCase(fallback)) {
            return null;
        }
        return java.awt.Font.SANS_SERIF;
    }
}
