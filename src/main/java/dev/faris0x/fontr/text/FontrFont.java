package dev.faris0x.fontr.text;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The loaded TTF/OTF faces and their metrics, expressed in Minecraft's 8px font
 * space. Cap height is normalised to the requested line, then shrunk if the
 * font's own ascent + descent would overrun the 8px cell.
 */
public final class FontrFont {
    public enum Style {
        REGULAR, BOLD, ITALIC, BOLD_ITALIC
    }

    /** Vanilla's text line is 8px tall. */
    public static final int LINE = 8;

    private final Font base;
    private final Font bold;
    private final Font italic;
    private final Font boldItalic;
    private final Font fallback;
    private final FontRenderContext frc;
    private final boolean antialias;
    private final float baseline;

    private FontrFont(Font base, Font bold, Font italic, Font boldItalic, Font fallback,
                      FontRenderContext frc, boolean antialias, float baseline) {
        this.base = base;
        this.bold = bold;
        this.italic = italic;
        this.boldItalic = boldItalic;
        this.fallback = fallback;
        this.frc = frc;
        this.antialias = antialias;
        this.baseline = baseline;
    }

    public static FontrFont load(Path regular, Path boldPath, Path italicPath, Path boldItalicPath,
                                 int fontSize, float fontScale, boolean antialias,
                                 String fallbackName) throws Exception {
        Font created = read(regular);
        FontRenderContext frc = new FontRenderContext(null, true, false);
        float user = Math.max(0.5F, Math.min(1.5F, fontScale));

        // Measure at 100px so integer pixel rounding does not distort the ratio,
        // then pick the size whose ascent + descent fits the 8px line.
        Font probe = created.deriveFont(Font.PLAIN, 100.0F);
        float[] metrics = measure(probe, frc);
        float total = metrics[0] + metrics[1];
        float size = total > 0.0F ? (float) LINE * 100.0F / total : (float) Math.max(4, fontSize - 1);
        size = Math.max(4.0F, size * user);
        Font base = created.deriveFont(Font.PLAIN, size);

        float ascent = (metrics[0] / 100.0F) * size;
        float descent = (metrics[1] / 100.0F) * size;
        float baseline = Math.max(0.0F, Math.min(7.0F, ascent));

        Font boldFace = face(boldPath, regular, base);
        Font italicFace = face(italicPath, regular, base);
        Font boldItalicFace = face(boldItalicPath, regular, base);
        // No face files found: bake AWT's synthetic face so style sheets still
        // exist (real faces are used when available).
        Font derivedBold = boldFace != null ? boldFace : base.deriveFont(Font.BOLD);
        Font derivedItalic = italicFace != null ? italicFace : base.deriveFont(Font.ITALIC);
        Font derivedBoldItalic = boldItalicFace != null ? boldItalicFace
            : base.deriveFont(Font.BOLD | Font.ITALIC);
        Font fallback = null;
        if (fallbackName != null) {
            try {
                Font candidate = new Font(fallbackName, Font.PLAIN, 1).deriveFont(base.getSize2D());
                if (candidate.canDisplay('A')) {
                    fallback = candidate;
                }
            } catch (Throwable ignored) {
            }
        }
        return new FontrFont(base, derivedBold, derivedItalic, derivedBoldItalic, fallback,
            frc, antialias, baseline);
    }

    private static Font read(Path path) throws Exception {
        return Font.createFont(Font.TRUETYPE_FONT, path.toFile());
    }

    private static Font face(Path path, Path regular, Font sized) {
        if (path == null) {
            return null;
        }
        try {
            if (regular != null && path.toAbsolutePath().equals(regular.toAbsolutePath())) {
                return null;
            }
            return read(path).deriveFont(Font.PLAIN, sized.getSize2D());
        } catch (Throwable t) {
            return null;
        }
    }

    private static Font derive(Font font, float capTarget, FontRenderContext frc) {
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
        float size = Math.max(1.0F, capTarget / capRatio);
        return probe.deriveFont(Font.PLAIN, size);
    }

    /** Returns {maxAscent, maxDescent} over the glyphs actually rendered. */
    private static float[] measure(Font font, FontRenderContext frc) {
        float ascent = 0.0F;
        float descent = 0.0F;
        Set<Integer> codepoints = new LinkedHashSet<Integer>();
        for (int cp = (int) '0'; cp <= (int) '9'; cp++) {
            codepoints.add(cp);
        }
        for (int cp = (int) 'A'; cp <= (int) 'Z'; cp++) {
            codepoints.add(cp);
        }
        for (int cp = (int) 'a'; cp <= (int) 'z'; cp++) {
            codepoints.add(cp);
        }
        codepoints.add((int) ',');
        codepoints.add((int) ';');
        codepoints.add((int) 'g');
        codepoints.add((int) 'j');
        codepoints.add((int) 'p');
        codepoints.add((int) 'q');
        codepoints.add((int) 'y');

        for (int cp : codepoints) {
            if (!font.canDisplay(cp)) {
                continue;
            }
            try {
                char[] chars = chars(cp);
                GlyphVector vector = font.layoutGlyphVector(frc, chars, 0, chars.length,
                    Font.LAYOUT_LEFT_TO_RIGHT);
                java.awt.Rectangle bounds = vector.getPixelBounds(frc, 0, 0);
                if (bounds.height <= 0) {
                    continue;
                }
                ascent = Math.max(ascent, -(float) bounds.y);
                descent = Math.max(descent, (float) (bounds.y + bounds.height));
            } catch (Throwable ignored) {
            }
        }
        return new float[]{ascent, descent};
    }

    public float baseline() {
        return baseline;
    }

    public float pointSize() {
        return base.getSize2D();
    }

    public FontRenderContext frc() {
        return frc;
    }

    /** The face used for this codepoint in the regular style (base or fallback). */
    public Font fontForCodepoint(int codepoint) {
        return faceFor(codepoint, Style.REGULAR);
    }

    public boolean hasStyle(Style style) {
        if (style == Style.REGULAR) {
            return true;
        }
        return face(style) != null;
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

    /** Advance in Minecraft pixels (1..8) from the regular face. */
    public int advance(int codepoint) {
        int advance = 4;
        try {
            Font font = base.canDisplay(codepoint) || fallback == null || !fallback.canDisplay(codepoint)
                ? base : fallback;
            char[] chars = chars(codepoint);
            GlyphVector vector = font.layoutGlyphVector(frc, chars, 0, chars.length,
                Font.LAYOUT_LEFT_TO_RIGHT);
            advance = (int) Math.round(vector.getGlyphPosition(vector.getNumGlyphs()).getX());
        } catch (Throwable ignored) {
        }
        return Math.max(1, Math.min(8, advance));
    }

    public boolean hasCoverage(int block) {
        int start = block << 8;
        for (int codepoint = start; codepoint < start + 256; codepoint++) {
            if (renderable(codepoint) && canDisplay(codepoint)) {
                return true;
            }
        }
        return false;
    }

    private Font face(Style style) {
        switch (style) {
            case BOLD:
                return bold;
            case ITALIC:
                return italic;
            case BOLD_ITALIC:
                return boldItalic;
            default:
                return base;
        }
    }

    private Font faceFor(int codepoint, Style style) {
        Font styled = face(style);
        if (styled != null && styled.canDisplay(codepoint)) {
            return styled;
        }
        if (base.canDisplay(codepoint)) {
            return base;
        }
        if (fallback != null && fallback.canDisplay(codepoint)) {
            return fallback;
        }
        return base;
    }

    /**
     * Draws one glyph with its pen at {@code x} and baseline at {@code y}.
     * Any overflow is scaled down uniformly instead of being clipped.
     */
    public void drawGlyph(Graphics2D graphics, int codepoint, float scale, float x, float y,
                          float maxAdvance, float embolden, Style style,
                          float cellTop, float cellBottom, float squeezeFloor) {
        Font chosen = faceFor(codepoint, style);
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

        float squeeze = 1.0F;
        float shift = 0.0F;
        try {
            java.awt.Rectangle bounds = vector.getPixelBounds(frc, x, y);
            if (bounds.width > 0 && bounds.height > 0) {
                if (bounds.x < x) {
                    shift = x - bounds.x;
                }
                float inkRight = bounds.x + bounds.width + shift;
                if (maxAdvance > 0.0F && inkRight - x > maxAdvance) {
                    squeeze = Math.min(squeeze, maxAdvance / (inkRight - x));
                }
                float vertical = 1.0F;
                if (bounds.y < cellTop) {
                    vertical = Math.min(vertical, (y - cellTop) / Math.max(1.0F, y - bounds.y));
                }
                float inkBottom = bounds.y + bounds.height;
                if (inkBottom > cellBottom) {
                    vertical = Math.min(vertical, (cellBottom - y) / Math.max(1.0F, inkBottom - y));
                }
                if (vertical >= squeezeFloor) {
                    squeeze = Math.min(squeeze, vertical);
                }
            }
        } catch (Throwable ignored) {
        }
        squeeze = Math.max(0.2F, Math.min(1.0F, squeeze));

        Shape clip = graphics.getClip();
        java.awt.geom.AffineTransform old = graphics.getTransform();
        graphics.translate(x + shift, y);
        if (squeeze != 1.0F) {
            graphics.scale(squeeze, squeeze);
        }
        graphics.drawGlyphVector(vector, 0.0F, 0.0F);
        if (embolden > 0.0F) {
            java.awt.Stroke previous = graphics.getStroke();
            graphics.setStroke(new java.awt.BasicStroke(Math.max(0.2F, embolden * scale * squeeze),
                java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            graphics.draw(vector.getOutline(0.0F, 0.0F));
            graphics.setStroke(previous);
        }
        graphics.setTransform(old);
        if (clip != null) {
            graphics.setClip(clip);
        }
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

    private static char[] chars(int codepoint) {
        String value = new String(Character.toChars(codepoint));
        return value.toCharArray();
    }
}
