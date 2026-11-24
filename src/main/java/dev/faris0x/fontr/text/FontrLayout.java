package dev.faris0x.fontr.text;

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.font.GlyphVector;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable laid-out text: visual glyphs, logical glyphs, widths and colour spans.
 */
public final class FontrLayout {
    private static final int[] COLORS = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
        0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
        0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    public final Glyph[] visual;
    public final Glyph[] logical;
    public final int advance;
    public final int baseline;
    public final Format[] formats;
    public final String original;

    private FontrLayout(Glyph[] visual, Glyph[] logical, int advance, int baseline,
                        Format[] formats, String original) {
        this.visual = visual;
        this.logical = logical;
        this.advance = advance;
        this.baseline = baseline;
        this.formats = formats;
        this.original = original;
    }

    public Format formatFor(Glyph glyph) {
        if (glyph.originalIndex < 0) {
            return Format.DEFAULT;
        }
        Format format = glyph.originalIndex < formats.length ? formats[glyph.originalIndex] : null;
        return format == null ? Format.DEFAULT : format;
    }

    public static FontrLayout build(String input, FontrFont font) {
        if (input == null || input.isEmpty()) {
            return new FontrLayout(new Glyph[0], new Glyph[0], 0, font.baseline,
                                   new Format[0], "");
        }

        int length = input.length();
        char[] stripped = new char[length];
        int[] strippedToOriginal = new int[length];
        Format[] formats = new Format[length];
        Format current = Format.DEFAULT;
        int count = 0;

        for (int i = 0; i < length; ) {
            char c = input.charAt(i);
            formats[i] = current;
            if (c == '\u00A7' && i + 1 < length) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                current = applyCode(current, code);
                i += 2;
                continue;
            }
            stripped[count] = c;
            strippedToOriginal[count] = i;
            count++;
            i++;
        }

        String plain = new String(stripped, 0, count);
        FontrFont.ShapedText shaped = font.shape(plain);
        String text = shaped.text;
        int[] shapedToOriginal = new int[text.length()];
        for (int i = 0; i < shapedToOriginal.length; i++) {
            int strippedIndex = shaped.map[i];
            if (strippedIndex < 0) strippedIndex = 0;
            if (strippedIndex >= count) strippedIndex = count - 1;
            shapedToOriginal[i] = count == 0 ? 0 : strippedToOriginal[strippedIndex];
        }

        List<Glyph> visual = new ArrayList<Glyph>();
        char[] chars = text.toCharArray();
        int pen = 0;

        try {
            Bidi bidi = new Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
            if (!Bidi.requiresBidi(chars, 0, chars.length)) {
                pen = layoutRun(chars, 0, chars.length, false, pen, shapedToOriginal, formats, font, visual);
            } else {
                int runCount = bidi.getRunCount();
                byte[] levels = new byte[runCount];
                Integer[] runs = new Integer[runCount];
                for (int i = 0; i < runCount; i++) {
                    levels[i] = (byte) bidi.getRunLevel(i);
                    runs[i] = i;
                }
                Bidi.reorderVisually(levels, 0, runs, 0, runCount);

                for (Integer logicalIndex : runs) {
                    int start = bidi.getRunStart(logicalIndex);
                    int limit = bidi.getRunLimit(logicalIndex);
                    boolean rtl = (bidi.getRunLevel(logicalIndex) & 1) == 1;
                    pen = layoutRun(chars, start, limit, rtl, pen, shapedToOriginal, formats, font, visual);
                }
            }
        } catch (Throwable ignored) {
            visual.clear();
            pen = layoutRun(chars, 0, chars.length, false, 0, shapedToOriginal, formats, font, visual);
        }

        Glyph[] visualArray = visual.toArray(new Glyph[0]);
        Glyph[] logicalArray = visualArray.clone();
        Arrays.sort(logicalArray);
        return new FontrLayout(visualArray, logicalArray, pen, font.baseline, formats, input);
    }

    private static int layoutRun(char[] text, int start, int limit, boolean rtl, int pen,
                                 int[] shapedToOriginal, Format[] formats, FontrFont font,
                                 List<Glyph> out) {
        int index = start;
        while (index < limit) {
            char first = text[index];
            Format format = formatAt(shapedToOriginal, formats, index);
            int style = format.style();
            Font runFont = font.fontFor(first, style);

            int end = index + 1;
            while (end < limit) {
                char next = text[end];
                Format nextFormat = formatAt(shapedToOriginal, formats, end);
                int nextStyle = nextFormat.style();
                Font nextFont = font.fontFor(next, nextStyle);
                if (nextStyle != style || !nextFont.equals(runFont)) {
                    break;
                }
                end++;
            }

            GlyphVector vector;
            try {
                vector = font.layout(runFont, text, index, end);
            } catch (Throwable t) {
                index = end;
                continue;
            }

            int glyphCount = vector.getNumGlyphs();
            double runWidth = vector.getGlyphPosition(glyphCount).getX();

            for (int i = 0; i < glyphCount; i++) {
                int shapedIndex = index + vector.getGlyphCharIndex(i);
                int originalIndex = shapedIndex >= 0 && shapedIndex < shapedToOriginal.length
                    ? shapedToOriginal[shapedIndex] : 0;
                int styleAtGlyph = formatAt(shapedToOriginal, formats, shapedIndex).style();

                Rectangle bounds = vector.getGlyphPixelBounds(i, font.frc(), 0, 0);
                double position = vector.getGlyphPosition(i).getX();
                double nextPosition = vector.getGlyphPosition(i + 1).getX();
                int glyphAdvance = (int) Math.round(nextPosition - position);
                float x = rtl
                    ? (float) (pen + (runWidth - (position + glyphAdvance)))
                    : (float) (pen + position);
                // The rasterised bitmap starts at bounds.x relative to the glyph
                // origin, not at the origin itself. Without this every glyph is
                // drawn shifted left by its left side bearing.
                x += bounds.x;

                out.add(new Glyph(originalIndex, runFont, styleAtGlyph, vector.getGlyphCode(i),
                                  x, bounds.y, bounds.width, bounds.height, glyphAdvance));
            }

            pen += (int) Math.round(runWidth);
            index = end;
        }
        return pen;
    }

    private static Format formatAt(int[] shapedToOriginal, Format[] formats, int shapedIndex) {
        if (shapedIndex < 0 || shapedIndex >= shapedToOriginal.length || formats.length == 0) {
            return Format.DEFAULT;
        }
        int original = shapedToOriginal[shapedIndex];
        if (original < 0) original = 0;
        if (original >= formats.length) original = formats.length - 1;
        Format format = formats[original];
        return format == null ? Format.DEFAULT : format;
    }

    private static Format applyCode(Format base, char code) {
        Format next = base.copy();
        if (code >= '0' && code <= '9') {
            next.color = COLORS[code - '0'];
            next.bold = false;
            next.italic = false;
            next.underline = false;
            next.strikethrough = false;
            next.obfuscated = false;
        } else if (code >= 'a' && code <= 'f') {
            next.color = COLORS[code - 'a' + 10];
            next.bold = false;
            next.italic = false;
            next.underline = false;
            next.strikethrough = false;
            next.obfuscated = false;
        } else if (code == 'l') {
            next.bold = true;
        } else if (code == 'm') {
            next.strikethrough = true;
        } else if (code == 'n') {
            next.underline = true;
        } else if (code == 'o') {
            next.italic = true;
        } else if (code == 'k') {
            next.obfuscated = true;
        } else if (code == 'r') {
            return Format.DEFAULT;
        }
        return next;
    }

    public String trimTo(int width, boolean reverse) {
        if (original.isEmpty()) {
            return "";
        }
        if (reverse) {
            int accumulated = 0;
            int cut = original.length();
            for (int i = logical.length - 1; i >= 0; i--) {
                Glyph glyph = logical[i];
                if (accumulated + glyph.advance > width) {
                    break;
                }
                accumulated += glyph.advance;
                cut = Math.min(cut, glyph.originalIndex);
            }
            cut = safeBoundary(cut);
            return original.substring(cut);
        }

        int accumulated = 0;
        int cut = 0;
        for (Glyph glyph : logical) {
            if (accumulated + glyph.advance > width) {
                break;
            }
            accumulated += glyph.advance;
            cut = Math.max(cut, glyph.originalIndex + 1);
        }
        cut = safeBoundary(cut);
        return original.substring(0, cut);
    }

    public int indexAtWidth(int width) {
        int accumulated = 0;
        int cut = 0;
        for (Glyph glyph : logical) {
            if (accumulated + glyph.advance > width) {
                break;
            }
            accumulated += glyph.advance;
            cut = Math.max(cut, glyph.originalIndex + 1);
        }
        cut = safeBoundary(cut);
        // insertLineBreaks() rebuilds the same string unless the cut is at a
        // whitespace boundary or the end. Returning a single-character cut
        // here caused an infinite recursion for words that did not fit.
        if (cut <= 0 || cut >= original.length()) {
            return original.length();
        }
        char before = original.charAt(cut - 1);
        char at = original.charAt(cut);
        if (before != ' ' && before != '\n' && at != ' ' && at != '\n') {
            int space = original.indexOf(' ', cut);
            int newline = original.indexOf('\n', cut);
            int next = space < 0 ? newline : (newline < 0 ? space : Math.min(space, newline));
            return next < 0 ? original.length() : next + 1;
        }
        return cut;
    }

    private int safeBoundary(int index) {
        if (index < 0) return 0;
        if (index > original.length()) return original.length();
        while (index > 0 && index < original.length() && original.charAt(index - 1) == '\u00A7') {
            index++;
        }
        if (index > 0 && index < original.length() && original.charAt(index) == '\u00A7') {
            index--;
        }
        return index;
    }

    public static final class Glyph implements Comparable<Glyph> {
        public final int originalIndex;
        public final Font font;
        public final int style;
        public final int glyphCode;
        public final float x;
        public final int y;
        public final int width;
        public final int height;
        public final int advance;

        Glyph(int originalIndex, Font font, int style, int glyphCode, float x, int y,
              int width, int height, int advance) {
            this.originalIndex = originalIndex;
            this.font = font;
            this.style = style;
            this.glyphCode = glyphCode;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.advance = advance;
        }

        @Override
        public int compareTo(Glyph other) {
            return Integer.compare(originalIndex, other.originalIndex);
        }
    }

    public static final class Format {
        public static final Format DEFAULT = new Format(-1, false, false, false, false, false);

        public int color;
        public boolean bold;
        public boolean italic;
        public boolean underline;
        public boolean strikethrough;
        public boolean obfuscated;

        Format(int color, boolean bold, boolean italic, boolean underline,
               boolean strikethrough, boolean obfuscated) {
            this.color = color;
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
            this.strikethrough = strikethrough;
            this.obfuscated = obfuscated;
        }

        Format copy() {
            return new Format(color, bold, italic, underline, strikethrough, obfuscated);
        }

        int style() {
            return (bold ? Font.BOLD : 0) | (italic ? Font.ITALIC : 0);
        }
    }
}
