// SPDX-License-Identifier: GPL-3.0-only

package dev.faris0x.fontr.compat;

import dev.faris0x.fontr.FontrSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers the Fontr faces with PolyCompose so Compose UI/HUD text matches the
 * in-game font. Bold/italic family names get the real faces when available.
 */
public final class PolyComposeFontBridge {
    private static final Logger LOGGER = LogManager.getLogger("Fontr");
    private static final String MANAGER = "org.polyfrost.compose.render.FontManager";

    private static final String[] REGULAR = {
        "fontr", "poppins", "poppins-regular", "poppins-thin", "poppins-medium"
    };
    private static final String[] BOLD = {
        "poppins-bold", "poppins-semibold", "poppins-black"
    };
    private static final String[] ITALIC = {
        "poppins-italic", "poppins-thin-italic", "poppins-medium-italic"
    };
    private static final String[] BOLD_ITALIC = {
        "poppins-bold-italic", "poppins-semibold-italic", "poppins-black-italic"
    };

    private static volatile boolean installed;
    private static volatile boolean unavailable;
    private static volatile Map<String, byte[]> expected = new LinkedHashMap<String, byte[]>();

    private PolyComposeFontBridge() {
    }

    public static void install(Path regular) {
        install(regular, null, null, null);
    }

    public static synchronized void install(Path regular, Path bold, Path italic, Path boldItalic) {
        if (!FontrSettings.applyToPolyCompose) {
            return;
        }
        try {
            byte[] regularBytes = Files.readAllBytes(regular);
            byte[] boldBytes = readOr(bold, regularBytes);
            byte[] italicBytes = readOr(italic, regularBytes);
            byte[] boldItalicBytes = readOr(boldItalic, regularBytes);

            Map<String, byte[]> aliases = new LinkedHashMap<String, byte[]>();
            put(aliases, REGULAR, regularBytes);
            put(aliases, BOLD, boldBytes);
            put(aliases, ITALIC, italicBytes);
            put(aliases, BOLD_ITALIC, boldItalicBytes);

            Class<?> managerClass = Class.forName(MANAGER);
            Object manager = managerClass.getField("INSTANCE").get(null);
            Method register = managerClass.getMethod("register", String.class, byte[].class, boolean.class);
            Method setDefault = managerClass.getMethod("setDefault", String.class);

            for (Map.Entry<String, byte[]> alias : aliases.entrySet()) {
                register.invoke(manager, alias.getKey(), alias.getValue(), false);
            }
            setDefault.invoke(manager, "fontr");
            expected = aliases;
            installed = true;
            unavailable = false;
            LOGGER.info("[Fontr] Registered font with PolyCompose");
        } catch (ClassNotFoundException e) {
            unavailable = true;
            LOGGER.info("[Fontr] PolyCompose not present; skipping UI font registration");
        } catch (Throwable t) {
            LOGGER.warn("[Fontr] PolyCompose font registration failed: " + t);
        }
    }

    private static void put(Map<String, byte[]> map, String[] names, byte[] bytes) {
        for (String name : names) {
            map.put(name, bytes);
        }
    }

    private static byte[] readOr(Path path, byte[] fallback) {
        if (path == null) {
            return fallback;
        }
        try {
            return Files.readAllBytes(path);
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static boolean isInstalled() {
        return installed;
    }

    /** Re-registers aliases after OneConfig or a resource reload replaced them. */
    public static void reassert() {
        Map<String, byte[]> aliases = expected;
        if (!installed || unavailable || aliases.isEmpty() || !FontrSettings.applyToPolyCompose) {
            return;
        }
        try {
            Class<?> managerClass = Class.forName(MANAGER);
            Object manager = managerClass.getField("INSTANCE").get(null);
            Method fontData = managerClass.getMethod("fontData", String.class);
            Method register = managerClass.getMethod("register", String.class, byte[].class, boolean.class);
            boolean changed = false;
            for (Map.Entry<String, byte[]> alias : aliases.entrySet()) {
                Object existing = fontData.invoke(manager, alias.getKey());
                if (existing != alias.getValue()) {
                    register.invoke(manager, alias.getKey(), alias.getValue(), false);
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
