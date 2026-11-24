package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.compat.FontEnumInjector;
import org.polyfrost.oneconfig.api.hud.v1.Font;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mixin(targets = "org.polyfrost.oneconfig.api.hud.v1.HudTextKt")
public abstract class HudTextFontMixin {
    private static boolean fontr$logged;

    @Redirect(
        require = 0,
        method = "HudRunText",
        remap = false,
        at = @At(
            value = "INVOKE",
            remap = false,
            target = "Lorg/polyfrost/oneconfig/api/hud/v1/Hud;getFont()Lorg/polyfrost/oneconfig/api/hud/v1/Font;"
        )
    )
    private static Font fontr$renderFont(Hud hud) {
        if (!fontr$logged) {
            fontr$logged = true;
            LogManager.getLogger("Fontr").info("[Fontr] HUD HudText Fontr redirect active");
        }
        return (Font) FontEnumInjector.equivalent(hud.getFont());
    }
}
