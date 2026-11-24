package dev.faris0x.fontr.text;

import dev.faris0x.fontr.text.FontrFont.Style;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds vanilla-shaped font sheets from a TTF.
 *
 * <p>The ASCII sheet uses Minecraft's legacy cell grid and full natural glyph
 * ink (vanilla draws one pixel less than the width table says). Unicode pages
 * stay at 256x256, are generated lazily per covered block, and are fitted
 * per block so scripts with taller glyphs (Arabic, CJK, ...) use the full cell
 * instead of being clipped by the Latin line.</p>
 */
public final class FontrSheets {
    private static final Logger LOGGER = LogManager.getLogger("Fontr");
    private static final int UNICODE_PAGE = 256;
    private static final int UNICODE_CELL = 16;

    /** Reads a resource from the packs underneath Fontr. */
    public interface BaseProvider {
        InputStream open(String path);
    }

    private static final class BlockFit {
        final float scale;
        final float baseline;
        final int[] advances = new int[256];

        BlockFit(float scale, float baseline) {
            this.scale = scale;
            this.baseline = baseline;
        }
    }

    private final FontrFont font;
    private final int resolution;
    private final int styledResolution;
    private final boolean trueStyles;
    private final Path cacheDir;
    private final BaseProvider base;
    private final float letterSpacing;
    private final float embolden;
    private final float scriptScale;
    private final float scriptOffset;
    private final float scriptHeight;
    private final boolean debug;

    private final Map<Style, byte[]> styledPng = new HashMap<Style, byte[]>();
    private final Map<Integer, BlockFit> blockFits = new HashMap<Integer, BlockFit>();
    private byte[] glyphSizes;
    private final Map<Integer, byte[]> unicodePng = new HashMap<Integer, byte[]>();

    public FontrSheets(FontrFont font, int resolution, int styledResolution, boolean trueStyles,
                       Path cacheDir, BaseProvider base, float letterSpacing, float embolden,
                       float scriptScale, float scriptOffset, float scriptHeight, boolean debug) {
        this.font = font;
        this.resolution = resolution;
        this.styledResolution = styledResolution;
        this.trueStyles = trueStyles;
        this.cacheDir = cacheDir;
        this.base = base;
        this.letterSpacing = letterSpacing;
        this.embolden = embolden;
        this.scriptScale = Math.max(0.5F, Math.min(2.5F, scriptScale));
        this.scriptOffset = Math.max(-6.0F, Math.min(6.0F, scriptOffset));
        this.scriptHeight = Math.max(0.5F, Math.min(2.0F, scriptHeight));
        this.debug = debug;
        try {
            Files.createDirectories(cacheDir);
        } catch (Throwable t) {
            LOGGER.warn("[Fontr] Could not create cache directory " + cacheDir + ": " + t);
        }
    }

    public synchronized byte[] asciiPng(Style style) {
        Style actual = trueStyles && font.hasStyle(style) ? style : Style.REGULAR;
        byte[] cached = styledPng.get(actual);
        if (cached != null) {
            return cached;
        }
        String name = fileName(actual);
        byte[] onDisk = readCache(name);
        if (onDisk != null) {
            styledPng.put(actual, onDisk);
            return onDisk;
        }
        long started = System.currentTimeMillis();
        BufferedImage image = buildAscii(actual);
        byte[] png = encode(image, name);
        styledPng.put(actual, png);
        log("Generated " + (actual == Style.REGULAR ? resolution : styledResolution) + "px "
            + actual.name().toLowerCase(java.util.Locale.ROOT) + " ASCII sheet in "
            + (System.currentTimeMillis() - started) + "ms");
        return png;
    }

    public byte[] regularPng() {
        return asciiPng(Style.REGULAR);
    }

    public boolean hasStyle(Style style) {
        return trueStyles && font.hasStyle(style);
    }

    private static String fileName(Style style) {
        switch (style) {
            case BOLD:
                return "ascii_bold.png";
            case ITALIC:
                return "ascii_italic.png";
            case BOLD_ITALIC:
                return "ascii_bold_italic.png";
            default:
                return "ascii.png";
        }
    }

    public synchronized byte[] glyphSizes() {
        if (glyphSizes != null) {
            return glyphSizes;
        }
        byte[] cached = readCache("glyph_sizes.bin");
        if (cached != null && cached.length == 65536) {
            glyphSizes = cached;
            return cached;
        }

        byte[] result = new byte[65536];
        byte[] original = readBaseBytes("font/glyph_sizes.bin");
        if (original != null && original.length == 65536) {
            System.arraycopy(original, 0, result, 0, result.length);
        }
        for (int block = 0; block < 256; block++) {
            BlockFit fit = blockFit(block);
            if (fit == null) {
                continue;
            }
            for (int offset = 0; offset < 256; offset++) {
                int advance = fit.advances[offset];
                if (advance <= 0) {
                    continue;
                }
                result[(block << 8) + offset] = (byte) Math.min(15, advance * 2 - 1);
            }
        }
        glyphSizes = result;
        writeCache("glyph_sizes.bin", result);
        return result;
    }

    /** Returns the PNG for a unicode block, or null when the font has no glyphs there. */
    public synchronized byte[] unicodePng(int block) {
        if (unicodePng.containsKey(block)) {
            return unicodePng.get(block);
        }
        String name = String.format("unicode_page_%02x.png", block);
        BlockFit fit = blockFit(block);
        if (fit == null) {
            unicodePng.put(block, null);
            return null;
        }
        byte[] cached = readCache(name);
        if (cached != null) {
            unicodePng.put(block, cached);
            return cached;
        }
        byte[] png = encode(buildUnicode(block, fit), name);
        unicodePng.put(block, png);
        if (debug) {
            log("Generated unicode page " + String.format("%02x", block));
        }
        return png;
    }

    private BufferedImage buildAscii(Style style) {
        int sheetSize = style == Style.REGULAR ? resolution : styledResolution;
        int cell = sheetSize / 16;
        int scale = Math.max(1, cell / FontrFont.LINE);
        BufferedImage sheet = new BufferedImage(sheetSize, sheetSize, BufferedImage.TYPE_INT_ARGB);
        BufferedImage baseImage = readBaseImage("textures/font/ascii.png");
        Graphics2D graphics = sheet.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int baseline = Math.round(font.baseline() * scale);
        for (int index = 0; index < 256; index++) {
            int codepoint = LegacyFontGrid.codepoint(index);
            int x = (index & 15) * cell;
            int y = (index >> 4) * cell;
            if (codepoint == 0 || codepoint == 0x20) {
                continue;
            }
            if (!font.canDisplay(codepoint)) {
                copyCell(graphics, baseImage, index, x, y, cell);
                int copiedAdvance = copiedAdvance(sheet, x, y, cell);
                int markerX = x + copiedAdvance * scale - 1;
                int markerY = y + baseline;
                if (markerX >= x && markerX < x + cell && markerY >= y && markerY < y + cell) {
                    sheet.setRGB(markerX, markerY, 0x01FFFFFF);
                }
                continue;
            }

            int advance = advance(codepoint);
            graphics.setClip(x, y, cell, cell);
            font.drawGlyph(graphics, codepoint, scale, x, y + baseline,
                (float) (advance - 1) * scale, embolden, style, y, y + cell, 0.8F, 1.0F);
            graphics.setClip(null);

            // readFontTexture() derives advances from the rightmost non-transparent
            // column; mark the intended advance with an (invisible) alpha-1 pixel.
            int markerX = x + advance * scale - 1;
            int markerY = y + baseline;
            if (markerX >= x && markerX < x + cell && markerY >= y && markerY < y + cell) {
                sheet.setRGB(markerX, markerY, 0x01FFFFFF);
            }
        }
        graphics.dispose();
        whitenTransparent(sheet);
        return sheet;
    }

    private BufferedImage buildUnicode(int block, BlockFit fit) {
        BufferedImage page = new BufferedImage(UNICODE_PAGE, UNICODE_PAGE, BufferedImage.TYPE_INT_ARGB);
        BufferedImage baseImage = readBaseImage(String.format("textures/font/unicode_page_%02x.png", block));
        Graphics2D graphics = page.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setColor(Color.WHITE);
        if (baseImage != null) {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(baseImage, 0, 0, UNICODE_PAGE, UNICODE_PAGE, null);
        }

        int start = block << 8;
        for (int offset = 0; offset < 256; offset++) {
            int codepoint = start + offset;
            if (codepoint < 0x20 || !font.canDisplay(codepoint) || fit.advances[offset] <= 0) {
                continue;
            }
            int x = (offset & 15) * UNICODE_CELL;
            int y = (offset >> 4) * UNICODE_CELL;

            // Replace the whole cell, otherwise the pack's glyph can show through
            // around our narrower/shorter one.
            graphics.setClip(x, y, UNICODE_CELL, UNICODE_CELL);
            graphics.setColor(new Color(0, 0, 0, 0));
            graphics.fillRect(x, y, UNICODE_CELL, UNICODE_CELL);
            graphics.setColor(Color.WHITE);
            font.drawGlyph(graphics, codepoint, fit.scale, x, y + fit.baseline,
                (float) fit.advances[offset] * 2.0F, embolden, Style.REGULAR, y, y + UNICODE_CELL,
                0.5F, scriptHeight);
            graphics.setClip(null);
        }
        graphics.dispose();
        whitenTransparent(page);
        return page;
    }

    /** Advance in the 8px line, including vanilla's one-pixel gap. */
    private int advance(int codepoint) {
        return Math.max(2, Math.min(8, Math.round(font.advance(codepoint) + letterSpacing) + 1));
    }

    private BlockFit blockFit(int block) {
        if (blockFits.containsKey(block)) {
            return blockFits.get(block);
        }
        BlockFit fit = computeBlockFit(block);
        blockFits.put(block, fit);
        return fit;
    }

    private BlockFit computeBlockFit(int block) {
        int start = block << 8;
        if (block == 0) {
            // The unicode cell is 16px for an 8px line, so block 0 draws at 2x
            // to match normal (ASCII-sheet) text when force-unicode is enabled.
            BlockFit fit = new BlockFit(2.0F, font.baseline() * 2.0F);
            for (int offset = 0x20; offset < 0x100; offset++) {
                int codepoint = start + offset;
                if (renderable(codepoint) && font.canDisplay(codepoint)) {
                    fit.advances[offset] = codepoint == 0x20 ? 4 : Math.max(1, advance(codepoint) - 1);
                }
            }
            return fit;
        }

        java.util.List<Integer> codepoints = new java.util.ArrayList<Integer>();
        for (int offset = 0; offset < 256; offset++) {
            int codepoint = start + offset;
            if (renderable(codepoint) && font.canDisplay(codepoint)) {
                codepoints.add(codepoint);
            }
        }
        if (codepoints.isEmpty()) {
            return null;
        }

        FontRenderContext frc = font.frc();
        java.util.List<Integer> ascents = new java.util.ArrayList<Integer>();
        java.util.List<Integer> descents = new java.util.ArrayList<Integer>();
        for (int codepoint : codepoints) {
            if (isMark(codepoint)) {
                continue;
            }
            Font face = font.fontForCodepoint(codepoint).deriveFont(Font.PLAIN, 100.0F);
            try {
                char[] chars = chars(codepoint);
                GlyphVector vector = face.layoutGlyphVector(frc, chars, 0, chars.length,
                    Font.LAYOUT_LEFT_TO_RIGHT);
                Rectangle bounds = vector.getPixelBounds(frc, 0, 0);
                if (bounds.height > 0) {
                    ascents.add((int) -(float) bounds.y);
                    descents.add((int) (bounds.y + bounds.height));
                }
            } catch (Throwable ignored) {
            }
        }

        float pAscent = percentile(ascents, 0.875F);
        float pDescent = percentile(descents, 0.75F);

        float total = pAscent + pDescent;
        float blockSize = font.pointSize() * 2.0F;
        if (total > 0.0F) {
            blockSize = Math.min(blockSize, (UNICODE_CELL - 0.4F) * 100.0F / total);
        }
        blockSize *= scriptScale;
        float scale = Math.max(0.1F, blockSize / Math.max(1.0F, font.pointSize()));
        float baseline = total > 0.0F ? (pAscent / 100.0F) * blockSize : font.baseline() * 2.0F;
        baseline += scriptOffset * 2.0F;
        baseline = Math.max(-8.0F, Math.min((float) UNICODE_CELL + 8.0F, baseline));

        BlockFit fit = new BlockFit(scale, baseline);
        for (int offset = 0; offset < 256; offset++) {
            int codepoint = start + offset;
            if (!renderable(codepoint) || !font.canDisplay(codepoint)) {
                continue;
            }
            int advance = 4;
            try {
                Font face = font.fontForCodepoint(codepoint).deriveFont(Font.PLAIN, blockSize);
                char[] chars = chars(codepoint);
                GlyphVector vector = face.layoutGlyphVector(frc, chars, 0, chars.length,
                    Font.LAYOUT_LEFT_TO_RIGHT);
                advance = (int) Math.round(vector.getGlyphPosition(vector.getNumGlyphs()).getX() / 2.0F);
            } catch (Throwable ignored) {
            }
            fit.advances[offset] = Math.max(1, Math.min(8, advance - 1));
        }
        if (debug) {
            log("Unicode block " + String.format("%02x", block) + " size="
                + String.format(java.util.Locale.ROOT, "%.2f", blockSize) + " baseline="
                + String.format(java.util.Locale.ROOT, "%.2f", baseline));
        }
        return fit;
    }

    /**
     * Transparent pixels are black by default. With linear filtering/mipmaps that
     * darkens glyph edges into halos, so make the background white-but-invisible.
     */
    private static void whitenTransparent(BufferedImage image) {
        try {
            int[] data = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < data.length; i++) {
                if ((data[i] & 0xFF000000) == 0) {
                    data[i] = 0x00FFFFFF;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static float percentile(java.util.List<Integer> values, float fraction) {
        if (values.isEmpty()) {
            return 0.0F;
        }
        java.util.Collections.sort(values);
        return values.get(Math.min(values.size() - 1, (int) (values.size() * fraction)));
    }

    private static int copiedAdvance(BufferedImage sheet, int x, int y, int cell) {
        int right = -1;
        for (int col = cell - 1; col >= 0; col--) {
            boolean any = false;
            for (int row = 0; row < cell; row++) {
                if ((sheet.getRGB(x + col, y + row) >>> 24) != 0) {
                    any = true;
                    break;
                }
            }
            if (any) {
                right = col;
                break;
            }
        }
        if (right < 0) {
            return 2;
        }
        int ink = (int) Math.ceil((right + 1) * 8.0 / cell);
        return Math.max(2, Math.min(8, ink + 1));
    }

    private void copyCell(Graphics2D graphics, BufferedImage baseImage, int index,
                          int x, int y, int cell) {
        if (baseImage == null) {
            return;
        }
        int baseCellW = baseImage.getWidth() / 16;
        int baseCellH = baseImage.getHeight() / 16;
        int col = index & 15;
        int row = index >> 4;
        try {
            BufferedImage sub = baseImage.getSubimage(col * baseCellW, row * baseCellH,
                baseCellW, baseCellH);
            graphics.drawImage(sub, x, y, cell, cell, null);
        } catch (Throwable ignored) {
        }
    }

    private BufferedImage readBaseImage(String path) {
        try (InputStream in = base.open(path)) {
            return in == null ? null : ImageIO.read(in);
        } catch (Throwable t) {
            return null;
        }
    }

    private byte[] readBaseBytes(String path) {
        try (InputStream in = base.open(path)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(65536);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private byte[] encode(BufferedImage image, String name) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (Throwable t) {
            LOGGER.warn("[Fontr] Could not encode " + name + ": " + t);
            return new byte[0];
        }
        byte[] bytes = out.toByteArray();
        writeCache(name, bytes);
        return bytes;
    }

    private byte[] readCache(String name) {
        try {
            Path path = cacheDir.resolve(name);
            if (Files.isRegularFile(path)) {
                return Files.readAllBytes(path);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void writeCache(String name, byte[] bytes) {
        try {
            Files.write(cacheDir.resolve(name), bytes);
        } catch (Throwable t) {
            LOGGER.warn("[Fontr] Could not write cache " + name + ": " + t);
        }
    }

    private static boolean isMark(int codepoint) {
        int type = Character.getType(codepoint);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
            || type == Character.COMBINING_SPACING_MARK;
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
        return new String(Character.toChars(codepoint)).toCharArray();
    }

    private void log(String message) {
        LOGGER.info("[Fontr] " + message);
    }
}
