package com.xtri6.grimstorage.block;

import com.xtri6.grimstorage.storage.NetworkManager;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Marker for blocks that join a storage network just by touching it. */
public interface NetworkMember {
    /** Whether this block passes the network through {@code side}. Buses do not connect through their front. */
    default boolean connectsTo(BlockState state, Direction side) {
        return true;
    }

    /** True if the network passes between {@code a} and the block on its {@code side}. */
    static boolean linked(BlockState a, Direction side, BlockState b) {
        return a.getBlock() instanceof NetworkMember ma && b.getBlock() instanceof NetworkMember mb
                && ma.connectsTo(a, side) && mb.connectsTo(b, side.getOpposite());
    }

    /** Call from onPlace/onRemove: only a real block change (not a state change) reshapes networks. */
    static void changed(BlockState a, BlockState b) {
        if (!a.is(b.getBlock())) {
            NetworkManager.markDirty();
        }
    }
}
