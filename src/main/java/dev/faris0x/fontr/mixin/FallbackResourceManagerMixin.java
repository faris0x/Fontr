package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrEngine;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.FallbackResourceManager;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Serves generated font sheets from vanilla's per-namespace resource manager. */
@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManagerMixin {
    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true, require = 0)
    private void fontr$resource(Identifier location, CallbackInfoReturnable<Resource> cir) {
        Resource replacement = FontrEngine.INSTANCE.resourceFor(location, (ResourceManager) (Object) this);
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}
