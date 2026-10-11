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

    /** Name color for the tier, so higher tiers stand out in tooltips and inventories. */
    public net.minecraft.world.item.Rarity rarity() {
        return switch (this) {
            case COPPER -> net.minecraft.world.item.Rarity.COMMON;
            case GOLD -> net.minecraft.world.item.Rarity.UNCOMMON;
            case DIAMOND -> net.minecraft.world.item.Rarity.RARE;
            case NETHERITE, SINGULARITY -> net.minecraft.world.item.Rarity.EPIC;
        };
    }

    /** Top tiers shimmer with the enchantment glint. */
    public boolean glints() {
        return this == NETHERITE || this == SINGULARITY;
    }

    /** "Tier: ◆◆◆◇ Diamond" line for cell tooltips, in the tier's color. */
    public net.minecraft.network.chat.Component tooltipLine() {
        int n = ordinal() + 1;
        String pips = singular() ? "\u2726" : "\u25C6".repeat(n) + "\u25C7".repeat(4 - n);
        net.minecraft.ChatFormatting color = switch (this) {
            case COPPER -> net.minecraft.ChatFormatting.GOLD;
            case GOLD -> net.minecraft.ChatFormatting.YELLOW;
            case DIAMOND -> net.minecraft.ChatFormatting.AQUA;
            case NETHERITE -> net.minecraft.ChatFormatting.RED;
            case SINGULARITY -> net.minecraft.ChatFormatting.LIGHT_PURPLE;
        };
        return net.minecraft.network.chat.Component.translatable("tooltip.grimstorage.cell.tier", pips,
                net.minecraft.network.chat.Component.translatable("tier.grimstorage." + name().toLowerCase(java.util.Locale.ROOT))).withStyle(color);
    }
}
