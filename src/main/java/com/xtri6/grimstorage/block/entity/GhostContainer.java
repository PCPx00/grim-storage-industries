package com.xtri6.grimstorage.block.entity;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A filter-style container: it only remembers which items you picked, it never holds real items. */
public class GhostContainer implements Container {
    private final NonNullList<ItemStack> items;
    private final Runnable onChange;
    private final boolean counted;

    public GhostContainer(int size, boolean counted, Runnable onChange) {
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
        this.counted = counted;
        this.onChange = onChange;
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    /** Counted ghosts (processing cards) keep stack sizes; others are always count 1. */
    public boolean counted() {
        return counted;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(counted ? Math.max(1, Math.min(stack.getCount(), 64)) : 1));
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public void setChanged() {
        onChange.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }
}
