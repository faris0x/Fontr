package dev.faris0x.fontr.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Argentum's ASCII glyphs also use charWidth-1; use the full advance. */
@Mixin(targets = "dev.rdh.argentum.impl.render.gui.font.FontSet", remap = false)
public abstract class ArgentumGlyphWidthMixin {
    @ModifyConstant(method = "bitmap", constant = @Constant(floatValue = 1.01F), remap = false,
        require = 0)
    private float fontr$width(float original) {
        return 0.0F;
    }
}
