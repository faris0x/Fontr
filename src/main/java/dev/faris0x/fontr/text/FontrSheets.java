package dev.faris0x.fontr.text;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds vanilla-shaped font sheets from a TTF.
 *
 * <p>The ASCII sheet is a 16x16 grid at the configured resolution (4096 by
 * default, 8192 optionally). Unicode pages stay at vanilla's 256x256 layout so
 * every 1.8.9 consumer (vanilla, Argentum, Cera) can read them unchanged, and
 * are generated lazily for the blocks the font actually covers.</p>
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
    private final Path cacheDir;
    private final BaseProvider base;
    private final boolean debug;

    private byte[] asciiPng;
    private byte[] glyphSizes;
    private final java.util.Map<Integer, byte[]> unicodePng = new java.util.HashMap<Integer, byte[]>();

    public FontrSheets(FontrFont font, int resolution, Path cacheDir, BaseProvider base,
                       boolean debug) {
        this.font = font;
        this.resolution = resolution;
        this.cacheDir = cacheDir;
        this.base = base;
        this.debug = debug;
        try {
            Files.createDirectories(cacheDir);
        } catch (Throwable t) {
            LOGGER.warn("[Fontr] Could not create cache directory " + cacheDir + ": " + t);
        }
    }

    public synchronized byte[] asciiPng() {
        if (asciiPng != null) {
            return asciiPng;
        }
        byte[] cached = readCache("ascii.png");
        if (cached != null) {
            asciiPng = cached;
            return cached;
        }
        long started = System.currentTimeMillis();
        BufferedImage image = buildAscii();
        asciiPng = encode(image, "ascii.png");
        log("Generated " + resolution + "x" + resolution + " ASCII sheet in "
            + (System.currentTimeMillis() - started) + "ms");
        return asciiPng;
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
            int advance = codepoint == 0x20 ? 4 : Math.min(8, font.advance(codepoint) + 1);
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

    private BufferedImage buildAscii() {
        int cell = resolution / 16;
        int scale = Math.max(1, cell / FontrFont.LINE);
        BufferedImage sheet = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_ARGB);
        BufferedImage baseImage = readBaseImage("textures/font/ascii.png");
        Graphics2D graphics = sheet.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setColor(java.awt.Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        for (int codepoint = 0x20; codepoint < 0x100; codepoint++) {
            int col = codepoint & 15;
            int row = codepoint >> 4;
            int x = col * cell;
            int y = row * cell;
            if (codepoint == 0x20) {
                continue; // vanilla hardcodes a 4px space
            }
            if (!font.canDisplay(codepoint)) {
                copyCell(graphics, baseImage, codepoint, x, y, cell);
                continue;
            }

            // Vanilla draws each glyph charWidth-1 pixels wide. Use one extra pixel
            // for the advance so glyphs keep their natural width and get a 1px gap.
            int advance = Math.min(8, font.advance(codepoint) + 1);
            graphics.setClip(x, y, cell, cell);
            font.drawGlyph(graphics, codepoint, scale, x, y + (float) FontrFont.BASELINE * scale,
                (float) (advance - 1) * scale);
            graphics.setClip(null);

            // readFontTexture() derives advances from the rightmost non-transparent
            // column; mark the intended advance with an (invisible) alpha-1 pixel.
            int markerX = x + advance * scale - 1;
            int markerY = y + FontrFont.BASELINE * scale;
            if (markerX >= x && markerX < x + cell && markerY >= y && markerY < y + cell) {
                sheet.setRGB(markerX, markerY, 0x01FFFFFF);
            }
        }
        graphics.dispose();
        return sheet;
    }

    private BufferedImage buildUnicode(int block) {
        BufferedImage page = new BufferedImage(UNICODE_PAGE, UNICODE_PAGE, BufferedImage.TYPE_INT_ARGB);
        BufferedImage baseImage = readBaseImage(String.format("textures/font/unicode_page_%02x.png", block));
        Graphics2D graphics = page.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setColor(java.awt.Color.WHITE);
        if (baseImage != null) {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(baseImage, 0, 0, UNICODE_PAGE, UNICODE_PAGE, null);
        }

        int start = block << 8;
        for (int offset = 0; offset < 256; offset++) {
            int codepoint = start + offset;
            if (codepoint < 0x20 || !font.canDisplay(codepoint)) {
                continue;
            }
            int x = (offset & 15) * UNICODE_CELL;
            int y = (offset >> 4) * UNICODE_CELL;
            int advance = Math.min(8, font.advance(codepoint) + 1);
            graphics.setClip(x, y, UNICODE_CELL, UNICODE_CELL);
            font.drawGlyph(graphics, codepoint, UNICODE_SCALE, x,
                y + (float) FontrFont.BASELINE * UNICODE_SCALE,
                (float) (advance - 1) * UNICODE_SCALE);
            graphics.setClip(null);
        }
        graphics.dispose();
        return page;
    }

    private void copyCell(Graphics2D graphics, BufferedImage baseImage, int codepoint,
                          int x, int y, int cell) {
        if (baseImage == null) {
            return;
        }
        int baseCellW = baseImage.getWidth() / 16;
        int baseCellH = baseImage.getHeight() / 16;
        int col = codepoint & 15;
        int row = codepoint >> 4;
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
