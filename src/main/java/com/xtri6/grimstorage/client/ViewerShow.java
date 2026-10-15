package com.xtri6.grimstorage.client;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;

/**
 * Opens the installed recipe viewer (JEI, EMI or REI) on an item's recipes. Each viewer plugin connects its own
 * opener when it starts; with no viewer installed, the terminal's R key does nothing.
 */
public final class ViewerShow {
    @Nullable
    private static Consumer<ItemStack> opener;

    private ViewerShow() {}

    public static void connect(Consumer<ItemStack> open) {
        opener = open;
    }

    public static boolean available() {
        return opener != null;
    }

    public static void show(ItemStack stack) {
        try {
            if (opener != null && !stack.isEmpty()) {
                opener.accept(stack.copyWithCount(1));
            }
        } catch (RuntimeException ignored) {
            // the viewer is busy or reloading; the key can be pressed again
        }
    }
}
