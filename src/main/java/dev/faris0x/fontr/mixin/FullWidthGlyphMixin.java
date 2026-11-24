package dev.faris0x.fontr.mixin;

import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Vanilla draws ASCII glyphs charWidth-1 pixels wide. Fontr uses the font's
 * natural advance, so draw the full charWidth instead.
 */
@Mixin(TextRenderer.class)
public abstract class FullWidthGlyphMixin {
    @ModifyConstant(method = "drawBasicGlyph", constant = @Constant(floatValue = 1.0F), require = 0)
    private float fontr$fullWidth(float original) {
        return 0.0F;
    }
}
