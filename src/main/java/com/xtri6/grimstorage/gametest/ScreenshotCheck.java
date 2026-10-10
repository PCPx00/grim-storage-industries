package com.xtri6.grimstorage.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.xtri6.grimstorage.GrimStorage;
import com.xtri6.grimstorage.client.ItemCategories;
import com.xtri6.grimstorage.client.TerminalScreen;
import com.xtri6.grimstorage.menu.Menus;
import com.xtri6.grimstorage.storage.ItemKey;
import com.xtri6.grimstorage.storage.NetworkManager;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Dev-only: loads the system-check world in a real client and screenshots the terminal on every filter tab. */
public final class ScreenshotCheck {
    private static final BlockPos BASE = new BlockPos(0, 220, 0);
    private static final Item[] SAMPLES = {Items.IRON_ORE, Items.RAW_IRON, Items.RAW_GOLD, Items.DIAMOND, Items.IRON_INGOT, Items.GOLD_NUGGET,
            Items.REDSTONE, Items.IRON_SWORD, Items.DIAMOND_PICKAXE, Items.BOW, Items.SHEARS, Items.IRON_CHESTPLATE, Items.DIAMOND_HELMET,
            Items.ELYTRA, Items.BREAD, Items.APPLE, Items.COOKED_BEEF, Items.CARROT, Items.BONE, Items.STRING, Items.ROTTEN_FLESH,
            Items.GUNPOWDER, Items.ENDER_PEARL, Items.WHEAT_SEEDS, Items.WHEAT, Items.OAK_SAPLING, Items.BONE_MEAL, Items.DANDELION,
            Items.BRICKS, Items.STONE_BRICKS, Items.GLASS, Items.OAK_STAIRS, Items.WHITE_WOOL, Items.COMPASS, Items.NAME_TAG,
            Items.HOPPER, Items.TORCH};

    private static final List<ItemCategories.Category> TABS = new ArrayList<>(List.of(ItemCategories.Category.values()));
    private static int tick;
    private static int index = -1;

    private ScreenshotCheck() {}

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> onTick());
    }

    private static void setTab(ItemCategories.Category c) {
        try {
            Field f = TerminalScreen.class.getDeclaredField("lastCategory");
            f.setAccessible(true);
            f.set(null, c);
        } catch (ReflectiveOperationException ex) {
            GrimStorage.LOGGER.error("[Shots] could not set tab", ex);
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
                    net.extract(ItemKey.of(new ItemStack(Items.GRAVEL)), 5000, false);
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
            if (index >= TABS.size()) {
                GrimStorage.LOGGER.info("[Shots] done");
                mc.stop();
                return;
            }
            if (mc.screen != null) {
                mc.player.closeContainer();
            }
            setTab(TABS.get(index));
            server.execute(() -> Menus.openTerminal(server.getPlayerList().getPlayers().get(0), BASE.offset(5, 0, 0)));
        } else if (local == 30 && index < TABS.size()) {
            String name = String.format("tab_%02d_%s", index, TABS.get(index).name().toLowerCase());
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> GrimStorage.LOGGER.info("[Shots] {}", msg.getString()));
        }
    }
}
