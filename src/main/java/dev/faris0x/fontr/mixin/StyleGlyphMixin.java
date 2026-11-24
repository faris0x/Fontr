package dev.faris0x.fontr.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.faris0x.fontr.FontrEngine;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Uses the real bold/italic sheets for ASCII glyphs and removes vanilla's
 * synthetic double-draw / slant when a real face is available.
 */
@Mixin(TextRenderer.class)
public abstract class StyleGlyphMixin {
    @Shadow
    private boolean bold;

    @Shadow
    private boolean italic;

    @WrapOperation(
        method = "drawBasicGlyph",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/texture/TextureManager;bind(Lnet/minecraft/resource/Identifier;)V"),
        require = 0
    )
    private void fontr$bind(TextureManager manager, Identifier original, Operation<Void> operation) {
        Identifier styled = FontrEngine.INSTANCE.styledSheet(this.bold, this.italic);
        operation.call(manager, styled != null ? styled : original);
    }

    @WrapOperation(
        method = "drawLayer(Ljava/lang/String;Z)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/TextRenderer;drawGlyph(CZ)F",
            ordinal = 0),
        require = 0
    )
    private float fontr$draw(TextRenderer self, char chr, boolean italic, Operation<Float> operation) {
        boolean trueItalic = chr < 256 && this.italic
            && FontrEngine.INSTANCE.styledSheet(this.bold, this.italic) != null;
        return operation.call(self, chr, trueItalic ? false : italic);
    }

    @WrapOperation(
        method = "drawLayer(Ljava/lang/String;Z)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/TextRenderer;drawGlyph(CZ)F",
            ordinal = 1),
        require = 0
    )
    private float fontr$drawBold(TextRenderer self, char chr, boolean italic, Operation<Float> operation) {
        if (chr < 256 && this.bold
            && FontrEngine.INSTANCE.styledSheet(this.bold, this.italic) != null) {
            return 0.0F;
        }
        return operation.call(self, chr, italic);
    }
}
