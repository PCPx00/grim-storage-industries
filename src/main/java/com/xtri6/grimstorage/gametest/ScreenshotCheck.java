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
        SHOTS.add(new Shot("01_terminal_dark", true, ScreenshotCheck::terminalWithGrid, none));
        SHOTS.add(new Shot("02_terminal_craft_dark", true, ScreenshotCheck::terminalWithGrid, ScreenshotCheck::openCraftDialog));
        SHOTS.add(new Shot("03_terminal_light", false, ScreenshotCheck::terminalWithGrid, none));
        SHOTS.add(new Shot("04_controller_dark", true, sp -> Menus.openController(sp, BASE), none));
        SHOTS.add(new Shot("05_stock_keeper_dark", true, sp -> Menus.openStockKeeper(sp, BASE.offset(0, 1, 0)), none));
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
            ClientConfig.setDark(shot.dark());
            if (mc.screen != null) {
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
