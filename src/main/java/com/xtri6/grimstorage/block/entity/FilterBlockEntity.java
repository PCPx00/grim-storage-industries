package com.xtri6.grimstorage.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** A network block configured with up to 9 ghost items. */
public abstract class FilterBlockEntity extends NetworkBlockEntity {
    public static final int SLOTS = 9;
    protected final GhostContainer filters = new GhostContainer(SLOTS, false, this::setChanged);

    protected FilterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public GhostContainer filters() {
        return filters;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveGhosts(tag, "filters", filters.items(), registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadGhosts(tag, "filters", filters.items(), registries);
    }
}
