package com.xtri6.grimstorage.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.block.entity.WirelessDevice;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Every wireless device that is loaded right now, so networks can find the ones bound to them. */
public final class WirelessDevices {
    private static final String KEY = "wirelessNetwork";
    private static final Map<GlobalPos, BlockEntity> LOADED = new HashMap<>();

    private WirelessDevices() {}

    public static void add(BlockEntity be) {
        if (be.getLevel() != null) {
            LOADED.put(GlobalPos.of(be.getLevel().dimension(), be.getBlockPos()), be);
        }
    }

    public static void remove(BlockEntity be) {
        if (be.getLevel() != null) {
            LOADED.remove(GlobalPos.of(be.getLevel().dimension(), be.getBlockPos()), be);
        }
    }

    /** The loaded, live devices bound to this network. */
    public static List<BlockEntity> bound(UUID network) {
        List<BlockEntity> out = new ArrayList<>();
        for (BlockEntity be : LOADED.values()) {
            if (!be.isRemoved() && be instanceof WirelessDevice w && network.equals(w.boundNetwork())) {
                out.add(be);
            }
        }
        return out;
    }

    /** Only counts devices that are loaded, so an unloaded device does not count against the bind limit. */
    public static int boundCount(UUID network) {
        return bound(network).size();
    }

    public static void save(CompoundTag tag, @Nullable UUID network) {
        if (network != null) {
            tag.putUUID(KEY, network);
        }
    }

    @Nullable
    public static UUID load(CompoundTag tag) {
        return tag.hasUUID(KEY) ? tag.getUUID(KEY) : null;
    }

    public static void clear() {
        LOADED.clear();
    }
}
