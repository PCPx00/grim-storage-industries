package com.xtri6.grimstorage.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.item.CardData;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * One way the network knows to make something: a crafting recipe card, a machine Processing Card, or a recipe a
 * Machine Bus learned from the machine it faces. A machine recipe can be backed by several machines at once; the
 * network spreads the work across all of them.
 */
public final class Pattern {
    public enum Kind { CRAFTING, PROCESSING }

    private final Kind kind;
    private final Map<ItemKey, Long> inputs;
    private final Map<ItemKey, Long> outputs;
    private final List<ItemStack> grid;
    @Nullable
    private final RecipeHolder<CraftingRecipe> recipe;
    /** Machines that can run this recipe (processing only). */
    private final List<MachineLink> machines = new ArrayList<>();
    private int nextMachine;
    /** Learned from a machine rather than written on a card. */
    private final boolean learned;

    private Pattern(Kind kind, Map<ItemKey, Long> inputs, Map<ItemKey, Long> outputs, List<ItemStack> grid,
                    @Nullable RecipeHolder<CraftingRecipe> recipe, @Nullable MachineLink processor, boolean learned) {
        this.kind = kind;
        this.inputs = Collections.unmodifiableMap(inputs);
        this.outputs = Collections.unmodifiableMap(outputs);
        this.grid = grid;
        this.recipe = recipe;
        if (processor != null) {
            machines.add(processor);
        }
        this.learned = learned;
    }

    @Nullable
    public static Pattern crafting(Level level, ItemStack card) {
        CardData.Recipe data = card.get(Registration.RECIPE_CARD_DATA.get());
        if (data == null || !data.valid()) {
            return null;
        }
        List<ItemStack> grid = new ArrayList<>(9);
        for (ItemStack s : data.grid()) {
            grid.add(s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1));
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        Optional<RecipeHolder<CraftingRecipe>> found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (found.isEmpty()) {
            return null;
        }
        ItemStack result = found.get().value().assemble(input, level.registryAccess());
        ItemKey out = ItemKey.of(result);
        if (out == null) {
            return null;
        }
        Map<ItemKey, Long> in = new LinkedHashMap<>();
        for (ItemStack s : grid) {
            ItemKey k = ItemKey.of(s);
            if (k != null) {
                in.merge(k, 1L, Long::sum);
            }
        }
        Map<ItemKey, Long> outs = new LinkedHashMap<>();
        outs.put(out, (long) result.getCount());
        return new Pattern(Kind.CRAFTING, in, outs, List.copyOf(grid), found.get(), null, false);
    }

    @Nullable
    public static Pattern processing(ItemStack card, MachineLink processor) {
        CardData.Processing data = card.get(Registration.PROCESSING_CARD_DATA.get());
        if (data == null || !data.valid()) {
            return null;
        }
        Map<ItemKey, Long> in = new LinkedHashMap<>();
        for (ItemStack s : data.inputs()) {
            ItemKey k = ItemKey.of(s);
            if (k != null) {
                in.merge(k, (long) s.getCount(), Long::sum);
            }
        }
        Map<ItemKey, Long> outs = new LinkedHashMap<>();
        for (ItemStack s : data.outputs()) {
            ItemKey k = ItemKey.of(s);
            if (k != null) {
                outs.merge(k, (long) s.getCount(), Long::sum);
            }
        }
        if (in.isEmpty() || outs.isEmpty()) {
            return null;
        }
        return new Pattern(Kind.PROCESSING, in, outs, List.of(), null, processor, false);
    }

    /** A one-input, one-output machine recipe a Machine Bus learned (for example a furnace's smelting recipes). */
    public static Pattern learned(ItemKey input, long inCount, ItemKey output, long outCount, MachineLink machine) {
        Map<ItemKey, Long> in = new LinkedHashMap<>();
        in.put(input, inCount);
        Map<ItemKey, Long> outs = new LinkedHashMap<>();
        outs.put(output, outCount);
        return new Pattern(Kind.PROCESSING, in, outs, List.of(), null, machine, true);
    }

    public boolean learned() {
        return learned;
    }

    /** Same kind, same ingredients, same results: two machines offering this can share the work. */
    public boolean sameRecipe(Pattern o) {
        return kind == o.kind && inputs.equals(o.inputs) && outputs.equals(o.outputs);
    }

    public void addMachines(Pattern o) {
        for (MachineLink m : o.machines) {
            if (!machines.contains(m)) {
                machines.add(m);
            }
        }
    }

    public List<MachineLink> machines() {
        return machines;
    }

    /** The machines to try, starting after the one that was used last, so the work is spread out. */
    public List<MachineLink> machinesInTurn() {
        if (machines.size() <= 1) {
            return machines;
        }
        List<MachineLink> out = new ArrayList<>(machines.size());
        for (int i = 0; i < machines.size(); i++) {
            out.add(machines.get((nextMachine + i) % machines.size()));
        }
        return out;
    }

    public void usedMachine(MachineLink m) {
        int i = machines.indexOf(m);
        if (i >= 0) {
            nextMachine = (i + 1) % machines.size();
        }
    }

    public Kind kind() {
        return kind;
    }

    public Map<ItemKey, Long> inputs() {
        return inputs;
    }

    public Map<ItemKey, Long> outputs() {
        return outputs;
    }

    public ItemKey primary() {
        return outputs.keySet().iterator().next();
    }

    public long outputCount(ItemKey key) {
        return outputs.getOrDefault(key, 0L);
    }

    @Nullable
    public RecipeHolder<CraftingRecipe> recipe() {
        return recipe;
    }

    @Nullable
    public MachineLink processor() {
        return machines.isEmpty() ? null : machines.get(0);
    }

    public CraftingInput craftingInput() {
        List<ItemStack> copy = new ArrayList<>(9);
        for (ItemStack s : grid) {
            copy.add(s.copy());
        }
        return CraftingInput.of(3, 3, copy);
    }
}
