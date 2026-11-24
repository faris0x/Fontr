package dev.faris0x.fontr.compat;

import dev.faris0x.fontr.config.FontrConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Registers the Fontr TTF with OneClient's PolyCompose/Skia text stack.
 *
 * <p>PolyCompose keeps its own font registry ({@code FontManager}) and resolves
 * Compose text through it. Vanilla {@code TextRenderer} hooks cannot reach that
 * path, which is why OneConfig UI/HUD modules kept their old fonts. Everything
 * here is reflective so Fontr still builds and runs without PolyCompose.</p>
 */
public final class PolyComposeFontBridge {
    private static final Logger LOGGER = LogManager.getLogger("Fontr");
    private static final String MANAGER = "org.polyfrost.compose.render.FontManager";
    private static final String DEFAULT_KEY = "__default__";
    private static final String FONTR_KEY = "fontr";

    /**
     * OneConfig exposes its HUD font dropdown as Minecraft/Poppins. Enum values
     * cannot be added at runtime, so Fontr occupies the Poppins slot: every
     * Poppins variant resolves to the user's TTF. The dropdown label is changed
     * separately by {@link HudFontSelector}.
     */
    private static final String[] FAMILIES = {
        "poppins", "poppins-italic",
        "poppins-thin", "poppins-thin-italic",
        "poppins-medium", "poppins-medium-italic",
        "poppins-semibold", "poppins-bold", "poppins-bold-italic",
        "poppins-black", "poppins-black-italic"
    };

    private static volatile boolean installed;
    private static volatile boolean unavailable;
    private static byte[] installedBytes;
    private static Path installedPath;
    private static int tickCounter;

    private PolyComposeFontBridge() {
    }

    /** Called after a font has been loaded from disk. */
    public static void install(Path path, boolean pixelAligned) {
        if (!FontrConfig.applyToPolyCompose) {
            return;
        }
        try {
            installedPath = path;
            byte[] bytes = Files.readAllBytes(path);
            install(bytes, pixelAligned);
        } catch (Throwable t) {
            log("PolyCompose could not read font: " + t);
        }
    }

    private static synchronized void install(byte[] bytes, boolean pixelAligned) {
        if (!FontrConfig.applyToPolyCompose) {
            return;
        }
        try {
            Class<?> manager = Class.forName(MANAGER);
            Field instanceField = manager.getField("INSTANCE");
            Object instance = instanceField.get(null);
            Method register = manager.getMethod("register", String.class, byte[].class, boolean.class);
            Method setDefault = manager.getMethod("setDefault", String.class);

            register.invoke(instance, FONTR_KEY, bytes, pixelAligned);
            for (String family : FAMILIES) {
                register.invoke(instance, family, bytes, pixelAligned);
            }
            setDefault.invoke(instance, FONTR_KEY);

            installedBytes = bytes;
            installed = true;
            unavailable = false;
            log("Registered Fontr with PolyCompose (pixelAligned=" + pixelAligned + ")");
        } catch (ClassNotFoundException e) {
            unavailable = true;
            if (!installed) {
                log("PolyCompose not present; skipping Compose font registration");
            }
        } catch (Throwable t) {
            log("PolyCompose font registration failed: " + t);
        }
    }

    /**
     * Cheap watchdog. OneClient can call {@code setDefault} later during startup,
     * which would silently restore its bundled font. Called from the render path;
     * only performs reflection every N calls.
     */
    public static void tick() {
        if (!installed || unavailable || !FontrConfig.applyToPolyCompose) {
            return;
        }
        if ((++tickCounter & 0xFF) != 0) {
            return;
        }
        HudFontSelector.install();
        try {
            Class<?> manager = Class.forName(MANAGER);
            Object instance = manager.getField("INSTANCE").get(null);
            Method fontData = manager.getMethod("fontData", String.class);
            boolean changed = fontData.invoke(instance, DEFAULT_KEY) != installedBytes;
            if (changed) {
                manager.getMethod("setDefault", String.class).invoke(instance, FONTR_KEY);
                log("Re-asserted Fontr as PolyCompose default font");
            }
            // PolyCompose registers Inter after mods initialise. If that
            // replaced any of our aliases, put them back.
            Method register = manager.getMethod("register", String.class, byte[].class, boolean.class);
            for (String family : FAMILIES) {
                if (fontData.invoke(instance, family) != installedBytes) {
                    register.invoke(instance, family, installedBytes, FontrConfig.pixelSnap);
                    changed = true;
                }
            }
            if (changed) {
                log("Re-asserted Fontr PolyCompose font aliases");
            }
        } catch (Throwable ignored) {
            unavailable = true;
        }
    }

    /** Re-register all aliases using the last installed TTF. */
    public static void reassert() {
        byte[] bytes = installedBytes;
        if (bytes != null) {
            install(bytes, FontrConfig.pixelSnap);
        }
    }

    /** True when PolyCompose has been found and the font registered. */
    public static boolean isInstalled() {
        return installed;
    }

    public static Path installedPath() {
        return installedPath;
    }

    private static void log(String message) {
        if (FontrConfig.debug) {
            LOGGER.info("[Fontr] " + message);
        }
    }
}
