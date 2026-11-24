package dev.faris0x.fontr.compat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import sun.misc.Unsafe;

/**
 * OneConfig's HUD font selector is an enum with only Minecraft and Poppins.
 * Enums cannot be extended normally, so append a real Fontr constant to the
 * enum's $VALUES/$ENTRIES arrays. The dropdown then renders the third value
 * exactly like any other enum option.
 */
public final class FontEnumInjector {
    private static final Logger LOGGER = LogManager.getLogger("Fontr");
    private static final String ENUM = "org.polyfrost.oneconfig.api.hud.v1.Font";

    private static Class<?> fontClass;
    private static Object minecraft;
    private static Object poppins;
    private static Object fontr;
    private static boolean attempted;

    private FontEnumInjector() {
    }

    public static synchronized void install() {
        if (attempted) {
            return;
        }
        attempted = true;
        try {
            fontClass = Class.forName(ENUM);
            Object[] values = (Object[]) fontClass.getMethod("values").invoke(null);
            for (Object value : values) {
                if ("Minecraft".equals(String.valueOf(value))) {
                    minecraft = value;
                } else if ("Poppins".equals(String.valueOf(value))) {
                    poppins = value;
                } else if ("Fontr".equals(String.valueOf(value))) {
                    fontr = value;
                }
            }
            if (fontr != null) {
                return;
            }

            Unsafe unsafe = unsafe();
            fontr = unsafe.allocateInstance(fontClass);
            unsafe.putObject(fontr, unsafe.objectFieldOffset(Enum.class.getDeclaredField("name")), "Fontr");
            unsafe.putInt(fontr, unsafe.objectFieldOffset(Enum.class.getDeclaredField("ordinal")), values.length);

            Object[] nextValues = Arrays.copyOf(values, values.length + 1);
            nextValues[values.length] = fontr;
            setStatic(fontClass.getDeclaredField("$VALUES"), nextValues, unsafe);

            // Kotlin 1.9+ enums also have a cached EnumEntries list.
            try {
                Field entriesField = fontClass.getDeclaredField("$ENTRIES");
                Object entries = unsafe.getObject(unsafe.staticFieldBase(entriesField),
                                                  unsafe.staticFieldOffset(entriesField));
                List<Object> nextEntries = new ArrayList<Object>();
                for (Object value : values) {
                    nextEntries.add(value);
                }
                nextEntries.add(fontr);
                Object entryList = nextEntries;
                try {
                    Class<?> enumEntries = Class.forName("kotlin.enums.EnumEntriesKt");
                    Object created = enumEntries.getMethod("enumEntries", List.class)
                        .invoke(null, nextEntries);
                    if (created != null) {
                        entryList = created;
                    }
                } catch (Throwable ignored) {
                }
                setStatic(entriesField, entryList, unsafe);
                LOGGER.info("[Fontr] Updated Kotlin enum entries");
            } catch (NoSuchFieldException ignored) {
            }

            // Class caches enum lookups; clear them so valueOf/getEnumConstants
            // see the new constant.
            clearClassCache("enumConstantDirectory", unsafe);
            clearClassCache("enumConstants", unsafe);

            LOGGER.info("[Fontr] Added Fontr to OneConfig Font enum");
        } catch (Throwable t) {
            attempted = false;
            LOGGER.warn("[Fontr] Could not add Fontr to Font enum: " + t);
        }
    }

    /** Treat Fontr and Poppins as the same enum value for rendering branches. */
    public static boolean equal(Object a, Object b) {
        if (fontr != null && poppins != null) {
            if ((a == fontr && b == poppins) || (a == poppins && b == fontr)) {
                return true;
            }
        }
        return java.util.Objects.equals(a, b);
    }

    public static Object fontr() {
        return fontr;
    }

    /** Map the injected constant onto Poppins for code that checks the enum. */
    public static Object equivalent(Object value) {
        return value == fontr && poppins != null ? poppins : value;
    }

    public static boolean isFontr(Object value) {
        return fontr != null && value == fontr;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setStatic(Field field, Object value, Unsafe unsafe) {
        unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
    }

    private static void clearClassCache(String name, Unsafe unsafe) {
        try {
            Field field = Class.class.getDeclaredField(name);
            unsafe.putObject(Class.class, unsafe.objectFieldOffset(field), null);
        } catch (Throwable ignored) {
        }
    }
}
