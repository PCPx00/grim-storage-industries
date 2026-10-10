package com.xtri6.grimstorage.gametest;

import java.util.function.Predicate;

import com.mojang.datafixers.util.Pair;
import com.xtri6.grimstorage.GrimStorage;
import com.xtri6.grimstorage.Registration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Dev-only: generates real chunks in the three Soulite locations, counts the ore that actually spawned,
 * logs it and stops the server. Never ships (the gametest package is excluded from the jar).
 */
public final class OreGenCheck {
    private OreGenCheck() {}

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(OreGenCheck::onStarted);
    }

    private static void onStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        boolean ok = true;
        try {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            ok &= report("Overworld Soulite near spawn", count(overworld, spawn, 4,
                    s -> s.is(Registration.SOULITE_ORE.get()) || s.is(Registration.DEEPSLATE_SOULITE_ORE.get())));
            ok &= reportOther("Overworld Soul Sand ore (must be 0)", count(overworld, spawn, 4,
                    s -> s.is(Registration.SOUL_SAND_SOULITE_ORE.get())) == 0);

            ok &= biomeCheck(overworld, Biomes.DEEP_DARK, spawn, Registration.ECHO_SOULITE_ORE.get(), "Deep Dark Echo Soulite");

            ServerLevel nether = server.getLevel(Level.NETHER);
            ok &= biomeCheck(nether, Biomes.SOUL_SAND_VALLEY, new BlockPos(0, 64, 0), Registration.SOUL_SAND_SOULITE_ORE.get(),
                    "Soul Sand Valley Soulite");
        } catch (Throwable t) {
            GrimStorage.LOGGER.error("[OreGenCheck] crashed", t);
            ok = false;
        }
        GrimStorage.LOGGER.info("[OreGenCheck] RESULT: {}", ok ? "PASS" : "FAIL");
        server.halt(false);
    }

    private static boolean biomeCheck(ServerLevel level, ResourceKey<Biome> biome, BlockPos from, Block ore, String label) {
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(h -> h.is(biome), from, 6400, 32, 64);
        if (found == null) {
            GrimStorage.LOGGER.warn("[OreGenCheck] {}: biome not found within range", label);
            return false;
        }
        GrimStorage.LOGGER.info("[OreGenCheck] {}: biome found at {}", label, found.getFirst());
        return report(label, count(level, found.getFirst(), 3, s -> s.is(ore)));
    }

    private static boolean report(String label, int n) {
        GrimStorage.LOGGER.info("[OreGenCheck] {}: {} blocks", label, n);
        return n > 0;
    }

    private static boolean reportOther(String label, boolean pass) {
        GrimStorage.LOGGER.info("[OreGenCheck] {}: {}", label, pass ? "ok" : "WRONG");
        return pass;
    }

    private static int count(ServerLevel level, BlockPos center, int radius, Predicate<BlockState> match) {
        int cx = center.getX() >> 4, cz = center.getZ() >> 4, total = 0;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                LevelChunk chunk = level.getChunk(x, z);
                for (LevelChunkSection section : chunk.getSections()) {
                    if (section.hasOnlyAir() || !section.maybeHas(match)) {
                        continue;
                    }
                    for (int i = 0; i < 4096; i++) {
                        if (match.test(section.getBlockState(i & 15, (i >> 8) & 15, (i >> 4) & 15))) {
                            total++;
                        }
                    }
                }
            }
        }
        return total;
    }
}
