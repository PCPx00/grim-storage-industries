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

    public enum TerminalSize {
        /** As many rows as fit on the screen. */
        AUTO(0),
        NORMAL(6),
        TALL(9),
        TALLER(12);

        public final int rows;

        TerminalSize(int rows) {
            this.rows = rows;
        }
    }

    public static final ModConfigSpec.BooleanValue JEI_SYNC = BUILDER
            .comment("Soul Terminal: share one search with JEI. Typing in either one searches both.")
            .define("terminalJeiSync", true);

    public static final ModConfigSpec.BooleanValue FOCUS_SEARCH = BUILDER
            .comment("Soul Terminal: put the cursor in the search box as soon as the terminal opens.")
            .define("terminalFocusSearch", false);

    public static final ModConfigSpec.BooleanValue KEEP_SEARCH = BUILDER
            .comment("Soul Terminal: keep what you searched for after closing the terminal.")
            .define("terminalKeepSearch", false);

    public static final ModConfigSpec.EnumValue<TerminalSize> TERMINAL_SIZE = BUILDER
            .comment("Soul Terminal height: AUTO fits the screen, NORMAL is 6 rows, TALL 9, TALLER 12.")
            .defineEnum("terminalSize", TerminalSize.AUTO);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}

    public static boolean dark() {
        return !SPEC.isLoaded() || DARK_MODE.get();
    }

    public static int opacity() {
        return SPEC.isLoaded() ? OPACITY.get() : 88;
    }

    public static boolean jeiSync() {
        return !SPEC.isLoaded() || JEI_SYNC.get();
    }

    public static boolean focusSearch() {
        return SPEC.isLoaded() && FOCUS_SEARCH.get();
    }

    public static boolean keepSearch() {
        return SPEC.isLoaded() && KEEP_SEARCH.get();
    }

    public static TerminalSize terminalSize() {
        return SPEC.isLoaded() ? TERMINAL_SIZE.get() : TerminalSize.AUTO;
    }

    public static void set(ModConfigSpec.BooleanValue value, boolean on) {
        if (SPEC.isLoaded()) {
            value.set(on);
            value.save();
        }
    }

    public static void setTerminalSize(TerminalSize size) {
        if (SPEC.isLoaded()) {
            TERMINAL_SIZE.set(size);
            TERMINAL_SIZE.save();
        }
    }

    public static void setDark(boolean dark) {
        if (SPEC.isLoaded()) {
            DARK_MODE.set(dark);
            DARK_MODE.save();
        }
    }
}
