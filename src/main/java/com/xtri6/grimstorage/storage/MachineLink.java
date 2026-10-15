package com.xtri6.grimstorage.storage;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A block that lets the network run a machine as a crafting step: the Processing Interface and the Machine Bus.
 * The network sends a recipe's inputs into {@link #machineInput()} and the link hands finished outputs back.
 */
public interface MachineLink {
    /** Where a recipe's ingredients go, or null if no machine is there. */
    @Nullable
    IItemHandler machineInput();

    /** Moves finished outputs from the machine into the network (and claims them for waiting jobs). */
    void pullOutputs(StorageNetwork network);

    /** Processing Cards in this link. */
    List<ItemStack> cards();

    /** Recipes the link worked out by itself from the machine it faces (none by default). */
    default List<Pattern> learnedPatterns() {
        return List.of();
    }

    boolean linkRemoved();

    /** How many recipes it learned from its machine last time (for debug reports). */
    default int learnedCount() {
        return 0;
    }

    /** A recipe that also needs fluids or chemicals was just sent here: keep the machine supplied with them. */
    default void onSent(Pattern pattern) {}

    /**
     * Fills the machine with up to {@code amount} of a fluid or chemical from the network, for a recipe that only
     * takes those. With {@code simulate}, how much it would take. Returns how much (none by default).
     */
    default long fillBulk(StorageNetwork network, BulkKey key, long amount, boolean simulate) {
        return 0;
    }

    /** The machine this link runs, for the terminal's crafting tab (null if nothing is there). */
    @Nullable
    default net.minecraft.network.chat.Component machineName() {
        return null;
    }
}
