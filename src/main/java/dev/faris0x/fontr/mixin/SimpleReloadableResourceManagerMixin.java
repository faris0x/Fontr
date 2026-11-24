package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrEngine;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.manager.SimpleReloadableResourceManager;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Outer vanilla resource manager; covers implementations that bypass FallbackResourceManager. */
@Mixin(SimpleReloadableResourceManager.class)
public abstract class SimpleReloadableResourceManagerMixin {
    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true, require = 0)
    private void fontr$resource(Identifier location, CallbackInfoReturnable<Resource> cir) {
        Resource replacement = FontrEngine.INSTANCE.resourceFor(location, (ResourceManager) (Object) this);
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}
