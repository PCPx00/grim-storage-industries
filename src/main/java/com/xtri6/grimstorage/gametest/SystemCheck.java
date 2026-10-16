package com.xtri6.grimstorage.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.xtri6.grimstorage.GrimStorage;
import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.FacingDeviceBlock;
import com.xtri6.grimstorage.block.entity.AssemblerBlockEntity;
import com.xtri6.grimstorage.block.entity.DriveBlockEntity;
import com.xtri6.grimstorage.block.entity.InterfaceBlockEntity;
import com.xtri6.grimstorage.block.entity.ProcessingInterfaceBlockEntity;
import com.xtri6.grimstorage.block.entity.QuantumLinkBlockEntity;
import com.xtri6.grimstorage.block.entity.StockKeeperBlockEntity;
import com.xtri6.grimstorage.block.entity.VoidFilterBlockEntity;
import com.xtri6.grimstorage.item.CardData;
import com.xtri6.grimstorage.menu.CardMenus;
import com.xtri6.grimstorage.menu.TerminalMenu;
import com.xtri6.grimstorage.storage.BulkKey;
import com.xtri6.grimstorage.storage.BulkKind;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import com.xtri6.grimstorage.storage.CellTier;
import com.xtri6.grimstorage.storage.CraftingPlanner;
import com.xtri6.grimstorage.storage.ItemKey;
import com.xtri6.grimstorage.storage.NetworkManager;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Dev-only end-to-end test. Builds a real network in the sky above spawn and drives it through storage,
 * autocrafting, machine processing, stock keeping, voiding, pipe access, terminal clicks, card editing and a
 * Nether quantum link. Run twice: the second run checks everything was saved. Never ships.
 */
public final class SystemCheck {
    private static final BlockPos BASE = new BlockPos(0, 220, 0);
    private static final Path TOTAL_FILE = Path.of("systemcheck_total.txt");

    private record Step(String name, int waitTicks, BooleanSupplier test) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> FAILURES = new ArrayList<>();
    private static MinecraftServer server;
    private static ServerLevel level;
    private static int stepIndex;
    private static int waited;
    private static int stepTicks;
    private static boolean persistenceRun;

    private SystemCheck() {}

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(SystemCheck::onStarted);
        NeoForge.EVENT_BUS.addListener(SystemCheck::onTick);
    }

    private static BlockPos at(int x, int y, int z) {
        return BASE.offset(x, y, z);
    }

    private static StorageNetwork net() {
        return NetworkManager.get(level, BASE);
    }

    private static ItemKey key(Item item) {
        return ItemKey.of(new ItemStack(item));
    }

    private static long count(Item item) {
        StorageNetwork n = net();
        return n == null ? -1 : n.count(key(item));
    }

    private static void info(String fmt, Object... args) {
        GrimStorage.LOGGER.info("[SystemCheck] " + fmt, args);
    }

    private static ItemStack recipeCard(Item... grid) {
        ItemStack card = new ItemStack(Registration.RECIPE_CARD.get());
        List<ItemStack> g = new ArrayList<>();
        for (Item i : grid) {
            g.add(i == Items.AIR ? ItemStack.EMPTY : new ItemStack(i));
        }
        card.set(Registration.RECIPE_CARD_DATA.get(), new CardData.Recipe(g, new ItemStack(Items.STONE)));
        return card;
    }

    private static void onStarted(ServerStartedEvent e) {
        server = e.getServer();
        level = server.overworld();
        level.setChunkForced(0, 0, true);
        persistenceRun = level.getBlockState(BASE).is(Registration.CONTROLLER.get());
        info("starting ({} run)", persistenceRun ? "persistence" : "first");
        if (persistenceRun) {
            buildPersistenceSteps();
        } else {
            build();
            buildSteps();
        }
    }

    // ------------------------------------------------------------------ world setup

    private static void build() {
        level.setBlockAndUpdate(BASE, Registration.CONTROLLER.get().defaultBlockState());
        level.setBlockAndUpdate(at(1, 0, 0), Registration.DRIVE.get().defaultBlockState());
        level.setBlockAndUpdate(at(2, 0, 0), Registration.ASSEMBLER.get().defaultBlockState());
        level.setBlockAndUpdate(at(3, 0, 0), Registration.CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(at(4, 0, 0), Registration.SOUL_GLASS.get().defaultBlockState());
        level.setBlockAndUpdate(at(5, 0, 0), Registration.TERMINAL.get().defaultBlockState());
        level.setBlockAndUpdate(at(-1, 0, 0), Registration.INTERFACE.get().defaultBlockState());
        level.setBlockAndUpdate(at(0, 1, 0), Registration.STOCK_KEEPER.get().defaultBlockState());
        level.setBlockAndUpdate(at(0, -1, 0), Registration.VOID_FILTER.get().defaultBlockState());
        level.setBlockAndUpdate(at(0, 0, -1), Registration.PROCESSING_INTERFACE.get().defaultBlockState()
                .setValue(FacingDeviceBlock.All.FACING, Direction.NORTH));
        level.setBlockAndUpdate(at(0, 0, -2), Registration.SOUL_INFUSER.get().defaultBlockState());
        level.setBlockAndUpdate(at(0, 0, 1), Registration.QUANTUM_LINK.get().defaultBlockState());

        DriveBlockEntity drive = (DriveBlockEntity) level.getBlockEntity(at(1, 0, 0));
        drive.cells().setStackInSlot(0, new ItemStack(Registration.CELLS.get(CellTier.COPPER).get()));
        ItemStack sing = new ItemStack(Registration.CELLS.get(CellTier.SINGULARITY).get());
        sing.set(Registration.CELL_LOCK.get(), new CardData.Locked(new ItemStack(Items.COBBLESTONE)));
        drive.cells().setStackInSlot(1, sing);

        AssemblerBlockEntity asm = (AssemblerBlockEntity) level.getBlockEntity(at(2, 0, 0));
        asm.cardHandler().setStackInSlot(0, recipeCard(Items.OAK_LOG));
        asm.cardHandler().setStackInSlot(1, recipeCard(Items.OAK_PLANKS, Items.AIR, Items.AIR, Items.OAK_PLANKS));
        asm.upgradeHandler().setStackInSlot(0, new ItemStack(Registration.SPEED_UPGRADE.get()));

        ProcessingInterfaceBlockEntity proc = (ProcessingInterfaceBlockEntity) level.getBlockEntity(at(0, 0, -1));
        ItemStack pcard = new ItemStack(Registration.PROCESSING_CARD.get());
        pcard.set(Registration.PROCESSING_CARD_DATA.get(), new CardData.Processing(
                List.of(new ItemStack(Registration.SOULITE_INGOT.get()), new ItemStack(Items.SOUL_SAND), new ItemStack(Items.QUARTZ)),
                List.of(new ItemStack(Registration.INFUSED_SOULITE.get()))));
        proc.cardHandler().setStackInSlot(0, pcard);

        // Nether side of a quantum link, with a second drive.
        ServerLevel nether = server.getLevel(Level.NETHER);
        nether.setChunkForced(0, 0, true);
        BlockPos nb = new BlockPos(0, 100, 0);
        nether.setBlockAndUpdate(nb, Registration.QUANTUM_LINK.get().defaultBlockState());
        nether.setBlockAndUpdate(nb.east(), Registration.DRIVE.get().defaultBlockState());
        ((DriveBlockEntity) nether.getBlockEntity(nb.east())).cells().setStackInSlot(0, new ItemStack(Registration.CELLS.get(CellTier.GOLD).get()));
        QuantumLinkBlockEntity here = (QuantumLinkBlockEntity) level.getBlockEntity(at(0, 0, 1));
        QuantumLinkBlockEntity there = (QuantumLinkBlockEntity) nether.getBlockEntity(nb);
        here.setPartner(GlobalPos.of(Level.NETHER, nb));
        there.setPartner(GlobalPos.of(Level.OVERWORLD, at(0, 0, 1)));
    }

    private static net.minecraft.world.level.block.state.BlockState bus(boolean importing) {
        return (importing ? Registration.IMPORT_BUS.get() : Registration.EXPORT_BUS.get()).defaultBlockState()
                .setValue(FacingDeviceBlock.All.FACING, Direction.UP);
    }

    /** Places a Grim Pipe Industries omni pipe (found by reflection, it is only on the dev classpath). */
    private static void grimPipe(BlockPos pos, @javax.annotation.Nullable Direction pullFrom) {
        grimPipe(level, pos, pullFrom);
    }

    /** Places a Grim Pipe Industries omni pipe in any level (also used by the screenshot check). */
    static void grimPipe(ServerLevel level, BlockPos pos, @javax.annotation.Nullable Direction pullFrom) {
        try {
            level.setBlockAndUpdate(pos, net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .get(ResourceLocation.fromNamespaceAndPath("grimpipes", "reaper_pipe")).defaultBlockState());
            Object be = level.getBlockEntity(pos);
            ClassLoader cl = be.getClass().getClassLoader();
            @SuppressWarnings({"unchecked", "rawtypes"})
            Enum omni = Enum.valueOf((Class) Class.forName("com.xtri6.grimpipes.pipe.PipeType", true, cl), "OMNI");
            be.getClass().getMethod("addType", omni.getClass()).invoke(be, omni);
            if (pullFrom != null) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Enum input = Enum.valueOf((Class) Class.forName("com.xtri6.grimpipes.pipe.ConnectionMode", true, cl), "INPUT");
                be.getClass().getMethod("setMode", int.class, Direction.class, input.getClass()).invoke(be, omni.ordinal(), pullFrom, input);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static double avgTickMs() {
        long[] t = server.getTickTimesNanos();
        long sum = 0;
        for (long v : t) {
            sum += v;
        }
        return sum / (double) t.length / 1_000_000.0;
    }

    /** Sets every chemical side of a Mekanism machine, like a player with a Configurator (new tanks start closed). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void mekanismSidesTo(BlockPos pos, String dataType) {
        mekanismSidesTo(pos, "CHEMICAL", dataType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void mekanismSidesTo(BlockPos pos, String transmission, String dataType) {
        try {
            Object be = level.getBlockEntity(pos);
            Object config = be.getClass().getMethod("getConfig").invoke(be);
            Class tt = Class.forName("mekanism.common.lib.transmitter.TransmissionType");
            Object infoObj = config.getClass().getMethod("getConfig", tt).invoke(config, Enum.valueOf(tt, transmission));
            Class dt = Class.forName("mekanism.common.tile.component.config.DataType");
            Class rs = Class.forName("mekanism.api.RelativeSide");
            java.lang.reflect.Method set = infoObj.getClass().getMethod("setDataType", dt, rs);
            for (Object side : rs.getEnumConstants()) {
                set.invoke(infoObj, Enum.valueOf(dt, dataType), side);
            }
            level.invalidateCapabilities(pos);
        } catch (Throwable t) {
            GrimStorage.LOGGER.error("[SystemCheck] could not configure Mekanism sides", t);
        }
    }

    private static void buildSteps() {
        STEPS.add(new Step("cable placed by a command connects to blocks that were already there", 0, () -> {
            BlockPos o = at(0, 30, 40);
            level.setBlockAndUpdate(o, Registration.CONTROLLER.get().defaultBlockState());
            level.setBlockAndUpdate(o.east(), Registration.CABLE.get().defaultBlockState());
            level.setBlockAndUpdate(o.east(2), Registration.IMPORT_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, Direction.EAST));
            net.minecraft.world.level.block.state.BlockState c = level.getBlockState(o.east());
            info("cable state {}", c);
            boolean ok = com.xtri6.grimstorage.block.CableBlock.side(c, Direction.WEST) == com.xtri6.grimstorage.block.CableBlock.Side.CABLE && com.xtri6.grimstorage.block.CableBlock.side(c, Direction.EAST) == com.xtri6.grimstorage.block.CableBlock.Side.CABLE;
            level.removeBlock(o.east(2), false);
            level.removeBlock(o.east(), false);
            level.removeBlock(o, false);
            return ok;
        }));
        STEPS.add(new Step("network forms with 3 drives' worth of cells across dimensions", 5, () -> {
            StorageNetwork n = net();
            if (n == null) {
                return false;
            }
            info("members={} drives={}", n.memberCount(), n.driveCount());
            return n.driveCount() == 2 && n.memberCount() >= 12;
        }));
        STEPS.add(new Step("guide book: every item and recipe it mentions exists", 0, () -> {
            List<String> broken = new ArrayList<>();
            int pages = 0, recipes = 0, items = 0;
            try (var in = GrimStorage.class.getResourceAsStream("/assets/grimstorage/guide/en_us.json")) {
                var chapters = com.xtri6.grimstorage.guide.GuideData.parse(
                        com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject());
                for (var c : chapters) {
                    if (!BuiltInRegistries.ITEM.containsKey(c.icon())) {
                        broken.add("icon " + c.icon());
                    }
                    for (var page : c.pages()) {
                        pages++;
                        for (var id : page.items()) {
                            items++;
                            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                                broken.add("item " + id);
                            }
                        }
                        if (page.recipe() != null) {
                            recipes++;
                            if (server.getRecipeManager().byKey(page.recipe()).isEmpty()) {
                                broken.add("recipe " + page.recipe());
                            }
                        }
                    }
                }
                info("guide: {} chapters, {} pages, {} item icons, {} recipes, broken={}", chapters.size(), pages, items, recipes, broken);
                return chapters.size() == 13 && broken.isEmpty();
            } catch (Exception ex) {
                GrimStorage.LOGGER.error("[SystemCheck] guide parse failed", ex);
                return false;
            }
        }));
        STEPS.add(new Step("guide book is given once on first join", 0, () -> {
            FakePlayer fp = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "guide_tester"));
            boolean first = com.xtri6.grimstorage.item.GuideBookItem.giveIfFirstJoin(fp);
            boolean second = com.xtri6.grimstorage.item.GuideBookItem.giveIfFirstJoin(fp);
            int books = fp.getInventory().countItem(Registration.GUIDE_BOOK.get());
            info("first join gave={} second gave={} books={}", first, second, books);
            return first && !second && books == 1;
        }));
        STEPS.add(new Step("insert and extract", 1, () -> {
            StorageNetwork n = net();
            long a = n.insert(key(Items.OAK_LOG), 64, false);
            long b = n.insert(key(Items.COBBLESTONE), 100_000, false);
            long c = n.extract(key(Items.OAK_LOG), 10, false);
            info("logs in={} cobble in={} logs out={} logs now={} cobble now={}", a, b, c, count(Items.OAK_LOG), count(Items.COBBLESTONE));
            return a == 64 && b == 100_000 && c == 10 && count(Items.OAK_LOG) == 54 && count(Items.COBBLESTONE) == 100_000;
        }));
        STEPS.add(new Step("cobble went to the singularity cell (copper cell untouched)", 25, () -> {
            DriveBlockEntity drive = (DriveBlockEntity) level.getBlockEntity(at(1, 0, 0));
            CardData.CellStats copper = drive.cells().getStackInSlot(0).get(Registration.CELL_STATS.get());
            CardData.CellStats sing = drive.cells().getStackInSlot(1).get(Registration.CELL_STATS.get());
            info("copper stats={} singularity stats={}", copper, sing);
            return copper != null && copper.used() == 54 && sing != null && sing.used() == 100_000;
        }));
        STEPS.add(new Step("plan reports missing items", 0, () -> {
            CraftingPlanner.Plan p = net().plan(key(Items.DIAMOND_BLOCK), 1);
            return !p.ok() && p.missing().containsKey(key(Items.DIAMOND_BLOCK));
        }));
        STEPS.add(new Step("autocraft 16 sticks from logs (two-level chain)", 0, () -> {
            CraftingPlanner.Plan p = net().request(key(Items.STICK), 16, "test");
            info("plan ok={} steps={} missing={}", p.ok(), p.steps().size(), p.missing());
            return p.ok() && p.steps().size() == 2;
        }));
        STEPS.add(new Step("sticks done", 200, () -> {
            info("sticks={} planks={} logs={} jobs={}", count(Items.STICK), count(Items.OAK_PLANKS), count(Items.OAK_LOG), net().jobs().size());
            return count(Items.STICK) == 16 && count(Items.OAK_PLANKS) == 0 && count(Items.OAK_LOG) == 52 && net().jobs().isEmpty();
        }));
        STEPS.add(new Step("machine processing through the Soul Infuser", 0, () -> {
            StorageNetwork n = net();
            n.insert(key(Registration.SOULITE_INGOT.get()), 4, false);
            n.insert(key(Items.SOUL_SAND), 4, false);
            n.insert(key(Items.QUARTZ), 4, false);
            CraftingPlanner.Plan p = n.request(key(Registration.INFUSED_SOULITE.get()), 2, "test");
            info("processing plan ok={} missing={}", p.ok(), p.missing());
            return p.ok();
        }));
        STEPS.add(new Step("infused soulite came back", 400, () -> {
            info("infused={} ingots left={} jobs={}", count(Registration.INFUSED_SOULITE.get()), count(Registration.SOULITE_INGOT.get()),
                    net().jobs().isEmpty() ? "none" : net().jobs().get(0).status().getString());
            return count(Registration.INFUSED_SOULITE.get()) == 2 && count(Registration.SOULITE_INGOT.get()) == 2 && net().jobs().isEmpty();
        }));
        STEPS.add(new Step("stock keeper keeps 40 sticks", 0, () -> {
            StockKeeperBlockEntity k = (StockKeeperBlockEntity) level.getBlockEntity(at(0, 1, 0));
            k.filters().setItem(0, new ItemStack(Items.STICK));
            k.setTarget(0, 40);
            return true;
        }));
        STEPS.add(new Step("stock reached", 300, () -> {
            info("sticks={}", count(Items.STICK));
            return count(Items.STICK) >= 40;
        }));
        STEPS.add(new Step("network interface: pipes pull filtered items, push anything", 0, () -> {
            InterfaceBlockEntity itf = (InterfaceBlockEntity) level.getBlockEntity(at(-1, 0, 0));
            itf.filters().setItem(0, new ItemStack(Items.STICK));
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, at(-1, 0, 0), Direction.WEST);
            long before = count(Items.STICK);
            ItemStack got = h.extractItem(0, 5, false);
            ItemStack blocked = h.extractItem(1, 5, false);
            ItemStack rest = h.insertItem(9, new ItemStack(Items.FLINT, 7), false);
            info("pulled {} sticks, unfiltered slot gave {}, flint rest {}", got.getCount(), blocked.getCount(), rest.getCount());
            return got.getCount() == 5 && count(Items.STICK) == before - 5 && blocked.isEmpty() && rest.isEmpty() && count(Items.FLINT) == 7;
        }));
        STEPS.add(new Step("terminal: take a stack to cursor, put it back", 0, () -> {
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
            fp.setPos(BASE.getX() + 5.5, BASE.getY() + 1, BASE.getZ() + 0.5);
            TerminalMenu menu = new TerminalMenu(999, fp.getInventory(), () -> NetworkManager.get(level, at(5, 0, 0)), p -> true);
            long before = count(Items.OAK_LOG);
            menu.handleClick(fp, key(Items.OAK_LOG), TerminalMenu.EXTRACT_STACK);
            int carried = menu.getCarried().getCount();
            long during = count(Items.OAK_LOG);
            menu.handleClick(fp, null, TerminalMenu.INSERT_ALL);
            info("carried={} during={} after={}", carried, during, count(Items.OAK_LOG));
            return carried == Math.min(64, before) && during == before - carried && count(Items.OAK_LOG) == before && menu.getCarried().isEmpty();
        }));
        STEPS.add(new Step("recipe card editor fills in the result", 0, () -> {
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
            fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.RECIPE_CARD.get()));
            CardMenus.Recipe menu = new CardMenus.Recipe(998, fp.getInventory(), InteractionHand.MAIN_HAND);
            menu.setCarried(new ItemStack(Items.IRON_INGOT, 5));
            for (int slot : new int[] {0, 2, 3, 5, 6, 7, 8}) {
                menu.clicked(slot, 0, ClickType.PICKUP, fp);
            }
            CardData.Recipe data = fp.getMainHandItem().get(Registration.RECIPE_CARD_DATA.get());
            boolean carriedKept = menu.getCarried().getCount() == 5;
            fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            info("card result={} carried kept={}", data == null ? "none" : data.result(), carriedKept);
            return data != null && data.result().is(Items.CAULDRON) && carriedKept;
        }));
        STEPS.add(new Step("terminal crafting grid refills from the network", 0, () -> {
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
            fp.getInventory().clearContent();
            StorageNetwork n = net();
            long planksBefore = count(Items.OAK_PLANKS);
            n.insert(key(Items.OAK_PLANKS), 10, false);
            TerminalMenu menu = new TerminalMenu(997, fp.getInventory(), () -> NetworkManager.get(level, at(5, 0, 0)), p -> true);
            menu.getSlot(menu.gridStart).set(new ItemStack(Items.OAK_PLANKS));
            menu.getSlot(menu.gridStart + 3).set(new ItemStack(Items.OAK_PLANKS));
            ItemStack preview = menu.getSlot(menu.resultIndex).getItem().copy();
            menu.clicked(menu.resultIndex, 0, ClickType.QUICK_MOVE, fp);
            int sticks = fp.getInventory().countItem(Items.STICK);
            long planksLeft = count(Items.OAK_PLANKS);
            // one shift-click crafts what the network can feed the grid (up to a stack), and the grid keeps its planks
            boolean gridKept = menu.getSlot(menu.gridStart).getItem().is(Items.OAK_PLANKS)
                    && menu.getSlot(menu.gridStart + 3).getItem().is(Items.OAK_PLANKS);
            menu.getSlot(menu.gridStart).set(new ItemStack(Items.FLINT, 3));
            menu.clearGrid(fp);
            boolean gridEmpty = menu.getSlot(menu.gridStart).getItem().isEmpty();
            info("preview={} sticks crafted={} planks left in network={} (before {}) grid kept={} cleared={} flint={}",
                    preview, sticks, planksLeft, planksBefore, gridKept, gridEmpty, count(Items.FLINT));
            fp.getInventory().clearContent();
            // one shift-click makes at most one stack of output (64 sticks = 16 crafts)
            long crafts = Math.min(16, (planksBefore + 10) / 2);
            return preview.is(Items.STICK) && preview.getCount() == 4 && sticks == 4 * crafts
                    && planksLeft == planksBefore + 10 - 2 * crafts && gridKept && gridEmpty;
        }));
        STEPS.add(new Step("fluid, chemical, energy and Source cells go in the drive", 0, () -> {
            DriveBlockEntity drive = (DriveBlockEntity) level.getBlockEntity(at(1, 0, 0));
            int slot = 2;
            for (BulkKind kind : BulkKind.values()) {
                drive.cells().setStackInSlot(slot++, new ItemStack(Registration.BULK_CELLS.get(kind).get(CellTier.COPPER).get()));
            }
            return true;
        }));
        STEPS.add(new Step("interface: fluids in, pull only when switched on", 2, () -> {
            IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, at(-1, 0, 0), Direction.WEST);
            int in = h.fill(new FluidStack(Fluids.WATER, 5000), IFluidHandler.FluidAction.EXECUTE);
            int lavaIn = h.fill(new FluidStack(Fluids.LAVA, 3000), IFluidHandler.FluidAction.EXECUTE);
            FluidStack blocked = h.drain(1000, IFluidHandler.FluidAction.EXECUTE);
            ((InterfaceBlockEntity) level.getBlockEntity(at(-1, 0, 0))).toggleExport(BulkKind.FLUID);
            FluidStack got = h.drain(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
            long water = net().countBulk(BulkKey.fluid(new FluidStack(Fluids.WATER, 1)));
            info("water in={} lava in={} drain while off={} drain while on={} water stored={} tanks={}", in, lavaIn, blocked.getAmount(),
                    got.getAmount(), water, h.getTanks());
            return in == 5000 && lavaIn == 3000 && blocked.isEmpty() && got.getAmount() == 1000 && water == 4000 && h.getTanks() == 3;
        }));
        STEPS.add(new Step("interface: energy in, pull only when switched on", 0, () -> {
            IEnergyStorage e = level.getCapability(Capabilities.EnergyStorage.BLOCK, at(-1, 0, 0), Direction.WEST);
            int in = e.receiveEnergy(100_000, false);
            int blocked = e.extractEnergy(500, false);
            ((InterfaceBlockEntity) level.getBlockEntity(at(-1, 0, 0))).toggleExport(BulkKind.ENERGY);
            int got = e.extractEnergy(500, false);
            info("energy in={} pulled while off={} on={} stored={} max={}", in, blocked, got, e.getEnergyStored(), e.getMaxEnergyStored());
            return in == 100_000 && blocked == 0 && got == 500 && e.getEnergyStored() == 99_500 && e.getMaxEnergyStored() == 16_000_000;
        }));
        STEPS.add(new Step("Mekanism chemicals through the interface", 0, () -> {
            BlockCapability<IChemicalHandler, Direction> cap = BlockCapability.createSided(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);
            IChemicalHandler h = level.getCapability(cap, at(-1, 0, 0), Direction.WEST);
            if (h == null) {
                info("no chemical handler (Mekanism missing?)");
                return false;
            }
            Chemical hydrogen = MekanismAPI.CHEMICAL_REGISTRY.get(ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen"));
            ChemicalStack left = h.insertChemical(new ChemicalStack(hydrogen, 2000), Action.EXECUTE);
            ChemicalStack blocked = h.extractChemical(500, Action.EXECUTE);
            long stored = net().countBulk(BulkKey.chemical(ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen")));
            info("hydrogen leftover={} pulled while off={} stored={} in tank 0={}", left.getAmount(), blocked.getAmount(), stored,
                    h.getChemicalInTank(0).getAmount());
            return left.isEmpty() && blocked.isEmpty() && stored == 2000 && h.getChemicalInTank(0).getAmount() == 2000;
        }));
        STEPS.add(new Step("Source pool", 0, () -> {
            BulkKey src = BulkKey.flat(BulkKind.SOURCE);
            long in = net().insertBulk(src, 15_000, false);
            info("source accepted={} (cell holds 10k) stored={}", in, net().countBulk(src));
            return in == 10_000 && net().countBulk(src) == 10_000;
        }));
        STEPS.add(new Step("terminal: fill and empty a bucket and a Mekanism tank", 0, () -> {
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
            fp.getInventory().clearContent();
            TerminalMenu menu = new TerminalMenu(996, fp.getInventory(), () -> NetworkManager.get(level, at(5, 0, 0)), p -> true);
            BulkKey water = BulkKey.fluid(new FluidStack(Fluids.WATER, 1));
            menu.setCarried(new ItemStack(Items.BUCKET));
            menu.handleBulkClick(fp, BulkKind.FLUID, water);
            boolean filled = menu.getCarried().is(Items.WATER_BUCKET);
            long afterFill = net().countBulk(water);
            menu.handleBulkClick(fp, BulkKind.FLUID, null);
            boolean emptied = menu.getCarried().is(Items.BUCKET);
            long afterEmpty = net().countBulk(water);
            BulkKey h2 = BulkKey.chemical(ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen"));
            menu.setCarried(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("mekanism", "basic_chemical_tank"))));
            menu.handleBulkClick(fp, BulkKind.CHEMICAL, h2);
            long h2After = net().countBulk(h2);
            menu.handleBulkClick(fp, BulkKind.CHEMICAL, null);
            long h2Back = net().countBulk(h2);
            menu.setCarried(ItemStack.EMPTY);
            info("bucket filled={} water {}->{} emptied={} water back={} hydrogen into tank: {} left, after emptying tank: {}",
                    filled, 4000, afterFill, emptied, afterEmpty, h2After, h2Back);
            return filled && afterFill == 3000 && emptied && afterEmpty == 4000 && h2After == 0 && h2Back == 2000;
        }));
        // ---- Import / Export Bus
        final long[] before = new long[4];
        STEPS.add(new Step("buses: place import and export buses", 0, () -> {
            before[0] = count(Items.IRON_INGOT);
            before[1] = net().countBulk(BulkKey.fluid(new FluidStack(Fluids.WATER, 1)));
            before[2] = net().countBulk(BulkKey.flat(BulkKind.EXPERIENCE));
            before[3] = count(Items.APPLE);
            // Import bus on the cable, facing up into a barrel full of iron and apples.
            level.setBlockAndUpdate(at(3, 2, 0), Blocks.BARREL.defaultBlockState());
            IItemHandler barrel = level.getCapability(Capabilities.ItemHandler.BLOCK, at(3, 2, 0), Direction.DOWN);
            for (int i = 0; i < 3; i++) {
                barrel.insertItem(i, new ItemStack(Items.IRON_INGOT, 64), false);
            }
            barrel.insertItem(3, new ItemStack(Items.APPLE, 10), false);
            level.setBlockAndUpdate(at(3, 1, 0), bus(true));
            // Import bus on the terminal, facing up into a full water cauldron.
            level.setBlockAndUpdate(at(5, 2, 0), Blocks.WATER_CAULDRON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
            level.setBlockAndUpdate(at(5, 1, 0), bus(true));
            // Import bus on the assembler, facing open air, with XP orbs and an item floating there.
            level.setBlockAndUpdate(at(2, 1, 0), bus(true));
            level.addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level, BASE.getX() + 2.5, BASE.getY() + 2.3, BASE.getZ() + 0.5, 50));
            level.addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level, BASE.getX() + 2.5, BASE.getY() + 2.3, BASE.getZ() + 0.5, 25));
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level,
                    BASE.getX() + 2.5, BASE.getY() + 2.2, BASE.getZ() + 0.5, new ItemStack(Items.BONE, 5));
            drop.setNoGravity(true);
            level.addFreshEntity(drop);
            return true;
        }));
        STEPS.add(new Step("import bus empties a barrel", 40, () -> {
            IItemHandler barrel = level.getCapability(Capabilities.ItemHandler.BLOCK, at(3, 2, 0), Direction.DOWN);
            int left = 0;
            for (int i = 0; i < barrel.getSlots(); i++) {
                left += barrel.getStackInSlot(i).getCount();
            }
            long iron = count(Items.IRON_INGOT) - before[0];
            info("barrel left={} iron imported={} apples imported={}", left, iron, count(Items.APPLE) - before[3]);
            return left == 0 && iron == 192 && count(Items.APPLE) - before[3] == 10;
        }));
        STEPS.add(new Step("import bus drains a cauldron", 40, () -> {
            long water = net().countBulk(BulkKey.fluid(new FluidStack(Fluids.WATER, 1))) - before[1];
            boolean empty = level.getBlockState(at(5, 2, 0)).is(Blocks.CAULDRON);
            info("water imported={} cauldron empty={}", water, empty);
            return water == 1000 && empty;
        }));
        STEPS.add(new Step("import bus in open air picks up XP orbs and items", 40, () -> {
            long xp = net().countBulk(BulkKey.flat(BulkKind.EXPERIENCE)) - before[2];
            long bones = count(Items.BONE);
            info("xp collected={} bones={}", xp, bones);
            return xp == 75 && bones >= 5;
        }));
        STEPS.add(new Step("export bus sends only filtered items", 40, () -> {
            if (!level.getBlockState(at(4, 1, 0)).is(Registration.EXPORT_BUS.get())) {
                level.setBlockAndUpdate(at(4, 2, 0), Blocks.BARREL.defaultBlockState());
                level.setBlockAndUpdate(at(4, 1, 0), bus(false));
                com.xtri6.grimstorage.block.entity.BusBlockEntity b = (com.xtri6.grimstorage.block.entity.BusBlockEntity) level.getBlockEntity(at(4, 1, 0));
                b.filters().setItem(0, new ItemStack(Items.APPLE));
                return false;
            }
            IItemHandler barrel = level.getCapability(Capabilities.ItemHandler.BLOCK, at(4, 2, 0), Direction.DOWN);
            int apples = 0, other = 0;
            for (int i = 0; i < barrel.getSlots(); i++) {
                ItemStack st = barrel.getStackInSlot(i);
                if (st.is(Items.APPLE)) {
                    apples += st.getCount();
                } else {
                    other += st.getCount();
                }
            }
            info("barrel apples={} other={} apples left in network={}", apples, other, count(Items.APPLE));
            return apples == before[3] + 10 && other == 0 && count(Items.APPLE) == 0;
        }));
        STEPS.add(new Step("bus settings: blacklist, resource switches and saving", 0, () -> {
            com.xtri6.grimstorage.block.entity.BusBlockEntity b = (com.xtri6.grimstorage.block.entity.BusBlockEntity) level.getBlockEntity(at(4, 1, 0));
            boolean defaults = !b.blacklist() && b.moves(com.xtri6.grimstorage.block.entity.BusBlockEntity.ITEMS_BIT) && !b.moves(BulkKind.FLUID);
            b.toggleResource(1 + BulkKind.ENERGY.ordinal());
            var tag = b.saveWithoutMetadata(level.registryAccess());
            com.xtri6.grimstorage.block.entity.BusBlockEntity copy = new com.xtri6.grimstorage.block.entity.BusBlockEntity(at(4, 1, 0), b.getBlockState());
            copy.loadWithComponents(tag, level.registryAccess());
            b.toggleResource(1 + BulkKind.ENERGY.ordinal());
            boolean importDefaults = ((com.xtri6.grimstorage.block.entity.BusBlockEntity) level.getBlockEntity(at(3, 1, 0))).blacklist();
            info("export defaults ok={} energy saved={} filter saved={} import defaults to blacklist={}", defaults, copy.moves(BulkKind.ENERGY),
                    copy.filters().getItem(0).getItem(), importDefaults);
            return defaults && copy.moves(BulkKind.ENERGY) && copy.filters().getItem(0).is(Items.APPLE) && importDefaults;
        }));
        final int[] pipe = {0};
        final long[] pipeBefore = new long[2];
        STEPS.add(new Step("pipes: any mod's pipes push into an Import Bus and pull from an Export Bus", 80, () -> {
            com.xtri6.grimstorage.block.entity.BusBlockEntity exp = (com.xtri6.grimstorage.block.entity.BusBlockEntity) level.getBlockEntity(at(4, 1, 0));
            if (pipe[0] == 0) {
                pipe[0] = 1;
                pipeBefore[0] = count(Items.GOLD_NUGGET);
                // A hopper pointing into the side of the Import Bus stands in for any item pipe.
                level.setBlockAndUpdate(at(3, 1, 1), Blocks.HOPPER.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.HopperBlock.FACING, Direction.NORTH));
                level.getCapability(Capabilities.ItemHandler.BLOCK, at(3, 1, 1), Direction.UP).insertItem(0, new ItemStack(Items.GOLD_NUGGET, 4), false);
                return false;
            }
            if (count(Items.GOLD_NUGGET) - pipeBefore[0] < 4) {
                return false;
            }
            Direction side = Direction.SOUTH;
            // Items: an Export Bus offers only its filtered items to pipes, an Import Bus offers nothing.
            net().insert(new ItemStack(Items.APPLE, 5), false);
            IItemHandler outItems = level.getCapability(Capabilities.ItemHandler.BLOCK, at(4, 1, 0), side);
            IItemHandler inItems = level.getCapability(Capabilities.ItemHandler.BLOCK, at(3, 1, 0), side);
            ItemStack pulled = ItemStack.EMPTY;
            for (int i = 0; i < outItems.getSlots(); i++) {
                ItemStack st = outItems.extractItem(i, 64, false);
                if (!st.isEmpty()) {
                    pulled = st;
                    break;
                }
            }
            ItemStack fromImport = inItems.extractItem(0, 64, true);
            ItemStack intoExport = outItems.insertItem(0, new ItemStack(Items.STONE), true);
            // Fluids.
            IFluidHandler inFluid = level.getCapability(Capabilities.FluidHandler.BLOCK, at(3, 1, 0), side);
            IFluidHandler outFluid = level.getCapability(Capabilities.FluidHandler.BLOCK, at(4, 1, 0), side);
            BulkKey water = BulkKey.fluid(new FluidStack(Fluids.WATER, 1));
            long w0 = net().countBulk(water);
            int filled = inFluid.fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);
            int drainedOff = outFluid.drain(new FluidStack(Fluids.WATER, 300), IFluidHandler.FluidAction.EXECUTE).getAmount();
            exp.toggleResource(1 + BulkKind.FLUID.ordinal());
            exp.filters().setItem(1, new ItemStack(Items.WATER_BUCKET));
            int drainedOn = outFluid.drain(300, IFluidHandler.FluidAction.EXECUTE).getAmount();
            exp.filters().setItem(1, ItemStack.EMPTY);
            exp.toggleResource(1 + BulkKind.FLUID.ordinal());
            long w1 = net().countBulk(water);
            // Energy.
            IEnergyStorage inE = level.getCapability(Capabilities.EnergyStorage.BLOCK, at(3, 1, 0), side);
            IEnergyStorage outE = level.getCapability(Capabilities.EnergyStorage.BLOCK, at(4, 1, 0), side);
            int eIn = inE.receiveEnergy(1000, false);
            int eOff = outE.extractEnergy(400, false);
            exp.toggleResource(1 + BulkKind.ENERGY.ordinal());
            int eOn = outE.extractEnergy(400, false);
            exp.toggleResource(1 + BulkKind.ENERGY.ordinal());
            // Chemicals.
            BlockCapability<IChemicalHandler, Direction> cap = BlockCapability.createSided(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);
            IChemicalHandler chem = level.getCapability(cap, at(3, 1, 0), side);
            BulkKey h2 = BulkKey.chemical(ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen"));
            long c0 = net().countBulk(h2);
            Chemical hydrogen = MekanismAPI.CHEMICAL_REGISTRY.get(ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen"));
            ChemicalStack cLeft = chem == null ? null : chem.insertChemical(new ChemicalStack(hydrogen, 100), Action.EXECUTE);
            long cIn = net().countBulk(h2) - c0;
            level.removeBlock(at(3, 1, 1), false);
            info("hopper pushed nuggets in={} | export pulled={} import offered={} export accepts stone={} | water filled={} drained off={} on={} net change={}"
                    + " | energy in={} out off={} on={} | hydrogen in={}", count(Items.GOLD_NUGGET) - pipeBefore[0], pulled, fromImport,
                    intoExport.isEmpty(), filled, drainedOff, drainedOn, w1 - w0, eIn, eOff, eOn, cIn);
            return pulled.is(Items.APPLE) && pulled.getCount() == 5 && fromImport.isEmpty() && !intoExport.isEmpty()
                    && filled == 500 && drainedOff == 0 && drainedOn == 300 && w1 - w0 == 200
                    && eIn == 1000 && eOff == 0 && eOn == 400 && cLeft != null && cLeft.isEmpty() && cIn == 100;
        }));
        final int[] gp = {0};
        final long[] gpBefore = new long[1];
        STEPS.add(new Step("Grim Pipe Industries: omni pipes feed an Import Bus and drain an Export Bus", 200, () -> {
            if (!net.neoforged.fml.ModList.get().isLoaded("grimpipes")) {
                info("Grim Pipe Industries not in libs-dev, skipped");
                return true;
            }
            if (gp[0] == 0) {
                gp[0] = 1;
                gpBefore[0] = count(Items.REDSTONE);
                level.setBlockAndUpdate(at(0, -2, 0), Registration.CABLE.get().defaultBlockState());
                level.setBlockAndUpdate(at(0, -3, 0), Registration.CABLE.get().defaultBlockState());
                // Barrel of redstone -> pipe (pulling) -> pipe -> side of an Import Bus.
                level.setBlockAndUpdate(at(1, -3, 0), Registration.IMPORT_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, Direction.EAST));
                level.setBlockAndUpdate(at(2, -3, 0), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(at(1, -3, 3), Blocks.BARREL.defaultBlockState());
                level.getCapability(Capabilities.ItemHandler.BLOCK, at(1, -3, 3), Direction.NORTH).insertItem(0, new ItemStack(Items.REDSTONE, 32), false);
                grimPipe(at(1, -3, 1), null);
                grimPipe(at(1, -3, 2), Direction.SOUTH);
                // Export Bus (coal and water) -> pipe (pulling from the bus) -> pipe -> barrel and cauldron.
                level.setBlockAndUpdate(at(-1, -3, 0), Registration.EXPORT_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, Direction.WEST));
                level.setBlockAndUpdate(at(-2, -3, 0), Blocks.STONE.defaultBlockState());
                com.xtri6.grimstorage.block.entity.BusBlockEntity exp = (com.xtri6.grimstorage.block.entity.BusBlockEntity) level.getBlockEntity(at(-1, -3, 0));
                exp.filters().setItem(0, new ItemStack(Items.COAL));
                exp.filters().setItem(1, new ItemStack(Items.WATER_BUCKET));
                exp.toggleResource(1 + BulkKind.FLUID.ordinal());
                net().insert(new ItemStack(Items.COAL, 24), false);
                net().insertBulk(BulkKey.fluid(new FluidStack(Fluids.WATER, 1)), 2000, false);
                level.setBlockAndUpdate(at(-1, -3, 3), Blocks.BARREL.defaultBlockState());
                // Pipes deliver fluid a little at a time, so the sink is a Mekanism tank (a cauldron only takes whole buckets).
                level.setBlockAndUpdate(at(-2, -3, 2), net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .get(ResourceLocation.fromNamespaceAndPath("mekanism", "basic_fluid_tank")).defaultBlockState());
                grimPipe(at(-1, -3, 1), Direction.NORTH);
                grimPipe(at(-1, -3, 2), null);
                return false;
            }
            long redstone = count(Items.REDSTONE) - gpBefore[0];
            IItemHandler out = level.getCapability(Capabilities.ItemHandler.BLOCK, at(-1, -3, 3), Direction.NORTH);
            int coal = 0;
            for (int i = 0; i < out.getSlots(); i++) {
                coal += out.getStackInSlot(i).is(Items.COAL) ? out.getStackInSlot(i).getCount() : 0;
            }
            IFluidHandler tank = level.getCapability(Capabilities.FluidHandler.BLOCK, at(-2, -3, 2), Direction.EAST);
            int tankWater = tank == null ? -1 : tank.getFluidInTank(0).getAmount();
            long waterLeft = net().countBulk(BulkKey.fluid(new FluidStack(Fluids.WATER, 1)));
            boolean water = tankWater >= 2000 && waterLeft == 0;
            if (gp[0]++ % 20 == 1) {
                info("redstone piped into the network={} coal piped out={} water in tank={} water left in network={}", redstone, coal, tankWater, waterLeft);
            }
            return redstone == 32 && coal == 24 && count(Items.COAL) == 0 && water;
        }));
        final int[] cb = {0};
        final long[] cbBefore = new long[1];
        STEPS.add(new Step("cable buses: use an Import Bus on a barrel next to a cable, it goes on the cable", 60, () -> {
            BlockPos cable = at(1, 1, 0);
            BlockPos barrel = at(1, 1, -1);
            if (cb[0] == 0) {
                cb[0] = 1;
                cbBefore[0] = count(Items.EMERALD);
                level.setBlockAndUpdate(cable, Registration.CABLE.get().defaultBlockState());
                level.setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());
                level.getCapability(Capabilities.ItemHandler.BLOCK, barrel, Direction.SOUTH).insertItem(0, new ItemStack(Items.EMERALD, 40), false);
                FakePlayer fp = FakePlayerFactory.getMinecraft(level);
                ItemStack held = new ItemStack(Registration.IMPORT_BUS_ITEM.get(), 2);
                fp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
                // click the barrel's south face, where the cable is
                var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(barrel).add(0, 0, 0.5), Direction.SOUTH, barrel, false);
                var result = held.useOn(new net.minecraft.world.item.context.UseOnContext(fp, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
                var st = level.getBlockState(cable);
                boolean onCable = com.xtri6.grimstorage.block.CableBlock.side(st, Direction.NORTH) == com.xtri6.grimstorage.block.CableBlock.Side.IMPORT
                        && level.getBlockEntity(cable) instanceof com.xtri6.grimstorage.block.entity.CableBusBlockEntity be && be.part(Direction.NORTH) != null;
                                info("use result={} bus on cable west side={} used one item={} network blocked on bus side={} cable down={}", result, onCable,
                        held.getCount() == 1, !((com.xtri6.grimstorage.block.CableBlock) st.getBlock()).connectsTo(st, Direction.NORTH),
                        com.xtri6.grimstorage.block.CableBlock.side(st, Direction.DOWN));
                return false;
            }
            long got = count(Items.EMERALD) - cbBefore[0];
            if (cb[0]++ % 10 == 1) {
                info("emeralds into the network through the cable bus={}", got);
            }
            return got == 40;
        }));
        final int[] ce = {0};
        STEPS.add(new Step("cable buses: Export Bus used on the cable itself, filter screen, pipes, saving and taking it off", 60, () -> {
            BlockPos cable = at(1, 2, 0);
            BlockPos barrel = at(1, 2, -1);
            if (ce[0] == 0) {
                ce[0] = 1;
                level.setBlockAndUpdate(cable, Registration.CABLE.get().defaultBlockState());
                level.setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());
                FakePlayer fp = FakePlayerFactory.getMinecraft(level);
                ItemStack held = new ItemStack(Registration.EXPORT_BUS_ITEM.get());
                fp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
                // click the cable's own north face
                var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(cable).add(0, 0, -0.4), Direction.NORTH, cable, false);
                held.useOn(new net.minecraft.world.item.context.UseOnContext(fp, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
                var be = (com.xtri6.grimstorage.block.entity.CableBusBlockEntity) level.getBlockEntity(cable);
                // set its filter through the real bus screen
                com.xtri6.grimstorage.menu.BlockMenus.Bus menu = com.xtri6.grimstorage.menu.BlockMenus.Bus.server(990, fp.getInventory(), be.part(Direction.NORTH),
                        net.minecraft.world.inventory.ContainerLevelAccess.create(level, cable), level.getBlockState(cable).getBlock());
                menu.getSlot(0).set(new ItemStack(Items.EMERALD));
                info("export bus on cable={} filter={} menu still valid={}", be.part(Direction.NORTH).isExport(), be.part(Direction.NORTH).filters().getItem(0),
                        menu.stillValid(fp));
                return false;
            }
            IItemHandler out = level.getCapability(Capabilities.ItemHandler.BLOCK, barrel, Direction.SOUTH);
            int em = 0;
            for (int i = 0; i < out.getSlots(); i++) {
                em += out.getStackInSlot(i).is(Items.EMERALD) ? out.getStackInSlot(i).getCount() : 0;
            }
            if (em < 40) {
                return false;
            }
            var be = (com.xtri6.grimstorage.block.entity.CableBusBlockEntity) level.getBlockEntity(cable);
            // pipes: the cable offers the export bus on its west side only
            IItemHandler west = level.getCapability(Capabilities.ItemHandler.BLOCK, cable, Direction.NORTH);
            IItemHandler north = level.getCapability(Capabilities.ItemHandler.BLOCK, cable, Direction.WEST);
            // saving
            var tag = be.saveWithoutMetadata(level.registryAccess());
            var copy = new com.xtri6.grimstorage.block.entity.CableBusBlockEntity(cable, level.getBlockState(cable));
            copy.loadWithComponents(tag, level.registryAccess());
            boolean saved = copy.part(Direction.NORTH) != null && copy.part(Direction.NORTH).isExport()
                    && copy.part(Direction.NORTH).filters().getItem(0).is(Items.EMERALD);
            // take it off: the cable stays, the bus drops, the block entity goes
            com.xtri6.grimstorage.block.CableBlock.removeBus(level, cable, Direction.NORTH, true);
            var st = level.getBlockState(cable);
            boolean off = st.is(Registration.CABLE.get()) && com.xtri6.grimstorage.block.CableBlock.side(st, Direction.NORTH) == com.xtri6.grimstorage.block.CableBlock.Side.NONE
                    && level.getBlockEntity(cable) == null;
            int dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(cable).inflate(1.5),
                    e -> e.getItem().is(Registration.EXPORT_BUS_ITEM.get())).size();
            info("barrel emeralds={} pipe handler on bus side={} other side={} saved={} removed={} bus item dropped={}", em, west != null, north != null, saved, off, dropped);
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(cable).inflate(1.5)).forEach(e -> e.discard());
            return em == 40 && count(Items.EMERALD) == 0 && west != null && north == null && saved && off && dropped == 1;
        }));
        final int[] cf = {0};
        final long[] cfBefore = new long[1];
        STEPS.add(new Step("wrong bus: an Export Bus on a full double chest says so, and one click turns it into an Import Bus", 80, () -> {
            BlockPos cable = at(1, 3, 0);
            BlockPos chest = at(1, 3, -1);
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
            if (cf[0] == 0) {
                cf[0] = 1;
                cfBefore[0] = count(Items.IRON_NUGGET);
                level.setBlockAndUpdate(cable, Registration.CABLE.get().defaultBlockState());
                var cs = Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH);
                level.setBlockAndUpdate(chest, cs.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
                level.setBlockAndUpdate(chest.east(), cs.setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT));
                IItemHandler inv = level.getCapability(Capabilities.ItemHandler.BLOCK, chest, Direction.SOUTH);
                info("double chest slots={}", inv.getSlots());
                inv.insertItem(0, new ItemStack(Items.IRON_NUGGET, 64), false);
                inv.insertItem(40, new ItemStack(Items.IRON_NUGGET, 64), false);
                ItemStack held = new ItemStack(Registration.EXPORT_BUS_ITEM.get());
                fp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
                var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(chest).add(0, 0, 0.5), Direction.SOUTH, chest, false);
                held.useOn(new net.minecraft.world.item.context.UseOnContext(fp, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
                return false;
            }
            var be = (com.xtri6.grimstorage.block.entity.CableBusBlockEntity) level.getBlockEntity(cable);
            var bus = be.part(Direction.NORTH);
            if (cf[0] == 1) {
                if (cf[0]++ == 1) {
                    return false;
                }
            }
            if (cf[0] < 25) {
                cf[0]++;
                return false;
            }
            if (cf[0] == 25) {
                cf[0]++;
                long moved = count(Items.IRON_NUGGET) - cfBefore[0];
                info("as an Export Bus: nuggets moved={} status={}", moved, bus.status());
                if (moved != 0 || bus.status() != com.xtri6.grimstorage.block.entity.BusLogic.Status.NO_FILTER) {
                    return false;
                }
                var menu = com.xtri6.grimstorage.menu.BlockMenus.Bus.server(989, fp.getInventory(), bus,
                        net.minecraft.world.inventory.ContainerLevelAccess.create(level, cable), level.getBlockState(cable).getBlock());
                menu.press(com.xtri6.grimstorage.menu.BlockMenus.Bus.DIRECTION, fp);
                info("after the direction button: cable side={} bus export={} blacklist={}", com.xtri6.grimstorage.block.CableBlock.side(level.getBlockState(cable), Direction.NORTH),
                        bus.isExport(), bus.blacklist());
                // a free-standing bus switches too, keeping its facing
                level.setBlockAndUpdate(at(1, 4, 0), Registration.EXPORT_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, Direction.UP));
                ((com.xtri6.grimstorage.block.entity.BusBlockEntity) level.getBlockEntity(at(1, 4, 0))).switchDirection(fp);
                var st = level.getBlockState(at(1, 4, 0));
                info("free-standing bus after switching: {}", st);
                boolean standalone = st.is(Registration.IMPORT_BUS.get()) && st.getValue(FacingDeviceBlock.All.FACING) == Direction.UP
                        && level.getBlockEntity(at(1, 4, 0)) instanceof com.xtri6.grimstorage.block.entity.BusBlockEntity nb && !nb.isExport();
                level.removeBlock(at(1, 4, 0), false);
                return false;
            }
            long moved = count(Items.IRON_NUGGET) - cfBefore[0];
            if (cf[0]++ % 10 == 0) {
                info("as an Import Bus: nuggets into the network={}", moved);
            }
            return moved == 128 && !bus.isExport();
        }));
        final int[] gp2 = {0};
        final long[] gp2Before = new long[1];
        STEPS.add(new Step("a pipe placed first sees an Import Bus clipped onto the cable next to it", 200, () -> {
            if (!net.neoforged.fml.ModList.get().isLoaded("grimpipes")) {
                info("Grim Pipe Industries not in libs-dev, skipped");
                return true;
            }
            BlockPos cable = at(1, 4, 0);
            if (gp2[0] == 0) {
                gp2[0] = 1;
                gp2Before[0] = count(Items.LAPIS_LAZULI);
                // barrel <- pipe (pulling) <- pipe, all placed before the cable and its bus exist
                level.setBlockAndUpdate(at(1, 4, -3), Blocks.BARREL.defaultBlockState());
                level.getCapability(Capabilities.ItemHandler.BLOCK, at(1, 4, -3), Direction.SOUTH).insertItem(0, new ItemStack(Items.LAPIS_LAZULI, 48), false);
                grimPipe(at(1, 4, -2), Direction.NORTH);
                grimPipe(at(1, 4, -1), null);
                level.setBlockAndUpdate(cable, Registration.CABLE.get().defaultBlockState());
                com.xtri6.grimstorage.block.CableBlock.attachBus(level, cable, Direction.NORTH, false);
                return false;
            }
            long got = count(Items.LAPIS_LAZULI) - gp2Before[0];
            if (gp2[0]++ % 20 == 1) {
                info("lapis piped into the cable bus={}", got);
            }
            return got == 48;
        }));
        final int[] ff = {0};
        final long[] ffBefore = new long[2];
        final String[] ffStatus = {""};
        STEPS.add(new Step("buses facing straight into a pipe end: pipe feeds an Import Bus, an Export Bus feeds the pipe", 200, () -> {
            if (!net.neoforged.fml.ModList.get().isLoaded("grimpipes")) {
                return true;
            }
            if (ff[0] == 0) {
                ff[0] = 1;
                ffBefore[0] = count(Items.QUARTZ);
                // network cable column at x = 6, z = 5..; Import Bus at (6,0,5)? keep clear of other tests: use y = 8
                // cable from the Network Interface at (-1,0,0) out to x = -3 and up to y = 8
                level.setBlockAndUpdate(at(-2, 0, 0), Registration.CABLE.get().defaultBlockState());
                for (int y = 0; y <= 8; y++) {
                    level.setBlockAndUpdate(at(-3, y, 0), Registration.CABLE.get().defaultBlockState());
                }
                // Import Bus at (1,8,-1) facing north into a pipe at (1,8,-2); the pipe pulls from a barrel at (1,8,-3)
                level.setBlockAndUpdate(at(-3, 8, -3), Blocks.BARREL.defaultBlockState());
                level.getCapability(Capabilities.ItemHandler.BLOCK, at(-3, 8, -3), Direction.SOUTH).insertItem(0, new ItemStack(Items.QUARTZ, 32), false);
                grimPipe(at(-3, 8, -2), Direction.NORTH);
                level.setBlockAndUpdate(at(-3, 8, -1), Registration.IMPORT_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, Direction.NORTH));
                // Export Bus at (1,8,1) facing south into a pipe at (1,8,2), which runs into a barrel at (1,8,3)
                ffBefore[1] = 0;
                net().insert(new ItemStack(Items.PRISMARINE_SHARD, 20), false);
                level.setBlockAndUpdate(at(-3, 8, 1), Registration.EXPORT_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, Direction.SOUTH));
                ((com.xtri6.grimstorage.block.entity.BusBlockEntity) level.getBlockEntity(at(-3, 8, 1))).filters().setItem(0, new ItemStack(Items.PRISMARINE_SHARD));
                // a pipe end set to pull (the pipe mod's Input mode) takes from the Export Bus
                grimPipe(at(-3, 8, 2), Direction.NORTH);
                level.setBlockAndUpdate(at(-3, 8, 3), Blocks.BARREL.defaultBlockState());
                return false;
            }
            long in = count(Items.QUARTZ) - ffBefore[0];
            IItemHandler out = level.getCapability(Capabilities.ItemHandler.BLOCK, at(-3, 8, 3), Direction.NORTH);
            int shards = 0;
            for (int i = 0; i < out.getSlots(); i++) {
                shards += out.getStackInSlot(i).is(Items.PRISMARINE_SHARD) ? out.getStackInSlot(i).getCount() : 0;
            }
            if (ff[0]++ % 20 == 1) {
                var ib = (com.xtri6.grimstorage.block.entity.BusBlockEntity) level.getBlockEntity(at(-3, 8, -1));
                var eb = (com.xtri6.grimstorage.block.entity.BusBlockEntity) level.getBlockEntity(at(-3, 8, 1));
                info("face to face: quartz into network={} (import status {}) shards into the far barrel={} (export status {})", in, ib.status(), shards, eb.status());
            }
            var ibs = ((com.xtri6.grimstorage.block.entity.BusBlockEntity) level.getBlockEntity(at(-3, 8, -1))).status();
            if (in == 32 && shards == 20) {
                info("face to face done: import status while fed={}", ffStatus[0]);
            }
            if (in > 0 && in < 32) {
                ffStatus[0] = ibs.name();
            }
            return in == 32 && shards == 20 && "WORKING".equals(ffStatus[0]);
        }));
        final int[] sl = {0};
        final long[] slBefore = new long[1];
        STEPS.add(new Step("a bus with cable touching only its side links up, draws an arm that way and works", 60, () -> {
            BlockPos bus = at(-4, 5, 0);
            BlockPos barrel = at(-4, 5, -1);
            if (sl[0] == 0) {
                sl[0] = 1;
                slBefore[0] = count(Items.AMETHYST_SHARD);
                level.setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());
                level.getCapability(Capabilities.ItemHandler.BLOCK, barrel, Direction.SOUTH).insertItem(0, new ItemStack(Items.AMETHYST_SHARD, 24), false);
                // facing north into the barrel; the network cable column at x = -3 touches its east side
                level.setBlockAndUpdate(bus, Registration.IMPORT_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, Direction.NORTH));
                return false;
            }
            var st = level.getBlockState(bus);
            var cable = level.getBlockState(at(-3, 5, 0));
            long got = count(Items.AMETHYST_SHARD) - slBefore[0];
            boolean arms = st.getValue(com.xtri6.grimstorage.block.BusBlock.LINKS.get(Direction.EAST))
                    && !st.getValue(com.xtri6.grimstorage.block.BusBlock.LINKS.get(Direction.NORTH))
                    && !st.getValue(com.xtri6.grimstorage.block.BusBlock.LINKS.get(Direction.SOUTH))
                    && com.xtri6.grimstorage.block.CableBlock.side(cable, Direction.WEST) == com.xtri6.grimstorage.block.CableBlock.Side.CABLE;
            if (sl[0]++ % 10 == 1) {
                info("side-linked bus: {} cable west side={} shards in={}", st, com.xtri6.grimstorage.block.CableBlock.side(cable, Direction.WEST), got);
            }
            return arms && got == 24;
        }));
        final int[] sp = {0};
        final long[] spBefore = new long[1];
        STEPS.add(new Step("speed upgrades: 8 in a bus make it 9x as fast, and they drop when the bus comes off", 40, () -> {
            BlockPos cable = at(1, 5, 0);
            BlockPos barrel = at(1, 5, -1);
            if (sp[0] == 0) {
                sp[0] = 1;
                spBefore[0] = count(Items.FLINT);
                level.setBlockAndUpdate(cable, Registration.CABLE.get().defaultBlockState());
                level.setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());
                IItemHandler inv = level.getCapability(Capabilities.ItemHandler.BLOCK, barrel, Direction.SOUTH);
                for (int i = 0; i < 18; i++) {
                    inv.insertItem(i, new ItemStack(Items.FLINT, 64), false);
                }
                com.xtri6.grimstorage.block.CableBlock.attachBus(level, cable, Direction.NORTH, false);
                var bus = ((com.xtri6.grimstorage.block.entity.CableBusBlockEntity) level.getBlockEntity(cable)).part(Direction.NORTH);
                ItemStack extra = bus.upgrades().insertItem(0, new ItemStack(Registration.SPEED_UPGRADE.get(), 2), false);
                for (int i = 1; i < 8; i++) {
                    bus.upgrades().insertItem(i, new ItemStack(Registration.SPEED_UPGRADE.get()), false);
                }
                ItemStack ninth = new ItemStack(Items.FLINT);
                info("speed upgrades in={} speed=x{} second one in slot 0 refused={} flint refused={}", bus.upgradeCount(), bus.speed(),
                        extra.getCount() == 1, !bus.upgrades().isItemValid(0, ninth));
                return false;
            }
            if (sp[0]++ < 4) {
                return false;
            }
            long got = count(Items.FLINT) - spBefore[0];
            var bus = ((com.xtri6.grimstorage.block.entity.CableBusBlockEntity) level.getBlockEntity(cable)).part(Direction.NORTH);
            boolean fast = got == 1152 && bus.speed() == 9;
            // take it off: 1 bus + 8 upgrades drop
            com.xtri6.grimstorage.block.CableBlock.removeBus(level, cable, Direction.NORTH, true);
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(cable).inflate(1.5));
            int ups = 0, buses = 0;
            for (var e : drops) {
                ups += e.getItem().is(Registration.SPEED_UPGRADE.get()) ? e.getItem().getCount() : 0;
                buses += e.getItem().is(Registration.IMPORT_BUS_ITEM.get()) ? e.getItem().getCount() : 0;
                e.discard();
            }
            info("flint moved in 3 runs={} (1152 = 128 x 9) drops: upgrades={} buses={}", got, ups, buses);
            return fast && ups == 8 && buses == 1;
        }));
        STEPS.add(new Step("soul wrench: cut and rejoin a cable link, turn a machine, leave a chest alone", 0, () -> {
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
            ItemStack wrench = new ItemStack(Registration.SOUL_WRENCH.get());
            fp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wrench);
            BlockPos a = at(1, 6, 0);
            BlockPos b = at(1, 7, 0);
            level.setBlockAndUpdate(a, Registration.CABLE.get().defaultBlockState());
            level.setBlockAndUpdate(b, Registration.CABLE.get().defaultBlockState());
            var cs = com.xtri6.grimstorage.block.CableBlock.Side.class;
            // click the upper arm of the lower cable
            var hitArm = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(a.getX() + 0.5, a.getY() + 0.95, a.getZ() + 0.5), Direction.EAST, a, false);
            wrench.useOn(new net.minecraft.world.item.context.UseOnContext(fp, net.minecraft.world.InteractionHand.MAIN_HAND, hitArm));
            var sa = com.xtri6.grimstorage.block.CableBlock.side(level.getBlockState(a), Direction.UP);
            var sb = com.xtri6.grimstorage.block.CableBlock.side(level.getBlockState(b), Direction.DOWN);
            boolean cut = sa == com.xtri6.grimstorage.block.CableBlock.Side.BLOCKED && sb == com.xtri6.grimstorage.block.CableBlock.Side.NONE;
            // click the top face of the core to join it back
            var hitCore = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(a.getX() + 0.5, a.getY() + 0.625, a.getZ() + 0.5), Direction.UP, a, false);
            wrench.useOn(new net.minecraft.world.item.context.UseOnContext(fp, net.minecraft.world.InteractionHand.MAIN_HAND, hitCore));
            boolean joined = com.xtri6.grimstorage.block.CableBlock.side(level.getBlockState(a), Direction.UP) == com.xtri6.grimstorage.block.CableBlock.Side.CABLE
                    && com.xtri6.grimstorage.block.CableBlock.side(level.getBlockState(b), Direction.DOWN) == com.xtri6.grimstorage.block.CableBlock.Side.CABLE;
            // turn a drive to face east
            BlockPos drive = at(3, 6, 0);
            level.setBlockAndUpdate(drive, Registration.DRIVE.get().defaultBlockState());
            var driveBefore = level.getBlockState(drive);
            var hitDrive = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(drive).add(0.5, 0, 0), Direction.EAST, drive, false);
            var r = wrench.useOn(new net.minecraft.world.item.context.UseOnContext(fp, net.minecraft.world.InteractionHand.MAIN_HAND, hitDrive));
            var after = level.getBlockState(drive);
            BlockPos chest = at(4, 6, 0);
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            var hitChest = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(chest).add(0.5, 0, 0), Direction.EAST, chest, false);
            var rc = wrench.useOn(new net.minecraft.world.item.context.UseOnContext(fp, net.minecraft.world.InteractionHand.MAIN_HAND, hitChest));
            info("cut={} joined={} drive {} -> {} ({}) chest result={}", cut, joined, driveBefore, after, r, rc);
            boolean turned = after.toString().contains("facing=east") && !driveBefore.toString().contains("facing=east");
            for (BlockPos p2 : List.of(a, b, drive, chest)) {
                level.removeBlock(p2, false);
            }
            return cut && joined && turned && rc == net.minecraft.world.InteractionResult.PASS;
        }));
        STEPS.add(new Step("terminal: store and take XP", 0, () -> {
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
            TerminalMenu menu = new TerminalMenu(995, fp.getInventory(), () -> NetworkManager.get(level, at(5, 0, 0)), p -> true);
            BulkKey xp = BulkKey.flat(BulkKind.EXPERIENCE);
            com.xtri6.grimstorage.storage.Experience.setPoints(fp, 1000);
            long start = net().countBulk(xp);
            int startLevel = fp.experienceLevel;
            menu.handleXp(fp, 2);
            long stored = net().countBulk(xp) - start;
            long left = com.xtri6.grimstorage.storage.Experience.points(fp);
            menu.handleXp(fp, 1);
            int lvl = fp.experienceLevel;
            long after = net().countBulk(xp);
            info("1000 points = level {}; stored={} player left={} after taking 10 levels: level={} network xp={}", startLevel, stored, left, lvl,
                    after);
            return startLevel == 26 && stored == 1000 && left == 0 && lvl == 10 && after == start + 1000 - com.xtri6.grimstorage.storage.Experience.pointsForLevel(10);
        }));
        final int[] phase = {0};
        STEPS.add(new Step("import bus pulls chemicals from another network, and its front never merges networks", 60, () -> {
            BulkKey h2 = BulkKey.chemical(ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen"));
            if (phase[0] == 0) {
                // A second, separate network: interface (8) - controller (9) - drive (10). The main network reaches it
                // only through an Import Bus at (7) facing east, connected by cable at (6).
                level.setBlockAndUpdate(at(8, 0, 0), Registration.INTERFACE.get().defaultBlockState());
                level.setBlockAndUpdate(at(9, 0, 0), Registration.CONTROLLER.get().defaultBlockState());
                level.setBlockAndUpdate(at(10, 0, 0), Registration.DRIVE.get().defaultBlockState());
                ((DriveBlockEntity) level.getBlockEntity(at(10, 0, 0))).cells().setStackInSlot(0,
                        new ItemStack(Registration.BULK_CELLS.get(BulkKind.CHEMICAL).get(CellTier.COPPER).get()));
                ((InterfaceBlockEntity) level.getBlockEntity(at(8, 0, 0))).toggleExport(BulkKind.CHEMICAL);
                phase[0] = 1;
                return false;
            }
            StorageNetwork other = NetworkManager.get(level, at(9, 0, 0));
            if (phase[0] == 1) {
                if (other == null) {
                    return false;
                }
                before[0] = net().countBulk(h2);
                long in = other.insertBulk(h2, 3000, false);
                level.setBlockAndUpdate(at(6, 0, 0), Registration.CABLE.get().defaultBlockState());
                level.setBlockAndUpdate(at(7, 0, 0), Registration.IMPORT_BUS.get().defaultBlockState()
                        .setValue(FacingDeviceBlock.All.FACING, Direction.EAST));
                info("second network up, hydrogen put in={}", in);
                phase[0] = 2;
                return false;
            }
            StorageNetwork main = net();
            long got = main == null ? -1 : main.countBulk(h2) - before[0];
            long left = other == null ? -1 : other.countBulk(h2);
            info("main network ok={} other network ok={} hydrogen moved={} left over there={}", main != null, other != null, got, left);
            return main != null && other != null && main != other && got == 3000 && left == 0;
        }));
        // ---- Lag check: 256 idle buses on the network
        final double[] tickMs = new double[2];
        final int[] lagWait = {0};
        final long[] epochAt = {0};
        STEPS.add(new Step("lag check: baseline tick time", 120, () -> {
            if (lagWait[0]++ < 110) {
                return false;
            }
            tickMs[0] = avgTickMs();
            lagWait[0] = 0;
            for (int cx = 0; cx <= 4; cx++) {
                level.setChunkForced(cx, 0, true);
            }
            level.setBlockAndUpdate(at(0, 0, 2), Registration.CABLE.get().defaultBlockState());
            for (int x = 0; x < 64; x++) {
                BlockPos cable = at(x, 0, 3);
                level.setBlockAndUpdate(cable, Registration.CABLE.get().defaultBlockState());
                for (Direction d : new Direction[] {Direction.UP, Direction.DOWN, Direction.SOUTH}) {
                    level.setBlockAndUpdate(cable.relative(d), Registration.IMPORT_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, d));
                    level.setBlockAndUpdate(cable.relative(d, 2), Blocks.BARREL.defaultBlockState());
                }
                // and an Export Bus with a filter, facing an empty barrel
                BlockPos top = at(x, 0, 3).relative(Direction.UP, 3);
                level.setBlockAndUpdate(top, Blocks.AIR.defaultBlockState());
            }
            for (int x = 0; x < 64; x++) {
                BlockPos side = at(x, 0, 3).relative(Direction.NORTH);
                if (x == 0) {
                    continue;
                }
                level.setBlockAndUpdate(side, Registration.EXPORT_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, Direction.NORTH));
                level.setBlockAndUpdate(side.relative(Direction.NORTH), Blocks.BARREL.defaultBlockState());
                ((com.xtri6.grimstorage.block.entity.BusBlockEntity) level.getBlockEntity(side)).filters().setItem(0, new ItemStack(Items.DIAMOND));
            }
            return true;
        }));
        STEPS.add(new Step("lag check: 255 idle buses barely cost anything", 140, () -> {
            if (lagWait[0] == 30) {
                epochAt[0] = NetworkManager.epoch();
                com.xtri6.grimstorage.block.entity.BusLogic.profileNanos = 0;
                com.xtri6.grimstorage.block.entity.BusLogic.profileTicks = 0;
            }
            if (lagWait[0]++ < 130) {
                return false;
            }
            double busUs = com.xtri6.grimstorage.block.entity.BusLogic.profileNanos / 1000.0
                    / Math.max(1, com.xtri6.grimstorage.block.entity.BusLogic.profileTicks);
            tickMs[1] = avgTickMs();
            StorageNetwork n = net();
            int buses = 0;
            for (int x = 0; x < 64; x++) {
                for (Direction d : Direction.values()) {
                    if (level.getBlockEntity(at(x, 0, 3).relative(d)) instanceof com.xtri6.grimstorage.block.entity.BusBlockEntity b && b.network() == n) {
                        buses++;
                    }
                }
            }
            double perBusUs = (tickMs[1] - tickMs[0]) * 1000.0 / Math.max(1, buses);
            info("buses on network={} avg tick before={} ms after={} ms (incl. 5 extra force-loaded chunks); measured inside the buses: {} microseconds per bus per tick, {} ms per tick for all of them",
                    buses, String.format("%.3f", tickMs[0]), String.format("%.3f", tickMs[1]), String.format("%.3f", busUs),
                    String.format("%.3f", busUs * buses / 1000.0));
            info("network rebuilds during the window: {}", NetworkManager.epoch() - epochAt[0]);
            return buses == 255 && busUs < 1.0;
        }));
        final int[] mek = {0};
        final long[] mekHeld = {0};
        STEPS.add(new Step("Mekanism: import bus pulls hydrogen out of a real chemical tank (sides set to output)", 80, () -> {
            BlockPos tankPos = at(-1, 2, 0);
            BulkKey h2 = BulkKey.chemical(ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen"));
            BlockCapability<IChemicalHandler, Direction> cap = BlockCapability.createSided(
                    ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);
            if (mek[0] == 0) {
                net.minecraft.world.level.block.Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mekanism", "basic_chemical_tank"));
                level.setBlockAndUpdate(tankPos, block.defaultBlockState());
                block.setPlacedBy(level, tankPos, level.getBlockState(tankPos), FakePlayerFactory.getMinecraft(level), new ItemStack(block.asItem()));
                mek[0] = 1;
                return false;
            }
            if (mek[0] < 10) {
                mek[0]++;
                return false;
            }
            if (mek[0] == 10) {
                mekanismSidesTo(tankPos, "OUTPUT");
                Chemical hydrogen = MekanismAPI.CHEMICAL_REGISTRY.get(ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen"));
                ((mekanism.api.chemical.IMekanismChemicalHandler) level.getBlockEntity(tankPos))
                        .insertChemical(0, new ChemicalStack(hydrogen, 4000), null, Action.EXECUTE);
                before[0] = net().countBulk(h2);
                IChemicalHandler all0 = level.getCapability(cap, tankPos, null);
                mekHeld[0] = all0 == null ? 0 : all0.getChemicalInTank(0).getAmount();
                info("tank down side open={} tank holds={} network chemical stats={}", level.getCapability(cap, tankPos, Direction.DOWN) != null,
                        all0 == null ? -1 : all0.getChemicalInTank(0).getAmount(), java.util.Arrays.toString(net().bulkStats(BulkKind.CHEMICAL)));
                level.setBlockAndUpdate(at(-1, 1, 0), bus(true));
                mek[0] = 11;
                return false;
            }
            long got = net().countBulk(h2) - before[0];
            IChemicalHandler all = level.getCapability(cap, tankPos, null);
            long left = all == null ? -1 : all.getChemicalInTank(0).getAmount();
            info("hydrogen pulled from the Mekanism tank={} left in tank={}", got, left);
            return mekHeld[0] > 0 && got == mekHeld[0] && left == 0;
        }));
        STEPS.add(new Step("craft preview: steps, what it takes from storage, what's missing", 0, () -> {
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
            TerminalMenu menu = new TerminalMenu(990, fp.getInventory(), () -> NetworkManager.get(level, at(5, 0, 0)), p -> true);
            com.xtri6.grimstorage.net.Payloads.Preview ok = menu.preview(key(Items.STICK), 8);
            com.xtri6.grimstorage.net.Payloads.Preview bad = menu.preview(key(Items.DIAMOND_BLOCK), 1);
            boolean stepsOk = ok != null && ok.ok() && ok.steps().size() == 2 && ok.steps().get(0).item().is(Items.STICK)
                    && ok.steps().get(0).by().getString().contains("Assembler") && ok.used().stream().anyMatch(l -> l.item().is(Items.OAK_LOG));
            boolean badOk = bad != null && !bad.ok() && bad.missing().stream().anyMatch(l -> l.item().is(Items.DIAMOND_BLOCK));
            info("preview sticks: ok={} steps={} used={} seconds={}; diamond block missing={}", ok == null ? "-" : ok.ok(),
                    ok == null ? "-" : ok.steps().stream().map(l -> l.amount() + "x" + l.item().getItem() + " by " + l.by().getString()).toList(),
                    ok == null ? "-" : ok.used().stream().map(l -> l.amount() + "x" + l.item().getItem()).toList(), ok == null ? -1 : ok.seconds(),
                    bad == null ? "-" : bad.missing().size());
            return stepsOk && badOk;
        }));
        final long[] variant = new long[2];
        STEPS.add(new Step("any-variant card: an oak-plank chest card crafts with birch planks", 0, () -> {
            AssemblerBlockEntity asm = (AssemblerBlockEntity) level.getBlockEntity(at(2, 0, 0));
            asm.cardHandler().setStackInSlot(2, recipeCard(Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.AIR,
                    Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS));
            NetworkManager.markDirty();
            net().insert(key(Items.BIRCH_PLANKS), 8, false);
            variant[0] = count(Items.OAK_LOG);
            variant[1] = count(Items.CHEST);
            return true;
        }));
        STEPS.add(new Step("any-variant card: order a chest", 5, () -> {
            CraftingPlanner.Plan p = net().request(key(Items.CHEST), 1, "test");
            info("chest plan ok={} steps={} first step inputs={}", p.ok(), p.steps().size(), p.steps().isEmpty() ? "-" : p.steps().get(0).pattern().inputs());
            return p.ok() && p.steps().size() == 1 && p.steps().get(0).pattern().inputs().containsKey(key(Items.BIRCH_PLANKS));
        }));
        STEPS.add(new Step("any-variant card: chest made from the birch, oak logs untouched", 100, () -> {
            info("chests {} -> {} birch planks={} oak logs {} -> {}", variant[1], count(Items.CHEST), count(Items.BIRCH_PLANKS), variant[0], count(Items.OAK_LOG));
            return count(Items.CHEST) == variant[1] + 1 && count(Items.BIRCH_PLANKS) == 0 && count(Items.OAK_LOG) == variant[0] && net().jobs().isEmpty();
        }));
        STEPS.add(new Step("exact card: only oak will do (birch is ignored)", 5, () -> {
            AssemblerBlockEntity asm = (AssemblerBlockEntity) level.getBlockEntity(at(2, 0, 0));
            ItemStack card = asm.cardHandler().getStackInSlot(2).copy();
            card.set(Registration.RECIPE_EXACT.get(), true);
            asm.cardHandler().setStackInSlot(2, card);
            NetworkManager.markDirty();
            if (net().patternsFor(key(Items.CHEST)).size() != 1) {
                return false;
            }
            net().insert(key(Items.BIRCH_PLANKS), 8, false);
            CraftingPlanner.Plan p = net().plan(key(Items.CHEST), 1);
            boolean usesOak = p.steps().stream().anyMatch(st -> st.pattern().inputs().containsKey(key(Items.OAK_PLANKS)));
            boolean usesBirch = p.steps().stream().anyMatch(st -> st.pattern().inputs().containsKey(key(Items.BIRCH_PLANKS)));
            net().extract(key(Items.BIRCH_PLANKS), 8, false);
            asm.cardHandler().setStackInSlot(2, ItemStack.EMPTY);
            NetworkManager.markDirty();
            info("exact plan ok={} uses oak={} uses birch={}", p.ok(), usesOak, usesBirch);
            return p.ok() && usesOak && !usesBirch;
        }));
        final long[] camp = new long[1];
        STEPS.add(new Step("every ingredient takes its variants: campfires split across coal and charcoal", 5, () -> {
            AssemblerBlockEntity asm = (AssemblerBlockEntity) level.getBlockEntity(at(2, 0, 0));
            if (asm.cardHandler().getStackInSlot(3).isEmpty()) {
                // a card written with oak logs and coal: logs are the most flexible ingredient, coal is the second
                asm.cardHandler().setStackInSlot(3, recipeCard(Items.AIR, Items.STICK, Items.AIR, Items.STICK, Items.COAL, Items.STICK,
                        Items.OAK_LOG, Items.OAK_LOG, Items.OAK_LOG));
                NetworkManager.markDirty();
                StorageNetwork n = net();
                n.extract(key(Items.COAL), n.count(key(Items.COAL)), false);
                n.extract(key(Items.CHARCOAL), n.count(key(Items.CHARCOAL)), false);
                n.insert(key(Items.COAL), 1, false);
                n.insert(key(Items.CHARCOAL), 1, false);
                n.insert(key(Items.STICK), 6, false);
                camp[0] = count(Items.CAMPFIRE);
                return false;
            }
            CraftingPlanner.Plan p = net().request(key(Items.CAMPFIRE), 2, "test");
            List<String> steps = p.steps().stream().map(st -> st.pattern().inputs() + " x" + st.times()).toList();
            boolean coal = p.steps().stream().anyMatch(st -> st.pattern().inputs().containsKey(key(Items.COAL)));
            boolean charcoal = p.steps().stream().anyMatch(st -> st.pattern().inputs().containsKey(key(Items.CHARCOAL)));
            info("campfire plan ok={} steps={} missing={}", p.ok(), steps, p.missing());
            return p.ok() && coal && charcoal;
        }));
        STEPS.add(new Step("every ingredient takes its variants: both campfires made, one coal and one charcoal used", 200, () -> {
            info("campfires {} -> {} coal={} charcoal={} jobs={}", camp[0], count(Items.CAMPFIRE), count(Items.COAL), count(Items.CHARCOAL), net().jobs().size());
            boolean done = count(Items.CAMPFIRE) == camp[0] + 2 && count(Items.COAL) == 0 && count(Items.CHARCOAL) == 0 && net().jobs().isEmpty();
            if (done) {
                ((AssemblerBlockEntity) level.getBlockEntity(at(2, 0, 0))).cardHandler().setStackInSlot(3, ItemStack.EMPTY);
                NetworkManager.markDirty();
            }
            return done;
        }));
        STEPS.add(new Step("JEI recipe button writes a Recipe Card without holding any of the items", 0, () -> {
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
            fp.getInventory().clearContent();
            fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.RECIPE_CARD.get()));
            CardMenus.Recipe menu = new CardMenus.Recipe(993, fp.getInventory(), InteractionHand.MAIN_HAND);
            // what JEI's + on the torch recipe sends: coal over a stick, in grid order
            menu.setGhosts(true, List.of(1, 4, 0), List.of(new ItemStack(Items.COAL, 5), new ItemStack(Items.STICK), ItemStack.EMPTY));
            CardData.Recipe data = fp.getMainHandItem().get(Registration.RECIPE_CARD_DATA.get());
            // a ghost update can never touch a real slot
            int realSlot = menu.slots.size() - 1;
            menu.setGhosts(false, List.of(realSlot), List.of(new ItemStack(Items.DIAMOND, 64)));
            boolean noDiamonds = !fp.getInventory().contains(new ItemStack(Items.DIAMOND));
            info("card result={} grid coal count={} real slot untouched={}", data == null ? "none" : data.result(),
                    data == null ? -1 : data.grid().get(1).getCount(), noDiamonds);
            fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return data != null && data.result().is(Items.TORCH) && data.grid().get(1).getCount() == 1 && noDiamonds;
        }));
        STEPS.add(new Step("JEI recipe button writes a Processing Card from any recipe", 0, () -> {
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
            fp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Registration.PROCESSING_CARD.get()));
            CardMenus.Processing menu = new CardMenus.Processing(992, fp.getInventory(), InteractionHand.MAIN_HAND);
            menu.setGhosts(true, List.of(0, 1, 9), List.of(new ItemStack(Items.RAW_GOLD, 3), new ItemStack(Items.COAL), new ItemStack(Items.GOLD_INGOT, 4)));
            CardData.Processing data = fp.getMainHandItem().get(Registration.PROCESSING_CARD_DATA.get());
            fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            info("processing card in={} out={}", data == null ? "none" : data.inputs(), data == null ? "none" : data.outputs());
            return data != null && data.inputs().get(0).is(Items.RAW_GOLD) && data.inputs().get(0).getCount() == 3
                    && data.outputs().get(0).is(Items.GOLD_INGOT) && data.outputs().get(0).getCount() == 4;
        }));
        STEPS.add(new Step("JEI + on the terminal fills the crafting grid from the network, then a card is written from it", 0, () -> {
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
            fp.getInventory().clearContent();
            StorageNetwork n = net();
            n.insert(key(Items.OAK_PLANKS), 8, false);
            long planksBefore = count(Items.OAK_PLANKS);
            TerminalMenu menu = new TerminalMenu(991, fp.getInventory(), () -> NetworkManager.get(level, at(5, 0, 0)), p -> true);
            List<List<ItemStack>> opts = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                opts.add(i == 0 || i == 3 ? List.of(new ItemStack(Items.BIRCH_PLANKS), new ItemStack(Items.OAK_PLANKS)) : List.of());
            }
            menu.fillGrid(fp, opts);
            ItemStack result = menu.getSlot(menu.resultIndex).getItem().copy();
            long during = count(Items.OAK_PLANKS);
            fp.getInventory().add(new ItemStack(Registration.RECIPE_CARD.get(), 2));
            boolean wrote = menu.writeCard(fp);
            ItemStack written = ItemStack.EMPTY;
            for (ItemStack st : fp.getInventory().items) {
                if (st.has(Registration.RECIPE_CARD_DATA.get())) {
                    written = st;
                }
            }
            CardData.Recipe data = written.get(Registration.RECIPE_CARD_DATA.get());
            boolean gridKept = menu.getSlot(menu.gridStart).getItem().is(Items.OAK_PLANKS);
            menu.clearGrid(fp);
            info("grid result={} planks {} -> {} -> {} wrote={} card={} grid kept={}", result, planksBefore, during, count(Items.OAK_PLANKS), wrote,
                    data == null ? "none" : data.result(), gridKept);
            fp.getInventory().clearContent();
            return result.is(Items.STICK) && during == planksBefore - 2 && count(Items.OAK_PLANKS) == planksBefore && wrote && data != null
                    && data.result().is(Items.STICK) && gridKept;
        }));
        STEPS.add(new Step("void filter destroys overflow only", 0, () -> {
            VoidFilterBlockEntity v = (VoidFilterBlockEntity) level.getBlockEntity(at(0, -1, 0));
            v.filters().setItem(0, new ItemStack(Items.DIRT));
            StorageNetwork n = net();
            long gravel = n.insert(key(Items.GRAVEL), 1_000_000, false);
            long dirt = n.insert(key(Items.DIRT), 50_000, false);
            long stored = count(Items.DIRT);
            info("gravel accepted={} (fills cells), dirt accepted={} dirt stored={}", gravel, dirt, stored);
            return gravel < 1_000_000 && dirt == 50_000 && stored == 0;
        }));
        addMachineBusSteps();
        STEPS.add(new Step("save totals for the persistence run", 0, () -> {
            info("cable bus at save: {} be={}", level.getBlockState(at(1, 1, 0)), level.getBlockEntity(at(1, 1, 0)));
            long total = net().contents().values().stream().mapToLong(Long::longValue).sum();
            for (BulkKind kind : BulkKind.values()) {
                total += net().bulkStats(kind)[0];
            }
            try {
                Files.writeString(TOTAL_FILE, Long.toString(total));
            } catch (Exception ex) {
                return false;
            }
            info("total items stored={}", total);
            return total > 100_000;
        }));
    }

    // ------------------------------------------------------------------ machine bus (its own small network)

    private static final BlockPos MB = new BlockPos(24, 230, 0);

    private static StorageNetwork mbNet() {
        return NetworkManager.get(level, MB);
    }

    private static com.xtri6.grimstorage.block.entity.MachineBusBlockEntity machineBus(Direction d) {
        return (com.xtri6.grimstorage.block.entity.MachineBusBlockEntity) level.getBlockEntity(MB.relative(d));
    }

    private static net.minecraft.world.level.block.entity.FurnaceBlockEntity mbFurnace(Direction d) {
        return (net.minecraft.world.level.block.entity.FurnaceBlockEntity) level.getBlockEntity(MB.relative(d, 2));
    }

    private static void addMachineBusSteps() {
        STEPS.add(new Step("machine bus: build two furnaces and a chest on their own network", 0, () -> {
            level.setChunkForced(MB.getX() >> 4, MB.getZ() >> 4, true);
            level.setBlockAndUpdate(MB, Registration.CONTROLLER.get().defaultBlockState());
            level.setBlockAndUpdate(MB.west(), Registration.DRIVE.get().defaultBlockState());
            ((DriveBlockEntity) level.getBlockEntity(MB.west())).cells().setStackInSlot(0, new ItemStack(Registration.CELLS.get(CellTier.GOLD).get()));
            level.setBlockAndUpdate(MB.east(2), Blocks.FURNACE.defaultBlockState());
            level.setBlockAndUpdate(MB.south(2), Blocks.FURNACE.defaultBlockState());
            level.setBlockAndUpdate(MB.north(2), Blocks.CHEST.defaultBlockState());
            for (Direction d : new Direction[] {Direction.EAST, Direction.SOUTH, Direction.NORTH}) {
                level.setBlockAndUpdate(MB.relative(d), Registration.MACHINE_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, d));
            }
            ItemStack card = new ItemStack(Registration.PROCESSING_CARD.get());
            card.set(Registration.PROCESSING_CARD_DATA.get(), new CardData.Processing(
                    List.of(new ItemStack(Items.DIRT)), List.of(new ItemStack(Items.DIAMOND))));
            machineBus(Direction.NORTH).cardHandler().setStackInSlot(0, card);
            NetworkManager.markDirty();
            return true;
        }));
        STEPS.add(new Step("machine bus learns every furnace recipe with no cards", 60, () -> {
            StorageNetwork n = mbNet();
            if (n == null) {
                return false;
            }
            n.insert(key(Items.RAW_IRON), 4, false);
            CraftingPlanner.Plan p = n.plan(key(Items.IRON_INGOT), 4);
            int learned = machineBus(Direction.EAST).recipeCount();
            info("east bus recipes={} chest bus recipes={} plan ok={} missing={} explicit has iron={}", learned,
                    machineBus(Direction.NORTH).recipeCount(), p.ok(), p.missing(), n.explicitCraftables().contains(key(Items.IRON_INGOT)));
            n.extract(key(Items.RAW_IRON), 4, false);
            return learned > 20 && machineBus(Direction.NORTH).recipeCount() == 1 && p.ok()
                    && !n.explicitCraftables().contains(key(Items.IRON_INGOT));
        }));
        STEPS.add(new Step("planner picks the option it can actually make (charcoal from birch, not oak)", 0, () -> {
            StorageNetwork n = mbNet();
            n.insert(key(Items.BIRCH_LOG), 2, false);
            CraftingPlanner.Plan p = n.plan(key(Items.CHARCOAL), 2);
            info("charcoal plan ok={} missing={}", p.ok(), p.missing());
            n.extract(key(Items.BIRCH_LOG), 2, false);
            return p.ok();
        }));
        STEPS.add(new Step("order 4 iron ingots: both furnaces get work", 40, () -> {
            StorageNetwork n = mbNet();
            if (n.jobs().isEmpty()) {
                n.insert(key(Items.RAW_IRON), 4, false);
                n.insert(key(Items.COAL), 4, false);
                CraftingPlanner.Plan p = n.request(key(Items.IRON_INGOT), 4, "test");
                info("smelt plan ok={} missing={}", p.ok(), p.missing());
            }
            ItemStack a = mbFurnace(Direction.EAST).getItem(0), b = mbFurnace(Direction.SOUTH).getItem(0);
            info("furnace inputs east={} south={}", a, b);
            return a.is(Items.RAW_IRON) && b.is(Items.RAW_IRON);
        }));
        STEPS.add(new Step("both furnaces were fuelled from the network", 40, () -> {
            boolean lit = mbFurnace(Direction.EAST).getBlockState().getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT)
                    && mbFurnace(Direction.SOUTH).getBlockState().getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT);
            long coal = mbNet().count(key(Items.COAL));
            info("lit={} coal left in network={}", lit, coal);
            return lit && coal < 4;
        }));
        STEPS.add(new Step("terminal crafting tab sees the job, its furnace step and both furnaces", 0, () -> {
            List<com.xtri6.grimstorage.net.Payloads.JobView> views = TerminalMenu.jobViews(mbNet());
            if (views.size() != 1) {
                info("job views={}", views.size());
                return false;
            }
            com.xtri6.grimstorage.net.Payloads.JobView v = views.get(0);
            com.xtri6.grimstorage.net.Payloads.StepView st = v.steps().get(0);
            info("job {}x {} status={} progress={} step machine={} total={} finished={} inMachines={}", v.info().amount(),
                    v.info().icon().getItem(), v.info().status().getString(), v.info().progress(), st.machine().getString(), st.total(),
                    st.finished(), st.inMachines());
            return v.info().icon().is(Items.IRON_INGOT) && v.steps().size() == 1 && st.processing() && st.total() == 4
                    && st.inMachines() == 4 && st.machine().getString().equals("Furnace x2") && v.info().progress() > 0.4f;
        }));
        STEPS.add(new Step("terminal can move its inventory rows (taller sizes) without breaking slots", 0, () -> {
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
            TerminalMenu menu = new TerminalMenu(994, fp.getInventory(), null, p -> true);
            int first = 0;
            net.minecraft.world.inventory.Slot before = menu.getSlot(first);
            menu.movePlayerInventory(140 + 6 * 18);
            net.minecraft.world.inventory.Slot after = menu.getSlot(first);
            net.minecraft.world.inventory.Slot hotbar = menu.getSlot(27);
            info("slot0 y {} -> {} index {} hotbar y={}", before.y, after.y, after.index, hotbar.y);
            return after.y == before.y + 108 && after.index == before.index && after.getContainerSlot() == before.getContainerSlot()
                    && hotbar.y == 140 + 108 + 58 && menu.getSlot(menu.gridStart).y == TerminalMenu.GRID_Y;
        }));
        STEPS.add(new Step("iron ingots came back from the result slots only", 900, () -> {
            StorageNetwork n = mbNet();
            long iron = n.count(key(Items.IRON_INGOT)), raw = n.count(key(Items.RAW_IRON));
            info("iron={} raw={} jobs={}", iron, raw, n.jobs().isEmpty() ? "none" : n.jobs().get(0).status().getString());
            return iron == 4 && raw == 0 && n.jobs().isEmpty()
                    && mbFurnace(Direction.EAST).getItem(2).isEmpty() && mbFurnace(Direction.SOUTH).getItem(2).isEmpty();
        }));
        STEPS.add(new Step("machine bus with a card only takes that card's outputs", 20, () -> {
            IItemHandler chest = level.getCapability(Capabilities.ItemHandler.BLOCK, MB.north(2), null);
            if (chest.getStackInSlot(0).isEmpty() && chest.getStackInSlot(1).isEmpty()) {
                chest.insertItem(0, new ItemStack(Items.DIAMOND, 3), false);
                chest.insertItem(1, new ItemStack(Items.DIRT, 5), false);
            }
            long diamonds = mbNet().count(key(Items.DIAMOND));
            info("diamonds pulled={} dirt left in chest={}", diamonds, chest.getStackInSlot(1).getCount());
            return diamonds == 3 && chest.getStackInSlot(1).getCount() == 5;
        }));
        final Object[] mek = new Object[3];
        STEPS.add(new Step("machine bus learns Mekanism machines (enrichment chamber, sawmill) by itself", 60, () -> {
            if (mek[0] == null) {
                mek[0] = true;
                ((DriveBlockEntity) level.getBlockEntity(MB.west())).cells().setStackInSlot(1,
                        new ItemStack(Registration.BULK_CELLS.get(BulkKind.ENERGY).get(CellTier.COPPER).get()));
                level.setBlockAndUpdate(MB.above(2), BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mekanism", "enrichment_chamber")).defaultBlockState());
                level.setBlockAndUpdate(MB.below(2), BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mekanism", "precision_sawmill")).defaultBlockState());
                level.setBlockAndUpdate(MB.above(), Registration.MACHINE_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, Direction.UP));
                level.setBlockAndUpdate(MB.below(), Registration.MACHINE_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, Direction.DOWN));
                NetworkManager.markDirty();
                return false;
            }
            StorageNetwork n = mbNet();
            n.insertBulk(BulkKey.flat(BulkKind.ENERGY), 2_000_000, false);
            int up = machineBus(Direction.UP).recipeCount(), down = machineBus(Direction.DOWN).recipeCount();
            info("enrichment chamber recipes={} sawmill recipes={} energy={}", up, down, n.bulkStats(BulkKind.ENERGY)[0]);
            return up > 5 && down > 5 && n.bulkStats(BulkKind.ENERGY)[0] > 0;
        }));
        STEPS.add(new Step("order something only the enrichment chamber makes", 0, () -> {
            StorageNetwork n = mbNet();
            for (com.xtri6.grimstorage.storage.Pattern p : machineBus(Direction.UP).learnedPatterns()) {
                boolean vanilla = p.inputs().keySet().stream().allMatch(k -> BuiltInRegistries.ITEM.getKey(k.item()).getNamespace().equals("minecraft"));
                if (!vanilla) {
                    continue;
                }
                p.inputs().forEach((k, c) -> n.insert(k, c * 2, false));
                long before = n.count(p.primary());
                CraftingPlanner.Plan plan = n.request(p.primary(), p.outputCount(p.primary()), "test");
                info("enrich {} -> {} x{}: plan ok={} missing={}", p.inputs(), p.primary().item(), p.outputCount(p.primary()), plan.ok(), plan.missing());
                mek[1] = p;
                mek[2] = before;
                return plan.ok();
            }
            return false;
        }));
        STEPS.add(new Step("enrichment chamber was powered from the network and its result came back", 600, () -> {
            com.xtri6.grimstorage.storage.Pattern p = (com.xtri6.grimstorage.storage.Pattern) mek[1];
            StorageNetwork n = mbNet();
            long now = n.count(p.primary());
            info("enriched {}: {} -> {} jobs={}", p.primary().item(), mek[2], now, n.jobs().isEmpty() ? "none" : n.jobs().get(0).status().getString());
            return now >= (Long) mek[2] + p.outputCount(p.primary()) && n.jobs().isEmpty();
        }));
        STEPS.add(new Step("order planks from the precision sawmill (4 logs)", 0, () -> {
            StorageNetwork n = mbNet();
            n.insert(key(Items.OAK_LOG), 4, false);
            mek[2] = n.count(key(Items.OAK_PLANKS));
            CraftingPlanner.Plan plan = n.request(key(Items.OAK_PLANKS), 24, "test");
            info("sawmill plan ok={} missing={} steps={}", plan.ok(), plan.missing(), plan.steps().size());
            return plan.ok();
        }));
        STEPS.add(new Step("planks came back and the sawmill's extra output (sawdust) did not clog it", 900, () -> {
            StorageNetwork n = mbNet();
            IItemHandler saw = level.getCapability(Capabilities.ItemHandler.BLOCK, MB.below(2), null);
            int left = 0;
            for (int i = 0; saw != null && i < saw.getSlots(); i++) {
                left += saw.getStackInSlot(i).getCount();
            }
            long planks = n.count(key(Items.OAK_PLANKS)) - (Long) mek[2];
            long sawdust = n.count(ItemKey.of(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("mekanism", "sawdust")))));
            info("planks gained={} sawdust in network={} items left in sawmill={} jobs={}", planks, sawdust, left,
                    n.jobs().isEmpty() ? "none" : n.jobs().get(0).status().getString());
            return planks >= 24 && left == 0 && n.jobs().isEmpty();
        }));
        STEPS.add(new Step("recipe ingredients: a tag with more than 32 items still offers every one", 0, () -> {
            java.util.List<ItemStack> many = new java.util.ArrayList<>();
            // 60 other items first, then wheat seeds last, the way a big pack's c:seeds tag can list them
            for (net.minecraft.world.item.Item it : BuiltInRegistries.ITEM) {
                if (many.size() >= 60) {
                    break;
                }
                if (it != Items.WHEAT_SEEDS && it != Items.AIR) {
                    many.add(new ItemStack(it));
                }
            }
            many.add(new ItemStack(Items.WHEAT_SEEDS));
            com.xtri6.grimstorage.storage.RecipeReader.Input in = com.xtri6.grimstorage.storage.RecipeReader.input(many);
            boolean seeds = in != null && in.options().contains(com.xtri6.grimstorage.storage.ItemKey.of(new ItemStack(Items.WHEAT_SEEDS)));
            int size = in == null ? -1 : in.options().size();
            info("ingredient options {} (wheat seeds offered: {})", size, seeds);
            return seeds && size == 61;
        }));
        STEPS.add(new Step("terminal search: contains, typos and short words", 0, () -> {
            boolean typo = com.xtri6.grimstorage.client.SearchMatch.matches("mending", "medning");
            boolean word = com.xtri6.grimstorage.client.SearchMatch.matches("diamond pickaxe", "pickaxe");
            boolean shortWord = !com.xtri6.grimstorage.client.SearchMatch.matches("stone", "stin");
            boolean far = !com.xtri6.grimstorage.client.SearchMatch.matches("mending", "zzzzzz");
            boolean two = com.xtri6.grimstorage.client.SearchMatch.matches("sharpness", "sharpnes");
            info("typo={} word={} shortWordNoFuzzy={} far={} two={}", typo, word, shortWord, far, two);
            return typo && word && shortWord && far && two;
        }));
        STEPS.add(new Step("machine table: datapack entries, name matching, and chemical recipes left out", 0, () -> {
            com.xtri6.grimstorage.storage.MachineRecipes mr = com.xtri6.grimstorage.storage.MachineRecipes.INSTANCE;
            java.util.function.Function<String, List<String>> types = id -> mr.typesFor(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState())
                    .stream().map(t -> BuiltInRegistries.RECIPE_TYPE.getKey(t).toString()).toList();
            List<String> crusher = types.apply("mekanism:crusher"), factory = types.apply("mekanism:elite_crushing_factory"),
                    compressor = types.apply("mekanism:osmium_compressor"), chest = types.apply("minecraft:chest");
            boolean stems = com.xtri6.grimstorage.storage.MachineRecipes.stem("crusher").equals(com.xtri6.grimstorage.storage.MachineRecipes.stem("crushing"))
                    && com.xtri6.grimstorage.storage.MachineRecipes.stem("sag_mill").equals(com.xtri6.grimstorage.storage.MachineRecipes.stem("sag_milling"))
                    && com.xtri6.grimstorage.storage.MachineRecipes.stem("machine_pulverizer").equals(com.xtri6.grimstorage.storage.MachineRecipes.stem("pulverizer"));
            // the osmium compressor is found by name, and its recipes (an item plus a chemical) are now learned too
            long learnable = 0;
            for (var t : mr.typesFor(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:osmium_compressor")).defaultBlockState())) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                List<net.minecraft.world.item.crafting.RecipeHolder<?>> rs = (List) level.getRecipeManager().getAllRecipesFor((net.minecraft.world.item.crafting.RecipeType) t);
                learnable += rs.stream().filter(h -> com.xtri6.grimstorage.storage.RecipeReader.read(h.value(), level.registryAccess()) != null).count();
            }
            info("crusher={} elite factory={} compressor={} (learnable {}) chest={} stems ok={}", crusher, factory, compressor, learnable, chest, stems);
            return crusher.contains("mekanism:crushing") && factory.contains("mekanism:crushing") && compressor.contains("mekanism:compressing")
                    && learnable > 0 && chest.isEmpty() && stems;
        }));
        STEPS.add(new Step("recipe reader: a machine recipe that also takes a fluid", 0, () -> {
            com.xtri6.grimstorage.storage.RecipeReader.Read r = com.xtri6.grimstorage.storage.RecipeReader.read(new FluidTestRecipe(), level.registryAccess());
            info("fluid recipe read: {}", r == null ? "null" : r.inputs() + " + " + r.bulk() + " -> " + r.output());
            return r != null && r.output().item() == Items.GOLD_INGOT && r.bulk().get(BulkKey.fluid(new FluidStack(Fluids.WATER, 1))) == 250L;
        }));
        final Object[] purify = new Object[2];
        BlockPos pc = MB.west().above(3);
        STEPS.add(new Step("Purification Chamber: learns its oxygen recipes", 60, () -> {
            if (purify[0] == null) {
                purify[0] = true;
                ((DriveBlockEntity) level.getBlockEntity(MB.west())).cells().setStackInSlot(2,
                        new ItemStack(Registration.BULK_CELLS.get(BulkKind.CHEMICAL).get(CellTier.COPPER).get()));
                level.setBlockAndUpdate(MB.west().above(), Registration.CABLE.get().defaultBlockState());
                level.setBlockAndUpdate(pc, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mekanism", "purification_chamber")).defaultBlockState());
                level.setBlockAndUpdate(MB.west().above(2), Registration.MACHINE_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, Direction.UP));
                NetworkManager.markDirty();
                return false;
            }
            var bus = (com.xtri6.grimstorage.block.entity.MachineBusBlockEntity) level.getBlockEntity(MB.west().above(2));
            long withChem = bus.learnedPatterns().stream().filter(p -> !p.bulkInputs().isEmpty()).count();
            info("purification chamber recipes={} needing a chemical={}", bus.recipeCount(), withChem);
            return withChem > 3;
        }));
        STEPS.add(new Step("Purification Chamber: no oxygen (or flint) in storage shows as missing", 0, () -> {
            CraftingPlanner.Plan p = mbNet().plan(ItemKey.of(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("mekanism", "clump_iron")))), 3);
            info("clump plan without oxygen ok={} missing items={} missing chemicals={}", p.ok(), p.missing(), p.missingBulk());
            // without oxygen it needs oxygen, or the flint Mekanism turns into oxygen (or what makes that flint)
            return !p.ok() && (!p.missingBulk().isEmpty() || !p.missing().isEmpty());
        }));
        STEPS.add(new Step("Purification Chamber: order iron clumps, the bus feeds it oxygen", 0, () -> {
            StorageNetwork n = mbNet();
            n.insertBulk(BulkKey.chemical(ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen")), 50_000, false);
            n.insertBulk(BulkKey.flat(BulkKind.ENERGY), 2_000_000, false);
            n.insert(key(Items.IRON_ORE), 1, false);
            ItemKey clump = ItemKey.of(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("mekanism", "clump_iron"))));
            purify[1] = n.count(clump);
            CraftingPlanner.Plan p = n.request(clump, 3, "test");
            info("clump plan ok={} missing={} bulk missing={} steps={}", p.ok(), p.missing(), p.missingBulk(),
                    p.steps().stream().map(st -> st.pattern().inputs() + "+" + st.pattern().bulkInputs()).toList());
            return p.ok();
        }));
        STEPS.add(new Step("Purification Chamber: clumps came back, oxygen was used from storage", 900, () -> {
            StorageNetwork n = mbNet();
            ItemKey clump = ItemKey.of(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("mekanism", "clump_iron"))));
            long oxygen = n.countBulk(BulkKey.chemical(ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen")));
            info("clumps {} -> {} oxygen left={} jobs={}", purify[1], n.count(clump), oxygen, n.jobs().isEmpty() ? "none" : n.jobs().get(0).status().getString());
            return n.count(clump) >= (Long) purify[1] + 3 && oxygen < 50_000 && n.jobs().isEmpty();
        }));
        BlockPos chest = MB.west().south(2);
        STEPS.add(new Step("Chest Link: a chest becomes part of storage", 20, () -> {
            if (level.getBlockState(chest).isAir()) {
                level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
                IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, chest, null);
                h.insertItem(0, new ItemStack(Items.EMERALD, 20), false);
                level.setBlockAndUpdate(MB.west().south(), Registration.CHEST_LINK.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, Direction.SOUTH));
                NetworkManager.markDirty();
                return false;
            }
            long emeralds = mbNet().count(key(Items.EMERALD));
            info("emeralds seen through the chest link={}", emeralds);
            return emeralds == 20;
        }));
        STEPS.add(new Step("Chest Link: take from the chest, items added later by hand show up too", 30, () -> {
            StorageNetwork n = mbNet();
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, chest, null);
            if (h.getStackInSlot(5).isEmpty()) {
                long took = n.extract(key(Items.EMERALD), 5, false);
                h.insertItem(5, new ItemStack(Items.LAPIS_LAZULI, 7), false);
                info("took {} emeralds", took);
            }
            info("emeralds={} lapis={}", n.count(key(Items.EMERALD)), n.count(key(Items.LAPIS_LAZULI)));
            return n.count(key(Items.EMERALD)) == 15 && n.count(key(Items.LAPIS_LAZULI)) == 7;
        }));
        STEPS.add(new Step("Chest Link: 'only put in' fills the chest first and stops the network taking from it", 0, () -> {
            var link = (com.xtri6.grimstorage.block.entity.ChestLinkBlockEntity) level.getBlockEntity(MB.west().south());
            link.toggleFillFirst();
            long put = mbNet().insert(key(Items.EMERALD), 10, false);
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, chest, null);
            int inChest = 0;
            for (int i = 0; i < h.getSlots(); i++) {
                if (h.getStackInSlot(i).is(Items.EMERALD)) {
                    inChest += h.getStackInSlot(i).getCount();
                }
            }
            link.cycleMode();
            long takeAfter = mbNet().extract(key(Items.LAPIS_LAZULI), 7, true);
            link.cycleMode();
            link.cycleMode();
            link.toggleFillFirst();
            info("put={} emeralds in chest={} could take lapis in insert-only mode={}", put, inChest, takeAfter);
            return put == 10 && inChest == 25 && takeAfter == 0;
        }));
        BlockPos emitter = MB.west().north();
        STEPS.add(new Step("Redstone Emitter: signal at 10 emeralds, none below", 30, () -> {
            if (!(level.getBlockEntity(emitter) instanceof com.xtri6.grimstorage.block.entity.RedstoneEmitterBlockEntity)) {
                level.setBlockAndUpdate(emitter, Registration.REDSTONE_EMITTER.get().defaultBlockState());
                var e = (com.xtri6.grimstorage.block.entity.RedstoneEmitterBlockEntity) level.getBlockEntity(emitter);
                e.filter().setItem(0, new ItemStack(Items.EMERALD));
                e.setAmount(10);
                NetworkManager.markDirty();
                return false;
            }
            int signal = level.getBestNeighborSignal(emitter.north());
            info("emeralds={} signal={}", mbNet().count(key(Items.EMERALD)), signal);
            return signal == 15;
        }));
        STEPS.add(new Step("Redstone Emitter: signal drops when the emeralds go", 30, () -> {
            StorageNetwork n = mbNet();
            if (n.count(key(Items.EMERALD)) >= 10) {
                n.extract(key(Items.EMERALD), n.count(key(Items.EMERALD)) - 3, false);
            }
            int signal = level.getBestNeighborSignal(emitter.north());
            info("emeralds={} signal={}", n.count(key(Items.EMERALD)), signal);
            return signal == 0;
        }));
        STEPS.add(new Step("Redstone Emitter: craft while powered (iron ingots from raw iron)", 0, () -> {
            var e = (com.xtri6.grimstorage.block.entity.RedstoneEmitterBlockEntity) level.getBlockEntity(emitter);
            e.filter().setItem(0, new ItemStack(Items.IRON_INGOT));
            e.setAmount((int) mbNet().count(key(Items.IRON_INGOT)) + 2);
            e.cycleMode();
            e.cycleMode();
            mbNet().insert(key(Items.RAW_IRON), 2, false);
            mbNet().insert(key(Items.COAL), 2, false);
            level.setBlockAndUpdate(emitter.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            return e.mode() == com.xtri6.grimstorage.block.entity.RedstoneEmitterBlockEntity.CRAFT;
        }));
        STEPS.add(new Step("Redstone Emitter: the iron got crafted", 700, () -> {
            var e = (com.xtri6.grimstorage.block.entity.RedstoneEmitterBlockEntity) level.getBlockEntity(emitter);
            long iron = mbNet().count(key(Items.IRON_INGOT));
            info("iron={} target={} jobs={}", iron, e.amount(), mbNet().jobs().size());
            boolean done = iron >= e.amount() && mbNet().jobs().isEmpty();
            if (done) {
                level.removeBlock(emitter.above(), false);
            }
            return done;
        }));
        STEPS.add(new Step("terminal: recently added items carry when they came in", 3, () -> {
            StorageNetwork n = mbNet();
            if (n.lastAdded(key(Items.AMETHYST_SHARD)) == 0) {
                n.insert(key(Items.AMETHYST_SHARD), 1, false);
                return false;
            }
            n.insert(key(Items.PRISMARINE_CRYSTALS), 1, false);
            boolean order = n.lastAdded(key(Items.PRISMARINE_CRYSTALS)) > n.lastAdded(key(Items.AMETHYST_SHARD));
            boolean never = n.lastAdded(key(Items.NETHER_STAR)) == 0;
            info("amethyst={} prismarine={} order ok={}", n.lastAdded(key(Items.AMETHYST_SHARD)), n.lastAdded(key(Items.PRISMARINE_CRYSTALS)), order);
            return order && never;
        }));
        addModMachineSteps();
        addScaleAndConfigSteps();
        addChainSteps();
        STEPS.add(new Step("network access: owner, trusted players, open mode", 0, () -> {
            com.xtri6.grimstorage.block.entity.ControllerBlockEntity c = (com.xtri6.grimstorage.block.entity.ControllerBlockEntity) level.getBlockEntity(MB);
            FakePlayer alice = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("alice".getBytes()), "Alice"));
            FakePlayer bob = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("bob".getBytes()), "Bob"));
            boolean unownedOpen = c.allows(bob);
            c.setOwner(alice);
            boolean ownerIn = c.allows(alice), bobOut = !c.allows(bob) && !com.xtri6.grimstorage.storage.NetworkAccess.check(bob, level, MB.east());
            c.trust(bob.getUUID(), "Bob");
            boolean bobTrusted = c.allows(bob) && !c.canManage(bob);
            c.untrust("bob");
            boolean bobGone = !c.allows(bob);
            c.toggleOpen();
            boolean open = c.allows(bob);
            c.toggleOpen();
            c.trust(bob.getUUID(), "Bob");
            info("unowned open={} owner in={} stranger out={} trusted={} untrusted={} open mode={}", unownedOpen, ownerIn, bobOut, bobTrusted, bobGone, open);
            return unownedOpen && ownerIn && bobOut && bobTrusted && bobGone && open;
        }));
        STEPS.add(new Step("machine bus fuel switch saves and stops refuelling", 0, () -> {
            com.xtri6.grimstorage.block.entity.MachineBusBlockEntity bus = machineBus(Direction.EAST);
            bus.toggleFuel();
            net.minecraft.nbt.CompoundTag tag = bus.saveWithoutMetadata(level.registryAccess());
            boolean saved = !tag.getBoolean("fuel") && tag.getBoolean("learn");
            bus.toggleFuel();
            return saved && bus.fuels();
        }));
    }

    // ------------------------------------------------------------------ other mods' machines (only if they're in the dev game)

    private static final BlockPos MOD = new BlockPos(24, 230, 24);
    private static final String[][] MOD_MACHINES = {
            {"create", "millstone"},
            {"enderio", "sag_mill"},
            {"enderio", "alloy_smelter"},
            {"ars_nouveau", "imbuement_chamber"},
            {"immersiveengineering", "crusher"}};
    private static final List<String> MOD_REPORT = new ArrayList<>();

    private static void addModMachineSteps() {
        final Object[][] state = new Object[MOD_MACHINES.length][];
        STEPS.add(new Step("other mods' machines: build a test row", 0, () -> {
            level.setChunkForced(MOD.getX() >> 4, MOD.getZ() >> 4, true);
            level.setBlockAndUpdate(MOD, Registration.CONTROLLER.get().defaultBlockState());
            level.setBlockAndUpdate(MOD.west(), Registration.DRIVE.get().defaultBlockState());
            DriveBlockEntity d = (DriveBlockEntity) level.getBlockEntity(MOD.west());
            d.cells().setStackInSlot(0, new ItemStack(Registration.CELLS.get(CellTier.GOLD).get()));
            d.cells().setStackInSlot(1, new ItemStack(Registration.BULK_CELLS.get(BulkKind.ENERGY).get(CellTier.GOLD).get()));
            for (int i = 0; i < MOD_MACHINES.length; i++) {
                BlockPos c = MOD.east(1 + i * 2);
                level.setBlockAndUpdate(c, Registration.CABLE.get().defaultBlockState());
                if (i > 0) {
                    level.setBlockAndUpdate(c.west(), Registration.CABLE.get().defaultBlockState());
                }
                var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(MOD_MACHINES[i][0], MOD_MACHINES[i][1]));
                if (block.isEmpty()) {
                    continue;
                }
                BlockPos machine = c.above().north();
                level.setBlockAndUpdate(machine, block.get().defaultBlockState());
                level.setBlockAndUpdate(c.above(), Registration.MACHINE_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, Direction.NORTH));
                if (MOD_MACHINES[i][0].equals("enderio")) {
                    // Ender IO machines run on a capacitor, which the player puts in
                    BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("enderio", "basic_capacitor")).ifPresent(cap -> {
                        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, machine, null);
                        ItemStack left = new ItemStack(cap);
                        for (int s2 = 0; h != null && s2 < h.getSlots() && !left.isEmpty(); s2++) {
                            left = h.insertItem(s2, left, false);
                        }
                        info("enderio capacitor inserted={}", left.isEmpty());
                    });
                }
                if (MOD_MACHINES[i][0].equals("create")) {
                    // a Create machine needs to turn: a creative motor under it
                    BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "creative_motor")).ifPresent(m -> {
                        var st = m.defaultBlockState();
                        for (var prop : st.getProperties()) {
                            if (prop.getName().equals("facing")) {
                                @SuppressWarnings({"unchecked", "rawtypes"})
                                var dp = (net.minecraft.world.level.block.state.properties.Property) prop;
                                st = st.setValue(dp, Direction.UP);
                            }
                        }
                        level.setBlockAndUpdate(c.north(), st);
                    });
                }
            }
            NetworkManager.markDirty();
            return true;
        }));
        STEPS.add(new Step("other mods' machines: each learns its recipes", 60, () -> {
            StorageNetwork n = NetworkManager.get(level, MOD);
            if (n == null) {
                return false;
            }
            n.insertBulk(BulkKey.flat(BulkKind.ENERGY), 50_000_000, false);
            boolean allLearned = true;
            MOD_REPORT.clear();
            for (int i = 0; i < MOD_MACHINES.length; i++) {
                String id = MOD_MACHINES[i][0] + ":" + MOD_MACHINES[i][1];
                if (!(level.getBlockEntity(MOD.east(1 + i * 2).above()) instanceof com.xtri6.grimstorage.block.entity.MachineBusBlockEntity bus)) {
                    MOD_REPORT.add(id + ": mod not in the dev game");
                    continue;
                }
                int r = bus.learnedPatterns().size();
                MOD_REPORT.add(id + ": " + r + " recipes");
                allLearned &= r > 0 || id.startsWith("immersiveengineering");
            }
            info("mod machines: {}", MOD_REPORT);
            return allLearned;
        }));
        for (int idx = 0; idx < MOD_MACHINES.length; idx++) {
            final int i = idx;
            String id = MOD_MACHINES[i][0] + ":" + MOD_MACHINES[i][1];
            if (id.startsWith("ars_nouveau") || id.startsWith("immersiveengineering")) {
                continue; // Imbuement needs Source from jars; IE's machines are multiblocks that need building first
            }
            STEPS.add(new Step(id + ": order something it makes", 0, () -> {
                if (!(level.getBlockEntity(MOD.east(1 + i * 2).above()) instanceof com.xtri6.grimstorage.block.entity.MachineBusBlockEntity bus)) {
                    return true;
                }
                StorageNetwork n = NetworkManager.get(level, MOD);
                for (com.xtri6.grimstorage.storage.Pattern p : bus.learnedPatterns()) {
                    boolean vanilla = p.bulkInputs().isEmpty() && p.inputs().keySet().stream()
                            .allMatch(k -> BuiltInRegistries.ITEM.getKey(k.item()).getNamespace().equals("minecraft"));
                    if (!vanilla || n.count(p.primary()) > 0) {
                        continue;
                    }
                    p.inputs().forEach((k, c) -> n.insert(k, c * 2, false));
                    CraftingPlanner.Plan plan = n.request(p.primary(), p.outputCount(p.primary()), "test");
                    state[i] = new Object[] {p, plan.ok()};
                    info("{}: ordering {} from {} ok={} missing={}", id, p.primary().item(), p.inputs(), plan.ok(), plan.missing());
                    return plan.ok();
                }
                info("{}: no vanilla-only recipe to try", id);
                return false;
            }));
            STEPS.add(new Step(id + ": the result came back", 900, () -> {
                if (state[i] == null) {
                    return true;
                }
                com.xtri6.grimstorage.storage.Pattern p = (com.xtri6.grimstorage.storage.Pattern) state[i][0];
                StorageNetwork n = NetworkManager.get(level, MOD);
                long have = n.count(p.primary());
                boolean ok = have >= p.outputCount(p.primary()) && n.jobs().isEmpty();
                if (ok) {
                    MOD_REPORT.add(id + ": made " + have + "x " + p.primary().item());
                    info("{}: made {}x {}", id, have, p.primary().item());
                } else if (stepTicks >= 899) {
                    info("{}: still {} jobs={}", id, have, n.jobs().isEmpty() ? "none" : n.jobs().get(0).status().getString());
                }
                return ok;
            }));
        }
    }

    // ------------------------------------------------------------------ per-network caching and the balance config

    private static void addScaleAndConfigSteps() {
        STEPS.add(new Step("scale: a change on one network doesn't make another network refresh", 0, () -> {
            StorageNetwork a = net(), b = mbNet();
            long va = a.contentVersion(), vb = b.contentVersion();
            long before = a.contents().size();
            b.insert(key(Items.KELP), 3, false);
            long va2 = a.contentVersion(), vb2 = b.contentVersion();
            // and the network's own changes still show
            b.extract(key(Items.KELP), 3, false);
            long vb3 = b.contentVersion();
            info("network A version {} -> {} (should stay), B {} -> {} -> {} (should change), A types {}", va, va2, vb, vb2, vb3, before);
            return va == va2 && vb != vb2 && vb2 != vb3 && a.contents().size() == before;
        }));
        STEPS.add(new Step("scale: the terminal's hot paths stay fast (5,000 reads of a network's cached state)", 0, () -> {
            StorageNetwork a = net();
            a.contentVersion();
            a.stats();
            long t0 = System.nanoTime();
            long sink = 0;
            for (int i = 0; i < 5000; i++) {
                sink += a.contentVersion() + a.stats()[0] + a.contents().size();
            }
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            info("5000 reads of cached state took {} ms (sink {})", ms, sink);
            // a generous limit: this fails only if the cache stops working and every read rescans the drives
            return ms < 2000;
        }));
        STEPS.add(new Step("scale: a cell put in a drive is used straight away (cached cell list refreshes)", 10, () -> {
            DriveBlockEntity d = (DriveBlockEntity) level.getBlockEntity(MB.west());
            int free = -1;
            for (int i = 0; i < DriveBlockEntity.SLOTS; i++) {
                if (d.cells().getStackInSlot(i).isEmpty()) {
                    free = i;
                    break;
                }
            }
            if (free < 0) {
                return false;
            }
            long capBefore = mbNet().stats()[1];
            d.cells().setStackInSlot(free, new ItemStack(Registration.CELLS.get(CellTier.COPPER).get()));
            // the network rebuilds on its next tick; until then cells are read straight from the drives
            long capNow = mbNet().stats()[1];
            d.cells().setStackInSlot(free, ItemStack.EMPTY);
            info("capacity {} -> {} with a copper cell added", capBefore, capNow);
            return capNow == capBefore + CellTier.COPPER.capacity();
        }));
        STEPS.add(new Step("config: cell capacities, bus speed and wireless range follow the server config", 0, () -> {
            List<? extends Long> items = com.xtri6.grimstorage.BalanceConfig.ITEM_CELLS.get();
            List<? extends Long> fluids = com.xtri6.grimstorage.BalanceConfig.bulkCells(BulkKind.FLUID).get();
            double speed = com.xtri6.grimstorage.BalanceConfig.BUS_SPEED.get();
            int range = com.xtri6.grimstorage.BalanceConfig.WIRELESS_RANGE.get();
            boolean loaded = com.xtri6.grimstorage.BalanceConfig.SPEC.isLoaded();
            long copperDefault = CellTier.COPPER.capacity();
            com.xtri6.grimstorage.block.entity.BusBlockEntity bus = (com.xtri6.grimstorage.block.entity.BusBlockEntity) level.getBlockEntity(at(4, 1, 0));
            int busSpeed = bus.logic().speed();
            com.xtri6.grimstorage.BalanceConfig.ITEM_CELLS.set(List.of(1_000L, 2_000L, 3_000L, 4_000L));
            com.xtri6.grimstorage.BalanceConfig.BUS_SPEED.set(3.0);
            com.xtri6.grimstorage.BalanceConfig.WIRELESS_RANGE.set(512);
            com.xtri6.grimstorage.BalanceConfig.bulkCells(BulkKind.FLUID).set(List.of(7L, 8L, 9L, 10L));
            long copper = CellTier.COPPER.capacity(), sing = CellTier.SINGULARITY.capacity();
            long fluidGold = BulkKind.FLUID.capacity(CellTier.GOLD);
            int busFast = bus.logic().speed();
            int wireless = com.xtri6.grimstorage.item.WirelessTerminalItem.range();
            com.xtri6.grimstorage.BalanceConfig.ITEM_CELLS.set(items);
            com.xtri6.grimstorage.BalanceConfig.BUS_SPEED.set(speed);
            com.xtri6.grimstorage.BalanceConfig.WIRELESS_RANGE.set(range);
            com.xtri6.grimstorage.BalanceConfig.bulkCells(BulkKind.FLUID).set(fluids);
            info("config loaded={} copper {} -> {} (singularity {}), gold fluid cell {}, bus speed {} -> {}, wireless range {} -> {}; back to copper {}",
                    loaded, copperDefault, copper, sing, fluidGold, busSpeed, busFast, range, wireless, CellTier.COPPER.capacity());
            return loaded && copperDefault == 4_096 && copper == 1_000 && sing > 1_000_000_000L && fluidGold == 8 && busFast == busSpeed * 3
                    && wireless == 512 && CellTier.COPPER.capacity() == 4_096;
        }));
    }

    // ------------------------------------------------------------------ chains of chemical machines, Source, the job queue

    private static final BlockPos CHAIN = new BlockPos(48, 230, 48);

    private static BulkKey mekChem(String id) {
        return BulkKey.chemical(ResourceLocation.fromNamespaceAndPath("mekanism", id));
    }

    private static ItemKey modItem(String ns, String id) {
        return ItemKey.of(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(ns, id))));
    }

    private static StorageNetwork chainNet() {
        return NetworkManager.get(level, CHAIN);
    }

    /** A machine with a Machine Bus on it, fed from cable {@code cable}: the bus sits on top, the machine north of it. */
    private static void busOn(BlockPos cable, String ns, String id) {
        level.setBlockAndUpdate(cable, Registration.CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(cable.above().north(), BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(ns, id)).defaultBlockState());
        level.setBlockAndUpdate(cable.above(), Registration.MACHINE_BUS.get().defaultBlockState().setValue(FacingDeviceBlock.All.FACING, Direction.NORTH));
    }

    private static void addChainSteps() {
        boolean mek = net.neoforged.fml.ModList.get().isLoaded("mekanism");
        boolean ars = net.neoforged.fml.ModList.get().isLoaded("ars_nouveau");
        STEPS.add(new Step("machine chains: build a Chemical Washer and a Crystallizer (and an Imbuement Chamber)", 60, () -> {
            if (chainNet() == null) {
                level.setChunkForced(CHAIN.getX() >> 4, CHAIN.getZ() >> 4, true);
                level.setBlockAndUpdate(CHAIN, Registration.CONTROLLER.get().defaultBlockState());
                level.setBlockAndUpdate(CHAIN.west(), Registration.DRIVE.get().defaultBlockState());
                DriveBlockEntity d = (DriveBlockEntity) level.getBlockEntity(CHAIN.west());
                d.cells().setStackInSlot(0, new ItemStack(Registration.CELLS.get(CellTier.GOLD).get()));
                d.cells().setStackInSlot(1, new ItemStack(Registration.BULK_CELLS.get(BulkKind.ENERGY).get(CellTier.GOLD).get()));
                d.cells().setStackInSlot(2, new ItemStack(Registration.BULK_CELLS.get(BulkKind.FLUID).get(CellTier.GOLD).get()));
                if (mek) {
                    d.cells().setStackInSlot(3, new ItemStack(Registration.BULK_CELLS.get(BulkKind.CHEMICAL).get(CellTier.GOLD).get()));
                }
                if (ars) {
                    d.cells().setStackInSlot(4, new ItemStack(Registration.BULK_CELLS.get(BulkKind.SOURCE).get(CellTier.GOLD).get()));
                }
                level.setBlockAndUpdate(CHAIN.east(), Registration.CABLE.get().defaultBlockState());
                if (mek) {
                    busOn(CHAIN.east(2), "mekanism", "chemical_washer");
                    level.setBlockAndUpdate(CHAIN.east(3), Registration.CABLE.get().defaultBlockState());
                    busOn(CHAIN.east(4), "mekanism", "chemical_crystallizer");
                    level.setBlockAndUpdate(CHAIN.east(7), Registration.CABLE.get().defaultBlockState());
                    busOn(CHAIN.east(8), "mekanism", "metallurgic_infuser");
                }
                level.setBlockAndUpdate(CHAIN.east(5), Registration.CABLE.get().defaultBlockState());
                if (ars) {
                    busOn(CHAIN.east(6), "ars_nouveau", "imbuement_chamber");
                }
                NetworkManager.markDirty();
                return false;
            }
            StorageNetwork n = chainNet();
            n.insertBulk(BulkKey.flat(BulkKind.ENERGY), 50_000_000, false);
            return n.isActive();
        }));
        if (mek) {
            STEPS.add(new Step("Chemical Washer: learns slurry recipes that take a chemical and water and make a chemical", 60, () -> {
                var bus = (com.xtri6.grimstorage.block.entity.MachineBusBlockEntity) level.getBlockEntity(CHAIN.east(2).above());
                List<com.xtri6.grimstorage.storage.Pattern> ps = bus.learnedPatterns();
                long chemOnly = ps.stream().filter(p -> p.inputs().isEmpty() && p.outputs().isEmpty() && !p.bulkOutputs().isEmpty()).count();
                boolean iron = ps.stream().anyMatch(p -> p.bulkOutputs().containsKey(mekChem("clean_iron")) && p.bulkInputs().containsKey(mekChem("dirty_iron"))
                        && p.bulkInputs().containsKey(BulkKey.fluid(new FluidStack(Fluids.WATER, 1))));
                info("washer recipes={} chemical-only={} iron one ok={}", ps.size(), chemOnly, iron);
                return chemOnly > 3 && iron;
            }));
            STEPS.add(new Step("Chemical Crystallizer: learns slurry to crystal", 0, () -> {
                var bus = (com.xtri6.grimstorage.block.entity.MachineBusBlockEntity) level.getBlockEntity(CHAIN.east(4).above());
                boolean iron = bus.learnedPatterns().stream().anyMatch(p -> p.inputs().isEmpty()
                        && p.bulkInputs().containsKey(mekChem("clean_iron")) && modItem("mekanism", "crystal_iron").equals(p.primary()));
                info("crystallizer recipes={} iron crystal ok={}", bus.learnedPatterns().size(), iron);
                return iron;
            }));
            STEPS.add(new Step("Mekanism chemical machines: every one's recipes can be read", 0, () -> {
                String[] types = {"dissolution", "washing", "crystallizing", "oxidizing", "chemical_infusing", "activating", "centrifuging",
                        "separating", "reaction", "pigment_extracting", "pigment_mixing", "painting"};
                List<String> report = new ArrayList<>();
                boolean all = true;
                for (String t : types) {
                    var type = BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.fromNamespaceAndPath("mekanism", t));
                    if (type == null) {
                        report.add(t + ": no such type");
                        all = false;
                        continue;
                    }
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    List<net.minecraft.world.item.crafting.RecipeHolder<?>> rs = (List) level.getRecipeManager().getAllRecipesFor((net.minecraft.world.item.crafting.RecipeType) type);
                    long ok = rs.stream().map(h -> com.xtri6.grimstorage.storage.RecipeReader.read(h.value(), level.registryAccess()))
                            .filter(r -> r != null && r.usable()).count();
                    report.add(t + " " + ok + "/" + rs.size());
                    all &= ok > 0;
                }
                info("mekanism chemical recipes read: {}", report);
                return all;
            }));
            STEPS.add(new Step("machine chain: without dirty slurry, planning iron crystals says what's missing", 0, () -> {
                CraftingPlanner.Plan p = chainNet().plan(modItem("mekanism", "crystal_iron"), 2);
                info("plan ok={} missing bulk={}", p.ok(), p.missingBulk());
                return !p.ok() && p.missingBulk().containsKey(mekChem("dirty_iron"));
            }));
            STEPS.add(new Step("machine chain: order iron crystals, the plan goes washer -> crystallizer", 0, () -> {
                StorageNetwork n = chainNet();
                n.insertBulk(mekChem("dirty_iron"), 4_000, false);
                n.insertBulk(BulkKey.fluid(new FluidStack(Fluids.WATER, 1)), 40_000, false);
                CraftingPlanner.Plan p = n.request(modItem("mekanism", "crystal_iron"), 2, "test");
                List<String> steps = p.steps().stream().map(st -> st.pattern().primaryKey() instanceof BulkKey b ? b.id() + "x" + st.times()
                        : st.pattern().primary().item() + "x" + st.times()).toList();
                info("crystal plan ok={} steps={} missing={} {}", p.ok(), steps, p.missing(), p.missingBulk());
                return p.ok() && p.steps().size() == 2 && p.steps().get(0).pattern().primaryKey().equals(mekChem("clean_iron"));
            }));
            STEPS.add(new Step("machine chain: crystals came back, clean slurry went through the network", 1200, () -> {
                StorageNetwork n = chainNet();
                long crystals = n.count(modItem("mekanism", "crystal_iron"));
                long dirty = n.countBulk(mekChem("dirty_iron"));
                if (stepTicks % 200 == 199 || crystals >= 2) {
                    info("crystals={} dirty slurry left={} clean in storage={} jobs={}", crystals, dirty, n.countBulk(mekChem("clean_iron")),
                            n.jobs().isEmpty() ? "none" : n.jobs().get(0).status().getString() + " " + n.jobs().get(0).steps().stream()
                                    .map(s2 -> s2.finished() + "/" + s2.total() + " in " + s2.inMachines()).toList());
                }
                return crystals >= 2 && dirty < 4_000 && n.jobs().isEmpty();
            }));
            final long[] circuits = new long[2];
            STEPS.add(new Step("Metallurgic Infuser: plans Basic Control Circuits from redstone dust (no redstone chemical stored)", 0, () -> {
                StorageNetwork n = chainNet();
                ItemKey circuit = modItem("mekanism", "basic_control_circuit");
                n.insert(modItem("mekanism", "ingot_osmium"), 2, false);
                n.insert(key(Items.REDSTONE), 4, false);
                circuits[0] = n.count(circuit);
                circuits[1] = n.count(key(Items.REDSTONE));
                long chem = n.countBulk(mekChem("redstone"));
                CraftingPlanner.Plan p = n.request(circuit, 2, "test");
                info("circuit plan ok={} redstone chemical stored={} missing={} {} steps={}", p.ok(), chem, p.missing(), p.missingBulk(),
                        p.steps().stream().map(st -> st.pattern().inputs() + "+" + st.pattern().bulkInputs()).toList());
                return p.ok() && chem == 0;
            }));
            STEPS.add(new Step("Metallurgic Infuser: the circuits came back, the dust went into the machine", 900, () -> {
                StorageNetwork n = chainNet();
                long made = n.count(modItem("mekanism", "basic_control_circuit")) - circuits[0];
                long dust = n.count(key(Items.REDSTONE));
                if (stepTicks % 200 == 199 || made >= 2) {
                    info("circuits made={} redstone dust {} -> {} jobs={}", made, circuits[1], dust,
                            n.jobs().isEmpty() ? "none" : n.jobs().get(0).status().getString());
                }
                return made >= 2 && dust == circuits[1] - 4 && n.jobs().isEmpty();
            }));
            STEPS.add(new Step("missing hints: same item with other data, and another mod's look-alike", 0, () -> {
                StorageNetwork n = chainNet();
                ItemStack named = new ItemStack(Items.FLINT, 3);
                named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Lucky"));
                n.insert(named, false);
                List<String> flint = com.xtri6.grimstorage.storage.MissingHints.explain(n, key(Items.FLINT), null).stream()
                        .map(net.minecraft.network.chat.Component::getString).toList();
                List<String> dust = List.of();
                var ieDust = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("immersiveengineering", "dust_iron"));
                if (ieDust.isPresent()) {
                    n.insert(ItemKey.of(new ItemStack(ieDust.get())), 5, false);
                    dust = com.xtri6.grimstorage.storage.MissingHints.explain(n, modItem("mekanism", "dust_iron"), null).stream()
                            .map(net.minecraft.network.chat.Component::getString).toList();
                }
                List<String> none = com.xtri6.grimstorage.storage.MissingHints.explain(n, key(Items.NETHER_STAR), null).stream()
                        .map(net.minecraft.network.chat.Component::getString).toList();
                info("hints: flint={} mekanism iron dust={} nether star={}", flint, dust, none);
                boolean dustOk = ieDust.isEmpty() || dust.stream().anyMatch(h -> h.contains("Immersive") && h.contains("c:dusts/iron"));
                return flint.stream().anyMatch(h -> h.startsWith("3 more")) && dustOk && none.stream().anyMatch(h -> h.contains("nothing on the network"));
            }));
            STEPS.add(new Step("/grimstorage debug writes a network report", 0, () -> {
                java.nio.file.Path dir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("grimstorage-debug");
                int result;
                try {
                    result = server.getCommands().getDispatcher().execute("grimstorage debug " + CHAIN.getX() + " " + CHAIN.getY() + " " + CHAIN.getZ(),
                            server.createCommandSourceStack());
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
                    info("debug command failed: {}", ex.getMessage());
                    return false;
                }
                java.nio.file.Path newest = null;
                try (var files = java.nio.file.Files.list(dir)) {
                    newest = files.max(java.util.Comparator.comparing(f -> f.toFile().lastModified())).orElse(null);
                } catch (Exception ex) {
                    info("no debug folder: {}", ex.toString());
                }
                String text = "";
                try {
                    text = newest == null ? "" : java.nio.file.Files.readString(newest);
                } catch (Exception ignored) {
                    // checked below
                }
                info("debug command result={} file={} size={} has machines={} has mods={}", result, newest, text.length(),
                        text.contains("MachineBusBlockEntity -> Chemical Washer"), text.contains("== Mods"));
                return result == 1 && text.contains("MachineBusBlockEntity -> Chemical Washer") && text.contains("== Mods") && text.contains("learned");
            }));
            STEPS.add(new Step("job queue: one player can't take every job slot", 0, () -> {
                StorageNetwork n = chainNet();
                for (int i = 0; i < com.xtri6.grimstorage.ServerConfig.maxJobsPerPlayer() + 3; i++) {
                    n.request(modItem("mekanism", "crystal_iron"), 1, "Spammer");
                }
                int theirs = n.jobsOf("Spammer");
                boolean others = n.canStartJob("Someone");
                for (var j : new ArrayList<>(n.jobs())) {
                    n.cancel(j.id());
                }
                info("spammer jobs={} limit={} others can still order={}", theirs, com.xtri6.grimstorage.ServerConfig.maxJobsPerPlayer(), others);
                return theirs == com.xtri6.grimstorage.ServerConfig.maxJobsPerPlayer() && others && n.jobs().isEmpty();
            }));
            final java.util.UUID[] queue = new java.util.UUID[2];
            final long[] crystalsBefore = new long[1];
            STEPS.add(new Step("job queue: pause one job, move the other to the top", 0, () -> {
                StorageNetwork n = chainNet();
                crystalsBefore[0] = n.count(modItem("mekanism", "crystal_iron"));
                n.request(modItem("mekanism", "crystal_iron"), 1, "test");
                n.request(modItem("mekanism", "crystal_iron"), 1, "test");
                if (n.jobs().size() != 2) {
                    return false;
                }
                queue[0] = n.jobs().get(0).id();
                queue[1] = n.jobs().get(1).id();
                n.jobAction(queue[0], StorageNetwork.JobAction.PAUSE);
                n.jobAction(queue[1], StorageNetwork.JobAction.UP);
                boolean order = n.jobs().get(0).id().equals(queue[1]);
                boolean paused = n.jobs().get(1).paused() && !n.jobs().get(0).paused();
                info("queue order ok={} paused ok={} priorities={}", order, paused, n.jobs().stream().map(j -> j.priority()).toList());
                return order && paused;
            }));
            STEPS.add(new Step("job queue: the paused job waits while the other one finishes", 900, () -> {
                StorageNetwork n = chainNet();
                var paused = n.jobs().stream().filter(j -> j.id().equals(queue[0])).findFirst().orElse(null);
                boolean otherDone = n.jobs().stream().noneMatch(j -> j.id().equals(queue[1]));
                boolean untouched = paused != null && paused.steps().stream().allMatch(st -> st.remaining() == st.total());
                if (otherDone) {
                    info("other job done={} paused job untouched={} status={}", otherDone, untouched, paused == null ? "gone" : paused.status().getString());
                }
                return otherDone && untouched;
            }));
            STEPS.add(new Step("job queue: resumed, it finishes too", 900, () -> {
                StorageNetwork n = chainNet();
                n.jobs().stream().filter(j -> j.id().equals(queue[0]) && j.paused()).findFirst()
                        .ifPresent(j -> n.jobAction(j.id(), StorageNetwork.JobAction.PAUSE));
                long crystals = n.count(modItem("mekanism", "crystal_iron"));
                return n.jobs().isEmpty() && crystals >= crystalsBefore[0] + 2;
            }));
        }
        if (ars) {
            final Object[] imbue = new Object[1];
            STEPS.add(new Step("Imbuement Chamber: learns its Source recipes (and skips the pedestal ones)", 60, () -> {
                var bus = (com.xtri6.grimstorage.block.entity.MachineBusBlockEntity) level.getBlockEntity(CHAIN.east(6).above());
                List<com.xtri6.grimstorage.storage.Pattern> ps = bus.learnedPatterns();
                long source = ps.stream().filter(p -> p.bulkInputs().containsKey(BulkKey.flat(BulkKind.SOURCE))).count();
                info("imbuement recipes={} costing Source={}", ps.size(), source);
                return !ps.isEmpty() && source == ps.size();
            }));
            STEPS.add(new Step("Imbuement Chamber: order something, the bus feeds it Source", 0, () -> {
                StorageNetwork n = chainNet();
                var bus = (com.xtri6.grimstorage.block.entity.MachineBusBlockEntity) level.getBlockEntity(CHAIN.east(6).above());
                n.insertBulk(BulkKey.flat(BulkKind.SOURCE), 50_000, false);
                for (com.xtri6.grimstorage.storage.Pattern p : bus.learnedPatterns()) {
                    if (p.primary() == null || n.count(p.primary()) > 0 || !p.inputs().keySet().stream()
                            .allMatch(k -> BuiltInRegistries.ITEM.getKey(k.item()).getNamespace().equals("minecraft"))) {
                        continue;
                    }
                    p.inputs().forEach((k, c) -> n.insert(k, c, false));
                    CraftingPlanner.Plan plan = n.request(p.primary(), p.outputCount(p.primary()), "test");
                    imbue[0] = p;
                    info("imbuing {} from {} with {} Source: ok={} missing={} {}", p.primary().item(), p.inputs(), p.bulkInputs(), plan.ok(),
                            plan.missing(), plan.missingBulk());
                    return plan.ok();
                }
                return false;
            }));
            STEPS.add(new Step("Imbuement Chamber: the result came back and Source was used", 1200, () -> {
                StorageNetwork n = chainNet();
                if (imbue[0] == null) {
                    return false;
                }
                com.xtri6.grimstorage.storage.Pattern p = (com.xtri6.grimstorage.storage.Pattern) imbue[0];
                long have = n.count(p.primary());
                long src = n.countBulk(BulkKey.flat(BulkKind.SOURCE));
                if (stepTicks % 200 == 199 || have > 0) {
                    Object inside = level.getBlockEntity(CHAIN.east(6).above().north()) instanceof net.minecraft.world.Container c ? c.getItem(0) : "?";
                    info("imbued {}={} Source left={} chamber holds={} inputs back in storage={} jobs={}", p.primary().item(), have, src, inside,
                            p.inputs().keySet().stream().map(n::count).toList(), n.jobs().isEmpty() ? "none" : n.jobs().get(0).status().getString());
                }
                // only the recipe's Source is used: the chamber holds millions but is given just what the recipe costs
                return have >= p.outputCount(p.primary()) && src < 50_000 && src >= 50_000 - 2 * p.bulkInputs().values().iterator().next()
                        && n.jobs().isEmpty();
            }));
        }
        if (mek) {
            STEPS.add(new Step("restart test: leave a paused job and a running job for after the restart", 0, () -> {
                StorageNetwork n = chainNet();
                n.insert(modItem("mekanism", "ingot_osmium"), 6, false);
                n.insert(key(Items.REDSTONE), 12, false);
                ItemKey circuit = modItem("mekanism", "basic_control_circuit");
                CraftingPlanner.Plan paused = n.request(circuit, 2, "Paused Pat");
                CraftingPlanner.Plan running = n.request(circuit, 4, "Running Rae");
                for (java.util.UUID id : n.jobs().stream().filter(j -> j.source().equals("Paused Pat")).map(j -> j.id()).toList()) {
                    n.jobAction(id, StorageNetwork.JobAction.PAUSE);
                }
                try {
                    java.nio.file.Files.writeString(java.nio.file.Path.of("systemcheck_circuits.txt"), Long.toString(n.count(circuit)));
                } catch (Exception ex) {
                    return false;
                }
                info("left for the restart: paused ok={} running ok={} jobs={}", paused.ok(), running.ok(), n.jobs().size());
                return paused.ok() && running.ok() && n.jobs().size() == 2;
            }));
        }
    }

    /** A made-up machine recipe: an iron ingot and 250 mB of water make a gold ingot. */
    public static final class FluidTestRecipe implements net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.RecipeInput> {
        @Override
        public boolean matches(net.minecraft.world.item.crafting.RecipeInput in, Level l) {
            return false;
        }

        @Override
        public ItemStack assemble(net.minecraft.world.item.crafting.RecipeInput in, net.minecraft.core.HolderLookup.Provider r) {
            return new ItemStack(Items.GOLD_INGOT);
        }

        @Override
        public boolean canCraftInDimensions(int w, int h) {
            return true;
        }

        @Override
        public ItemStack getResultItem(net.minecraft.core.HolderLookup.Provider r) {
            return new ItemStack(Items.GOLD_INGOT);
        }

        @Override
        public net.minecraft.core.NonNullList<net.minecraft.world.item.crafting.Ingredient> getIngredients() {
            return net.minecraft.core.NonNullList.of(net.minecraft.world.item.crafting.Ingredient.EMPTY,
                    net.minecraft.world.item.crafting.Ingredient.of(Items.IRON_INGOT));
        }

        public net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient getFluidIngredient() {
            return net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient.of(Fluids.WATER, 250);
        }

        @Override
        public net.minecraft.world.item.crafting.RecipeSerializer<?> getSerializer() {
            return null;
        }

        @Override
        public net.minecraft.world.item.crafting.RecipeType<?> getType() {
            return net.minecraft.world.item.crafting.RecipeType.SMELTING;
        }
    }

    private static void buildPersistenceSteps() {
        if (net.neoforged.fml.ModList.get().isLoaded("mekanism")) {
            STEPS.add(new Step("after a restart: both crafting jobs are back, and find their machine", 200, () -> {
                StorageNetwork n = chainNet();
                if (n == null) {
                    return false;
                }
                var paused = n.jobs().stream().filter(j -> j.source().equals("Paused Pat")).findFirst().orElse(null);
                long jobs = n.jobs().size();
                boolean found = n.jobs().stream().allMatch(j -> j.steps().stream().allMatch(st -> st.pattern() != null));
                if (stepTicks % 50 == 49 || found) {
                    info("after restart: jobs={} paused one back={} still paused={} recipes found={} statuses={}", jobs, paused != null,
                            paused != null && paused.paused(), found, n.jobs().stream().map(j -> j.source() + ": " + j.status().getString()).toList());
                }
                // the running one may have finished already; the paused one must be there, paused, with its recipe found
                return paused != null && paused.paused() && found;
            }));
            STEPS.add(new Step("after a restart: resume the paused job, every circuit gets made", 1200, () -> {
                StorageNetwork n = chainNet();
                for (java.util.UUID id : n.jobs().stream().filter(j -> j.paused()).map(j -> j.id()).toList()) {
                    n.jobAction(id, StorageNetwork.JobAction.PAUSE);
                }
                long before;
                try {
                    before = Long.parseLong(java.nio.file.Files.readString(java.nio.file.Path.of("systemcheck_circuits.txt")).trim());
                } catch (Exception ex) {
                    return false;
                }
                long now = n.count(modItem("mekanism", "basic_control_circuit"));
                if (stepTicks % 200 == 199 || (now >= before + 6 && n.jobs().isEmpty())) {
                    info("circuits {} -> {} jobs={}", before, now, n.jobs().stream().map(j -> j.source() + ": " + j.status().getString()).toList());
                }
                return now >= before + 6 && n.jobs().isEmpty();
            }));
        }
        STEPS.add(new Step("network owner and trusted players survived a restart", 10, () -> {
            if (!(level.getBlockEntity(MB) instanceof com.xtri6.grimstorage.block.entity.ControllerBlockEntity c)) {
                return false;
            }
            info("owner={} trusted={} open={}", c.ownerName(), c.trusted().values(), c.isOpen());
            return c.ownerName().equals("Alice") && c.trusted().containsValue("Bob") && !c.isOpen();
        }));
        STEPS.add(new Step("contents survived a restart", 10, () -> {
            StorageNetwork n = net();
            if (n == null) {
                return false;
            }
            long total = n.contents().values().stream().mapToLong(Long::longValue).sum();
            for (BulkKind kind : BulkKind.values()) {
                total += n.bulkStats(kind)[0];
            }
            info("after restart: water={} energy={} hydrogen={} source={}", n.countBulk(BulkKey.fluid(new FluidStack(Fluids.WATER, 1))),
                    n.countBulk(BulkKey.flat(BulkKind.ENERGY)),
                    n.countBulk(BulkKey.chemical(ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen"))), n.countBulk(BulkKey.flat(BulkKind.SOURCE)));
            long expected;
            try {
                expected = Long.parseLong(Files.readString(TOTAL_FILE).trim());
            } catch (Exception ex) {
                return false;
            }
            boolean busKept = level.getBlockEntity(at(4, 1, 0)) instanceof com.xtri6.grimstorage.block.entity.BusBlockEntity b
                    && b.filters().getItem(0).is(Items.APPLE) && !b.blacklist();
            info("after restart total={} expected={} drives={} export bus kept its filter={} xp={}", total, expected, n.driveCount(), busKept,
                    n.countBulk(BulkKey.flat(BulkKind.EXPERIENCE)));
            boolean cableBusKept = level.getBlockEntity(at(1, 1, 0)) instanceof com.xtri6.grimstorage.block.entity.CableBusBlockEntity cbe
                    && cbe.part(Direction.NORTH) != null && !cbe.part(Direction.NORTH).isExport();
            info("cable bus kept after restart={} state={} be={}", cableBusKept, level.getBlockState(at(1, 1, 0)), level.getBlockEntity(at(1, 1, 0)));
            return total == expected && n.driveCount() == 2 && busKept && cableBusKept;
        }));
    }

    // ------------------------------------------------------------------ runner

    private static void onTick(ServerTickEvent.Post e) {
        if (server == null || stepIndex > STEPS.size()) {
            return;
        }
        if (stepIndex == STEPS.size()) {
            stepIndex++;
            info("RESULT: {} ({} failures) {}", FAILURES.isEmpty() ? "PASS" : "FAIL", FAILURES.size(), FAILURES);
            server.halt(false);
            return;
        }
        Step step = STEPS.get(stepIndex);
        waited++;
        if (waited < Math.max(1, step.waitTicks() == 0 ? 1 : 2)) {
            return;
        }
        boolean ok;
        try {
            ok = step.test().getAsBoolean();
        } catch (Throwable t) {
            GrimStorage.LOGGER.error("[SystemCheck] step '{}' crashed", step.name(), t);
            ok = false;
        }
        stepTicks++;
        if (ok || stepTicks >= Math.max(1, step.waitTicks())) {
            info("{} {}", ok ? "PASS" : "FAIL", step.name());
            if (!ok) {
                FAILURES.add(step.name());
            }
            stepIndex++;
            waited = 0;
            stepTicks = 0;
        }
    }
}
