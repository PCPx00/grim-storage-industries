package com.xtri6.grimstorage.storage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * A saved description of a recipe in a crafting job: what goes in and what comes out (and which ingredients were
 * swapped for stand-ins). Patterns themselves live only while the network is loaded, so after a world load each job
 * step finds its recipe again by this description.
 */
final class PatternRef {
    private PatternRef() {}

    static CompoundTag save(Pattern pattern, HolderLookup.Provider registries) {
        Pattern root = pattern.root();
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", root.kind().name());
        tag.put("in", items(root.inputs(), registries));
        tag.put("out", items(root.outputs(), registries));
        tag.put("bulkIn", bulk(root.bulkInputs(), registries));
        tag.put("bulkOut", bulk(root.bulkOutputs(), registries));
        ListTag swaps = new ListTag();
        pattern.swaps().forEach((from, to) -> {
            CompoundTag t = new CompoundTag();
            t.put("from", from.stack().save(registries));
            t.put("to", to.stack().save(registries));
            swaps.add(t);
        });
        tag.put("swaps", swaps);
        return tag;
    }

    /** The recipe on the network that matches, or null if it isn't there (yet). */
    @Nullable
    static Pattern find(StorageNetwork network, CompoundTag tag) {
        if (network.server() == null) {
            return null;
        }
        HolderLookup.Provider registries = network.server().registryAccess();
        Map<ItemKey, Long> in = readItems(tag.getList("in", Tag.TAG_COMPOUND), registries);
        Map<ItemKey, Long> out = readItems(tag.getList("out", Tag.TAG_COMPOUND), registries);
        Map<BulkKey, Long> bulkIn = readBulk(tag.getList("bulkIn", Tag.TAG_COMPOUND), registries);
        Map<BulkKey, Long> bulkOut = readBulk(tag.getList("bulkOut", Tag.TAG_COMPOUND), registries);
        if (in == null || out == null || bulkIn == null || bulkOut == null) {
            return null;
        }
        List<Pattern> candidates;
        if (!out.isEmpty()) {
            candidates = network.patternsFor(out.keySet().iterator().next());
        } else if (!bulkOut.isEmpty()) {
            candidates = network.patternsFor(bulkOut.keySet().iterator().next());
        } else {
            return null;
        }
        String kind = tag.getString("kind");
        for (Pattern p : candidates) {
            if (p.kind().name().equals(kind) && p.inputs().equals(in) && p.outputs().equals(out)
                    && p.bulkInputs().equals(bulkIn) && p.bulkOutputs().equals(bulkOut)) {
                Map<ItemKey, ItemKey> swaps = new LinkedHashMap<>();
                for (Tag t : tag.getList("swaps", Tag.TAG_COMPOUND)) {
                    CompoundTag c = (CompoundTag) t;
                    ItemKey from = key(c.getCompound("from"), registries);
                    ItemKey to = key(c.getCompound("to"), registries);
                    if (from == null || to == null) {
                        return null;
                    }
                    swaps.put(from, to);
                }
                return swaps.isEmpty() ? p : p.substitute(swaps, network.server().overworld());
            }
        }
        return null;
    }

    private static ListTag items(Map<ItemKey, Long> map, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        map.forEach((k, n) -> {
            CompoundTag t = new CompoundTag();
            t.put("item", k.stack().save(registries));
            t.putLong("n", n);
            list.add(t);
        });
        return list;
    }

    private static ListTag bulk(Map<BulkKey, Long> map, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        map.forEach((k, n) -> {
            CompoundTag t = k.save(registries);
            t.putString("kind", k.kind().name());
            t.putLong("n", n);
            list.add(t);
        });
        return list;
    }

    @Nullable
    private static ItemKey key(CompoundTag tag, HolderLookup.Provider registries) {
        return ItemKey.of(ItemStack.parse(registries, tag).orElse(ItemStack.EMPTY));
    }

    @Nullable
    private static Map<ItemKey, Long> readItems(ListTag list, HolderLookup.Provider registries) {
        Map<ItemKey, Long> out = new LinkedHashMap<>();
        for (Tag t : list) {
            CompoundTag c = (CompoundTag) t;
            ItemKey k = key(c.getCompound("item"), registries);
            if (k == null) {
                // an item from a mod that's gone
                return null;
            }
            out.merge(k, c.getLong("n"), Long::sum);
        }
        return out;
    }

    @Nullable
    private static Map<BulkKey, Long> readBulk(ListTag list, HolderLookup.Provider registries) {
        Map<BulkKey, Long> out = new LinkedHashMap<>();
        for (Tag t : list) {
            CompoundTag c = (CompoundTag) t;
            BulkKind kind;
            try {
                kind = BulkKind.valueOf(c.getString("kind"));
            } catch (IllegalArgumentException e) {
                return null;
            }
            BulkKey k = BulkKey.load(kind, c, registries);
            if (k == null) {
                return null;
            }
            out.merge(k, c.getLong("n"), Long::sum);
        }
        return out;
    }
}
