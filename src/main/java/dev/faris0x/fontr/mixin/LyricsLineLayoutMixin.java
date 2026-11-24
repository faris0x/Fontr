package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrEngine;
import dev.faris0x.fontr.FontrSettings;
import dev.faris0x.fontr.compat.LyricsRtl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * EvergreenHUD lays lyrics out word-by-word from left to right. For Arabic
 * rows, mirror the word order and x positions after construction so the text
 * and its karaoke sweep run right-to-left. Runs after {@code super()}, so an
 * instance handler is legal here; any failure leaves the original rows alone.
 */
@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.media.LineLayout", remap = false)
public abstract class LyricsLineLayoutMixin {
    @Shadow
    @Final
    @Mutable
    private List<List<Object>> rows;

    private static boolean fontr$logged;
    private static boolean fontr$failed;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void fontr$mirror(List<?> source, CallbackInfo ci) {
        if (fontr$failed || !FontrSettings.enabled || !FontrSettings.arabicShaping) {
            return;
        }
        try {
            boolean[] changed = new boolean[1];
            List<List<Object>> mirrored = LyricsRtl.mirrorRows(this.rows, changed);
            if (!changed[0]) {
                return;
            }
            this.rows = mirrored;
            if (!fontr$logged) {
                fontr$logged = true;
                FontrEngine.INSTANCE.log("HUD lyrics RTL layout active");
            }
        } catch (Throwable t) {
            fontr$failed = true;
            FontrEngine.INSTANCE.log("HUD lyrics RTL layout failed: " + t);
        }
    }
}
