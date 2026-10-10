package com.xtri6.grimstorage.menu;

import javax.annotation.Nullable;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Shared menu plumbing: the player inventory, ghost (filter) slots that copy whatever you click them with,
 * and shift-click moving between the player and the block's real slots.
 */
public abstract class GrimMenu extends AbstractContainerMenu {
    protected int playerStart = -1;
    protected int playerEnd = -1;

    protected GrimMenu(@Nullable MenuType<?> type, int id) {
        super(type, id);
    }

    /** A slot that shows an item but never holds a real one. */
    public static class GhostSlot extends Slot {
        private final boolean counted;

        public GhostSlot(Container container, int index, int x, int y, boolean counted) {
            super(container, index, x, y);
            this.counted = counted;
        }

        public boolean counted() {
            return counted;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isFake() {
            return true;
        }
    }

    /** A slot that only displays something (a recipe result preview). */
    public static class DisplaySlot extends Slot {
        public DisplaySlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isFake() {
            return true;
        }
    }

    protected void addPlayerInventory(Inventory inv, int x, int y) {
        playerStart = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, x + col * 18, y + 58));
        }
        playerEnd = slots.size();
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof GhostSlot ghost) {
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE || type == ClickType.CLONE) {
                ItemStack carried = getCarried();
                ItemStack current = ghost.getItem();
                if (carried.isEmpty()) {
                    if (ghost.counted() && button == 1 && current.getCount() > 1) {
                        ghost.set(current.copyWithCount(current.getCount() - 1));
                    } else {
                        ghost.set(ItemStack.EMPTY);
                    }
                } else if (ghost.counted() && button == 1) {
                    boolean same = ItemStack.isSameItemSameComponents(current, carried);
                    ghost.set(carried.copyWithCount(same ? Math.min(64, current.getCount() + 1) : 1));
                } else {
                    ghost.set(carried.copyWithCount(ghost.counted() ? carried.getCount() : 1));
                }
                onGhostChanged();
            }
            return;
        }
        if (slotId >= 0 && slotId < slots.size() && slots.get(slotId) instanceof DisplaySlot) {
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    /** Called after a ghost slot is changed by a click. */
    protected void onGhostChanged() {}

    /** Shift-click: player inventory -> real block slots (or the first free ghost slot); block slots -> player. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || slot instanceof GhostSlot || slot instanceof DisplaySlot) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index >= playerStart && index < playerEnd) {
            boolean moved = false;
            for (int i = 0; i < playerStart; i++) {
                Slot target = slots.get(i);
                if (!(target instanceof GhostSlot) && !(target instanceof DisplaySlot) && target.mayPlace(stack)) {
                    moved |= moveItemStackTo(stack, i, i + 1, false);
                    if (stack.isEmpty()) {
                        break;
                    }
                }
            }
            if (!moved) {
                ghostFromShiftClick(original);
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return stack.getCount() == original.getCount() ? ItemStack.EMPTY : original;
    }

    private void ghostFromShiftClick(ItemStack stack) {
        for (Slot s : slots) {
            if (s instanceof GhostSlot g && ItemStack.isSameItemSameComponents(g.getItem(), stack)) {
                return;
            }
        }
        for (Slot s : slots) {
            if (s instanceof GhostSlot g && !g.hasItem()) {
                g.set(stack.copyWithCount(g.counted() ? stack.getCount() : 1));
                onGhostChanged();
                return;
            }
        }
    }

    /** Ints over 32767 do not survive vanilla's data-slot sync, so big numbers are sent as two halves. */
    protected static int lo(int v) {
        return v & 0xFFFF;
    }

    protected static int hi(int v) {
        return (v >>> 16) & 0xFFFF;
    }

    protected static int join(int lo, int hi) {
        return ((hi & 0xFFFF) << 16) | (lo & 0xFFFF);
    }
}
