package com.xtri6.grimstorage.storage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;

/** Experience helpers: player XP in points, and liquid XP from other mods (any fluid tagged {@code c:experience}). */
public final class Experience {
    /** The usual conversion between liquid XP and points used by mods with an XP fluid. */
    public static final int MB_PER_POINT = 20;
    public static final BulkKey KEY = BulkKey.flat(BulkKind.EXPERIENCE);

    private Experience() {}

    public static boolean isXpFluid(FluidStack stack) {
        return !stack.isEmpty() && stack.is(Tags.Fluids.EXPERIENCE);
    }

    /** Every liquid XP fluid (source form) in this pack, so XP can be handed out as whichever one a tank takes. */
    public static java.util.List<Fluid> xpFluids() {
        java.util.List<Fluid> out = new java.util.ArrayList<>();
        for (net.minecraft.core.Holder<Fluid> h : BuiltInRegistries.FLUID.getTagOrEmpty(Tags.Fluids.EXPERIENCE)) {
            Fluid f = h.value();
            if (f.isSource(f.defaultFluidState()) || !(f instanceof net.minecraft.world.level.material.FlowingFluid)) {
                out.add(f);
            }
        }
        return out;
    }

    /** Points needed to go from level 0 to {@code level}. */
    public static long pointsForLevel(int level) {
        long l = level;
        if (level <= 16) {
            return l * l + 6 * l;
        }
        if (level <= 31) {
            return (long) (2.5 * l * l - 40.5 * l + 360);
        }
        return (long) (4.5 * l * l - 162.5 * l + 2220);
    }

    /** The player's total XP in points. */
    public static long points(ServerPlayer player) {
        return pointsForLevel(player.experienceLevel) + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    /** Sets the player's XP to an exact point total. */
    public static void setPoints(ServerPlayer player, long points) {
        points = Math.max(0, Math.min(points, Integer.MAX_VALUE));
        int level = 0;
        while (level < 21863 && pointsForLevel(level + 1) <= points) {
            level++;
        }
        player.setExperienceLevels(level);
        player.experienceProgress = 0;
        long rest = points - pointsForLevel(level);
        player.totalExperience = (int) points;
        player.setExperiencePoints((int) rest);
    }

    /** Points needed to reach the player's next level (and {@code levels - 1} more after it). */
    public static long pointsToGain(ServerPlayer player, int levels) {
        return pointsForLevel(player.experienceLevel + levels) - points(player);
    }
}
