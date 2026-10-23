package com.xtri6.grimstorage.block.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.storage.NetworkManager;
import com.xtri6.grimstorage.storage.WirelessDevices;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** Wireless Machine Bus: a Machine Bus that joins its network by binding to it, not by cable. */
public class WirelessMachineBusBlockEntity extends MachineBusBlockEntity implements WirelessDevice {
    @Nullable
    private UUID bound;

    public WirelessMachineBusBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.WIRELESS_MACHINE_BUS_ENTITY.get(), pos, state);
    }

    @Nullable
    @Override
    public UUID boundNetwork() {
        return bound;
    }

    @Override
    public void bindNetwork(@Nullable UUID network) {
        bound = network;
        setChanged();
        NetworkManager.markDirty();
    }

    /** The one range card slot. A Machine Bus has no speed upgrades, so this is its only extra slot. */
    private final net.neoforged.neoforge.items.ItemStackHandler range = handler(1, 1, true,
            s -> s.getItem() instanceof com.xtri6.grimstorage.item.RangeCardItem);

    public net.neoforged.neoforge.items.ItemStackHandler rangeHandler() {
        return range;
    }

    /** 0 connected, 1 not bound to a network, 2 bound but has no range card, so it never joins the network. */
    public int wirelessStatus() {
        if (bound == null) {
            return 1;
        }
        return rangeHandler().getStackInSlot(0).isEmpty() ? 2 : 0;
    }

    @Override
    public int wirelessRange() {
        var tier = com.xtri6.grimstorage.item.RangeCardItem.best(range);
        return tier == null ? 0 : tier.range();
    }

    @Override
    public boolean infiniteReach() {
        var tier = com.xtri6.grimstorage.item.RangeCardItem.best(range);
        return tier != null && tier.infinite();
    }

    @Override
    public void dropContents(net.minecraft.world.level.Level lvl, net.minecraft.core.BlockPos pos) {
        super.dropContents(lvl, pos);
        drop(lvl, pos, range);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        WirelessDevices.save(tag, bound);
        tag.put("range", range.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bound = WirelessDevices.load(tag);
        range.deserializeNBT(registries, tag.getCompound("range"));
    }
}
