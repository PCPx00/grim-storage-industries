package com.xtri6.grimstorage;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every block, item and creative tab the mod adds. */
public final class Registration {
    private static final String MODID = GrimStorage.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ---- Sounds: Soulite ores break with a soft crystal chime instead of a plain stone crunch ----
    private static final SoundType SOULITE_ORE_SOUND = new SoundType(1.0f, 1.3f,
            SoundEvents.AMETHYST_CLUSTER_BREAK, SoundEvents.STONE_STEP, SoundEvents.STONE_PLACE,
            SoundEvents.STONE_HIT, SoundEvents.STONE_FALL);
    private static final SoundType DEEPSLATE_SOULITE_ORE_SOUND = new SoundType(1.0f, 1.1f,
            SoundEvents.AMETHYST_CLUSTER_BREAK, SoundEvents.DEEPSLATE_STEP, SoundEvents.DEEPSLATE_PLACE,
            SoundEvents.DEEPSLATE_HIT, SoundEvents.DEEPSLATE_FALL);
    private static final SoundType SOUL_SAND_SOULITE_ORE_SOUND = new SoundType(1.0f, 1.0f,
            SoundEvents.AMETHYST_CLUSTER_BREAK, SoundEvents.SOUL_SOIL_STEP, SoundEvents.SOUL_SOIL_PLACE,
            SoundEvents.SOUL_SOIL_HIT, SoundEvents.SOUL_SOIL_FALL);
    private static final SoundType ECHO_SOULITE_ORE_SOUND = new SoundType(1.0f, 0.8f,
            SoundEvents.SCULK_CATALYST_BREAK, SoundEvents.DEEPSLATE_STEP, SoundEvents.DEEPSLATE_PLACE,
            SoundEvents.DEEPSLATE_HIT, SoundEvents.DEEPSLATE_FALL);

    // ---- Ores ----
    public static final DeferredBlock<DropExperienceBlock> SOULITE_ORE = ore("soulite_ore",
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(3.0f, 3.0f).sound(SOULITE_ORE_SOUND)
                    .lightLevel(s -> 2),
            ConstantInt.of(0));

    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_SOULITE_ORE = ore("deepslate_soulite_ore",
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(4.5f, 3.0f).sound(DEEPSLATE_SOULITE_ORE_SOUND)
                    .lightLevel(s -> 2),
            ConstantInt.of(0));

    /** Nether variant: soul sand shot through with Soulite. Richer, drops 2-3 raw Soulite. */
    public static final DeferredBlock<DropExperienceBlock> SOUL_SAND_SOULITE_ORE = ore("soul_sand_soulite_ore",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.COW_BELL)
                    .requiresCorrectToolForDrops().strength(2.0f, 3.0f).sound(SOUL_SAND_SOULITE_ORE_SOUND)
                    .lightLevel(s -> 4),
            UniformInt.of(0, 1));

    /** Deep Dark only. The single source of Echo Soulite Shards, which gate end-game storage. */
    public static final DeferredBlock<DropExperienceBlock> ECHO_SOULITE_ORE = ore("echo_soulite_ore",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(5.0f, 6.0f).sound(ECHO_SOULITE_ORE_SOUND)
                    .lightLevel(s -> 6),
            UniformInt.of(4, 8));

    // ---- Storage blocks ----
    public static final DeferredBlock<Block> RAW_SOULITE_BLOCK = block("raw_soulite_block",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(5.0f, 6.0f));

    public static final DeferredBlock<Block> SOULITE_BLOCK = block("soulite_block",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).instrument(NoteBlockInstrument.CHIME)
                    .requiresCorrectToolForDrops().strength(5.0f, 6.0f).sound(SoundType.METAL)
                    .lightLevel(s -> 5));

    // ---- Materials, in progression order ----
    public static final DeferredItem<Item> RAW_SOULITE = item("raw_soulite", new Item.Properties());
    public static final DeferredItem<Item> SOULITE_DUST = item("soulite_dust", new Item.Properties());
    public static final DeferredItem<Item> SOULITE_NUGGET = item("soulite_nugget", new Item.Properties());
    public static final DeferredItem<Item> SOULITE_INGOT = item("soulite_ingot", new Item.Properties());
    public static final DeferredItem<Item> INFUSED_SOULITE = item("infused_soulite",
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ECHO_SOULITE_SHARD = item("echo_soulite_shard",
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ECHO_SOULITE_ALLOY = item("echo_soulite_alloy",
            new Item.Properties().rarity(Rarity.RARE).fireResistant());
    public static final DeferredItem<Item> SINGULARITY_CORE = item("singularity_core",
            new Item.Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(16));

    // ---- Creative tab ----
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID))
                    .icon(() -> SOULITE_INGOT.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        List<Supplier<? extends net.minecraft.world.level.ItemLike>> order = List.of(
                                SOULITE_ORE, DEEPSLATE_SOULITE_ORE, SOUL_SAND_SOULITE_ORE, ECHO_SOULITE_ORE,
                                RAW_SOULITE_BLOCK, SOULITE_BLOCK,
                                RAW_SOULITE, SOULITE_DUST, SOULITE_NUGGET, SOULITE_INGOT,
                                INFUSED_SOULITE, ECHO_SOULITE_SHARD, ECHO_SOULITE_ALLOY, SINGULARITY_CORE);
                        order.forEach(entry -> output.accept(entry.get()));
                    })
                    .build());

    private Registration() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    private static DeferredBlock<DropExperienceBlock> ore(String name, BlockBehaviour.Properties props,
                                                          net.minecraft.util.valueproviders.IntProvider xp) {
        DeferredBlock<DropExperienceBlock> block = BLOCKS.register(name, () -> new DropExperienceBlock(xp, props));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    private static DeferredBlock<Block> block(String name, BlockBehaviour.Properties props) {
        DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(name, props);
        ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    private static DeferredItem<Item> item(String name, Item.Properties props) {
        return ITEMS.registerSimpleItem(name, props);
    }
}
