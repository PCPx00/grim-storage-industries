package com.xtri6.grimstorage.compat;

import com.xtri6.grimstorage.GrimStorage;

import mekanism.api.RelativeSide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Mekanism machines start with every side switched off. A Machine Bus clipped onto one opens the side it touches
 * for items (in and out), power, and chemicals and fluids going in, like a player would with a Configurator, so it works straight away. Sides a
 * player already set are left as they are. Mekanism keeps these settings out of its API, so this is done with
 * care through reflection and simply does nothing if Mekanism changes them.
 */
public final class MekanismSides {
    private MekanismSides() {}

    public static void open(Level level, BlockPos pos, Direction touched) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !be.getClass().getName().startsWith("mekanism.")) {
            return;
        }
        try {
            Object config = be.getClass().getMethod("getConfig").invoke(be);
            if (config == null) {
                return;
            }
            Direction front = (Direction) be.getClass().getMethod("getDirection").invoke(be);
            RelativeSide side = RelativeSide.fromDirections(front, touched);
            boolean changed = set(config, "ITEM", side, "INPUT_OUTPUT", "INPUT");
            changed |= set(config, "ENERGY", side, "INPUT");
            changed |= set(config, "CHEMICAL", side, "INPUT", "INPUT_1");
            changed |= set(config, "FLUID", side, "INPUT", "INPUT_1");
            // the rest of a chemical machine's tanks (a second input, the outputs) go on sides nobody uses yet, so
            // the bus can reach every tank through them; sides a player set are never touched
            // the slot that turns items into a chemical (a Metallurgic Infuser's infuse slot)
            changed |= spare(config, "ITEM", side, "EXTRA");
            for (String extra : new String[] {"INPUT_2", "OUTPUT", "OUTPUT_1", "OUTPUT_2"}) {
                changed |= spare(config, "CHEMICAL", side, extra);
                changed |= spare(config, "FLUID", side, extra);
            }
            if (changed) {
                be.setChanged();
                level.invalidateCapabilities(pos);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            GrimStorage.LOGGER.debug("Machine Bus could not open the side of {}", be, e);
        }
    }

    /** Puts one tank type on a side that is still off, unless some side already has it. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean spare(Object config, String transmission, RelativeSide touched, String wanted) throws ReflectiveOperationException {
        Class tt = Class.forName("mekanism.common.lib.transmitter.TransmissionType", true, config.getClass().getClassLoader());
        Object type = Enum.valueOf(tt, transmission);
        if (!(Boolean) config.getClass().getMethod("supports", tt).invoke(config, type)) {
            return false;
        }
        Object info = config.getClass().getMethod("getConfig", tt).invoke(config, type);
        if (info == null) {
            return false;
        }
        Class dt = Class.forName("mekanism.common.tile.component.config.DataType", true, config.getClass().getClassLoader());
        Object want = Enum.valueOf(dt, wanted);
        if (!(Boolean) info.getClass().getMethod("supports", dt).invoke(info, want)) {
            return false;
        }
        RelativeSide free = null;
        for (RelativeSide s : RelativeSide.values()) {
            Object now = info.getClass().getMethod("getDataType", RelativeSide.class).invoke(info, s);
            if (now == want) {
                return false;
            }
            if (free == null && s != touched && now != null && "NONE".equals(((Enum) now).name())) {
                free = s;
            }
        }
        if (free != null && (Boolean) info.getClass().getMethod("setDataType", dt, RelativeSide.class).invoke(info, want, free)) {
            config.getClass().getMethod("sideChanged", tt, RelativeSide.class).invoke(config, type, free);
            return true;
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean set(Object config, String transmission, RelativeSide side, String... wanted) throws ReflectiveOperationException {
        Class tt = Class.forName("mekanism.common.lib.transmitter.TransmissionType", true, config.getClass().getClassLoader());
        Object type = Enum.valueOf(tt, transmission);
        if (!(Boolean) config.getClass().getMethod("supports", tt).invoke(config, type)) {
            return false;
        }
        Object info = config.getClass().getMethod("getConfig", tt).invoke(config, type);
        if (info == null) {
            return false;
        }
        Class dt = Class.forName("mekanism.common.tile.component.config.DataType", true, config.getClass().getClassLoader());
        Object now = info.getClass().getMethod("getDataType", RelativeSide.class).invoke(info, side);
        if (now == null || !"NONE".equals(((Enum) now).name())) {
            return false;
        }
        for (String w : wanted) {
            Object want = Enum.valueOf(dt, w);
            if ((Boolean) info.getClass().getMethod("supports", dt).invoke(info, want)
                    && (Boolean) info.getClass().getMethod("setDataType", dt, RelativeSide.class).invoke(info, want, side)) {
                config.getClass().getMethod("sideChanged", tt, RelativeSide.class).invoke(config, type, side);
                return true;
            }
        }
        return false;
    }
}
