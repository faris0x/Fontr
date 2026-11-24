package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrSettings;
import org.polyfrost.oneconfig.api.hud.v1.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * VanillaHud captures vanilla text runs together with their x positions and
 * re-draws them with PolyFont. PolyFont advances differ from the captured
 * positions, which makes colour segments overlap.
 *
 * <p>For VanillaHud instances, force the Minecraft text path so the captured
 * positions and the glyph advances both come from Fontr's sheets.</p>
 */
@Mixin(targets = "org.polyfrost.oneconfig.api.hud.v1.Hud", remap = false)
public abstract class VanillaHudFontMixin {
    @Inject(method = "getFont", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void fontr$font(CallbackInfoReturnable<Font> cir) {
        if (!FontrSettings.enabled) {
            return;
        }
        if (getClass().getName().startsWith("org.polyfrost.vanillahud.")) {
            cir.setReturnValue(Font.Minecraft);
        }
    }
}
