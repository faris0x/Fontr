package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrEngine;
import dev.faris0x.fontr.FontrSettings;
import dev.faris0x.fontr.compat.ArabicText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * EvergreenHUD lays lyrics out word-by-word from left to right. Mirror the word
 * x positions for Arabic lines so the visual order and the karaoke sweep run
 * right-to-left. Timing stays attached to each word.
 */
@Mixin(targets = "org.polyfrost.evergreenhud.client.hud.media.LineLayout", remap = false)
public abstract class LyricsLineLayoutMixin {
    private static boolean fontr$logged;
    private static Constructor<?> fontr$ctor;
    private static Method fontr$text;
    private static Method fontr$x;
    private static Method fontr$ink;
    private static Method fontr$start;
    private static Method fontr$end;
    private static boolean fontr$failed;

    @Inject(method = "<init>", at = @At("HEAD"), remap = false, require = 0)
    private void fontr$mirror(List<?> rows, CallbackInfo ci) {
        if (!FontrSettings.enabled || !FontrSettings.arabicShaping
            || rows == null || rows.isEmpty() || fontr$failed) {
            return;
        }
        try {
            if (fontr$ctor == null) {
                Object sample = null;
                for (Object row : rows) {
                    if (row instanceof List && !((List<?>) row).isEmpty()) {
                        sample = ((List<?>) row).get(0);
                        break;
                    }
                }
                if (sample == null) {
                    return;
                }
                Class<?> type = sample.getClass();
                fontr$ctor = type.getConstructor(long.class, long.class, String.class, float.class, float.class);
                fontr$text = type.getMethod("getText");
                fontr$x = type.getMethod("getX");
                fontr$ink = type.getMethod("getInk");
                fontr$start = type.getMethod("getStartMs");
                fontr$end = type.getMethod("getEndMs");
            }

            for (Object rowObject : rows) {
                if (!(rowObject instanceof List)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<Object> row = (List<Object>) rowObject;
                if (row.isEmpty()) {
                    continue;
                }
                boolean arabic = false;
                float width = 0.0F;
                for (Object placed : row) {
                    String text = (String) fontr$text.invoke(placed);
                    if (!arabic && ArabicText.containsArabic(text)) {
                        arabic = true;
                    }
                    float x = (Float) fontr$x.invoke(placed);
                    float ink = (Float) fontr$ink.invoke(placed);
                    width = Math.max(width, x + ink);
                }
                if (!arabic) {
                    continue;
                }
                List<Object> mirrored = new ArrayList<Object>(row.size());
                for (int i = row.size() - 1; i >= 0; i--) {
                    Object placed = row.get(i);
                    long start = (Long) fontr$start.invoke(placed);
                    long end = (Long) fontr$end.invoke(placed);
                    String text = (String) fontr$text.invoke(placed);
                    float x = (Float) fontr$x.invoke(placed);
                    float ink = (Float) fontr$ink.invoke(placed);
                    mirrored.add(fontr$ctor.newInstance(start, end, text, width - (x + ink), ink));
                }
                row.clear();
                row.addAll(mirrored);
                if (!fontr$logged) {
                    fontr$logged = true;
                    FontrEngine.INSTANCE.log("HUD lyrics RTL layout active");
                }
            }
        } catch (Throwable t) {
            fontr$failed = true;
            FontrEngine.INSTANCE.log("HUD lyrics RTL layout failed: " + t);
        }
    }
}
