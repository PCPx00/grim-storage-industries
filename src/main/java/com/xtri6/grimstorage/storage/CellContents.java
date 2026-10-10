package com.xtri6.grimstorage.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The items inside one storage cell. Lives in {@link CellStorage} so big cells never bloat item NBT. */
public final class CellContents {
    private final CellStorage owner;
    private final LinkedHashMap<ItemKey, Long> items = new LinkedHashMap<>();
    private long total;

    CellContents(CellStorage owner) {
        this.owner = owner;
    }

    public long total() {
        return total;
    }

    public int types() {
        return items.size();
    }

    public long count(ItemKey key) {
        return items.getOrDefault(key, 0L);
    }

    public Map<ItemKey, Long> view() {
        return Collections.unmodifiableMap(items);
    }

    public long insert(ItemKey key, long amount, long capacity, boolean simulate) {
        long space = capacity - total;
        if (space <= 0 || amount <= 0) {
            return 0;
        }
        long accepted = Math.min(space, amount);
        if (!simulate) {
            items.merge(key, accepted, Long::sum);
            total += accepted;
            owner.changed();
        }
        return accepted;
    }

    public long extract(ItemKey key, long amount, boolean simulate) {
        long have = items.getOrDefault(key, 0L);
        long taken = Math.min(have, amount);
        if (taken <= 0) {
            return 0;
        }
        if (!simulate) {
            if (taken == have) {
                items.remove(key);
            } else {
                items.put(key, have - taken);
            }
            total -= taken;
            owner.changed();
        }
        return taken;
    }

    void load(ItemKey key, long amount) {
        items.merge(key, amount, Long::sum);
        total += amount;
    }
}
