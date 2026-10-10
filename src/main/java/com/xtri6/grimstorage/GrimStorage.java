package com.xtri6.grimstorage;

import com.mojang.logging.LogUtils;
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
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@Mod(GrimStorage.MODID)
public final class GrimStorage {
    public static final String MODID = "grimstorage";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GrimStorage(IEventBus modBus, ModContainer container) {
        Registration.register(modBus);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modBus.addListener(Payloads::register);
        modBus.addListener(GrimStorage::registerCapabilities);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> NetworkManager.clearAll());

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
    }
}
