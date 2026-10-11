package com.xtri6.grimstorage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.xtri6.grimstorage.block.BusBlock;
import com.xtri6.grimstorage.block.CableBlock;
import com.xtri6.grimstorage.block.FacingDeviceBlock;
import com.xtri6.grimstorage.block.NetworkDeviceBlock;
import com.xtri6.grimstorage.block.SoulGlassBlock;
import com.xtri6.grimstorage.block.SoulInfuserBlock;
import com.xtri6.grimstorage.block.entity.AssemblerBlockEntity;
import com.xtri6.grimstorage.block.entity.BusBlockEntity;
import com.xtri6.grimstorage.block.entity.ControllerBlockEntity;
import com.xtri6.grimstorage.block.entity.DriveBlockEntity;
import com.xtri6.grimstorage.block.entity.InterfaceBlockEntity;
import com.xtri6.grimstorage.block.entity.ProcessingInterfaceBlockEntity;
import com.xtri6.grimstorage.block.entity.QuantumLinkBlockEntity;
import com.xtri6.grimstorage.block.entity.SoulInfuserBlockEntity;
import com.xtri6.grimstorage.block.entity.StockKeeperBlockEntity;
import com.xtri6.grimstorage.block.entity.VoidFilterBlockEntity;
import com.xtri6.grimstorage.item.BulkCellItem;
import com.xtri6.grimstorage.item.CardData;
import com.xtri6.grimstorage.item.GuideBookItem;
import com.xtri6.grimstorage.item.ProcessingCardItem;
import com.xtri6.grimstorage.item.RecipeCardItem;
import com.xtri6.grimstorage.item.SoulTetherItem;
import com.xtri6.grimstorage.item.StorageCellItem;
import com.xtri6.grimstorage.item.WirelessTerminalItem;
import com.xtri6.grimstorage.menu.BlockMenus;
import com.xtri6.grimstorage.menu.CardMenus;
import com.xtri6.grimstorage.menu.ControllerMenu;
import com.xtri6.grimstorage.menu.Menus;
import com.xtri6.grimstorage.menu.TerminalMenu;
import com.xtri6.grimstorage.recipe.InfusingRecipe;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.CellTier;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
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

    // ======================================================================== storage network

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0f, 6.0f)
                .sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<NetworkDeviceBlock> CONTROLLER = device("network_controller",
            () -> new NetworkDeviceBlock(machine().lightLevel(s -> 7), Registration.CONTROLLER_ENTITY, Menus::openController));
    public static final DeferredBlock<NetworkDeviceBlock> DRIVE = device("soul_drive",
            () -> new FacingDeviceBlock.Horizontal(machine(), Registration.DRIVE_ENTITY, Menus::openDrive));
    public static final DeferredBlock<NetworkDeviceBlock> TERMINAL = device("soul_terminal",
            () -> new FacingDeviceBlock.Horizontal(machine().lightLevel(s -> 4), null, Menus::openTerminal));
    public static final DeferredBlock<NetworkDeviceBlock> ASSEMBLER = device("soul_assembler",
            () -> new NetworkDeviceBlock(machine(), Registration.ASSEMBLER_ENTITY, Menus::openAssembler));
    public static final DeferredBlock<NetworkDeviceBlock> INTERFACE = device("network_interface",
            () -> new NetworkDeviceBlock(machine(), Registration.INTERFACE_ENTITY, Menus::openFilter));
    public static final DeferredBlock<NetworkDeviceBlock> PROCESSING_INTERFACE = device("processing_interface",
            () -> new FacingDeviceBlock.All(machine(), Registration.PROCESSING_INTERFACE_ENTITY, Menus::openProcessing));
    public static final DeferredBlock<NetworkDeviceBlock> STOCK_KEEPER = device("stock_keeper",
            () -> new NetworkDeviceBlock(machine(), Registration.STOCK_KEEPER_ENTITY, Menus::openStockKeeper));
    public static final DeferredBlock<NetworkDeviceBlock> VOID_FILTER = device("void_filter",
            () -> new NetworkDeviceBlock(machine(), Registration.VOID_FILTER_ENTITY, Menus::openFilter));
    public static final DeferredBlock<NetworkDeviceBlock> IMPORT_BUS = BLOCKS.register("import_bus",
            () -> new BusBlock(machine().noOcclusion(), Registration.IMPORT_BUS_ENTITY, Menus::openBus));
    public static final DeferredBlock<NetworkDeviceBlock> EXPORT_BUS = BLOCKS.register("export_bus",
            () -> new BusBlock(machine().noOcclusion(), Registration.EXPORT_BUS_ENTITY, Menus::openBus));
    /** Bus items also go straight onto Soul Cable. */
    public static final DeferredItem<com.xtri6.grimstorage.item.BusItem> IMPORT_BUS_ITEM = ITEMS.register("import_bus",
            () -> new com.xtri6.grimstorage.item.BusItem(IMPORT_BUS.get(), false, new Item.Properties()));
    public static final DeferredItem<com.xtri6.grimstorage.item.BusItem> EXPORT_BUS_ITEM = ITEMS.register("export_bus",
            () -> new com.xtri6.grimstorage.item.BusItem(EXPORT_BUS.get(), true, new Item.Properties()));
    public static final DeferredBlock<NetworkDeviceBlock> QUANTUM_LINK = device("quantum_link",
            () -> new NetworkDeviceBlock(machine().lightLevel(s -> 9), Registration.QUANTUM_LINK_ENTITY, null));
    public static final DeferredBlock<CableBlock> CABLE = device("soul_cable",
            () -> new CableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.5f, 6.0f)
                    .sound(SoundType.COPPER).noOcclusion().forceSolidOn()));
    public static final DeferredBlock<SoulGlassBlock> SOUL_GLASS = device("soulite_glass",
            () -> new SoulGlassBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.5f)
                    .sound(SoundType.GLASS).noOcclusion().lightLevel(s -> 3)
                    .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final DeferredBlock<SoulInfuserBlock> SOUL_INFUSER = device("soul_infuser",
            () -> new SoulInfuserBlock(machine().lightLevel(s -> 6)));

    // ---- Block entities ----
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControllerBlockEntity>> CONTROLLER_ENTITY =
            be("network_controller", ControllerBlockEntity::new, CONTROLLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DriveBlockEntity>> DRIVE_ENTITY =
            be("soul_drive", DriveBlockEntity::new, DRIVE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblerBlockEntity>> ASSEMBLER_ENTITY =
            be("soul_assembler", AssemblerBlockEntity::new, ASSEMBLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InterfaceBlockEntity>> INTERFACE_ENTITY =
            be("network_interface", InterfaceBlockEntity::new, INTERFACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessingInterfaceBlockEntity>> PROCESSING_INTERFACE_ENTITY =
            be("processing_interface", ProcessingInterfaceBlockEntity::new, PROCESSING_INTERFACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StockKeeperBlockEntity>> STOCK_KEEPER_ENTITY =
            be("stock_keeper", StockKeeperBlockEntity::new, STOCK_KEEPER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VoidFilterBlockEntity>> VOID_FILTER_ENTITY =
            be("void_filter", VoidFilterBlockEntity::new, VOID_FILTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BusBlockEntity>> IMPORT_BUS_ENTITY =
            be("import_bus", BusBlockEntity::new, IMPORT_BUS);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BusBlockEntity>> EXPORT_BUS_ENTITY =
            be("export_bus", BusBlockEntity::new, EXPORT_BUS);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.xtri6.grimstorage.block.entity.CableBusBlockEntity>> CABLE_BUS_ENTITY =
            be("cable_bus", com.xtri6.grimstorage.block.entity.CableBusBlockEntity::new, CABLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumLinkBlockEntity>> QUANTUM_LINK_ENTITY =
            be("quantum_link", QuantumLinkBlockEntity::new, QUANTUM_LINK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SoulInfuserBlockEntity>> SOUL_INFUSER_ENTITY =
            be("soul_infuser", SoulInfuserBlockEntity::new, SOUL_INFUSER);

    // ---- Items ----
    public static final Map<CellTier, DeferredItem<StorageCellItem>> CELLS = new EnumMap<>(CellTier.class);

    static {
        for (CellTier tier : CellTier.values()) {
            Item.Properties props = new Item.Properties();
            if (tier == CellTier.NETHERITE || tier == CellTier.SINGULARITY) {
                props.fireResistant();
            }
            props.rarity(tier.rarity());
            CELLS.put(tier, ITEMS.register(tier.id(), () -> new StorageCellItem(tier, props)));
        }
    }

    /** Fluid, chemical, energy and Source cells, one per tier. */
    public static final Map<BulkKind, Map<CellTier, DeferredItem<BulkCellItem>>> BULK_CELLS = new EnumMap<>(BulkKind.class);

    static {
        for (BulkKind kind : BulkKind.values()) {
            Map<CellTier, DeferredItem<BulkCellItem>> tiers = new EnumMap<>(CellTier.class);
            for (CellTier tier : BulkKind.TIERS) {
                Item.Properties props = new Item.Properties().rarity(tier.rarity());
                if (tier == CellTier.NETHERITE) {
                    props.fireResistant();
                }
                tiers.put(tier, ITEMS.register(BulkCellItem.id(kind, tier), () -> new BulkCellItem(kind, tier, props)));
            }
            BULK_CELLS.put(kind, tiers);
        }
    }

    public static final DeferredItem<RecipeCardItem> RECIPE_CARD = ITEMS.register("recipe_card",
            () -> new RecipeCardItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<ProcessingCardItem> PROCESSING_CARD = ITEMS.register("processing_card",
            () -> new ProcessingCardItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<com.xtri6.grimstorage.item.SoulWrenchItem> SOUL_WRENCH = ITEMS.register("soul_wrench",
            () -> new com.xtri6.grimstorage.item.SoulWrenchItem(new Item.Properties()));
    public static final DeferredItem<Item> SPEED_UPGRADE = item("speed_upgrade", new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<WirelessTerminalItem> WIRELESS_TERMINAL = ITEMS.register("wireless_terminal",
            () -> new WirelessTerminalItem(false, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<WirelessTerminalItem> SINGULARITY_TERMINAL = ITEMS.register("singularity_terminal",
            () -> new WirelessTerminalItem(true, new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<GuideBookItem> GUIDE_BOOK = ITEMS.register("guide_book",
            () -> new GuideBookItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<SoulTetherItem> SOUL_TETHER = ITEMS.register("soul_tether",
            () -> new SoulTetherItem(new Item.Properties().rarity(Rarity.RARE)));

    // ---- Item components ----
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> CELL_ID = COMPONENTS.registerComponentType("cell_id",
            b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CardData.CellStats>> CELL_STATS = COMPONENTS.registerComponentType(
            "cell_stats", b -> b.persistent(CardData.CellStats.CODEC).networkSynchronized(CardData.CellStats.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CardData.Locked>> CELL_LOCK = COMPONENTS.registerComponentType(
            "cell_lock", b -> b.persistent(CardData.Locked.CODEC).networkSynchronized(CardData.Locked.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CardData.Recipe>> RECIPE_CARD_DATA = COMPONENTS.registerComponentType(
            "recipe_card", b -> b.persistent(CardData.Recipe.CODEC).networkSynchronized(CardData.Recipe.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CardData.Processing>> PROCESSING_CARD_DATA =
            COMPONENTS.registerComponentType("processing_card",
                    b -> b.persistent(CardData.Processing.CODEC).networkSynchronized(CardData.Processing.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> BOUND_POS = COMPONENTS.registerComponentType(
            "bound_pos", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    // ---- Menus ----
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<TerminalMenu>> TERMINAL_MENU = MENUS.register("terminal",
            () -> IMenuTypeExtension.create(TerminalMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> CONTROLLER_MENU = MENUS.register("controller",
            () -> IMenuTypeExtension.create(ControllerMenu::client));
    public static final DeferredHolder<MenuType<?>, MenuType<BlockMenus.Drive>> DRIVE_MENU = MENUS.register("drive",
            () -> IMenuTypeExtension.create(BlockMenus.Drive::client));
    public static final DeferredHolder<MenuType<?>, MenuType<BlockMenus.Assembler>> ASSEMBLER_MENU = MENUS.register("assembler",
            () -> IMenuTypeExtension.create(BlockMenus.Assembler::client));
    public static final DeferredHolder<MenuType<?>, MenuType<BlockMenus.Processing>> PROCESSING_MENU = MENUS.register("processing_interface",
            () -> IMenuTypeExtension.create(BlockMenus.Processing::client));
    public static final DeferredHolder<MenuType<?>, MenuType<BlockMenus.Filter>> FILTER_MENU = MENUS.register("filter",
            () -> IMenuTypeExtension.create(BlockMenus.Filter::client));
    public static final DeferredHolder<MenuType<?>, MenuType<BlockMenus.Bus>> BUS_MENU = MENUS.register("bus",
            () -> IMenuTypeExtension.create(BlockMenus.Bus::client));
    public static final DeferredHolder<MenuType<?>, MenuType<BlockMenus.StockKeeper>> STOCK_KEEPER_MENU = MENUS.register("stock_keeper",
            () -> IMenuTypeExtension.create(BlockMenus.StockKeeper::client));
    public static final DeferredHolder<MenuType<?>, MenuType<BlockMenus.Infuser>> INFUSER_MENU = MENUS.register("soul_infuser",
            () -> IMenuTypeExtension.create(BlockMenus.Infuser::client));
    public static final DeferredHolder<MenuType<?>, MenuType<CardMenus.Recipe>> RECIPE_CARD_MENU = MENUS.register("recipe_card",
            () -> IMenuTypeExtension.create(CardMenus.Recipe::client));
    public static final DeferredHolder<MenuType<?>, MenuType<CardMenus.Processing>> PROCESSING_CARD_MENU = MENUS.register("processing_card",
            () -> IMenuTypeExtension.create(CardMenus.Processing::client));

    // ---- Recipes ----
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<InfusingRecipe>> INFUSING_TYPE = RECIPE_TYPES.register("infusing",
            () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(MODID, "infusing")));
    public static final DeferredHolder<RecipeSerializer<?>, InfusingRecipe.Serializer> INFUSING_SERIALIZER = RECIPE_SERIALIZERS.register(
            "infusing", InfusingRecipe.Serializer::new);

    // ---- Creative tab ----
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID))
                    .icon(() -> Registration.CONTROLLER.get().asItem().getDefaultInstance())
                    .displayItems((params, output) -> {
                        List<Supplier<? extends ItemLike>> order = new ArrayList<>(List.of(GUIDE_BOOK,
                                SOULITE_ORE, DEEPSLATE_SOULITE_ORE, SOUL_SAND_SOULITE_ORE, ECHO_SOULITE_ORE,
                                RAW_SOULITE_BLOCK, SOULITE_BLOCK,
                                RAW_SOULITE, SOULITE_DUST, SOULITE_NUGGET, SOULITE_INGOT,
                                INFUSED_SOULITE, ECHO_SOULITE_SHARD, ECHO_SOULITE_ALLOY, SINGULARITY_CORE,
                                SOUL_INFUSER, CONTROLLER, DRIVE, TERMINAL, CABLE, SOUL_GLASS, INTERFACE, IMPORT_BUS, EXPORT_BUS,
                                ASSEMBLER, STOCK_KEEPER, VOID_FILTER, PROCESSING_INTERFACE, QUANTUM_LINK));
                        for (CellTier t : CellTier.values()) {
                            order.add(CELLS.get(t));
                        }
                        for (BulkKind kind : BulkKind.values()) {
                            if (kind.available()) {
                                for (CellTier t : BulkKind.TIERS) {
                                    order.add(BULK_CELLS.get(kind).get(t));
                                }
                            }
                        }
                        order.addAll(List.of(SOUL_WRENCH, RECIPE_CARD, PROCESSING_CARD, SPEED_UPGRADE, WIRELESS_TERMINAL, SINGULARITY_TERMINAL, SOUL_TETHER));
                        order.forEach(entry -> output.accept(entry.get()));
                    })
                    .build());

    private Registration() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        MENUS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        TABS.register(modBus);
    }

    private static <B extends Block> DeferredBlock<B> device(String name, Supplier<B> factory) {
        DeferredBlock<B> block = BLOCKS.register(name, factory);
        ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    @SuppressWarnings("DataFlowIssue")
    private static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> be(
            String name, BlockEntityType.BlockEntitySupplier<T> factory, DeferredBlock<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
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
