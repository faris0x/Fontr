// SPDX-License-Identifier: GPL-3.0-only

package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrEngine;
import dev.faris0x.fontr.compat.ArabicText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Shapes and reorders Arabic for PolyCompose HUD text (RenderContext path). */
@Mixin(targets = "org.polyfrost.compose.render.RenderContext", remap = false)
public abstract class HudTextArabicMixin {
    @ModifyVariable(method = "text", at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private String fontr$text(String text) {
        return transform(text);
    }

    @ModifyVariable(method = "textOutline", at = @At("HEAD"), argsOnly = true,
        remap = false, require = 0)
    private String fontr$textOutline(String text) {
        return transform(text);
    }

    @ModifyVariable(method = "measureText", at = @At("HEAD"), argsOnly = true,
        remap = false, require = 0)
    private String fontr$measureText(String text) {
        return transform(text);
    }

    private static boolean fontr$logged;

    private static String transform(String text) {
        String result = ArabicText.forHud(text);
        if (!fontr$logged && result != null && !result.equals(text)) {
            fontr$logged = true;
            FontrEngine.INSTANCE.log("HUD RenderContext Arabic transform active");
        }
        return result;
    }
}
