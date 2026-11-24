package dev.faris0x.fontr.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.faris0x.fontr.FontrEngine;
import dev.faris0x.fontr.compat.ArgentumStyles;
import dev.rdh.argentum.impl.render.gui.TextBatcher;
import dev.rdh.argentum.impl.render.gui.font.FontSet;
import dev.rdh.argentum.impl.render.gui.font.Glyph;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Teaches Argentum's text batcher to use Fontr's real bold/italic sheets.
 *
 * <p>Targets Argentum's own classes at runtime; nothing is patched on disk. The
 * mixin is non-fatal ({@code require = 0}) so a future Argentum refactor simply
 * disables the feature.</p>
 */
@Mixin(targets = "dev.rdh.argentum.impl.render.gui.TextBatcher", remap = false)
public abstract class ArgentumTextBatcherMixin {
    @Shadow
    private float[] widths;

    @Inject(method = "readWidths", at = @At("RETURN"), remap = false, require = 0)
    private void fontr$readWidths(Identifier location, int[] characterWidths, CallbackInfo ci) {
        // Only the main font sheet uses Fontr's one-pixel gap; leave
        // ascii_sga (enchanting table) and any other sheets untouched.
        if (location == null || !location.toString().endsWith("font/ascii.png")) {
            return;
        }
        FontrEngine.INSTANCE.adjustArgentumWidths(this.widths, characterWidths);
        ArgentumStyles.setWidths(this.widths);
    }

    @Inject(method = "clearCaches", at = @At("HEAD"), remap = false, require = 0)
    private void fontr$clearCaches(CallbackInfo ci) {
        ArgentumStyles.invalidate();
    }

    @WrapOperation(
        method = "layout",
        at = @At(value = "INVOKE",
            target = "Ldev/rdh/argentum/impl/render/gui/font/FontSet;glyph(IZ)Ldev/rdh/argentum/impl/render/gui/font/Glyph;"),
        remap = false,
        require = 0
    )
    private Glyph fontr$glyph(FontSet fonts, int codepoint, boolean unicode,
                              Operation<Glyph> original, @Local(argsOnly = true, type = int.class) int style) {
        if (!unicode && codepoint < 256) {
            FontSet styled = ArgentumStyles.setFor(style);
            if (styled != null) {
                return styled.glyph(codepoint, false);
            }
        }
        return original.call(fonts, codepoint, unicode);
    }

    @WrapOperation(
        method = "layout",
        at = @At(value = "INVOKE",
            target = "Ldev/rdh/argentum/impl/render/gui/font/Glyph;advance(Z)F"),
        remap = false,
        require = 0
    )
    private float fontr$advance(Glyph glyph, boolean bold, Operation<Float> original,
                                @Local(argsOnly = true, type = int.class) int style) {
        if (bold && ArgentumStyles.trueBold(style)) {
            return original.call(glyph, false);
        }
        return original.call(glyph, bold);
    }

    @WrapOperation(
        method = "layout",
        at = @At(value = "INVOKE",
            target = "Ldev/rdh/argentum/impl/render/gui/TextBatcher;glyph(Ldev/rdh/argentum/impl/render/gui/font/Glyph;FFZLnet/minecraft/unmapped/C_82920792;)V",
            ordinal = 0),
        remap = false,
        require = 0
    )
    private void fontr$draw(TextBatcher self, Glyph glyph, float x, float y, boolean italic,
                            TextureManager manager, Operation<Void> original,
                            @Local(argsOnly = true, type = int.class) int style) {
        boolean passItalic = italic && !ArgentumStyles.trueItalic(style);
        original.call(self, glyph, x, y, passItalic, manager);
    }

    @WrapOperation(
        method = "layout",
        at = @At(value = "INVOKE",
            target = "Ldev/rdh/argentum/impl/render/gui/TextBatcher;glyph(Ldev/rdh/argentum/impl/render/gui/font/Glyph;FFZLnet/minecraft/unmapped/C_82920792;)V",
            ordinal = 1),
        remap = false,
        require = 0
    )
    private void fontr$drawBold(TextBatcher self, Glyph glyph, float x, float y, boolean italic,
                                TextureManager manager, Operation<Void> original,
                                @Local(argsOnly = true, type = int.class) int style) {
        if (ArgentumStyles.trueBold(style)) {
            return;
        }
        original.call(self, glyph, x, y, italic, manager);
    }
}
