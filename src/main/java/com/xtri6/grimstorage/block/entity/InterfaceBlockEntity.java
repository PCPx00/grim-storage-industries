package com.xtri6.grimstorage.block.entity;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.ItemKey;
import com.xtri6.grimstorage.storage.NetworkEnergyStorage;
import com.xtri6.grimstorage.storage.NetworkFluidHandler;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Lets pipes, hoppers and other mods talk to the network. Anything can be pushed in. Only the items set in
 * the 9 export slots can be pulled out, so a hopper underneath cannot drain the whole system.
 */
public class InterfaceBlockEntity extends FilterBlockEntity {
    private final IItemHandler handler = new NetworkHandler();
    /** Per {@link BulkKind}: may pipes pull this resource out? Off by default so nothing drains by accident. */
    private final boolean[] exports = new boolean[BulkKind.values().length];
    private final NetworkFluidHandler fluids = new NetworkFluidHandler(this::network, () -> exports(BulkKind.FLUID), null);
    private final NetworkEnergyStorage energy = new NetworkEnergyStorage(this::network, () -> exports(BulkKind.ENERGY));

    public InterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.INTERFACE_ENTITY.get(), pos, state);
    }

    public IItemHandler itemHandler() {
        return handler;
    }

    public NetworkFluidHandler fluidHandler() {
        return fluids;
    }

    public NetworkEnergyStorage energyStorage() {
        return energy;
    }

    public boolean exports(BulkKind kind) {
        return exports[kind.ordinal()];
    }

    public void toggleExport(BulkKind kind) {
        exports[kind.ordinal()] = !exports[kind.ordinal()];
        setChanged();
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        byte[] b = new byte[exports.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (exports[i] ? 1 : 0);
        }
        tag.putByteArray("exports", b);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        byte[] b = tag.getByteArray("exports");
        for (int i = 0; i < exports.length; i++) {
            exports[i] = i < b.length && b[i] != 0;
        }
    }

    private final class NetworkHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return SLOTS + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            StorageNetwork net = network();
            if (net == null || slot >= SLOTS) {
                return ItemStack.EMPTY;
            }
            ItemKey key = ItemKey.of(filters.getItem(slot));
            if (key == null) {
                return ItemStack.EMPTY;
            }
            long n = Math.min(net.count(key), key.maxStackSize());
            return n <= 0 ? ItemStack.EMPTY : key.toStack((int) n);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            StorageNetwork net = network();
            if (net == null || stack.isEmpty()) {
                return stack;
            }
            long in = net.insert(stack, simulate);
            return in >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - (int) in);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            StorageNetwork net = network();
            if (net == null || slot >= SLOTS || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemKey key = ItemKey.of(filters.getItem(slot));
            if (key == null) {
                return ItemStack.EMPTY;
            }
            long got = net.extract(key, Math.min(amount, key.maxStackSize()), simulate);
            return got <= 0 ? ItemStack.EMPTY : key.toStack((int) got);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }
}
