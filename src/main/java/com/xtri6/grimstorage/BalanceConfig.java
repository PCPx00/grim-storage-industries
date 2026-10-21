package com.xtri6.grimstorage;

import java.util.List;

import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.CellTier;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance settings for pack makers (serverconfig/grimstorage-server.toml in each world, or defaultconfigs/ for every
 * new world). The server sends them to players when they join, so tooltips always show the server's numbers.
 */
public final class BalanceConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final List<Long> ITEM_DEFAULTS = List.of(4_096L, 65_536L, 1_048_576L, 67_108_864L);

    public static final ModConfigSpec.ConfigValue<List<? extends Long>> ITEM_CELLS = cells("itemCellCapacities",
            "Items each storage cell holds: copper, gold, diamond, netherite. (The Singularity Cell has no limit.)", ITEM_DEFAULTS);

    private static final java.util.Map<BulkKind, ModConfigSpec.ConfigValue<List<? extends Long>>> BULK_CELLS = new java.util.EnumMap<>(BulkKind.class);

    static {
        for (BulkKind k : BulkKind.values()) {
            List<Long> defaults = new java.util.ArrayList<>();
            for (CellTier t : BulkKind.TIERS) {
                defaults.add(k.defaultCapacity(t));
            }
            String unit = switch (k) {
                case FLUID, CHEMICAL -> "mB";
                case ENERGY -> "FE";
                case SOURCE -> "Source";
                case EXPERIENCE -> "experience points";
            };
            BULK_CELLS.put(k, cells(k.id() + "CellCapacities", "How much each " + k.id() + " cell holds, in " + unit
                    + ": copper, gold, diamond, netherite.", defaults));
        }
    }

    public static final ModConfigSpec.DoubleValue BUS_SPEED = BUILDER
            .comment("How fast Import and Export Buses move things, as a multiple of the normal speed (Speed Upgrades multiply on top).",
                    "A bus never goes slower than its normal speed without upgrades.")
            .defineInRange("busSpeedMultiplier", 1.0, 0.25, 64.0);

    public static final ModConfigSpec.DoubleValue ASSEMBLER_SPEED = BUILDER
            .comment("How fast Soul Assemblers craft, as a multiple of the normal speed (Speed Upgrades multiply on top).",
                    "At 1.0 a plain Assembler makes one craft every half second, so a stack of 64 takes about 32 seconds.")
            .defineInRange("assemblerSpeedMultiplier", 1.0, 0.25, 64.0);

    public static final ModConfigSpec.IntValue WIRELESS_RANGE = BUILDER
            .comment("How far a Wireless Terminal reaches from its controller, in blocks (same dimension only).",
                    "The Singularity Terminal always reaches everywhere.")
            .defineInRange("wirelessRange", 128, 8, 30_000_000);

    public static final ModConfigSpec.IntValue WIRELESS_LOW_RANGE = BUILDER
            .comment("Reach of the low-range wireless card, in blocks.")
            .defineInRange("wirelessLowRange", 32, 1, 30_000_000);

    public static final ModConfigSpec.IntValue WIRELESS_HIGH_RANGE = BUILDER
            .comment("Reach of the high-range wireless card, in blocks. The medium card uses wirelessRange.")
            .defineInRange("wirelessHighRange", 512, 1, 30_000_000);

    public static final ModConfigSpec.BooleanValue INFINITE_WIRELESS = BUILDER
            .comment("Whether the infinite wireless card (any distance, any dimension) can be crafted at all.")
            .define("infiniteWirelessCard", true);

    public static final ModConfigSpec.IntValue WIRELESS_BIND_LIMIT = BUILDER
            .comment("How many wireless devices one network can bind. 0 means no limit.")
            .defineInRange("wirelessBindLimit", 0, 0, 1_000_000);

    public static final ModConfigSpec.BooleanValue WIRELESS_CROSS_DIMENSION = BUILDER
            .comment("Whether a wireless device can bind to a network in another dimension (only the infinite card reaches that far).")
            .define("wirelessCrossDimension", true);

    public static final ModConfigSpec.IntValue WIRELESS_ENERGY_PER_TICK = BUILDER
            .comment("Energy each wireless device uses per tick, on top of its network's own use.")
            .defineInRange("wirelessEnergyPerTick", 16, 0, 1_000_000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BalanceConfig() {}

    private static ModConfigSpec.ConfigValue<List<? extends Long>> cells(String name, String comment, List<Long> defaults) {
        return BUILDER.comment(comment, "Exactly four numbers, each at least 1.")
                .defineList(name, defaults, () -> 1L, o -> o instanceof Number n && n.longValue() >= 1);
    }

    private static long pick(ModConfigSpec.ConfigValue<List<? extends Long>> value, int tier, long fallback) {
        if (!SPEC.isLoaded()) {
            return fallback;
        }
        List<? extends Number> list = value.get();
        return tier < list.size() ? Math.max(1, list.get(tier).longValue()) : fallback;
    }

    public static long itemCapacity(CellTier tier) {
        return pick(ITEM_CELLS, tier.ordinal(), ITEM_DEFAULTS.get(Math.min(tier.ordinal(), 3)));
    }

    public static long bulkCapacity(BulkKind kind, CellTier tier) {
        int i = Math.min(tier.ordinal(), BulkKind.TIERS.length - 1);
        return pick(BULK_CELLS.get(kind), i, kind.defaultCapacity(tier));
    }

    /** The capacity setting of one kind of resource cell. */
    public static ModConfigSpec.ConfigValue<List<? extends Long>> bulkCells(BulkKind kind) {
        return BULK_CELLS.get(kind);
    }

    public static double assemblerSpeed() {
        return SPEC.isLoaded() ? ASSEMBLER_SPEED.get() : 1.0;
    }

    public static double busSpeed() {
        return SPEC.isLoaded() ? BUS_SPEED.get() : 1.0;
    }

    public static int wirelessRange() {
        return SPEC.isLoaded() ? WIRELESS_RANGE.get() : 128;
    }

    public static int wirelessLowRange() {
        return SPEC.isLoaded() ? WIRELESS_LOW_RANGE.get() : 32;
    }

    public static int wirelessHighRange() {
        return SPEC.isLoaded() ? WIRELESS_HIGH_RANGE.get() : 512;
    }

    public static boolean infiniteWireless() {
        return !SPEC.isLoaded() || INFINITE_WIRELESS.get();
    }

    public static int wirelessBindLimit() {
        return SPEC.isLoaded() ? WIRELESS_BIND_LIMIT.get() : 0;
    }

    public static boolean wirelessCrossDimension() {
        return !SPEC.isLoaded() || WIRELESS_CROSS_DIMENSION.get();
    }

    public static int wirelessEnergyPerTick() {
        return SPEC.isLoaded() ? WIRELESS_ENERGY_PER_TICK.get() : 16;
    }
}
