package com.xtri6.grimstorage.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What one storage cell holds: items (keyed by {@link ItemKey}) or fluids, chemicals, energy or Source (keyed by
 * {@link BulkKey}). Lives in {@link CellStorage} so big cells never bloat item NBT.
 */
public final class CellContents<K> {
    private final CellStorage owner;
    private final LinkedHashMap<K, Long> items = new LinkedHashMap<>();
    private long total;
    /** Goes up on every change to this cell, so a network only refreshes when one of its own cells changed. */
    private long version;

    public long version() {
        return version;
    }

    CellContents(CellStorage owner) {
        this.owner = owner;
    }

    public long total() {
        return total;
    }

    public int types() {
        return items.size();
    }

    public long count(K key) {
        return items.getOrDefault(key, 0L);
    }

    public Map<K, Long> view() {
        return Collections.unmodifiableMap(items);
    }

    public long insert(K key, long amount, long capacity, boolean simulate) {
        long space = capacity - total;
        if (space <= 0 || amount <= 0) {
            return 0;
        }
        long accepted = Math.min(space, amount);
        if (!simulate) {
            items.merge(key, accepted, Long::sum);
            total += accepted;
            version++;
            owner.changed();
        }
        return accepted;
    }

    public long extract(K key, long amount, boolean simulate) {
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
            version++;
            owner.changed();
        }
        return taken;
    }

    void load(K key, long amount) {
        items.merge(key, amount, Long::sum);
        total += amount;
    }
}
