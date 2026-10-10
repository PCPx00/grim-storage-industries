package com.xtri6.grimstorage.block.entity;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.storage.NetworkManager;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Base for network block entities: loading or unloading one reshapes the network. */
public abstract class NetworkBlockEntity extends BlockEntity {
    protected NetworkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            NetworkManager.markDirty();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        NetworkManager.markDirty();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        NetworkManager.markDirty();
    }

    @Nullable
    public StorageNetwork network() {
        return level instanceof ServerLevel ? NetworkManager.get(level, worldPosition) : null;
    }

    /** An item handler that saves the block entity when it changes and can reshape the network. */
    protected ItemStackHandler handler(int size, int limit, boolean reshapesNetwork, java.util.function.Predicate<ItemStack> valid) {
        return new ItemStackHandler(size) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (reshapesNetwork) {
                    NetworkManager.markDirty();
                }
                NetworkBlockEntity.this.onSlotChanged(slot);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return valid.test(stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return limit;
            }
        };
    }

    protected void onSlotChanged(int slot) {}

    protected static void drop(Level level, BlockPos pos, ItemStackHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s.copy());
                handler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    protected static void saveGhosts(CompoundTag tag, String key, NonNullList<ItemStack> ghosts, HolderLookup.Provider registries) {
        CompoundTag inner = new CompoundTag();
        net.minecraft.world.ContainerHelper.saveAllItems(inner, ghosts, registries);
        tag.put(key, inner);
    }

    protected static void loadGhosts(CompoundTag tag, String key, NonNullList<ItemStack> ghosts, HolderLookup.Provider registries) {
        for (int i = 0; i < ghosts.size(); i++) {
            ghosts.set(i, ItemStack.EMPTY);
        }
        net.minecraft.world.ContainerHelper.loadAllItems(tag.getCompound(key), ghosts, registries);
    }
}
