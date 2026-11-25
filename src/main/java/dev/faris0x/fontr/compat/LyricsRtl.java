// SPDX-License-Identifier: GPL-3.0-only

package dev.faris0x.fontr.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors EvergreenHUD lyric rows for right-to-left playback.
 *
 * <p>EvergreenHUD lays lyrics out as positioned words ("Placed" entries) from
 * left to right and animates a left-to-right sweep. For Arabic rows the word
 * order and each x position are mirrored inside the row, keeping each word's
 * timestamps so the sweep advances right-to-left. The classes involved are
 * package-private, so they are accessed reflectively.</p>
 */
public final class LyricsRtl {
    private LyricsRtl() {
    }

    /** Returns a mirrored copy of {@code rows}; sets {@code changed[0]} if anything moved. */
    public static List<List<Object>> mirrorRows(List<?> rows, boolean[] changed) {
        List<List<Object>> result = new ArrayList<List<Object>>(rows.size());
        if (rows.isEmpty()) {
            return result;
        }
        Access access = Access.forRow(rows);
        if (access == null) {
            throw new IllegalStateException("unrecognised lyric entry");
        }
        for (Object rowObject : rows) {
            if (!(rowObject instanceof List)) {
                throw new IllegalStateException("lyric row is not a list");
            }
            List<?> row = (List<?>) rowObject;
            if (row.isEmpty() || !containsArabic(row, access)) {
                result.add(new ArrayList<Object>(row));
                continue;
            }

            float width = 0.0F;
            for (Object placed : row) {
                float x = access.x(placed);
                float ink = access.ink(placed);
                width = Math.max(width, x + ink);
            }

            List<Object> mirrored = new ArrayList<Object>(row.size());
            for (int i = row.size() - 1; i >= 0; i--) {
                Object placed = row.get(i);
                mirrored.add(access.create(
                    access.start(placed),
                    access.end(placed),
                    access.text(placed),
                    access.trimmed(placed),
                    width - (access.x(placed) + access.ink(placed)),
                    access.ink(placed)));
            }
            result.add(mirrored);
            changed[0] = true;
        }
        return result;
    }

    private static boolean containsArabic(List<?> row, Access access) {
        for (Object placed : row) {
            if (ArabicText.containsArabicVisual(access.text(placed))) {
                return true;
            }
        }
        return false;
    }

    private static final class Access {
        private static Class<?> cachedType;
        private static Access cached;

        private final Constructor<?> constructor;
        private final boolean hasTrimmed;
        private final Method text;
        private final Method trimmed;
        private final Method x;
        private final Method ink;
        private final Method start;
        private final Method end;

        private Access(Class<?> type) throws Exception {
            Constructor<?> found;
            boolean six = true;
            try {
                found = type.getDeclaredConstructor(long.class, long.class, String.class,
                    String.class, float.class, float.class);
            } catch (NoSuchMethodException e) {
                six = false;
                found = type.getDeclaredConstructor(long.class, long.class, String.class,
                    float.class, float.class);
            }
            found.setAccessible(true);
            constructor = found;
            hasTrimmed = six;
            Method trim = null;
            if (six) {
                try {
                    trim = method(type, "getTrimmed");
                } catch (NoSuchMethodException ignored) {
                }
            }
            trimmed = trim;
            text = method(type, "getText");
            x = method(type, "getX");
            ink = method(type, "getInk");
            start = method(type, "getStartMs");
            end = method(type, "getEndMs");
        }

        private static Method method(Class<?> type, String name) throws Exception {
            Method method = type.getMethod(name);
            method.setAccessible(true);
            return method;
        }

        static Access forRow(List<?> rows) {
            for (Object rowObject : rows) {
                if (!(rowObject instanceof List)) {
                    continue;
                }
                for (Object placed : (List<?>) rowObject) {
                    if (placed == null) {
                        continue;
                    }
                    Class<?> type = placed.getClass();
                    if (cached != null && cachedType == type) {
                        return cached;
                    }
                    try {
                        cached = new Access(type);
                        cachedType = type;
                        return cached;
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
            return null;
        }

        String text(Object placed) {
            try {
                return (String) text.invoke(placed);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        float x(Object placed) {
            return number(placed, x);
        }

        float ink(Object placed) {
            return number(placed, ink);
        }

        long start(Object placed) {
            return ((Number) value(placed, start)).longValue();
        }

        long end(Object placed) {
            return ((Number) value(placed, end)).longValue();
        }

        String trimmed(Object placed) {
            if (trimmed == null) {
                return text(placed).trim();
            }
            try {
                return (String) trimmed.invoke(placed);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        Object create(long startMs, long endMs, String value, String trimmedValue,
            float x, float ink) {
            try {
                if (hasTrimmed) {
                    return constructor.newInstance(startMs, endMs, value, trimmedValue, x, ink);
                }
                return constructor.newInstance(startMs, endMs, value, x, ink);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private float number(Object placed, Method method) {
            return ((Number) value(placed, method)).floatValue();
        }

        private Object value(Object placed, Method method) {
            try {
                return method.invoke(placed);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
