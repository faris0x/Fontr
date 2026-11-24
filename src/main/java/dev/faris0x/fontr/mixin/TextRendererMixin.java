package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrEngine;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ornithe/Feather adapter.
 *
 * <p>We hook {@code drawLayer} instead of {@code draw} so vanilla still handles
 * the shadow offset and calls us twice (normal + shadow). We also replace the
 * width/layout helpers so chat wrapping, text fields and HUD centring all agree
 * with what is actually rendered.</p>
 */
@Mixin(TextRenderer.class)
public abstract class TextRendererMixin {
    @Shadow
    @Final
    private Identifier fontLocation;

    @Shadow
    private float x;

    @Inject(method = "drawLayer(Ljava/lang/String;FFIZ)I", at = @At("HEAD"), cancellable = true)
    private void fontr$drawLayer(String text, float x, float y, int color, boolean shadow,
                                 CallbackInfoReturnable<Integer> cir) {
        if (!FontrEngine.INSTANCE.shouldHandle(fontLocation)) {
            return;
        }

        if (text == null || text.isEmpty()) {
            cir.setReturnValue(0);
            return;
        }

        // drawLayer normally performs this before handing off to drawLayer.
        int argb = (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
        if (shadow) {
            argb = (argb & 0xFCFCFC) >> 2 | (argb & 0xFF000000);
        }

        Integer advance = FontrEngine.INSTANCE.render(text, x, y, argb, shadow);
        if (advance != null) {
            this.x = x + advance;
            cir.setReturnValue(advance);
        }
    }

    @Inject(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void fontr$getWidth(String text, CallbackInfoReturnable<Integer> cir) {
        if (!FontrEngine.INSTANCE.shouldHandle(fontLocation)) {
            return;
        }
        Integer width = FontrEngine.INSTANCE.measure(text);
        if (width != null) {
            cir.setReturnValue(width);
        }
    }

    @Inject(method = "getWidth(C)I", at = @At("HEAD"), cancellable = true)
    private void fontr$getCharWidth(char c, CallbackInfoReturnable<Integer> cir) {
        if (!FontrEngine.INSTANCE.shouldHandle(fontLocation)) {
            return;
        }
        Integer width = FontrEngine.INSTANCE.measureChar(c);
        if (width != null) {
            cir.setReturnValue(width);
        }
    }

    @Inject(method = "trim(Ljava/lang/String;I)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void fontr$trim(String text, int width, CallbackInfoReturnable<String> cir) {
        if (!FontrEngine.INSTANCE.shouldHandle(fontLocation)) {
            return;
        }
        String trimmed = FontrEngine.INSTANCE.trim(text, width, false);
        if (trimmed != null) {
            cir.setReturnValue(trimmed);
        }
    }

    @Inject(method = "trim(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void fontr$trimReverse(String text, int width, boolean reverse, CallbackInfoReturnable<String> cir) {
        if (!FontrEngine.INSTANCE.shouldHandle(fontLocation)) {
            return;
        }
        String trimmed = FontrEngine.INSTANCE.trim(text, width, reverse);
        if (trimmed != null) {
            cir.setReturnValue(trimmed);
        }
    }

    @Inject(method = "indexAtWidth(Ljava/lang/String;I)I", at = @At("HEAD"), cancellable = true)
    private void fontr$indexAtWidth(String text, int width, CallbackInfoReturnable<Integer> cir) {
        if (!FontrEngine.INSTANCE.shouldHandle(fontLocation)) {
            return;
        }
        Integer index = FontrEngine.INSTANCE.indexAtWidth(text, width);
        if (index != null) {
            cir.setReturnValue(index);
        }
    }
}
