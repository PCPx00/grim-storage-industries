package com.xtri6.grimstorage.client;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.xtri6.grimstorage.Registration;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.neoforge.common.Tags;

/**
 * Sorts items into the terminal's filter tabs. Uses the shared c: tags first, so modded items land in the
 * right tab, then item types and vanilla's creative tabs. An item can sit in more than one tab (a carrot is
 * food and farming); Misc is whatever fits nowhere else.
 */
public final class ItemCategories {
    public enum Category {
        ALL("all", Items.CHEST),
        ORES("ores", Items.IRON_ORE),
        INGOTS("ingots", Items.IRON_INGOT),
        TOOLS("tools", Items.IRON_SWORD),
        ARMOR("armor", Items.IRON_CHESTPLATE),
        FOOD("food", Items.BREAD),
        MOB_DROPS("mob_drops", Items.BONE),
        FARMING("farming", Items.WHEAT),
        BUILDING("building", Items.BRICKS),
        MISC("misc", Items.COMPASS),
        /** Everything the network knows how to make, stored or not. */
        CRAFTABLE("craftable", Items.CRAFTING_TABLE);

        private final String id;
        private final Item icon;

        Category(String id, Item icon) {
            this.id = id;
            this.icon = icon;
        }

        public String langKey() {
            return "gui.grimstorage.tab." + id;
        }

        public ItemStack icon() {
            return this == ORES ? new ItemStack(Registration.SOULITE_ORE.get()) : new ItemStack(icon);
        }
    }

    private static final Set<Item> MOB_DROP_ITEMS = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.PHANTOM_MEMBRANE, Items.GHAST_TEAR,
            Items.MAGMA_CREAM, Items.INK_SAC, Items.GLOW_INK_SAC, Items.RABBIT_HIDE, Items.RABBIT_FOOT, Items.PRISMARINE_SHARD,
            Items.PRISMARINE_CRYSTALS, Items.SHULKER_SHELL, Items.NAUTILUS_SHELL, Items.TURTLE_SCUTE, Items.ARMADILLO_SCUTE,
            Items.BREEZE_ROD, Items.BLAZE_ROD, Items.BLAZE_POWDER, Items.BONE_MEAL, Items.NETHER_STAR, Items.DRAGON_BREATH,
            Items.HONEYCOMB, Items.WITHER_SKELETON_SKULL, Items.SKELETON_SKULL, Items.ZOMBIE_HEAD, Items.CREEPER_HEAD,
            Items.PIGLIN_HEAD, Items.ECHO_SHARD, Items.HEART_OF_THE_SEA, Items.TOTEM_OF_UNDYING, Items.EXPERIENCE_BOTTLE);

    private static final Set<Item> FARMING_ITEMS = Set.of(Items.BONE_MEAL, Items.SUGAR_CANE, Items.CACTUS, Items.PUMPKIN, Items.MELON,
            Items.MELON_SLICE, Items.BAMBOO, Items.KELP, Items.NETHER_WART, Items.COCOA_BEANS, Items.SWEET_BERRIES, Items.GLOW_BERRIES,
            Items.HAY_BLOCK, Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.COMPOSTER, Items.LILY_PAD, Items.VINE,
            Items.SEA_PICKLE, Items.CHORUS_FRUIT, Items.CHORUS_FLOWER, Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD, Items.EGG,
            Items.MOSS_BLOCK, Items.DIRT, Items.FARMLAND, Items.WATER_BUCKET);

    private static final Map<Item, Integer> CACHE = new IdentityHashMap<>();
    private static Set<Item> building;

    private ItemCategories() {}

    /** Call when the terminal opens: tags or packs may have changed since last time. */
    public static void reset() {
        CACHE.clear();
        building = null;
    }

    public static boolean matches(Category category, ItemStack stack) {
        if (category == Category.ALL) {
            return true;
        }
        if (category == Category.CRAFTABLE) {
            return false;
        }
        int mask = CACHE.computeIfAbsent(stack.getItem(), i -> classify(stack));
        return category == Category.MISC ? mask == 0 : (mask & (1 << category.ordinal())) != 0;
    }

    @SafeVarargs
    private static boolean any(ItemStack s, TagKey<Item>... tags) {
        for (TagKey<Item> t : tags) {
            if (s.is(t)) {
                return true;
            }
        }
        return false;
    }

    private static int classify(ItemStack s) {
        Item item = s.getItem();
        int mask = 0;
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();

        boolean ore = any(s, Tags.Items.ORES, Tags.Items.RAW_MATERIALS)
                || (item instanceof BlockItem && path.startsWith("raw_") && s.is(Tags.Items.STORAGE_BLOCKS));
        if (ore) {
            mask |= bit(Category.ORES);
        }
        if (any(s, Tags.Items.INGOTS, Tags.Items.NUGGETS, Tags.Items.GEMS, Tags.Items.DUSTS)) {
            mask |= bit(Category.INGOTS);
        }
        if (any(s, Tags.Items.TOOLS, ItemTags.SWORDS, ItemTags.AXES, ItemTags.PICKAXES, ItemTags.SHOVELS, ItemTags.HOES)
                || item instanceof TieredItem || item instanceof ProjectileWeaponItem || item instanceof TridentItem
                || item instanceof MaceItem || item instanceof ShieldItem || item instanceof ShearsItem
                || item instanceof FishingRodItem || item instanceof FlintAndSteelItem) {
            mask |= bit(Category.TOOLS);
        }
        if (s.is(Tags.Items.ARMORS) || item instanceof ArmorItem || item instanceof AnimalArmorItem || item instanceof ElytraItem) {
            mask |= bit(Category.ARMOR);
        }
        if (s.has(DataComponents.FOOD) || s.is(Tags.Items.FOODS)) {
            mask |= bit(Category.FOOD);
        }
        if (MOB_DROP_ITEMS.contains(item) || any(s, Tags.Items.BONES, Tags.Items.STRINGS, Tags.Items.LEATHERS, Tags.Items.FEATHERS,
                Tags.Items.GUNPOWDERS, Tags.Items.SLIME_BALLS, Tags.Items.ENDER_PEARLS, Tags.Items.RODS_BLAZE, Tags.Items.RODS_BREEZE, Tags.Items.EGGS)) {
            mask |= bit(Category.MOB_DROPS);
        }
        boolean farming = FARMING_ITEMS.contains(item)
                || any(s, Tags.Items.SEEDS, Tags.Items.CROPS, Tags.Items.MUSHROOMS, ItemTags.SAPLINGS, ItemTags.FLOWERS);
        if (farming) {
            mask |= bit(Category.FARMING);
        }
        if (!ore && !farming && item instanceof BlockItem && buildingBlocks().contains(item)) {
            mask |= bit(Category.BUILDING);
        }
        return mask;
    }

    private static int bit(Category c) {
        return 1 << c.ordinal();
    }

    /** Items in vanilla's Building, Colored and Natural creative tabs (mods add to these too). */
    private static Set<Item> buildingBlocks() {
        if (building != null) {
            return building;
        }
        Set<Item> out = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(),
                    mc.options.operatorItemsTab().get() && mc.player.canUseGameMasterBlocks(), mc.level.registryAccess());
            for (ResourceKey<CreativeModeTab> key : new ResourceKey[] {CreativeModeTabs.BUILDING_BLOCKS, CreativeModeTabs.COLORED_BLOCKS,
                    CreativeModeTabs.NATURAL_BLOCKS}) {
                for (ItemStack s : BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(key).getDisplayItems()) {
                    out.add(s.getItem());
                }
            }
        }
        building = out;
        return out;
    }
}
