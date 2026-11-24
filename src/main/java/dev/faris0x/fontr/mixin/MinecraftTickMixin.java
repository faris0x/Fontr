// SPDX-License-Identifier: GPL-3.0-only

package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrEngine;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Polls the OneConfig page so a changed font or resolution reloads the sheets. */
@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin {
    @Inject(method = "tick()V", at = @At("HEAD"), require = 0)
    private void fontr$tick(CallbackInfo ci) {
        FontrEngine.INSTANCE.tick();
    }
}
