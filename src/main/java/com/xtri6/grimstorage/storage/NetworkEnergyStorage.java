package com.xtri6.grimstorage.storage;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.neoforged.neoforge.energy.IEnergyStorage;

/** The network's energy cells as a standard FE storage. Anything can charge it; discharging is optional. */
public final class NetworkEnergyStorage implements IEnergyStorage {
    private static final BulkKey KEY = BulkKey.flat(BulkKind.ENERGY);
    private final Supplier<StorageNetwork> network;
    private final BooleanSupplier canExtract;

    public NetworkEnergyStorage(Supplier<StorageNetwork> network, BooleanSupplier canExtract) {
        this.network = network;
        this.canExtract = canExtract;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        StorageNetwork net = network.get();
        return net == null ? 0 : (int) net.insertBulk(KEY, toReceive, simulate);
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        StorageNetwork net = network.get();
        return net == null || !canExtract.getAsBoolean() ? 0 : (int) net.extractBulk(KEY, toExtract, simulate);
    }

    @Override
    public int getEnergyStored() {
        StorageNetwork net = network.get();
        return net == null ? 0 : (int) Math.min(Integer.MAX_VALUE, net.countBulk(KEY));
    }

    @Override
    public int getMaxEnergyStored() {
        StorageNetwork net = network.get();
        return net == null ? 0 : (int) Math.min(Integer.MAX_VALUE, net.bulkStats(BulkKind.ENERGY)[1]);
    }

    @Override
    public boolean canExtract() {
        return canExtract.getAsBoolean();
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
