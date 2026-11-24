package dev.faris0x.fontr.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.faris0x.fontr.FontrEngine;
import dev.faris0x.fontr.compat.ArabicText;
import org.polyfrost.evergreenhud.client.hud.media.GlyphMark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps EvergreenHUD karaoke marks correct for Arabic:
 *
 * <ul>
 *   <li>Long words are normally split into one mark per character in logical
 *       order, which draws the letters left-to-right. Arabic words keep a
 *       single mark so the whole word is shaped and reordered as one string.</li>
 *   <li>The sung wipe grows from the left edge of the word. For Arabic the clip
 *       rectangle is mirrored about the word centre, so the wipe grows from the
 *       right instead.</li>
 * </ul>
 */
@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.media.LyricsHudKt", remap = false)
public abstract class LyricsKaraokeMixin {
    private static String fontr$word;
    private static boolean fontr$logged;

    @ModifyVariable(method = "buildFrame", at = @At("STORE"), name = "trimmed",
        remap = false, require = 0)
    private String fontr$remember(String value) {
        fontr$word = value;
        return value;
    }

    @ModifyVariable(method = "buildFrame", at = @At("STORE"), name = "held",
        remap = false, require = 0)
    private boolean fontr$wholeWord(boolean held) {
        if (held && ArabicText.containsArabicVisual(fontr$word)) {
            if (!fontr$logged) {
                fontr$logged = true;
                FontrEngine.INSTANCE.log("HUD lyrics Arabic words drawn whole");
            }
            return false;
        }
        return held;
    }

    @WrapOperation(method = "drawGlyph$slice",
        at = @At(value = "INVOKE",
            target = "Lorg/polyfrost/compose/render/RenderContext;clipRect(FFFF)V"),
        remap = false, require = 0)
    private static void fontr$mirrorWipe(float x, float y, float width, float height,
        Operation<Void> original, @Local(argsOnly = true) GlyphMark mark) {
        if (mark != null && ArabicText.containsArabicVisual(mark.getText())) {
            float wordWidth = mark.getWidth();
            original.call(-wordWidth - (x + width), y, width, height);
        } else {
            original.call(x, y, width, height);
        }
    }
}
