package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.compat.FontEnumInjector;
import org.polyfrost.oneconfig.api.hud.v1.Font;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Renders the injected Fontr enum value through the Poppins path without
 * changing the value stored in the config (so the selector keeps showing Fontr).
 */
@Mixin(targets = "org.polyfrost.oneconfig.api.hud.v1.TextHud")
public abstract class TextHudFontMixin {
    @Redirect(
        method = "Content",
        at = @At(
            value = "INVOKE",
            target = "Lorg/polyfrost/oneconfig/api/hud/v1/Hud;getFont()Lorg/polyfrost/oneconfig/api/hud/v1/Font;"
        )
    )
    private Font fontr$renderFont(Hud hud) {
        return (Font) FontEnumInjector.equivalent(hud.getFont());
    }
}
