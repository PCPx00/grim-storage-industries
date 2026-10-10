package com.xtri6.grimstorage.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** World save data holding the contents of every storage cell, keyed by the cell's id. */
public final class CellStorage extends SavedData {
    private static final String NAME = "grimstorage_cells";
    private static long version = 1;

    private final Map<UUID, CellContents<ItemKey>> cells = new HashMap<>();
    private final Map<UUID, CellContents<BulkKey>> bulk = new HashMap<>();
    private final Map<UUID, BulkKind> bulkKinds = new HashMap<>();

    /** Bumps on any change to any cell, so views of network contents know when to refresh. */
    public static long version() {
        return version;
    }

    public static CellStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CellStorage::new, CellStorage::load), NAME);
    }

    public CellContents<ItemKey> contents(UUID id) {
        return cells.computeIfAbsent(id, k -> new CellContents<>(this));
    }

    /** Contents of a fluid / chemical / energy / Source cell. */
    public CellContents<BulkKey> bulk(UUID id) {
        return bulk.computeIfAbsent(id, k -> new CellContents<>(this));
    }

    /** Same, remembering which kind the cell holds so it can be saved. */
    public CellContents<BulkKey> bulk(UUID id, BulkKind kind) {
        bulkKinds.putIfAbsent(id, kind);
        return bulk(id);
    }

    void changed() {
        version++;
        setDirty();
    }

    private static CellStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        CellStorage storage = new CellStorage();
        ListTag list = tag.getList("cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag cell = list.getCompound(i);
            CellContents<ItemKey> contents = storage.contents(cell.getUUID("id"));
            ListTag items = cell.getList("items", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                CompoundTag entry = items.getCompound(j);
                ItemStack stack = ItemStack.parse(registries, entry.getCompound("item")).orElse(ItemStack.EMPTY);
                ItemKey key = ItemKey.of(stack);
                long count = entry.getLong("count");
                if (key != null && count > 0) {
                    contents.load(key, count);
                }
            }
        }
        ListTag bulkList = tag.getList("bulk", Tag.TAG_COMPOUND);
        for (int i = 0; i < bulkList.size(); i++) {
            CompoundTag cell = bulkList.getCompound(i);
            BulkKind kind;
            try {
                kind = BulkKind.valueOf(cell.getString("kind"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            CellContents<BulkKey> contents = storage.bulk(cell.getUUID("id"), kind);
            ListTag entries = cell.getList("entries", Tag.TAG_COMPOUND);
            for (int j = 0; j < entries.size(); j++) {
                CompoundTag entry = entries.getCompound(j);
                BulkKey key = BulkKey.load(kind, entry.getCompound("key"), registries);
                long amount = entry.getLong("amount");
                if (key != null && amount > 0) {
                    contents.load(key, amount);
                }
            }
        }
        version++;
        return storage;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        cells.forEach((id, contents) -> {
            if (contents.total() <= 0) {
                return;
            }
            CompoundTag cell = new CompoundTag();
            cell.putUUID("id", id);
            ListTag items = new ListTag();
            contents.view().forEach((key, count) -> {
                CompoundTag entry = new CompoundTag();
                entry.put("item", key.stack().save(registries));
                entry.putLong("count", count);
                items.add(entry);
            });
            cell.put("items", items);
            list.add(cell);
        });
        tag.put("cells", list);
        ListTag bulkList = new ListTag();
        bulk.forEach((id, contents) -> {
            BulkKind kind = bulkKinds.get(id);
            if (contents.total() <= 0 || kind == null) {
                return;
            }
            CompoundTag cell = new CompoundTag();
            cell.putUUID("id", id);
            cell.putString("kind", kind.name());
            ListTag entries = new ListTag();
            contents.view().forEach((key, amount) -> {
                CompoundTag entry = new CompoundTag();
                entry.put("key", key.save(registries));
                entry.putLong("amount", amount);
                entries.add(entry);
            });
            cell.put("entries", entries);
            bulkList.add(cell);
        });
        tag.put("bulk", bulkList);
        return tag;
    }
}
