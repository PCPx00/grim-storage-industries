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
        try {
            Object be = level.getBlockEntity(pos);
            Object config = be.getClass().getMethod("getConfig").invoke(be);
            Class tt = Class.forName("mekanism.common.lib.transmitter.TransmissionType");
            Object infoObj = config.getClass().getMethod("getConfig", tt).invoke(config, Enum.valueOf(tt, "CHEMICAL"));
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
            menu.getSlot(menu.gridStart).set(new ItemStack(Items.FLINT, 3));
            menu.clearGrid(fp);
            boolean gridEmpty = menu.getSlot(menu.gridStart).getItem().isEmpty();
            info("preview={} sticks crafted={} planks left in network={} (before {}) grid cleared={} flint={}",
                    preview, sticks, planksLeft, planksBefore, gridEmpty, count(Items.FLINT));
            fp.getInventory().clearContent();
            return preview.is(Items.STICK) && preview.getCount() == 4 && sticks == 4 * ((int) (planksBefore + 12) / 2)
                    && planksLeft == (planksBefore + 12) % 2 && gridEmpty;
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
                com.xtri6.grimstorage.block.entity.BusBlockEntity.profileNanos = 0;
                com.xtri6.grimstorage.block.entity.BusBlockEntity.profileTicks = 0;
            }
            if (lagWait[0]++ < 130) {
                return false;
            }
            double busUs = com.xtri6.grimstorage.block.entity.BusBlockEntity.profileNanos / 1000.0
                    / Math.max(1, com.xtri6.grimstorage.block.entity.BusBlockEntity.profileTicks);
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
        STEPS.add(new Step("save totals for the persistence run", 0, () -> {
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

    private static void buildPersistenceSteps() {
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
            return total == expected && n.driveCount() == 2 && busKept;
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
