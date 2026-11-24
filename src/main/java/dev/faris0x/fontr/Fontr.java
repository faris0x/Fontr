package dev.faris0x.fontr;

import dev.faris0x.fontr.config.FontrConfig;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fontr client entrypoint.
 *
 * <p>The actual font work lives in {@link FontrEngine}. This entrypoint only
 * registers the OneConfig page and lets the engine initialise lazily on the
 * first text operation.</p>
 */
public final class Fontr implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        try {
            FontrConfig.INSTANCE.register();
            FontrEngine.INSTANCE.warmUp();
            dev.faris0x.fontr.compat.HudFontSelector.install();
            dev.faris0x.fontr.compat.HudFontSelector.refreshLabel();
            FontrEngine.INSTANCE.log("Fontr loaded");
        } catch (Throwable t) {
            System.out.println("[Fontr] OneConfig registration failed: " + t);
        }
    }
}
