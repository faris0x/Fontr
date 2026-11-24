package dev.faris0x.fontr.mixin;

import dev.faris0x.fontr.FontrEngine;
import dev.faris0x.fontr.FontrSettings;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.AbstractTexture;
import net.minecraft.client.render.texture.SimpleTexture;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla uploads font sheets with GL_NEAREST and no mipmaps, which makes a
 * high-resolution sheet look worse than the 128px one. Use linear filtering and
 * a short mip chain so the extra resolution actually reaches the screen.
 */
@Mixin(SimpleTexture.class)
public abstract class SimpleTextureFilterMixin {
    @Shadow
    protected Identifier location;

    private static boolean fontr$logged;

    @Inject(method = "load", at = @At("RETURN"), require = 0)
    private void fontr$filter(CallbackInfo ci) {
        if (location == null || !location.toString().contains("/font/")) {
            return;
        }
        int id;
        try {
            id = ((AbstractTexture) (Object) this).getGlId();
        } catch (Throwable t) {
            return;
        }
        if (id <= 0) {
            return;
        }

        boolean mipmaps = FontrSettings.mipmaps && GLContext.getCapabilities().OpenGL30;
        GlStateManager.bindTexture(id);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
            mipmaps ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
        if (mipmaps) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 4);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }
        if (!fontr$logged) {
            fontr$logged = true;
            FontrEngine.INSTANCE.log("Applied LINEAR" + (mipmaps ? "+mipmaps" : "")
                + " filtering to " + location);
        }
    }
}
