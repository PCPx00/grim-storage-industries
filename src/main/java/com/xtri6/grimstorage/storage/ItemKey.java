package com.xtri6.grimstorage.storage;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** An item type plus its components, with count ignored. Safe to use as a map key. */
public final class ItemKey {
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemKey> STREAM_CODEC =
            ItemStack.STREAM_CODEC.map(ItemKey::of, ItemKey::stack);

    private final ItemStack stack;
    private final int hash;

    private ItemKey(ItemStack stack) {
        this.stack = stack;
        this.hash = ItemStack.hashItemAndComponents(stack);
    }

    @Nullable
    public static ItemKey of(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : new ItemKey(stack.copyWithCount(1));
    }

    /** The template stack (count 1). Never modify it; use {@link #toStack(int)} for a working copy. */
    public ItemStack stack() {
        return stack;
    }

    public ItemStack toStack(int count) {
        return stack.copyWithCount(count);
    }

    public Item item() {
        return stack.getItem();
    }

    public int maxStackSize() {
        return stack.getMaxStackSize();
    }

    public boolean matches(ItemStack other) {
        return ItemStack.isSameItemSameComponents(stack, other);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ItemKey k && hash == k.hash && ItemStack.isSameItemSameComponents(stack, k.stack));
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return stack.getItem().toString();
    }
}
