package dev.faris0x.fontr;

import dev.faris0x.fontr.config.FontrConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Fontr client entrypoint. */
public final class Fontr implements ClientModInitializer {
    private static final Logger LOGGER = LogManager.getLogger("Fontr");

    @Override
    public void onInitializeClient() {
        if (FabricLoader.getInstance().isModLoaded("oneconfigv1")) {
            try {
                FontrConfig.INSTANCE.register();
                FontrConfig.refresh();
                FontrEngine.INSTANCE.setSettingsPoller(FontrConfig::refresh);
            } catch (Throwable t) {
                LOGGER.warn("[Fontr] OneConfig page unavailable: " + t);
            }
        } else {
            LOGGER.info("[Fontr] OneConfig not present; using default settings");
        }

        try {
            dev.faris0x.fontr.compat.FontrPacks.init();
            FontrEngine.INSTANCE.init();
            LOGGER.info("[Fontr] Loaded");
        } catch (Throwable t) {
            LOGGER.warn("[Fontr] Initialisation failed: " + t);
        }
    }
}
