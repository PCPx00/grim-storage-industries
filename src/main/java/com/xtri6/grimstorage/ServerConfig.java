package com.xtri6.grimstorage;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Gameplay settings shared by server and client (config/grimstorage-common.toml). */
public final class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue GIVE_GUIDE = BUILDER
            .comment("Give each player a Grim Storage Guide the first time they join a world.")
            .define("giveGuideOnFirstJoin", true);

    public static final ModConfigSpec.IntValue MAX_JOBS = BUILDER
            .comment("Most crafting jobs one network runs at the same time.")
            .defineInRange("maxJobsPerNetwork", 32, 1, 256);

    public static final ModConfigSpec.IntValue MAX_BLOCKS = BUILDER
            .comment("Largest number of blocks one network can join together.",
                    "A network bigger than this is only partly connected, and the Network Controller shows a warning.")
            .defineInRange("maxNetworkBlocks", 16_384, 256, 1_048_576);

    public static final ModConfigSpec.IntValue MAX_JOBS_PER_PLAYER = BUILDER
            .comment("Most crafting jobs one player can have running on a network at the same time, so one big order",
                    "can't take every slot on a shared network. Set it to the same as maxJobsPerNetwork for no limit.")
            .defineInRange("maxJobsPerPlayer", 16, 1, 256);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ServerConfig() {}

    public static int maxJobs() {
        return SPEC.isLoaded() ? MAX_JOBS.get() : 32;
    }

    public static int maxJobsPerPlayer() {
        return SPEC.isLoaded() ? MAX_JOBS_PER_PLAYER.get() : 16;
    }

    public static int maxBlocks() {
        return SPEC.isLoaded() ? MAX_BLOCKS.get() : 16_384;
    }

    public static boolean giveGuide() {
        return !SPEC.isLoaded() || GIVE_GUIDE.get();
    }
}
