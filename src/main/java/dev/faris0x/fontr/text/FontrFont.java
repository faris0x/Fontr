package dev.faris0x.fontr.text;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * The loaded TTF/OTF and its metrics, expressed in Minecraft's 8px font space.
 *
 * <p>Cap height is normalised to {@code (fontSize - 1) * fontScale} logical
 * pixels, so generated glyphs line up with vanilla's 8px line and baseline at
 * 7px.</p>
 */
public final class FontrFont {
    /** Vanilla's text line is 8px tall with the baseline at y=7. */
    public static final int LINE = 8;
    public static final int BASELINE = 7;

    private final Font base;
    private final Font fallback;
    private final FontRenderContext frc;
    private final boolean antialias;
    private final float capLogical;

    private FontrFont(Font base, Font fallback, FontRenderContext frc, boolean antialias,
                      float capLogical) {
        this.base = base;
        this.fallback = fallback;
        this.frc = frc;
        this.antialias = antialias;
        this.capLogical = capLogical;
    }

    public static FontrFont load(Path path, int fontSize, float fontScale, boolean antialias,
                                 String fallbackName) throws Exception {
        Font created = Font.createFont(Font.TRUETYPE_FONT, path.toFile());
        FontRenderContext frc = new FontRenderContext(null, true, false);
        float capLogical = Math.min(7.0F, Math.max(4.0F, (fontSize - 1) * fontScale));

        Font base = derive(created, capLogical, frc);
        Font fallback = null;
        if (fallbackName != null) {
            Font candidate = new Font(fallbackName, Font.PLAIN, 1).deriveFont(100.0F);
            if (canDisplayFamily(candidate)) {
                fallback = derive(candidate, capLogical, frc);
            }
        }
        return new FontrFont(base, fallback, frc, antialias, capLogical);
    }

    private static Font derive(Font font, float capLogical, FontRenderContext frc) {
        Font probe = font.deriveFont(Font.PLAIN, 100.0F);
        float capRatio = 0.7F;
        try {
            GlyphVector vector = probe.createGlyphVector(frc, "H");
            java.awt.Rectangle bounds = vector.getPixelBounds(frc, 0, 0);
            if (bounds.height > 0) {
                capRatio = bounds.height / 100.0F;
            }
        } catch (Throwable ignored) {
        }
        float size = Math.max(1.0F, capLogical / capRatio);
        return probe.deriveFont(Font.PLAIN, size);
    }

    private static boolean canDisplayFamily(Font candidate) {
        try {
            return candidate.canDisplay('A');
        } catch (Throwable t) {
            return false;
        }
    }

    public float capLogical() {
        return capLogical;
    }

    public boolean canDisplay(int codepoint) {
        if (!Character.isValidCodePoint(codepoint)) {
            return false;
        }
        if (base.canDisplay(codepoint)) {
            return true;
        }
        return fallback != null && fallback.canDisplay(codepoint);
    }

    /** Advance in Minecraft pixels (1..8), matching vanilla's integer widths. */
    public int advance(int codepoint) {
        int advance = 4;
        try {
            char[] chars = chars(codepoint);
            GlyphVector vector = fontFor(codepoint).layoutGlyphVector(frc, chars, 0, chars.length,
                Font.LAYOUT_LEFT_TO_RIGHT);
            advance = (int) Math.round(vector.getGlyphPosition(vector.getNumGlyphs()).getX());
        } catch (Throwable ignored) {
        }
        return Math.max(1, Math.min(8, advance));
    }

    /** True if at least one renderable codepoint in the block has a glyph. */
    public boolean hasCoverage(int block) {
        int start = block << 8;
        for (int codepoint = start; codepoint < start + 256; codepoint++) {
            if (!renderable(codepoint)) {
                continue;
            }
            if (canDisplay(codepoint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean renderable(int codepoint) {
        if (codepoint < 0x20 || (codepoint >= 0x7F && codepoint < 0xA0)) {
            return false;
        }
        if (codepoint >= 0xD800 && codepoint <= 0xDFFF) {
            return false;
        }
        int type = Character.getType(codepoint);
        return type != Character.UNASSIGNED && type != Character.CONTROL && type != Character.FORMAT;
    }

    private Font fontFor(int codepoint) {
        if (base.canDisplay(codepoint) || fallback == null || !fallback.canDisplay(codepoint)) {
            return base;
        }
        return fallback;
    }

    /**
     * Draws one glyph with its pen at {@code x} and baseline at {@code y}.
     *
     * @param scale      sheet scale (1 = 8px cells, 32 = 256px cells, ...)
     * @param maxAdvance maximum ink width in sheet pixels
     */
    public void drawGlyph(Graphics2D graphics, int codepoint, float scale, float x, float y,
                          float maxAdvance) {
        Font chosen = fontFor(codepoint);
        Font font = chosen.deriveFont(Font.PLAIN, chosen.getSize2D() * scale);
        graphics.setFont(font);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            antialias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            antialias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        GlyphVector vector;
        try {
            char[] chars = chars(codepoint);
            vector = font.layoutGlyphVector(frc, chars, 0, chars.length, Font.LAYOUT_LEFT_TO_RIGHT);
        } catch (Throwable t) {
            return;
        }

        float scaleX = 1.0F;
        try {
            java.awt.Rectangle bounds = vector.getPixelBounds(frc, x, y);
            float inkRight = bounds.x + bounds.width;
            if (inkRight - x > maxAdvance && inkRight > x) {
                scaleX = Math.max(0.2F, maxAdvance / (inkRight - x));
            }
        } catch (Throwable ignored) {
        }

        Shape clip = graphics.getClip();
        java.awt.geom.AffineTransform old = graphics.getTransform();
        graphics.translate(x, y);
        if (scaleX != 1.0F) {
            graphics.scale(scaleX, 1.0F);
        }
        graphics.drawGlyphVector(vector, 0.0F, 0.0F);
        graphics.setTransform(old);
        if (clip != null) {
            graphics.setClip(clip);
        }
    }

    private static char[] chars(int codepoint) {
        String value = new String(Character.toChars(codepoint));
        return value.toCharArray();
    }
}
