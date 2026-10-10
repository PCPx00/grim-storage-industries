package com.xtri6.grimstorage;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Gameplay settings shared by server and client (config/grimstorage-common.toml). */
public final class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue GIVE_GUIDE = BUILDER
            .comment("Give each player a Grim Storage Guide the first time they join a world.")
            .define("giveGuideOnFirstJoin", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ServerConfig() {}

    public static boolean giveGuide() {
        return !SPEC.isLoaded() || GIVE_GUIDE.get();
    }
}
