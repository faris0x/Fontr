// SPDX-License-Identifier: GPL-3.0-only

package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrEngine;
import dev.faris0x.fontr.compat.ArabicText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Shaping for PolyCompose MC-font HUD text (the Default/Minecraft font path). */
@Mixin(targets = "org.polyfrost.compose.render.RenderContext", remap = false)
public abstract class HudMcTextArabicMixin {
    private static boolean fontr$logged;

    @ModifyVariable(method = "mcText(Ljava/lang/String;FFIZF)V", at = @At("HEAD"),
        argsOnly = true, remap = false, require = 0)
    private String fontr$mcText6(String text) {
        return transform(text);
    }

    @ModifyVariable(method = "mcText(Ljava/lang/String;FFIZFZ)V", at = @At("HEAD"),
        argsOnly = true, remap = false, require = 0)
    private String fontr$mcText7(String text) {
        return transform(text);
    }

    @ModifyVariable(method = "mcText(Ljava/lang/String;FFIZFZZ)V", at = @At("HEAD"),
        argsOnly = true, remap = false, require = 0)
    private String fontr$mcText8(String text) {
        return transform(text);
    }

    @ModifyVariable(method = "mcTextOutline(Ljava/lang/String;FFIIF)V", at = @At("HEAD"),
        argsOnly = true, remap = false, require = 0)
    private String fontr$outline6(String text) {
        return transform(text);
    }

    @ModifyVariable(method = "mcTextOutline(Ljava/lang/String;FFIIFZ)V", at = @At("HEAD"),
        argsOnly = true, remap = false, require = 0)
    private String fontr$outline7(String text) {
        return transform(text);
    }

    @ModifyVariable(method = "mcTextOutline(Ljava/lang/String;FFIIFZZ)V", at = @At("HEAD"),
        argsOnly = true, remap = false, require = 0)
    private String fontr$outline8(String text) {
        return transform(text);
    }

    @ModifyVariable(
        method = "mcTextLive(Ljava/lang/String;FFLkotlin/jvm/functions/Function0;ZF)V",
        at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private String fontr$live0(String text) {
        return transform(text);
    }

    @ModifyVariable(
        method = "mcTextLive(Ljava/lang/String;FFLkotlin/jvm/functions/Function0;ZFZ)V",
        at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private String fontr$live1(String text) {
        return transform(text);
    }

    @ModifyVariable(
        method = "mcTextLive(Ljava/lang/String;FFLjava/util/function/IntSupplier;ZF)V",
        at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private String fontr$live2(String text) {
        return transform(text);
    }

    @ModifyVariable(
        method = "mcTextLive(Ljava/lang/String;FFLjava/util/function/IntSupplier;ZFZ)V",
        at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private String fontr$live3(String text) {
        return transform(text);
    }

    private static String transform(String text) {
        String result = ArabicText.forHud(text);
        if (!fontr$logged && result != null && !result.equals(text)) {
            fontr$logged = true;
            FontrEngine.INSTANCE.log("HUD mcText Arabic transform active: "
                + text + " -> " + result);
        }
        return result;
    }
}
