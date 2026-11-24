package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrSettings;
import dev.faris0x.fontr.compat.ArabicText;
import net.minecraft.client.render.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Rewrites Arabic strings into shaped presentation forms in visual (RTL) order.
 *
 * <p>This only changes the argument, not the renderer: vanilla, Argentum and the
 * width/wrap helpers all see the same transformed string, so measurement and
 * drawing stay consistent.</p>
 */
@Mixin(TextRenderer.class)
public abstract class TextTransformMixin {
    @ModifyVariable(method = "draw(Ljava/lang/String;FFIZ)I", at = @At("HEAD"),
        argsOnly = true, require = 0)
    private String fontr$draw(String text) {
        return transform(text);
    }

    @ModifyVariable(method = "draw(Ljava/lang/String;III)I", at = @At("HEAD"),
        argsOnly = true, require = 0)
    private String fontr$drawInt(String text) {
        return transform(text);
    }

    @ModifyVariable(method = "drawWithShadow(Ljava/lang/String;FFI)I", at = @At("HEAD"),
        argsOnly = true, require = 0)
    private String fontr$drawShadow(String text) {
        return transform(text);
    }

    @ModifyVariable(method = "getWidth(Ljava/lang/String;)I", at = @At("HEAD"),
        argsOnly = true, require = 0)
    private String fontr$width(String text) {
        return transform(text);
    }

    @ModifyVariable(method = "trim(Ljava/lang/String;I)Ljava/lang/String;", at = @At("HEAD"),
        argsOnly = true, require = 0)
    private String fontr$trim(String text) {
        return transform(text);
    }

    @ModifyVariable(method = "trim(Ljava/lang/String;IZ)Ljava/lang/String;", at = @At("HEAD"),
        argsOnly = true, require = 0)
    private String fontr$trimReverse(String text) {
        return transform(text);
    }

    private static String transform(String text) {
        if (!FontrSettings.enabled || !FontrSettings.arabicShaping) {
            return text;
        }
        return ArabicText.transform(text);
    }
}
