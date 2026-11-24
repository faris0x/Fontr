// SPDX-License-Identifier: GPL-3.0-only

package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrSettings;
import dev.faris0x.fontr.compat.ArabicText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Compose bidi would double-reverse strings Fontr already reordered. */
@Mixin(targets = "org.polyfrost.compose.backend.PolyParagraph", remap = false)
public abstract class ComposeBidiMixin {
    @Inject(method = "needsBidi", at = @At("HEAD"), cancellable = true, remap = false,
        require = 0)
    private void fontr$needsBidi(String text, CallbackInfoReturnable<Boolean> cir) {
        if (FontrSettings.enabled && FontrSettings.arabicShaping
            && ArabicText.containsRtl(text)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
