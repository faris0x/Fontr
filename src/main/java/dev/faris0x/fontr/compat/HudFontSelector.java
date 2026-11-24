package dev.faris0x.fontr.compat;

import dev.faris0x.fontr.FontrEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.config.v1.MetaKeys;
import org.polyfrost.oneconfig.api.config.v1.Node;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.TreeObjects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OneConfig HUDs expose a {@code font} dropdown backed by
 * {@code org.polyfrost.oneconfig.api.hud.v1.Font} whose only values are
 * Minecraft and Poppins. Enums cannot be extended at runtime, so Fontr takes
 * over the Poppins slot: Poppins* font keys point at the user's TTF, and the
 * dropdown label for that slot is replaced with the installed font's family.
 */
public final class HudFontSelector {
    private static final Logger LOGGER = LogManager.getLogger("Fontr");
    private static final String FONT_ENUM = "org.polyfrost.oneconfig.api.hud.v1.Font";
    private static final String LABEL = "Fontr";
    private static boolean installed;

    private HudFontSelector() {
    }

    public static void install() {
        if (installed) {
            return;
        }
        FontEnumInjector.install();
        try {
            ConfigManager.addTreeRegistrationListener(HudFontSelector::apply);
            for (Tree tree : ConfigManager.core().trees()) {
                apply(tree);
            }
            PolyComposeFontBridge.reassert();
            installed = true;
            LOGGER.info("[Fontr] HUD font selector ready");
        } catch (Throwable t) {
            LOGGER.warn("[Fontr] Could not hook HUD font selector: " + t);
        }
    }

    /** Call after the font file changes so the selector is rebuilt. */
    public static void refreshLabel() {
        if (!installed) {
            return;
        }
        try {
            for (Tree tree : ConfigManager.core().trees()) {
                apply(tree);
            }
            PolyComposeFontBridge.reassert();
        } catch (Throwable ignored) {
        }
    }

    private static void apply(Tree tree) {
        if (tree == null || tree.map == null) {
            return;
        }
        for (Node node : tree.map.values()) {
            if (node instanceof Tree) {
                apply((Tree) node);
            } else if (node instanceof Property) {
                applyProperty((Property<?>) node);
            }
        }
    }

    private static void applyProperty(Property<?> property) {
        if (!"font".equals(property.getID()) || property.type == null) {
            return;
        }
        if (!FONT_ENUM.equals(property.type.getName())) {
            return;
        }

        List<String> options = optionsOf(property);
        if (options.isEmpty()) {
            return;
        }
        Map<String, String> labels = new HashMap<String, String>();
        for (String option : options) {
            if ("Minecraft".equals(option)) {
                labels.put(option, "Default");
            } else if ("Fontr".equals(option)) {
                labels.put(option, LABEL);
            } else {
                labels.put(option, option);
            }
        }
        try {
            TreeObjects.setOptions(property, options, labels);
        } catch (Throwable ignored) {
        }
    }

    private static List<String> optionsOf(Property<?> property) {
        List<String> options = new ArrayList<String>();
        if (property.type != null && property.type.isEnum()) {
            Object[] constants = property.type.getEnumConstants();
            if (constants != null) {
                for (Object constant : constants) {
                    options.add(String.valueOf(constant));
                }
                return options;
            }
        }
        Object metadata = property.getMetadata(MetaKeys.OPTIONS.getName());
        if (metadata instanceof String[]) {
            for (String value : (String[]) metadata) {
                options.add(value);
            }
        } else if (metadata instanceof Iterable<?>) {
            for (Object value : (Iterable<?>) metadata) {
                if (value != null) {
                    options.add(String.valueOf(value));
                }
            }
        }
        return options;
    }
}
