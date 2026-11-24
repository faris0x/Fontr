package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrEngine;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ornithe Standard Libraries adapts its pack system to Minecraft's
 * ResourceManager through this class. OneClient (and any OSL-based 1.8.9
 * client) reads every font asset from it, including Argentum's text batcher.
 *
 * <p>{@code remap = false} because OSL is not part of Minecraft's mappings; the
 * method name is the intermediary name of {@code ResourceManager#getResource}.</p>
 */
@Mixin(targets = "net.ornithemc.osl.resource.loader.impl.adapter.ResourceManagerAdapter", remap = false)
public abstract class OslResourceManagerAdapterMixin {
    @Inject(method = "m_47960211", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void fontr$resource(Identifier location, CallbackInfoReturnable<Resource> cir) {
        Resource replacement = FontrEngine.INSTANCE.resourceFor(location, (ResourceManager) (Object) this);
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}
