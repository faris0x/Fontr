// SPDX-License-Identifier: GPL-3.0-only

package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.compat.ArabicText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Measures HUD MC text using the same shaped string that is drawn. */
@Mixin(targets = "org.polyfrost.compose.mc.McFontQueue", remap = false)
public abstract class HudMcFontMeasureMixin {
    @ModifyVariable(method = "measureTextWidth(Ljava/lang/String;F)F", at = @At("HEAD"),
        argsOnly = true, remap = false, require = 0)
    private String fontr$measure1(String text) {
        return ArabicText.forHud(text);
    }

    @ModifyVariable(method = "measureTextWidth(Ljava/lang/String;FZ)F", at = @At("HEAD"),
        argsOnly = true, remap = false, require = 0)
    private String fontr$measure2(String text) {
        return ArabicText.forHud(text);
    }
}
