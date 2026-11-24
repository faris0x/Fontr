package dev.faris0x.fontr.text;

import dev.faris0x.fontr.text.FontrFont.Style;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * <p>The ASCII sheet is a 16x16 grid filled with the glyphs 1.8.9 expects in each
 * legacy cell. Unicode pages stay at vanilla's 256x256 layout so every consumer
 * can read them; they are generated lazily per covered block. Bold/italic sheets
 * are only generated when real faces are available.</p>
 */
public final class FontrSheets {
    private static final Logger LOGGER = LogManager.getLogger("Fontr");
    private static final int UNICODE_PAGE = 256;
    private static final int UNICODE_CELL = 16;
    private static final int UNICODE_SCALE = 2;

    /** Reads a resource from the packs underneath Fontr. */
    public interface BaseProvider {
        InputStream open(String path);
    }

    private final FontrFont font;
    private final int resolution;
    private final int styledResolution;
    private final boolean trueStyles;
    private final Path cacheDir;
    private final BaseProvider base;
    private final float letterSpacing;
    private final float embolden;
    private final boolean debug;

    private final Map<Style, byte[]> styledPng = new HashMap<Style, byte[]>();
    private byte[] glyphSizes;
    private final Map<Integer, byte[]> unicodePng = new HashMap<Integer, byte[]>();

    public FontrSheets(FontrFont font, int resolution, int styledResolution, boolean trueStyles,
                       Path cacheDir, BaseProvider base, float letterSpacing, float embolden,
                       boolean debug) {
        this.font = font;
        this.resolution = resolution;
        this.styledResolution = styledResolution;
        this.trueStyles = trueStyles;
        this.cacheDir = cacheDir;
        this.base = base;
        this.letterSpacing = letterSpacing;
        this.embolden = embolden;
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
        for (int codepoint = 0x20; codepoint < 0x10000; codepoint++) {
            if (!renderable(codepoint) || !font.canDisplay(codepoint)) {
                continue;
            }
            int advance = codepoint == 0x20 ? 4 : advance(codepoint);
            int end = Math.min(15, Math.max(0, advance * 2 - 1));
            result[codepoint] = (byte) end;
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
        if (!font.hasCoverage(block)) {
            unicodePng.put(block, null);
            return null;
        }
        byte[] cached = readCache(name);
        if (cached != null) {
            unicodePng.put(block, cached);
            return cached;
        }
        byte[] png = encode(buildUnicode(block), name);
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
                continue;
            }

            int advance = advance(codepoint);
            graphics.setClip(x, y, cell, cell);
            font.drawGlyph(graphics, codepoint, scale, x, y + baseline,
                (float) (advance - 1) * scale, embolden, style);
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

    private BufferedImage buildUnicode(int block) {
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

        int baseline = Math.round(font.baseline() * UNICODE_SCALE);
        int start = block << 8;
        for (int offset = 0; offset < 256; offset++) {
            int codepoint = start + offset;
            if (codepoint < 0x20 || !font.canDisplay(codepoint)) {
                continue;
            }
            int x = (offset & 15) * UNICODE_CELL;
            int y = (offset >> 4) * UNICODE_CELL;
            int advance = advance(codepoint);

            // Replace the whole cell, otherwise the pack's glyph can show through
            // around our narrower/shorter one.
            graphics.setClip(x, y, UNICODE_CELL, UNICODE_CELL);
            graphics.setColor(new Color(0, 0, 0, 0));
            graphics.fillRect(x, y, UNICODE_CELL, UNICODE_CELL);
            graphics.setColor(Color.WHITE);
            font.drawGlyph(graphics, codepoint, UNICODE_SCALE, x, y + baseline,
                (float) (advance - 1) * UNICODE_SCALE, embolden, Style.REGULAR);
            graphics.setClip(null);
        }
        graphics.dispose();
        whitenTransparent(page);
        return page;
    }

    private int advance(int codepoint) {
        return Math.max(2, Math.min(8, Math.round(font.advance(codepoint) + letterSpacing)));
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

    private void log(String message) {
        LOGGER.info("[Fontr] " + message);
    }
}
