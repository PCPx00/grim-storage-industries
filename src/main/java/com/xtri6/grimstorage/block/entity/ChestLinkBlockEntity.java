package com.xtri6.grimstorage.block.entity;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.BusBlock;
import com.xtri6.grimstorage.block.NetworkMember;
import com.xtri6.grimstorage.storage.ItemKey;
import com.xtri6.grimstorage.storage.NetworkManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Chest Link: clips onto a chest, barrel, drawer or any other inventory and makes it part of the network's storage.
 * Its items show in the terminal, crafts use them, and new items can go in. It can be set to only take items in or
 * only give them out, and to fill up before the network's cells.
 */
public class ChestLinkBlockEntity extends NetworkBlockEntity {
    public static final int BOTH = 0;
    public static final int INSERT_ONLY = 1;
    public static final int EXTRACT_ONLY = 2;

    private int mode = BOTH;
    private boolean fillFirst;

    public ChestLinkBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.CHEST_LINK_ENTITY.get(), pos, state);
    }

    public int mode() {
        return mode;
    }

    public boolean fillFirst() {
        return fillFirst;
    }

    public void cycleMode() {
        mode = (mode + 1) % 3;
        setChanged();
        NetworkManager.markDirty();
    }

    public void toggleFillFirst() {
        fillFirst = !fillFirst;
        setChanged();
    }

    public boolean canInsert() {
        return mode != EXTRACT_ONLY;
    }

    public boolean canExtract() {
        return mode != INSERT_ONLY;
    }

    private Direction facing() {
        BlockState st = getBlockState();
        return st.hasProperty(BusBlock.FACING) ? st.getValue(BusBlock.FACING) : Direction.NORTH;
    }

    public BlockPos target() {
        return worldPosition.relative(facing());
    }

    /** The inventory it links, or null. Never another network block (that would make the network contain itself). */
    @Nullable
    public IItemHandler handler() {
        if (level == null || isRemoved()) {
            return null;
        }
        BlockPos t = target();
        if (level.getBlockState(t).getBlock() instanceof NetworkMember || level.getBlockEntity(t) instanceof NetworkBlockEntity) {
            return null;
        }
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, t, facing().getOpposite());
        return h != null ? h : level.getCapability(Capabilities.ItemHandler.BLOCK, t, null);
    }

    /** How many of an item the linked inventory holds, whatever the link's mode. */
    public long rawCount(ItemKey key) {
        IItemHandler h = handler();
        long n = 0;
        for (int i = 0; h != null && i < h.getSlots(); i++) {
            if (key.matches(h.getStackInSlot(i))) {
                n += h.getStackInSlot(i).getCount();
            }
        }
        return n;
    }

    /** What is in the linked inventory, by item. */
    public Map<ItemKey, Long> snapshot() {
        Map<ItemKey, Long> out = new HashMap<>();
        IItemHandler h = handler();
        if (h == null || !canExtract()) {
            return out;
        }
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            ItemKey k = ItemKey.of(s);
            if (k != null) {
                out.merge(k, (long) s.getCount(), Long::sum);
            }
        }
        return out;
    }

    /** {items inside, room for} (room as 64 a slot, or the slot's own limit). */
    public long[] usage() {
        IItemHandler h = handler();
        long used = 0, cap = 0;
        if (h != null) {
            for (int i = 0; i < h.getSlots(); i++) {
                used += h.getStackInSlot(i).getCount();
                cap += Math.min(h.getSlotLimit(i), 64);
            }
        }
        return new long[] {used, cap};
    }

    public long count(ItemKey key) {
        IItemHandler h = handler();
        long n = 0;
        if (h != null) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (key.matches(s)) {
                    n += s.getCount();
                }
            }
        }
        return n;
    }

    /** Puts items in; returns how many fit. {@code onlyIfPresent}: only where this item already is. */
    public long insert(ItemKey key, long amount, boolean simulate, boolean onlyIfPresent) {
        IItemHandler h = handler();
        if (h == null || !canInsert() || amount <= 0 || (onlyIfPresent && count(key) <= 0)) {
            return 0;
        }
        long left = amount;
        int max = key.stack().getMaxStackSize();
        while (left > 0) {
            int n = (int) Math.min(left, max);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(h, key.toStack(n), simulate);
            int in = n - rest.getCount();
            left -= in;
            if (in < n || simulate) {
                // simulate can't stack calls (nothing really went in), so one stack is all we can promise
                break;
            }
        }
        return amount - left;
    }

    /** Takes items out; returns how many. */
    public long extract(ItemKey key, long amount, boolean simulate) {
        IItemHandler h = handler();
        if (h == null || !canExtract() || amount <= 0) {
            return 0;
        }
        long left = amount;
        for (int i = 0; i < h.getSlots() && left > 0; i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!key.matches(s)) {
                continue;
            }
            ItemStack got = h.extractItem(i, (int) Math.min(left, s.getCount()), simulate);
            left -= got.getCount();
        }
        return amount - left;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("mode", mode);
        tag.putBoolean("fillFirst", fillFirst);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = tag.getInt("mode");
        fillFirst = tag.getBoolean("fillFirst");
    }
}
