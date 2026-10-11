package com.xtri6.grimstorage.client;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * The search box of a recipe viewer (JEI), when one is installed. The JEI plugin fills these in when JEI starts, so
 * nothing here touches JEI classes and the terminal works the same without it.
 */
public final class RecipeViewerSearch {
    @Nullable
    private static Supplier<String> getter;
    @Nullable
    private static Consumer<String> setter;

    private RecipeViewerSearch() {}

    public static void connect(Supplier<String> get, Consumer<String> set) {
        getter = get;
        setter = set;
    }

    public static boolean available() {
        return getter != null && setter != null;
    }

    @Nullable
    public static String get() {
        try {
            return getter == null ? null : getter.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void set(String text) {
        try {
            if (setter != null) {
                setter.accept(text);
            }
        } catch (RuntimeException ignored) {
            // the viewer is reloading; try again on the next change
        }
    }
}
