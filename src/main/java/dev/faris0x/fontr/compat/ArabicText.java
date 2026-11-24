package dev.faris0x.fontr.compat;

import com.ibm.icu.text.ArabicShaping;

import java.text.Bidi;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shapes Arabic runs and converts them to visual (RTL) order.
 *
 * <p>Minecraft 1.8.9 draws characters strictly left-to-right and does no shaping,
 * so Arabic has to be rewritten before it reaches {@code TextRenderer}. Formatting
 * codes are preserved and only split the text into independent segments.</p>
 */
public final class ArabicText {
    private static final int CACHE_LIMIT = 512;
    private static final Map<String, String> CACHE =
        new LinkedHashMap<String, String>(256, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > CACHE_LIMIT;
            }
        };

    private ArabicText() {
    }

    /** Transform for HUD/Compose drawing, honouring the Fontr settings. */
    public static String forHud(String text) {
        if (!dev.faris0x.fontr.FontrSettings.enabled
            || !dev.faris0x.fontr.FontrSettings.arabicShaping) {
            return text;
        }
        return transform(text);
    }

    public static String transform(String text) {
        if (text == null || text.isEmpty() || !containsArabic(text)) {
            return text;
        }
        synchronized (CACHE) {
            String cached = CACHE.get(text);
            if (cached != null) {
                return cached;
            }
        }
        String result = process(text);
        synchronized (CACHE) {
            CACHE.put(text, result);
        }
        return result;
    }

    private static String process(String text) {
        StringBuilder out = new StringBuilder(text.length());
        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                flush(out, segment);
                out.append(c).append(text.charAt(i + 1));
                i += 2;
            } else {
                segment.append(c);
                i++;
            }
        }
        flush(out, segment);
        return out.toString();
    }

    private static void flush(StringBuilder out, StringBuilder segment) {
        if (segment.length() == 0) {
            return;
        }
        out.append(shape(segment.toString()));
        segment.setLength(0);
    }

    private static String shape(String text) {
        try {
            String shaped = new ArabicShaping(ArabicShaping.LETTERS_SHAPE).shape(text);
            char[] chars = shaped.toCharArray();
            if (!Bidi.requiresBidi(chars, 0, chars.length)) {
                return shaped;
            }
            Bidi bidi = new Bidi(chars, 0, null, 0, chars.length,
                Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT);
            int runs = bidi.getRunCount();
            byte[] levels = new byte[runs];
            Integer[] order = new Integer[runs];
            for (int i = 0; i < runs; i++) {
                levels[i] = (byte) bidi.getRunLevel(i);
                order[i] = i;
            }
            Bidi.reorderVisually(levels, 0, order, 0, runs);

            StringBuilder visual = new StringBuilder(shaped.length());
            for (int run : order) {
                int start = bidi.getRunStart(run);
                int limit = bidi.getRunLimit(run);
                String part = shaped.substring(start, limit);
                if ((bidi.getRunLevel(run) & 1) != 0) {
                    part = new StringBuilder(part).reverse().toString();
                }
                visual.append(part);
            }
            return visual.toString();
        } catch (Throwable t) {
            return text;
        }
    }

    public static boolean containsArabic(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '\u0600' && c <= '\u06FF')
                || (c >= '\u0750' && c <= '\u077F')
                || (c >= '\u08A0' && c <= '\u08FF')) {
                return true;
            }
        }
        return false;
    }
}
