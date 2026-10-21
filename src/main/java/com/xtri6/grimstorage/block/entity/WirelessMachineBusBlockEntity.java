package com.xtri6.grimstorage.block.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.BalanceConfig;
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

    @Override
    public int wirelessRange() {
        return BalanceConfig.wirelessRange();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        WirelessDevices.save(tag, bound);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bound = WirelessDevices.load(tag);
    }
}
