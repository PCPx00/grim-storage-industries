package com.xtri6.grimstorage.storage;

import javax.annotation.Nullable;

import net.neoforged.fml.ModList;

/** Non-item things the network can store. Each has its own cells and its own terminal tab. */
public enum BulkKind {
    FLUID("fluid", null, new long[] {256_000L, 4_096_000L, 65_536_000L, 1_048_576_000L}),
    CHEMICAL("chemical", "mekanism", new long[] {256_000L, 4_096_000L, 65_536_000L, 1_048_576_000L}),
    ENERGY("energy", null, new long[] {16_000_000L, 256_000_000L, 4_096_000_000L, 65_536_000_000L}),
    SOURCE("source", "ars_nouveau", new long[] {10_000L, 100_000L, 1_000_000L, 10_000_000L}),
    /** Experience points. Liquid XP from other mods ({@code c:experience}) is converted at 20 mB per point. */
    EXPERIENCE("experience", null, new long[] {100_000L, 2_000_000L, 50_000_000L, 2_000_000_000L});

    private final String id;
    @Nullable
    private final String requiredMod;
    private final long[] capacities;

    BulkKind(String id, @Nullable String requiredMod, long[] capacities) {
        this.id = id;
        this.requiredMod = requiredMod;
        this.capacities = capacities;
    }

    public String id() {
        return id;
    }

    /** Mod that must be installed for this kind to be useful (null = always available). */
    @Nullable
    public String requiredMod() {
        return requiredMod;
    }

    public boolean available() {
        return requiredMod == null || ModList.get().isLoaded(requiredMod);
    }

    /** Capacity for a cell tier, in mB for fluids and chemicals, FE for energy, Source for Source, points for XP. */
    public long capacity(CellTier tier) {
        return capacities[Math.min(tier.ordinal(), capacities.length - 1)];
    }

    /** Fluids and chemicals hold many types per cell; energy, Source and XP are a single pool. */
    public boolean typed() {
        return this == FLUID || this == CHEMICAL;
    }

    public static final CellTier[] TIERS = {CellTier.COPPER, CellTier.GOLD, CellTier.DIAMOND, CellTier.NETHERITE};
}
