package com.xtri6.grimstorage;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only settings (config/grimstorage-client.toml). Also editable from the mod list's Config button. */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DARK_MODE = BUILDER
            .comment("Use the dark theme for Grim Storage screens. Toggle it with the sun/moon button in any screen.")
            .define("darkMode", true);

    public static final ModConfigSpec.IntValue OPACITY = BUILDER
            .comment("How solid the screen panels are, in percent. Lower lets more of the world show through.")
            .defineInRange("panelOpacity", 88, 40, 100);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}

    public static boolean dark() {
        return !SPEC.isLoaded() || DARK_MODE.get();
    }

    public static int opacity() {
        return SPEC.isLoaded() ? OPACITY.get() : 88;
    }

    public static void setDark(boolean dark) {
        if (SPEC.isLoaded()) {
            DARK_MODE.set(dark);
            DARK_MODE.save();
        }
    }
}
