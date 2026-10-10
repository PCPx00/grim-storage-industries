package com.xtri6.grimstorage.storage;

/** Storage cell tiers. Capacity is a plain item count; there are no type limits. */
public enum CellTier {
    COPPER("storage_cell_4k", 4_096L),
    GOLD("storage_cell_64k", 65_536L),
    DIAMOND("storage_cell_1m", 1_048_576L),
    NETHERITE("storage_cell_64m", 67_108_864L),
    /** Holds one item type that you lock it to, with no limit. */
    SINGULARITY("singularity_cell", Long.MAX_VALUE / 4);

    private final String id;
    private final long capacity;

    CellTier(String id, long capacity) {
        this.id = id;
        this.capacity = capacity;
    }

    public String id() {
        return id;
    }

    public long capacity() {
        return capacity;
    }

    public boolean singular() {
        return this == SINGULARITY;
    }
}
