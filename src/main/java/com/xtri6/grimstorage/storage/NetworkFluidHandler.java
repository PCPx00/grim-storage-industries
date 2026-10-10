package com.xtri6.grimstorage.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The network's fluids as a standard fluid handler, for pipes, tanks and buckets. Anything can be filled in;
 * draining is allowed only when {@code canDrain} says so. If {@code only} is set, generic drains take that fluid.
 */
public final class NetworkFluidHandler implements IFluidHandler {
    private final Supplier<StorageNetwork> network;
    private final BooleanSupplier canDrain;
    @Nullable
    private final BulkKey only;

    public NetworkFluidHandler(Supplier<StorageNetwork> network, BooleanSupplier canDrain, @Nullable BulkKey only) {
        this.network = network;
        this.canDrain = canDrain;
        this.only = only;
    }

    private List<BulkKey> keys(StorageNetwork net) {
        return net == null ? List.of() : new ArrayList<>(net.bulkContents(BulkKind.FLUID).keySet());
    }

    @Override
    public int getTanks() {
        return keys(network.get()).size() + 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        StorageNetwork net = network.get();
        List<BulkKey> keys = keys(net);
        if (tank < 0 || tank >= keys.size()) {
            return FluidStack.EMPTY;
        }
        return keys.get(tank).fluid((int) Math.min(Integer.MAX_VALUE, net.countBulk(keys.get(tank))));
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        StorageNetwork net = network.get();
        return net == null || resource.isEmpty() ? 0 : (int) net.insertBulk(BulkKey.fluid(resource), resource.getAmount(), action.simulate());
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        StorageNetwork net = network.get();
        BulkKey key = BulkKey.fluid(resource);
        if (net == null || key == null || !canDrain.getAsBoolean() || (only != null && !only.equals(key))) {
            return FluidStack.EMPTY;
        }
        long got = net.extractBulk(key, resource.getAmount(), action.simulate());
        return got <= 0 ? FluidStack.EMPTY : key.fluid((int) got);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        StorageNetwork net = network.get();
        if (net == null || !canDrain.getAsBoolean() || maxDrain <= 0) {
            return FluidStack.EMPTY;
        }
        BulkKey key = only;
        if (key == null) {
            List<BulkKey> keys = keys(net);
            if (keys.isEmpty()) {
                return FluidStack.EMPTY;
            }
            key = keys.get(0);
        }
        long got = net.extractBulk(key, maxDrain, action.simulate());
        return got <= 0 ? FluidStack.EMPTY : key.fluid((int) got);
    }
}
