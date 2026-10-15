package com.xtri6.grimstorage.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.net.Payloads;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/** Client-only setup: screens and the "dump inventory into the network" key. */
public final class GrimStorageClient {
    public static final KeyMapping DUMP_KEY = new KeyMapping("key.grimstorage.dump", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.grimstorage");

    public static final KeyMapping WIRELESS_KEY = new KeyMapping("key.grimstorage.wireless", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.grimstorage");

    private GrimStorageClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(GrimStorageClient::screens);
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(DUMP_KEY);
            e.register(WIRELESS_KEY);
        });
        NeoForge.EVENT_BUS.addListener(GrimStorageClient::tick);
    }

    private static void screens(RegisterMenuScreensEvent e) {
        e.register(Registration.TERMINAL_MENU.get(), TerminalScreen::new);
        e.register(Registration.CONTROLLER_MENU.get(), ControllerScreen::new);
        e.register(Registration.DRIVE_MENU.get(), SimpleScreens.Drive::new);
        e.register(Registration.ASSEMBLER_MENU.get(), SimpleScreens.Assembler::new);
        e.register(Registration.PROCESSING_MENU.get(), SimpleScreens.Processing::new);
        e.register(Registration.FILTER_MENU.get(), SimpleScreens.Filter::new);
        e.register(Registration.BUS_MENU.get(), BusScreen::new);
        e.register(Registration.MACHINE_BUS_MENU.get(), MachineBusScreen::new);
        e.register(Registration.CHEST_LINK_MENU.get(), ChestLinkScreen::new);
        e.register(Registration.EMITTER_MENU.get(), EmitterScreen::new);
        e.register(Registration.STOCK_KEEPER_MENU.get(), StockKeeperScreen::new);
        e.register(Registration.INFUSER_MENU.get(), SimpleScreens.Infuser::new);
        e.register(Registration.RECIPE_CARD_MENU.get(), SimpleScreens.RecipeCard::new);
        e.register(Registration.PROCESSING_CARD_MENU.get(), SimpleScreens.ProcessingCard::new);
    }

    private static void tick(ClientTickEvent.Post e) {
        while (DUMP_KEY.consumeClick()) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null) {
                PacketDistributor.sendToServer(new Payloads.DumpInventory());
            }
        }
        while (WIRELESS_KEY.consumeClick()) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null) {
                PacketDistributor.sendToServer(new Payloads.OpenWireless());
            }
        }
    }
}
