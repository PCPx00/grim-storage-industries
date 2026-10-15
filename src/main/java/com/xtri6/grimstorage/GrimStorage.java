package com.xtri6.grimstorage;

import com.mojang.logging.LogUtils;
import com.xtri6.grimstorage.compat.Compat;
import com.xtri6.grimstorage.item.GuideBookItem;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.storage.NetworkManager;

import org.slf4j.Logger;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod(GrimStorage.MODID)
public final class GrimStorage {
    public static final String MODID = "grimstorage";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GrimStorage(IEventBus modBus, ModContainer container) {
        Registration.register(modBus);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.COMMON, ServerConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, BalanceConfig.SPEC);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> GuideBookItem.giveIfFirstJoin(e.getEntity()));
        modBus.addListener(Payloads::register);
        modBus.addListener(GrimStorage::registerCapabilities);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> NetworkManager.clearAll());
        NeoForge.EVENT_BUS.addListener(com.xtri6.grimstorage.storage.NetworkAccess::onBreak);
        NeoForge.EVENT_BUS.addListener(com.xtri6.grimstorage.command.DebugCommand::register);
        NeoForge.EVENT_BUS.addListener(com.xtri6.grimstorage.storage.NetworkAccess::onPlace);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.AddReloadListenerEvent e) ->
                e.addListener(com.xtri6.grimstorage.storage.MachineRecipes.INSTANCE));

        if (FMLEnvironment.dist.isClient()) {
            com.xtri6.grimstorage.client.GrimStorageClient.init(modBus, container);
        }

        // Dev-only checks; the gametest package is stripped from the release jar.
        for (String check : new String[] {"orecheck:OreGenCheck", "systemcheck:SystemCheck", "shots:ScreenshotCheck"}) {
            String[] parts = check.split(":");
            if (Boolean.getBoolean("grimstorage." + parts[0])) {
                try {
                    Class.forName("com.xtri6.grimstorage.gametest." + parts[1]).getMethod("register", IEventBus.class).invoke(null, modBus);
                } catch (ReflectiveOperationException ignored) {
                    // not present in release builds
                }
            }
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, Registration.INTERFACE_ENTITY.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, Registration.SOUL_INFUSER_ENTITY.get(), (be, side) -> be.automation());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, Registration.INTERFACE_ENTITY.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, Registration.INTERFACE_ENTITY.get(), (be, side) -> be.energyStorage());
        // Import / Export Buses take any mod's pipes on every side.
        for (var type : java.util.List.of(Registration.IMPORT_BUS_ENTITY.get(), Registration.EXPORT_BUS_ENTITY.get())) {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.pipeItems());
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, (be, side) -> be.pipeFluids());
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> be.pipeEnergy());
            if (Compat.chemicals() != null) {
                Compat.chemicals().registerCapabilities(event, type, (be, side) -> be);
            }
            if (Compat.source() != null) {
                Compat.source().registerCapabilities(event, type, (be, side) -> be);
            }
        }
        // Buses on Soul Cable: each one takes pipes on the side it faces.
        var cable = Registration.CABLE_BUS_ENTITY.get();
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, cable, (be, side) -> be.part(side) == null ? null : be.part(side).pipeItems());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, cable, (be, side) -> be.part(side) == null ? null : be.part(side).pipeFluids());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, cable, (be, side) -> be.part(side) == null ? null : be.part(side).pipeEnergy());
        if (Compat.chemicals() != null) {
            Compat.chemicals().registerCapabilities(event, cable, (be, side) -> be.part(side));
            Compat.chemicals().registerCapabilities(event, Registration.INTERFACE_ENTITY.get(), (be, side) -> be);
        }
        if (Compat.source() != null) {
            Compat.source().registerCapabilities(event, cable, (be, side) -> be.part(side));
            Compat.source().registerCapabilities(event, Registration.INTERFACE_ENTITY.get(), (be, side) -> be);
        }
    }
}
