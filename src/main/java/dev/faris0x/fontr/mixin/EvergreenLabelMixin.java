package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.compat.ArabicText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** EvergreenHUD measures HUD labels with the raw string; measure the shaped one. */
@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.media.Label", remap = false)
public abstract class EvergreenLabelMixin {
    @ModifyVariable(method = "width", at = @At("HEAD"), argsOnly = true, remap = false,
        require = 0)
    private String fontr$width(String text) {
        return ArabicText.forHud(text);
    }
}
