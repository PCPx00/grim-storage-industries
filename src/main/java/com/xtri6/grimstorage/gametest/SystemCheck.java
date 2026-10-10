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

    private static void buildSteps() {
        STEPS.add(new Step("network forms with 3 drives' worth of cells across dimensions", 5, () -> {
            StorageNetwork n = net();
            if (n == null) {
                return false;
            }
            info("members={} drives={}", n.memberCount(), n.driveCount());
            return n.driveCount() == 2 && n.memberCount() >= 12;
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
            long expected;
            try {
                expected = Long.parseLong(Files.readString(TOTAL_FILE).trim());
            } catch (Exception ex) {
                return false;
            }
            info("after restart total={} expected={} drives={}", total, expected, n.driveCount());
            return total == expected && n.driveCount() == 2;
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
