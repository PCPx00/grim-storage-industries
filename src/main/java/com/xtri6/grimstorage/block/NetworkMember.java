package com.xtri6.grimstorage.block;

import com.xtri6.grimstorage.storage.NetworkManager;

import net.minecraft.world.level.block.state.BlockState;

/** Marker for blocks that join a storage network just by touching it. */
public interface NetworkMember {
    /** Call from onPlace/onRemove: only a real block change (not a state change) reshapes networks. */
    static void changed(BlockState a, BlockState b) {
        if (!a.is(b.getBlock())) {
            NetworkManager.markDirty();
        }
    }
}
