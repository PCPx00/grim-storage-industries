package com.xtri6.grimstorage.storage;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World save data holding every network number handed out in this world. A number is an 8-digit code that players can
 * read and say aloud, and it is never handed out twice in the same world, so two networks cannot share one.
 */
public final class NetworkNumbers extends SavedData {
    private static final String NAME = "grimstorage_network_numbers";
    private static final int MIN = 10_000_000;
    private static final int SPAN = 90_000_000;
    private static final Random RANDOM = new Random();

    private final Set<Integer> used = new HashSet<>();

    public static NetworkNumbers get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NetworkNumbers::new, NetworkNumbers::load), NAME);
    }

    /** A new random number that no network in this world has. Keeps drawing until it finds a free one. */
    public synchronized int assign() {
        int number;
        do {
            number = MIN + RANDOM.nextInt(SPAN);
        } while (!used.add(number));
        setDirty();
        return number;
    }

    private static NetworkNumbers load(CompoundTag tag, HolderLookup.Provider registries) {
        NetworkNumbers numbers = new NetworkNumbers();
        ListTag list = tag.getList("numbers", Tag.TAG_INT);
        for (int i = 0; i < list.size(); i++) {
            numbers.used.add(((IntTag) list.get(i)).getAsInt());
        }
        return numbers;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int n : used) {
            list.add(IntTag.valueOf(n));
        }
        tag.put("numbers", list);
        return tag;
    }
}
