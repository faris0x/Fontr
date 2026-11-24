package dev.faris0x.fontr.text;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Batches glyphs per atlas page and draws the underline/strikethrough pass.
 */
public final class FontrRenderer {
    private FontrRenderer() {
    }

    public static void draw(FontrLayout layout, FontrAtlas atlas, float x, float y,
                            int initialColor, boolean shadow, boolean pixelSnap) {
        if (layout.visual.length == 0) {
            return;
        }

        Map<Integer, List<Draw>> byTexture = new LinkedHashMap<Integer, List<Draw>>();
        List<Draw> lines = new ArrayList<Draw>();

        float baseline = y + layout.baseline;
        for (FontrLayout.Glyph glyph : layout.visual) {
            FontrLayout.Format format = layout.formatFor(glyph);
            int color = color(format, initialColor, shadow);
            int a = color >>> 24 & 0xFF;
            int r = color >> 16 & 0xFF;
            int g = color >> 8 & 0xFF;
            int b = color & 0xFF;

            if (format.underline || format.strikethrough) {
                float lineX1 = x + glyph.x;
                float lineX2 = lineX1 + glyph.advance;
                float lineY = baseline + (format.underline ? 1 : -4);
                lines.add(new Draw(0, lineX1, lineY, lineX2, lineY + 1, color, null));
            }

            if (glyph.width <= 0 || glyph.height <= 0) {
                continue;
            }

            FontrAtlas.Entry entry = atlas.get(glyph.font, glyph.glyphCode);
            if (entry.texture == 0 || entry.width <= 0 || entry.height <= 0) {
                continue;
            }

            float x1 = x + glyph.x;
            float y1 = baseline + glyph.y;
            if (pixelSnap) {
                x1 = Math.round(x1);
                y1 = Math.round(y1);
            }
            Draw draw = new Draw(entry.texture, x1, y1, x1 + entry.width, y1 + entry.height,
                                 (a << 24) | (r << 16) | (g << 8) | b, entry);
            List<Draw> list = byTexture.get(entry.texture);
            if (list == null) {
                list = new ArrayList<Draw>();
                byTexture.put(entry.texture, list);
            }
            list.add(draw);
        }

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        for (Map.Entry<Integer, List<Draw>> page : byTexture.entrySet()) {
            GlStateManager.bindTexture(page.getKey());
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Draw draw : page.getValue()) {
                FontrAtlas.Entry e = draw.entry;
                int c = draw.color;
                float x1 = draw.x1;
                float y1 = draw.y1;
                float x2 = draw.x2;
                float y2 = draw.y2;
                buffer.vertex(x1, y1, 0).texture(e.u1, e.v1).color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24).nextVertex();
                buffer.vertex(x1, y2, 0).texture(e.u1, e.v2).color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24).nextVertex();
                buffer.vertex(x2, y2, 0).texture(e.u2, e.v2).color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24).nextVertex();
                buffer.vertex(x2, y1, 0).texture(e.u2, e.v1).color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24).nextVertex();
            }
            tessellator.end();
        }

        if (!lines.isEmpty()) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Draw line : lines) {
                int c = line.color;
                float x1 = line.x1;
                float y1 = line.y1;
                float x2 = line.x2;
                float y2 = line.y2;
                buffer.vertex(x1, y1, 0).color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24).nextVertex();
                buffer.vertex(x1, y2, 0).color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24).nextVertex();
                buffer.vertex(x2, y2, 0).color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24).nextVertex();
                buffer.vertex(x2, y1, 0).color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24).nextVertex();
            }
            tessellator.end();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }

    private static int color(FontrLayout.Format format, int initial, boolean shadow) {
        if (format.color < 0) {
            return initial;
        }
        int rgb = format.color;
        if (shadow) {
            rgb = (rgb & 0xFCFCFC) >> 2;
        }
        return (initial & 0xFF000000) | rgb;
    }

    private static final class Draw {
        final int texture;
        final float x1;
        final float y1;
        final float x2;
        final float y2;
        final int color;
        final FontrAtlas.Entry entry;

        Draw(int texture, float x1, float y1, float x2, float y2, int color, FontrAtlas.Entry entry) {
            this.texture = texture;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.color = color;
            this.entry = entry;
        }
    }
}
