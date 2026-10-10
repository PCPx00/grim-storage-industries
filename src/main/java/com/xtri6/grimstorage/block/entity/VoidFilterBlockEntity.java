package com.xtri6.grimstorage.block.entity;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.storage.ItemKey;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Deletes overflow: when storage is full, items set here are accepted and destroyed instead of rejected. */
public class VoidFilterBlockEntity extends FilterBlockEntity {
    public VoidFilterBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.VOID_FILTER_ENTITY.get(), pos, state);
    }

    public boolean voids(ItemKey key) {
        for (ItemStack s : filters.items()) {
            if (key.matches(s)) {
                return true;
            }
        }
        return false;
    }
}
