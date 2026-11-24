// SPDX-License-Identifier: GPL-3.0-only

package dev.faris0x.fontr.compat;

import dev.faris0x.fontr.FontrEngine;
import dev.rdh.argentum.impl.render.gui.font.FontSet;
import net.minecraft.resource.Identifier;

/**
 * Optional bridge for Argentum's batched text. Keeps style-specific
 * {@link FontSet}s that read Fontr's generated sheets. No Argentum code is
 * bundled or modified on disk; all calls target the runtime classes.
 */
public final class ArgentumStyles {
    public static final int BOLD = 0b00010;
    public static final int ITALIC = 0b10000;

    private static volatile float[] widths;
    private static volatile FontSet bold;
    private static volatile FontSet italic;
    private static volatile FontSet boldItalic;

    private ArgentumStyles() {
    }

    public static synchronized void setWidths(float[] source) {
        if (source == null || source == widths) {
            return;
        }
        widths = source;
        invalidate();
    }

    public static synchronized void invalidate() {
        bold = null;
        italic = null;
        boldItalic = null;
    }

    /** Styled font set for this style, or null when the style is unavailable. */
    public static FontSet setFor(int style) {
        if (!FontrEngine.INSTANCE.argentumStylesEnabled() || widths == null) {
            return null;
        }
        boolean useBold = (style & BOLD) != 0;
        boolean useItalic = (style & ITALIC) != 0;
        if (!useBold && !useItalic) {
            return null;
        }
        if (!FontrEngine.INSTANCE.styleAvailable(useBold, useItalic)) {
            return null;
        }
        try {
            if (useBold && useItalic) {
                if (boldItalic == null) {
                    boldItalic = create("textures/font/fontr_ascii_bold_italic.png");
                }
                return boldItalic;
            }
            if (useBold) {
                if (bold == null) {
                    bold = create("textures/font/fontr_ascii_bold.png");
                }
                return bold;
            }
            if (italic == null) {
                italic = create("textures/font/fontr_ascii_italic.png");
            }
            return italic;
        } catch (Throwable t) {
            return null;
        }
    }

    private static FontSet create(String path) {
        FontSet set = new FontSet(widths);
        set.setSheet(new Identifier("minecraft", path));
        return set;
    }

    public static boolean trueBold(int style) {
        if ((style & BOLD) == 0) {
            return false;
        }
        return FontrEngine.INSTANCE.styleAvailable(true, (style & ITALIC) != 0);
    }

    public static boolean trueItalic(int style) {
        if ((style & ITALIC) == 0) {
            return false;
        }
        return FontrEngine.INSTANCE.styleAvailable((style & BOLD) != 0, true);
    }
}
