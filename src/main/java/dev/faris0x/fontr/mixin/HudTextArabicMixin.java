package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.compat.ArabicText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Shapes and reorders Arabic for PolyCompose HUD text (RenderContext path). */
@Mixin(targets = "org.polyfrost.compose.render.RenderContext", remap = false)
public abstract class HudTextArabicMixin {
    @ModifyVariable(method = "text", at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private String fontr$text(String text) {
        return ArabicText.forHud(text);
    }

    @ModifyVariable(method = "textOutline", at = @At("HEAD"), argsOnly = true,
        remap = false, require = 0)
    private String fontr$textOutline(String text) {
        return ArabicText.forHud(text);
    }

    @ModifyVariable(method = "measureText", at = @At("HEAD"), argsOnly = true,
        remap = false, require = 0)
    private String fontr$measureText(String text) {
        return ArabicText.forHud(text);
    }
}
