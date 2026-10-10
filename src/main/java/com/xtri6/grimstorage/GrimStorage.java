package com.xtri6.grimstorage;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(GrimStorage.MODID)
public final class GrimStorage {
    public static final String MODID = "grimstorage";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GrimStorage(IEventBus modBus, ModContainer container) {
        Registration.register(modBus);

        // Dev-only checks; the gametest package is stripped from the release jar.
        if (Boolean.getBoolean("grimstorage.orecheck")) {
            try {
                Class.forName("com.xtri6.grimstorage.gametest.OreGenCheck").getMethod("register", IEventBus.class).invoke(null, modBus);
            } catch (ReflectiveOperationException ignored) {
                // not present in release builds
            }
        }
    }
}
