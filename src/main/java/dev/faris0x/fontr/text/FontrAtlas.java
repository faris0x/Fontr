package dev.faris0x.fontr.text;

import org.lwjgl.BufferUtils;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Path;

/**
 * A small shelf-packed glyph atlas. Rasterisation and GL calls happen only on
 * the render thread (drawLayer), never in the measuring path.
 */
public final class FontrAtlas {
    private static final int SIZE = 1024;
    private static final int PAD = 1;

    private final FontrFont loadedFont;
    private final Map<Key, Entry> glyphs = new HashMap<Key, Entry>();
    private final List<Page> pages = new ArrayList<Page>();

    public FontrAtlas(FontrFont font) {
        this.loadedFont = font;
    }

    public Entry get(Font font, int glyphCode) {
        Key key = new Key(font, glyphCode);
        Entry entry = glyphs.get(key);
        if (entry != null) {
            return entry;
        }
        entry = rasterize(font, glyphCode);
        glyphs.put(key, entry);
        return entry;
    }

    private Entry rasterize(Font font, int glyphCode) {
        GlyphVector vector;
        try {
            vector = font.createGlyphVector(loadedFont.frc(), new int[]{glyphCode});
        } catch (Throwable t) {
            return Entry.EMPTY;
        }

        java.awt.Rectangle bounds = vector.getPixelBounds(loadedFont.frc(), 0, 0);
        if (bounds.width <= 0 || bounds.height <= 0) {
            return Entry.EMPTY;
        }
        if (bounds.width > SIZE || bounds.height > SIZE) {
            return Entry.EMPTY;
        }

        BufferedImage image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            loadedFont.antialias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            loadedFont.antialias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.drawGlyphVector(vector, -bounds.x, -bounds.y);
        graphics.dispose();

        Page page = currentPage();
        if (page == null || page.x + bounds.width + PAD > SIZE) {
            page = newPage();
        }
        if (page.y + bounds.height + PAD > SIZE) {
            page = newPage();
        }

        int x = page.x;
        int y = page.y;
        page.graphics.drawImage(image, x, y, null);

        page.x += bounds.width + PAD;
        page.lineHeight = Math.max(page.lineHeight, bounds.height);
        if (page.x + PAD >= SIZE) {
            page.x = PAD;
            page.y += page.lineHeight + PAD;
            page.lineHeight = 0;
        }

        page.upload(x, y, bounds.width, bounds.height);

        return new Entry(page.texture, x, y, bounds.width, bounds.height);
    }

    private Page currentPage() {
        return pages.isEmpty() ? null : pages.get(pages.size() - 1);
    }

    private Page newPage() {
        Page page = new Page();
        pages.add(page);
        return page;
    }

    public void dump(Path directory) {
        try {
            java.nio.file.Files.createDirectories(directory);
            for (int i = 0; i < pages.size(); i++) {
                File out = directory.resolve("atlas-debug-" + i + ".png").toFile();
                ImageIO.write(pages.get(i).image, "png", out);
                System.out.println("[Fontr] atlas dump " + out);
            }
        } catch (Throwable t) {
            System.out.println("[Fontr] atlas dump failed: " + t);
        }
    }

    public void close() {
        for (Page page : pages) {
            if (page.texture != 0) {
                IntBuffer buffer = BufferUtils.createIntBuffer(1);
                buffer.put(page.texture);
                buffer.flip();
                GL11.glDeleteTextures(buffer);
            }
        }
        pages.clear();
        glyphs.clear();
    }

    public static final class Entry {
        public static final Entry EMPTY = new Entry(0, 0, 0, 0, 0);

        public final int texture;
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final float u1;
        public final float v1;
        public final float u2;
        public final float v2;

        Entry(int texture, int x, int y, int width, int height) {
            this.texture = texture;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.u1 = (float) x / SIZE;
            this.v1 = (float) y / SIZE;
            this.u2 = (float) (x + width) / SIZE;
            this.v2 = (float) (y + height) / SIZE;
        }
    }

    private final class Page {
        final BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        final int texture;
        int x = PAD;
        int y = PAD;
        int lineHeight;

        Page() {
            graphics.setComposite(AlphaComposite.Src);
            IntBuffer ids = BufferUtils.createIntBuffer(1);
            GL11.glGenTextures(ids);
            texture = ids.get(0);
            GlStateManager.activeTexture(0);
            GlStateManager.bindTexture(texture);

            ByteBuffer buffer = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE, 0,
                              GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            int filter = loadedFont.pixelSnap ? GL11.GL_NEAREST : GL11.GL_LINEAR;
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        }

        void upload(int x, int y, int width, int height) {
            int[] pixels = new int[width * height];
            image.getRGB(x, y, width, height, pixels, 0, width);
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            for (int pixel : pixels) {
                buffer.put((byte) (pixel >> 16));
                buffer.put((byte) (pixel >> 8));
                buffer.put((byte) pixel);
                buffer.put((byte) (pixel >> 24));
            }
            buffer.flip();

            GlStateManager.activeTexture(0);
            GlStateManager.bindTexture(texture);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height,
                                 GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        }
    }

    private static final class Key {
        final Font font;
        final int glyphCode;

        Key(Font font, int glyphCode) {
            this.font = font;
            this.glyphCode = glyphCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key key = (Key) o;
            return glyphCode == key.glyphCode && font.equals(key.font);
        }

        @Override
        public int hashCode() {
            return 31 * font.hashCode() + glyphCode;
        }
    }
}
