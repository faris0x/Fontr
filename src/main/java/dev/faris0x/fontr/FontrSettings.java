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
    public static volatile int resolution = 4096;
    public static volatile int fontSize = 8;
    public static volatile float fontScale = 0.95F;
    public static volatile boolean antialias = true;
    public static volatile String fallback = "System SansSerif";
    public static volatile boolean applyToPolyCompose = true;
    public static volatile boolean debug = false;

    private FontrSettings() {
    }

    /** Everything that changes the generated sheets. */
    public static String sheetKey() {
        return fontPath + '|' + resolution + '|' + fontSize + '|' + fontScale + '|'
            + antialias + '|' + fallback;
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
