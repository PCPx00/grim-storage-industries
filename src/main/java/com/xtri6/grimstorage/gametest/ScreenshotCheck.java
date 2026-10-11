package com.xtri6.grimstorage.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.xtri6.grimstorage.ClientConfig;
import com.xtri6.grimstorage.GrimStorage;
import com.xtri6.grimstorage.block.entity.AssemblerBlockEntity;
import com.xtri6.grimstorage.client.TerminalScreen;
import com.xtri6.grimstorage.menu.Menus;
import com.xtri6.grimstorage.menu.TerminalMenu;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.ItemKey;
import com.xtri6.grimstorage.storage.NetworkManager;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Dev-only: loads the system-check world in a real client and screenshots the screens in both themes. */
public final class ScreenshotCheck {
    private static final BlockPos BASE = new BlockPos(0, 220, 0);
    private static final Item[] SAMPLES = {Items.IRON_ORE, Items.RAW_IRON, Items.RAW_GOLD, Items.DIAMOND, Items.IRON_INGOT, Items.GOLD_NUGGET,
            Items.REDSTONE, Items.IRON_SWORD, Items.DIAMOND_PICKAXE, Items.BOW, Items.SHEARS, Items.IRON_CHESTPLATE, Items.DIAMOND_HELMET,
            Items.ELYTRA, Items.BREAD, Items.APPLE, Items.COOKED_BEEF, Items.CARROT, Items.BONE, Items.STRING, Items.ROTTEN_FLESH,
            Items.GUNPOWDER, Items.ENDER_PEARL, Items.WHEAT_SEEDS, Items.WHEAT, Items.OAK_SAPLING, Items.BONE_MEAL, Items.DANDELION,
            Items.BRICKS, Items.STONE_BRICKS, Items.GLASS, Items.OAK_STAIRS, Items.WHITE_WOOL, Items.COMPASS, Items.NAME_TAG,
            Items.HOPPER, Items.TORCH, Items.OAK_PLANKS};
    private record Shot(String name, boolean dark, Consumer<ServerPlayer> open, Runnable client) {}

    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int tick;
    private static int index = -1;

    private ScreenshotCheck() {}

    private static void terminalWithGrid(ServerPlayer sp) {
        Menus.openTerminal(sp, BASE.offset(5, 0, 0));
        if (sp.containerMenu instanceof TerminalMenu m) {
            m.getSlot(m.gridStart).set(new ItemStack(Items.OAK_PLANKS, 12));
            m.getSlot(m.gridStart + 3).set(new ItemStack(Items.OAK_PLANKS, 12));
            m.broadcastChanges();
        }
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> onTick());
        Runnable none = () -> {};
        SHOTS.add(new Shot("b03_terminal_xp", true, sp -> Menus.openTerminal(sp, BASE.offset(5, 0, 0)), () -> {}));
        SHOTS.add(new Shot("b01_import_bus_dark", true, sp -> Menus.openBus(sp, BASE.offset(3, 1, 0)), none));
        SHOTS.add(new Shot("b02_export_bus_light", false, sp -> Menus.openBus(sp, BASE.offset(4, 1, 0)), none));
        SHOTS.add(new Shot("b04_interface", true, sp -> Menus.openFilter(sp, BASE.offset(-1, 0, 0)), none));
        SHOTS.add(new Shot("b05_controller", true, sp -> Menus.openController(sp, BASE), none));
        SHOTS.add(new Shot("b11_terminal_xp", true, sp -> Menus.openTerminal(sp, BASE.offset(5, 0, 0)), () -> {}));
        SHOTS.add(new Shot("b06_guide_buses", true, sp -> {}, () -> book(9, 0)));
        SHOTS.add(new Shot("b07_guide_filters", true, sp -> {}, () -> book(9, 3)));
        SHOTS.add(new Shot("b08_guide_xp", true, sp -> {}, () -> book(5, 4)));
        SHOTS.add(new Shot("b10_guide_import_recipe", false, sp -> {}, () -> book(9, 1)));
        SHOTS.add(new Shot("b12_showcase", true, sp -> {
            BlockPos s0 = BASE.offset(20, 6, -6);
            var lvl = sp.serverLevel();
            var dir = com.xtri6.grimstorage.block.FacingDeviceBlock.All.FACING;
            lvl.setBlockAndUpdate(s0, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            lvl.setBlockAndUpdate(s0.east(), com.xtri6.grimstorage.Registration.IMPORT_BUS.get().defaultBlockState().setValue(dir, net.minecraft.core.Direction.WEST));
            lvl.setBlockAndUpdate(s0.east(2), com.xtri6.grimstorage.Registration.CABLE.get().defaultBlockState());
            lvl.setBlockAndUpdate(s0.east(3), com.xtri6.grimstorage.Registration.EXPORT_BUS.get().defaultBlockState().setValue(dir, net.minecraft.core.Direction.EAST));
            lvl.setBlockAndUpdate(s0.east(4), net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
            lvl.setBlockAndUpdate(s0.east(2).below(), com.xtri6.grimstorage.Registration.CABLE.get().defaultBlockState());
            lvl.setBlockAndUpdate(s0.east(2).below(2), com.xtri6.grimstorage.Registration.CONTROLLER.get().defaultBlockState());
            lvl.setBlockAndUpdate(s0.east(2).above(), com.xtri6.grimstorage.Registration.IMPORT_BUS.get().defaultBlockState().setValue(dir, net.minecraft.core.Direction.UP));
            lvl.setBlockAndUpdate(s0.east(2).above(2), net.minecraft.world.level.block.Blocks.CAULDRON.defaultBlockState());
            sp.teleportTo(lvl, s0.getX() + 2.5, s0.getY() - 0.4, s0.getZ() + 3.6, 180f, 8f);
        }, none));
        SHOTS.add(new Shot("b09_world", true, sp -> sp.teleportTo(sp.serverLevel(), 14.5, 221.0, 9.5, 155f, 12f), none));
    }

    private static void book(int chapter, int page) {
        try {
            Class<?> c = com.xtri6.grimstorage.client.guide.GuideScreen.class;
            java.lang.reflect.Field fc = c.getDeclaredField("lastChapter");
            java.lang.reflect.Field fp = c.getDeclaredField("lastPage");
            fc.setAccessible(true);
            fp.setAccessible(true);
            fc.set(null, chapter);
            fp.set(null, page);
        } catch (ReflectiveOperationException ex) {
            GrimStorage.LOGGER.error("[Shots] could not set page", ex);
        }
        com.xtri6.grimstorage.client.guide.GuideScreen.open();
    }

    private static void setMode(BulkKind mode) {
        try {
            java.lang.reflect.Field f = TerminalScreen.class.getDeclaredField("lastMode");
            f.setAccessible(true);
            f.set(null, mode);
        } catch (ReflectiveOperationException ex) {
            GrimStorage.LOGGER.error("[Shots] could not set mode", ex);
        }
    }

    private static void openCraftDialog() {
        if (Minecraft.getInstance().screen instanceof TerminalScreen ts) {
            for (TerminalMenu.Entry e : ts.getMenu().clientEntries) {
                if (e.craftable()) {
                    try {
                        Method open = TerminalScreen.class.getDeclaredMethod("openCraft", TerminalMenu.Entry.class);
                        open.setAccessible(true);
                        open.invoke(ts, e);
                    } catch (ReflectiveOperationException ex) {
                        GrimStorage.LOGGER.error("[Shots] could not open craft dialog", ex);
                    }
                    return;
                }
            }
        }
    }

    private static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) {
            return;
        }
        tick++;
        if (tick == 40) {
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                sp.setGameMode(GameType.CREATIVE);
                sp.getAbilities().flying = true;
                sp.onUpdateAbilities();
                sp.teleportTo(sp.serverLevel(), 2.5, 220.6, 4.5, 200f, 15f);
                StorageNetwork net = NetworkManager.get(sp.serverLevel(), BASE);
                if (net != null) {
                    net.extract(ItemKey.of(new ItemStack(Items.GRAVEL)), 20000, false);
                    for (Item i : SAMPLES) {
                        net.insert(ItemKey.of(new ItemStack(i)), 1 + (i.hashCode() & 63), false);
                    }
                }
            });
        }
        if (tick < 120) {
            return;
        }
        int local = (tick - 120) % 40;
        if (local == 0) {
            index++;
            if (index >= SHOTS.size()) {
                ClientConfig.setDark(true);
                GrimStorage.LOGGER.info("[Shots] done");
                mc.stop();
                return;
            }
            Shot shot = SHOTS.get(index);
            setMode(shot.name().contains("_xp") && !shot.name().contains("guide") ? BulkKind.EXPERIENCE : null);
            ClientConfig.setDark(shot.dark());
            if (mc.screen != null) {
                mc.setScreen(null);
                mc.player.closeContainer();
            }
            server.execute(() -> shot.open().accept(server.getPlayerList().getPlayers().get(0)));
        } else if (local == 20 && index < SHOTS.size()) {
            SHOTS.get(index).client().run();
        } else if (local == 32 && index < SHOTS.size()) {
            Screenshot.grab(mc.gameDirectory, SHOTS.get(index).name() + ".png", mc.getMainRenderTarget(),
                    msg -> GrimStorage.LOGGER.info("[Shots] {}", msg.getString()));
        }
    }
}
