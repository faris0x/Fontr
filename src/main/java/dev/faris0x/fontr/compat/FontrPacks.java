package dev.faris0x.fontr.compat;

import net.ornithemc.osl.resource.loader.api.client.ClientResourceLoaderEvents;
import net.ornithemc.osl.resource.loader.api.resource.pack.PackPosition;
import net.ornithemc.osl.resource.loader.api.resource.repository.ResourcePackRepository;
import net.ornithemc.osl.resource.loader.api.resource.repository.ResourcePackSummary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Registers {@link FontrPack} as a required, fixed, top-of-stack OSL pack. */
public final class FontrPacks {
    private static final Logger LOGGER = LogManager.getLogger("Fontr");

    private static volatile boolean installed;

    private FontrPacks() {
    }

    public static void init() {
        try {
            ClientResourceLoaderEvents.INIT_RESOURCE_PACK_REPOSITORY.register(FontrPacks::install);
            // Fallback in case the repository initialised before mod entrypoints ran.
            ClientResourceLoaderEvents.START_RESOURCE_PACKS_RELOAD.register(FontrPacks::install);
        } catch (Throwable t) {
            LOGGER.warn("[Fontr] Could not register resource pack source: " + t);
        }
    }

    private static synchronized void install(ResourcePackRepository repository) {
        if (installed) {
            return;
        }
        try {
            repository.addSource(summaries -> summaries.accept(ResourcePackSummary.create(
                FontrPack.ID, true, true, PackPosition.TOP, FontrPack::new)));
            installed = true;
        } catch (Throwable t) {
            LOGGER.warn("[Fontr] Could not add font resource pack: " + t);
        }
    }
}
