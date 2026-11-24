package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrEngine;
import dev.faris0x.fontr.FontrSettings;
import dev.faris0x.fontr.compat.ArabicText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Last-resort hook: any PolyTessellator text draw that still contains unshaped
 * base Arabic is shaped and visually reordered. Already-transformed text
 * (presentation forms) is left alone.
 */
@Mixin(targets = "org.polyfrost.compose.render.PolyTessellator", remap = false)
public abstract class HudPolyTextMixin {
    private static final String T5 =
        "text(Ljava/lang/String;FFILorg/polyfrost/compose/render/PolyFont;)V";
    private static final String T6 =
        "text(Ljava/lang/String;FFILorg/polyfrost/compose/render/PolyFont;F)V";
    private static final String T7 =
        "text(Ljava/lang/String;FFILorg/polyfrost/compose/render/PolyFont;FF)V";
    private static final String OUTLINE =
        "textOutline(Ljava/lang/String;FFILorg/polyfrost/compose/render/PolyFont;F)V";

    private static boolean fontr$logged;

    @ModifyVariable(method = T5, at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private String fontr$text5(String text) {
        return transform(text);
    }

    @ModifyVariable(method = T6, at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private String fontr$text6(String text) {
        return transform(text);
    }

    @ModifyVariable(method = T7, at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private String fontr$text7(String text) {
        return transform(text);
    }

    @ModifyVariable(method = OUTLINE, at = @At("HEAD"), argsOnly = true, remap = false,
        require = 0)
    private String fontr$outline(String text) {
        return transform(text);
    }

    private static String transform(String text) {
        if (!FontrSettings.enabled || !FontrSettings.arabicShaping
            || text == null || !ArabicText.containsArabic(text)) {
            return text;
        }
        String result = ArabicText.transform(text);
        if (!fontr$logged) {
            fontr$logged = true;
            FontrEngine.INSTANCE.log("HUD PolyTessellator Arabic transform active: "
                + text + " -> " + result);
        }
        return result;
    }
}
