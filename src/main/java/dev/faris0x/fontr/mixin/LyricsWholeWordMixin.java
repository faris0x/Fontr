// SPDX-License-Identifier: GPL-3.0-only

package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrEngine;
import dev.faris0x.fontr.compat.ArabicText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * EvergreenHUD splits long words into one mark per character, in logical
 * order, which draws Arabic letters left-to-right. Force the single-mark path
 * for Arabic words so the whole word is shaped and reordered normally.
 *
 * <p>Locals are matched by name from the local variable table: {@code trimmed}
 * is the word and {@code held} selects the per-character path. The slots moved
 * between EvergreenHUD 3.10.0 and 3.10.1, so names are used.</p>
 */
@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.media.LyricsHud", remap = false)
public abstract class LyricsWholeWordMixin {
    private static String fontr$word;
    private static boolean fontr$wordLogged;
    private static boolean fontr$heldLogged;
    private static boolean fontr$wholeLogged;

    @ModifyVariable(method = "buildFrame", at = @At("STORE"), name = "trimmed",
        remap = false, require = 0)
    private String fontr$record(String value) {
        fontr$word = value;
        if (!fontr$wordLogged) {
            fontr$wordLogged = true;
            FontrEngine.INSTANCE.log("HUD lyric word local hooked");
        }
        return value;
    }

    @ModifyVariable(method = "buildFrame", at = @At("STORE"), name = "held",
        remap = false, require = 0)
    private boolean fontr$wholeWord(boolean held) {
        if (!fontr$heldLogged) {
            fontr$heldLogged = true;
            FontrEngine.INSTANCE.log("HUD lyric split flag hooked");
        }
        if (held && ArabicText.containsArabicVisual(fontr$word)) {
            if (!fontr$wholeLogged) {
                fontr$wholeLogged = true;
                FontrEngine.INSTANCE.log("HUD lyrics Arabic words kept whole");
            }
            return false;
        }
        return held;
    }
}
