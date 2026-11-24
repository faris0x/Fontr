package dev.faris0x.fontr.compat;

import dev.faris0x.fontr.FontrSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers the Fontr TTF with OneConfig's PolyCompose renderer so Compose-based
 * UI (the mod menu, OneConfig HUD modules) matches the in-game font.
 *
 * <p>This is additive: vanilla/Argentum/Cera text is served through the
 * generated font resources, not through this bridge.</p>
 */
public final class PolyComposeFontBridge {
    private static final Logger LOGGER = LogManager.getLogger("Fontr");
    private static final String MANAGER = "org.polyfrost.compose.render.FontManager";
    private static final String FONTR_KEY = "fontr";

    private static final String[] ALIASES = {
        "poppins", "poppins-italic", "poppins-regular",
        "poppins-thin", "poppins-thin-italic",
        "poppins-medium", "poppins-medium-italic",
        "poppins-semibold", "poppins-semibold-italic",
        "poppins-bold", "poppins-bold-italic",
        "poppins-black", "poppins-black-italic"
    };

    private static volatile boolean installed;
    private static volatile boolean unavailable;

    private PolyComposeFontBridge() {
    }

    public static void install(Path path) {
        if (!FontrSettings.applyToPolyCompose || installed) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            Class<?> managerClass = Class.forName(MANAGER);
            Object manager = managerClass.getField("INSTANCE").get(null);
            Method register = managerClass.getMethod("register", String.class, byte[].class, boolean.class);
            Method setDefault = managerClass.getMethod("setDefault", String.class);

            register.invoke(manager, FONTR_KEY, bytes, false);
            for (String alias : ALIASES) {
                register.invoke(manager, alias, bytes, false);
            }
            setDefault.invoke(manager, FONTR_KEY);
            installed = true;
            LOGGER.info("[Fontr] Registered font with PolyCompose");
        } catch (ClassNotFoundException e) {
            unavailable = true;
            LOGGER.info("[Fontr] PolyCompose not present; skipping UI font registration");
        } catch (Throwable t) {
            LOGGER.warn("[Fontr] PolyCompose font registration failed: " + t);
        }
    }

    public static boolean isInstalled() {
        return installed;
    }

    /** Re-registers aliases after a resource reload, in case another mod replaced them. */
    public static void reassert(Path path) {
        if (!installed || unavailable || !FontrSettings.applyToPolyCompose) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            Class<?> managerClass = Class.forName(MANAGER);
            Object manager = managerClass.getField("INSTANCE").get(null);
            Method fontData = managerClass.getMethod("fontData", String.class);
            Method register = managerClass.getMethod("register", String.class, byte[].class, boolean.class);
            List<String> names = new ArrayList<String>();
            names.add(FONTR_KEY);
            for (String alias : ALIASES) {
                names.add(alias);
            }
            boolean changed = false;
            for (String name : names) {
                Object existing = fontData.invoke(manager, name);
                if (existing != bytes) {
                    register.invoke(manager, name, bytes, false);
                    changed = true;
                }
            }
            if (changed) {
                LOGGER.info("[Fontr] Re-registered PolyCompose aliases");
            }
        } catch (Throwable t) {
            unavailable = true;
        }
    }
}
