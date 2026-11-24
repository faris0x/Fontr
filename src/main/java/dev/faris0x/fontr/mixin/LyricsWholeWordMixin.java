package dev.faris0x.fontr.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.faris0x.fontr.FontrEngine;
import dev.faris0x.fontr.compat.ArabicText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * EvergreenHUD splits long words into one mark per character, in logical
 * order, which draws Arabic letters left-to-right. Report a neutral length for
 * Arabic words so the whole word is kept as a single mark and shaped normally.
 */
@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.media.LyricsHudKt", remap = false)
public abstract class LyricsWholeWordMixin {
    private static String fontr$word;
    private static boolean fontr$logged;

    @ModifyExpressionValue(method = "buildFrame",
        at = @At(value = "INVOKE",
            target = "Lkotlin/text/StringsKt;trimEnd(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;"),
        remap = false, require = 0)
    private static CharSequence fontr$record(CharSequence value) {
        fontr$word = value == null ? null : value.toString();
        return value;
    }

    @ModifyExpressionValue(method = "buildFrame",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;length()I", ordinal = 0),
        remap = false, require = 0)
    private static int fontr$wholeWord(int length) {
        if (ArabicText.containsArabicVisual(fontr$word)) {
            if (!fontr$logged) {
                fontr$logged = true;
                FontrEngine.INSTANCE.log("HUD lyrics Arabic words kept whole");
            }
            // 3..12 keeps EvergreenHUD on the single-mark path.
            return 5;
        }
        return length;
    }
}
