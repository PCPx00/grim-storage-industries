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
        SHOTS.add(new Shot("c01_gallery_night", true, ScreenshotCheck::gallery, none));
        SHOTS.add(new Shot("c02_gallery_night_later", true, sp -> {}, none));
        SHOTS.add(new Shot("c03_pipes_loading", true, ScreenshotCheck::pipes, none));
        SHOTS.add(new Shot("c03_pipes", true, sp -> {}, () -> {
            BlockPos o = BASE.offset(-40, 14, 40);
            var cl = Minecraft.getInstance().level;
            GrimStorage.LOGGER.info("[Shots] client cable east {} west {}", cl.getBlockState(o.east()), cl.getBlockState(o.west()));
        }));
        SHOTS.add(new Shot("c04_cells_inventory", true, sp -> {
            sp.setGameMode(GameType.SURVIVAL);
            sp.getAbilities().mayfly = true;
            sp.getAbilities().flying = true;
            sp.onUpdateAbilities();
            sp.getInventory().clearContent();
            int slot = 9;
            for (var tier : com.xtri6.grimstorage.storage.CellTier.values()) {
                sp.getInventory().setItem(slot++, new ItemStack(com.xtri6.grimstorage.Registration.CELLS.get(tier).get()));
            }
            for (var kind : BulkKind.values()) {
                int col = 0;
                for (var tier : BulkKind.TIERS) {
                    sp.getInventory().setItem(18 + (kind.ordinal() / 2) * 9 + (kind.ordinal() % 2) * 4 + col++,
                            new ItemStack(com.xtri6.grimstorage.Registration.BULK_CELLS.get(kind).get(tier).get()));
                }
            }
            ItemStack card = new ItemStack(com.xtri6.grimstorage.Registration.RECIPE_CARD.get());
            sp.getInventory().setItem(0, card);
            sp.getInventory().setItem(1, new ItemStack(com.xtri6.grimstorage.Registration.PROCESSING_CARD.get()));
        }, () -> Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player))));
        SHOTS.add(new Shot("c05_jei_infusing", true, sp -> sp.setGameMode(GameType.CREATIVE), () -> {
            if (com.xtri6.grimstorage.compat.jei.GrimJeiPlugin.runtime != null) {
                com.xtri6.grimstorage.compat.jei.GrimJeiPlugin.runtime.getRecipesGui()
                        .showTypes(List.of(com.xtri6.grimstorage.compat.jei.GrimJeiPlugin.INFUSING));
            }
        }));
        SHOTS.add(new Shot("c06_guide_tiers", true, sp -> {}, () -> book(4, 2)));
        SHOTS.add(new Shot("c07_guide_pipes", false, sp -> {}, () -> book(9, 3)));
        SHOTS.add(new Shot("c08_guide_cable", true, sp -> {}, () -> book(2, 3)));
    }

    /** Sets a block's "facing" property toward the camera, whatever kind of facing it uses. */
    private static net.minecraft.world.level.block.state.BlockState facingSouth(net.minecraft.world.level.block.Block block) {
        var st = block.defaultBlockState();
        for (var prop : st.getProperties()) {
            if (prop.getName().equals("facing") && prop.getPossibleValues().contains(net.minecraft.core.Direction.SOUTH)) {
                @SuppressWarnings("unchecked")
                var dp = (net.minecraft.world.level.block.state.properties.Property<net.minecraft.core.Direction>) prop;
                return st.setValue(dp, net.minecraft.core.Direction.SOUTH);
            }
        }
        return st;
    }

    private static void gallery(ServerPlayer sp) {
        var lvl = sp.serverLevel();
        lvl.setDayTime(18000);
        BlockPos o = BASE.offset(30, 10, -10);
        net.minecraft.world.level.block.Block[] row1 = {com.xtri6.grimstorage.Registration.CONTROLLER.get(), com.xtri6.grimstorage.Registration.DRIVE.get(),
                com.xtri6.grimstorage.Registration.TERMINAL.get(), com.xtri6.grimstorage.Registration.ASSEMBLER.get(),
                com.xtri6.grimstorage.Registration.STOCK_KEEPER.get(), com.xtri6.grimstorage.Registration.VOID_FILTER.get(),
                com.xtri6.grimstorage.Registration.QUANTUM_LINK.get()};
        net.minecraft.world.level.block.Block[] row2 = {com.xtri6.grimstorage.Registration.INTERFACE.get(), com.xtri6.grimstorage.Registration.SOUL_INFUSER.get(),
                com.xtri6.grimstorage.Registration.PROCESSING_INTERFACE.get(), com.xtri6.grimstorage.Registration.SOULITE_BLOCK.get(),
                com.xtri6.grimstorage.Registration.SOUL_GLASS.get(), com.xtri6.grimstorage.Registration.SOULITE_ORE.get(),
                com.xtri6.grimstorage.Registration.ECHO_SOULITE_ORE.get()};
        for (int i = 0; i < row1.length; i++) {
            lvl.setBlockAndUpdate(o.offset(i, 0, 0), facingSouth(row1[i]));
            lvl.setBlockAndUpdate(o.offset(i, 1, 0), facingSouth(row2[i]));
            lvl.setBlockAndUpdate(o.offset(i, -1, 0), com.xtri6.grimstorage.Registration.CABLE.get().defaultBlockState());
        }
        sp.teleportTo(lvl, o.getX() + 3.5, o.getY() - 0.2, o.getZ() + 5.2, 180f, 6f);
    }

    private static void pipes(ServerPlayer sp) {
        var lvl = sp.serverLevel();
        lvl.setDayTime(6000);
        BlockPos o = BASE.offset(-40, 14, 40);
        var dir = com.xtri6.grimstorage.block.FacingDeviceBlock.All.FACING;
        var pipe = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("grimpipes", "reaper_pipe"));
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                lvl.removeBlock(o.offset(dx, 0, dz), false);
            }
        }
        lvl.setBlockAndUpdate(o, com.xtri6.grimstorage.Registration.CONTROLLER.get().defaultBlockState());
        lvl.setBlockAndUpdate(o.east(), com.xtri6.grimstorage.Registration.CABLE.get().defaultBlockState());
        lvl.setBlockAndUpdate(o.west(), com.xtri6.grimstorage.Registration.CABLE.get().defaultBlockState());
        lvl.setBlockAndUpdate(o.east(2), com.xtri6.grimstorage.Registration.IMPORT_BUS.get().defaultBlockState().setValue(dir, net.minecraft.core.Direction.EAST));
        lvl.setBlockAndUpdate(o.west(2), com.xtri6.grimstorage.Registration.EXPORT_BUS.get().defaultBlockState().setValue(dir, net.minecraft.core.Direction.WEST));
        lvl.setBlockAndUpdate(o.east(3), net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
        lvl.setBlockAndUpdate(o.west(3), net.minecraft.world.level.block.Blocks.BLAST_FURNACE.defaultBlockState());
        lvl.setBlockAndUpdate(o.below(), com.xtri6.grimstorage.Registration.DRIVE.get().defaultBlockState());
        lvl.setBlockAndUpdate(o.east(2).south(3), net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState());
        lvl.setBlockAndUpdate(o.west(2).south(3), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        if (pipe != net.minecraft.world.level.block.Blocks.AIR) {
            SystemCheck.grimPipe(lvl, o.east(2).south(1), null);
            SystemCheck.grimPipe(lvl, o.east(2).south(2), net.minecraft.core.Direction.SOUTH);
            SystemCheck.grimPipe(lvl, o.west(2).south(1), net.minecraft.core.Direction.NORTH);
            SystemCheck.grimPipe(lvl, o.west(2).south(2), null);
        }
        sp.teleportTo(lvl, o.getX() + 0.5, o.getY() + 5.5, o.getZ() + 6.5, 180f, 48f);
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
