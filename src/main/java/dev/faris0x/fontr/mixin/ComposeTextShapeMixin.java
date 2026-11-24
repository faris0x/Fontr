// SPDX-License-Identifier: GPL-3.0-only

package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrEngine;
import dev.faris0x.fontr.compat.ArabicText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Shapes Arabic before PolyCompose paragraphs run their own bidi. */
@Mixin(targets = "org.polyfrost.compose.backend.PolyText", remap = false)
public abstract class ComposeTextShapeMixin {
    private static boolean fontr$logged;

    @ModifyVariable(method = "createParagraphIntrinsics", at = @At("HEAD"), argsOnly = true,
        remap = false, require = 0)
    private String fontr$shape(String text) {
        String result = ArabicText.forHud(text);
        if (!fontr$logged && result != null && !result.equals(text)) {
            fontr$logged = true;
            FontrEngine.INSTANCE.log("HUD Compose Arabic transform active: "
                + text + " -> " + result);
        }
        return result;
    }
}
