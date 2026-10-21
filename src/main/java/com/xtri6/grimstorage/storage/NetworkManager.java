package com.xtri6.grimstorage.storage;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;

/**
 * Tracks which network each block belongs to. Any network block being placed, broken, loaded or unloaded bumps
 * the epoch, and every controller rebuilds its network on its next tick. Simple, and cheap enough at mod scale.
 */
public final class NetworkManager {
    private static long epoch = 1;
    private static final Map<GlobalPos, StorageNetwork> INDEX = new HashMap<>();

    private NetworkManager() {}

    public static void markDirty() {
        epoch++;
    }

    public static long epoch() {
        return epoch;
    }

    /** The working network this block is part of, or null if none (or it has a problem). */
    @Nullable
    public static StorageNetwork get(Level level, BlockPos pos) {
        StorageNetwork network = INDEX.get(GlobalPos.of(level.dimension(), pos));
        return network != null && network.isActive() ? network : null;
    }

    /** The network for this block even if it is not working, for status displays. */
    @Nullable
    public static StorageNetwork getAny(Level level, BlockPos pos) {
        return INDEX.get(GlobalPos.of(level.dimension(), pos));
    }

    static void index(GlobalPos pos, StorageNetwork network) {
        INDEX.put(pos, network);
    }

    static void unindex(GlobalPos pos, StorageNetwork network) {
        INDEX.remove(pos, network);
    }

    public static void clearAll() {
        INDEX.clear();
        WirelessDevices.clear();
        epoch++;
    }
}
