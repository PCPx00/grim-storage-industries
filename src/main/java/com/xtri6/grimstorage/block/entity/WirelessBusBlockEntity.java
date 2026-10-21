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

/** Wireless Import / Export Bus: an ordinary bus that joins its network by binding to it, not by cable. */
public class WirelessBusBlockEntity extends BusBlockEntity implements WirelessDevice {
    @Nullable
    private UUID bound;

    public WirelessBusBlockEntity(BlockPos pos, BlockState state) {
        super(state.is(Registration.WIRELESS_EXPORT_BUS.get()) ? Registration.WIRELESS_EXPORT_BUS_ENTITY.get()
                : Registration.WIRELESS_IMPORT_BUS_ENTITY.get(), pos, state, state.is(Registration.WIRELESS_EXPORT_BUS.get()));
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
