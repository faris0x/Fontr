package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.compat.FontEnumInjector;
import org.polyfrost.oneconfig.api.hud.v1.Font;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Renders the injected Fontr enum value through the Poppins path without
 * changing the value stored in the config (so the selector keeps showing Fontr).
 */
@Mixin(targets = "org.polyfrost.oneconfig.api.hud.v1.TextHud")
public abstract class TextHudFontMixin {
    private static boolean fontr$logged;

    @Redirect(
        require = 0,
        method = "Content",
        remap = false,
        at = @At(
            value = "INVOKE",
            remap = false,
            target = "Lorg/polyfrost/oneconfig/api/hud/v1/TextHud;getFont()Lorg/polyfrost/oneconfig/api/hud/v1/Font;"
        )
    )
    private Font fontr$renderFont(Hud hud) {
        if (!fontr$logged) {
            fontr$logged = true;
            LogManager.getLogger("Fontr").info("[Fontr] HUD TextHud Fontr redirect active");
        }
        return (Font) FontEnumInjector.equivalent(hud.getFont());
    }
}
