package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.compat.FontEnumInjector;
import org.polyfrost.oneconfig.api.hud.v1.Font;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "org.polyfrost.oneconfig.api.hud.v1.HudTextKt")
public abstract class HudTextFontMixin {
    @Redirect(
        method = "HudRunText",
        remap = false,
        at = @At(
            value = "INVOKE",
            remap = false,
            target = "Lorg/polyfrost/oneconfig/api/hud/v1/Hud;getFont()Lorg/polyfrost/oneconfig/api/hud/v1/Font;"
        )
    )
    private static Font fontr$renderFont(Hud hud) {
        return (Font) FontEnumInjector.equivalent(hud.getFont());
    }
}
