package com.xtri6.grimstorage.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.xtri6.grimstorage.GrimStorage;
import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.entity.AssemblerBlockEntity;
import com.xtri6.grimstorage.block.entity.ProcessingInterfaceBlockEntity;
import com.xtri6.grimstorage.client.TerminalScreen;
import com.xtri6.grimstorage.menu.Menus;
import com.xtri6.grimstorage.menu.TerminalMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Dev-only: loads the system-check world in a real client, opens every screen and saves screenshots. */
public final class ScreenshotCheck {
    private static final BlockPos BASE = new BlockPos(0, 220, 0);
    private record Shot(String name, Consumer<ServerPlayer> open, Runnable clientExtra) {}

    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int tick;
    private static int index = -1;
    private static int phase;

    private ScreenshotCheck() {}

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> onTick());
        SHOTS.add(new Shot("00_world", sp -> {}, () -> Minecraft.getInstance().options.hideGui = true));
        SHOTS.add(new Shot("01_terminal", sp -> Menus.openTerminal(sp, BASE.offset(5, 0, 0)), () -> Minecraft.getInstance().options.hideGui = false));
        SHOTS.add(new Shot("02_terminal_craft", sp -> Menus.openTerminal(sp, BASE.offset(5, 0, 0)), ScreenshotCheck::openCraftDialog));
        SHOTS.add(new Shot("03_controller", sp -> Menus.openController(sp, BASE), () -> {}));
        SHOTS.add(new Shot("04_drive", sp -> Menus.openDrive(sp, BASE.offset(1, 0, 0)), () -> {}));
        SHOTS.add(new Shot("05_assembler", sp -> Menus.openAssembler(sp, BASE.offset(2, 0, 0)), () -> {}));
        SHOTS.add(new Shot("06_stock_keeper", sp -> Menus.openStockKeeper(sp, BASE.offset(0, 1, 0)), () -> {}));
        SHOTS.add(new Shot("07_infuser", sp -> Menus.openInfuser(sp, BASE.offset(0, 0, -2)), () -> {}));
        SHOTS.add(new Shot("08_processing", sp -> Menus.openProcessing(sp, BASE.offset(0, 0, -1)), () -> {}));
        SHOTS.add(new Shot("09_void_filter", sp -> Menus.openFilter(sp, BASE.offset(0, -1, 0)), () -> {}));
        SHOTS.add(new Shot("10_interface", sp -> Menus.openFilter(sp, BASE.offset(-1, 0, 0)), () -> {}));
        SHOTS.add(new Shot("11_recipe_card", sp -> {
            AssemblerBlockEntity a = (AssemblerBlockEntity) sp.serverLevel().getBlockEntity(BASE.offset(2, 0, 0));
            sp.setItemInHand(InteractionHand.MAIN_HAND, a.cardHandler().getStackInSlot(1).copy());
            Menus.openRecipeCard(sp, InteractionHand.MAIN_HAND);
        }, () -> {}));
        SHOTS.add(new Shot("12_processing_card", sp -> {
            ProcessingInterfaceBlockEntity p = (ProcessingInterfaceBlockEntity) sp.serverLevel().getBlockEntity(BASE.offset(0, 0, -1));
            sp.setItemInHand(InteractionHand.MAIN_HAND, p.cardHandler().getStackInSlot(0).copy());
            Menus.openProcessingCard(sp, InteractionHand.MAIN_HAND);
        }, () -> {}));
    }

    private static void openCraftDialog() {
        if (Minecraft.getInstance().screen instanceof TerminalScreen ts && ts.getMenu() instanceof TerminalMenu m) {
            for (TerminalMenu.Entry e : m.clientEntries) {
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

    private static void hoverFirstSlot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s) {
            double scale = mc.getWindow().getGuiScale();
            double x = (s.getGuiLeft() + 53 + 8) * scale;
            double y = (s.getGuiTop() + 26 + 8) * scale;
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), x, y);
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
            });
        }
        if (tick < 120) {
            return;
        }
        int local = (tick - 120) % 50;
        if (local == 0) {
            index++;
            if (index >= SHOTS.size()) {
                GrimStorage.LOGGER.info("[Shots] done");
                mc.stop();
                return;
            }
            Shot shot = SHOTS.get(index);
            if (mc.screen != null) {
                mc.player.closeContainer();
            }
            server.execute(() -> shot.open().accept(server.getPlayerList().getPlayers().get(0)));
        } else if (local == 25 && index < SHOTS.size()) {
            SHOTS.get(index).clientExtra().run();
        } else if (local == 40 && index < SHOTS.size()) {
            String name = SHOTS.get(index).name();
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> GrimStorage.LOGGER.info("[Shots] {}", msg.getString()));
        }
    }
}
