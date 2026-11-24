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

    /** Compose families that should resolve to Fontr. */
    private static final String[] FAMILIES = {
        "sans-serif", "serif", "monospace", "cursive",
        // Minecraft-styled text used by PolyCompose HUD modules.
        "polycompose:minecraft", "polycompose:minecraft-bold"
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
            PolyComposeGlyphSource.install(bytes, FontrConfig.antialias);

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
        PolyComposeGlyphSource.ensureInstalled();
        try {
            Class<?> manager = Class.forName(MANAGER);
            Object instance = manager.getField("INSTANCE").get(null);
            Method fontData = manager.getMethod("fontData", String.class);
            Object current = fontData.invoke(instance, DEFAULT_KEY);
            if (current != installedBytes) {
                manager.getMethod("setDefault", String.class).invoke(instance, FONTR_KEY);
                log("Re-asserted Fontr as PolyCompose default font");
            }
        } catch (Throwable ignored) {
            unavailable = true;
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
