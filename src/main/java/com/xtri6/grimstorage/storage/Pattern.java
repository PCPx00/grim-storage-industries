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
    /** Fluids and chemicals the machine also needs (learned machine recipes only). */
    private Map<BulkKey, Long> bulkInputs = Map.of();
    /** Fluids and chemicals the machine makes (learned machine recipes only). */
    private Map<BulkKey, Long> bulkOutputs = Map.of();
    /**
     * Other items the recipe takes in place of each input ("any copper ingot": the copper of every mod). The planner
     * uses whichever is in storage.
     */
    private Map<ItemKey, List<ItemKey>> alternatives = Map.of();
    /** For a pattern made by swapping inputs for alternatives: the pattern it came from, and what was swapped. */
    @Nullable
    private Pattern base;
    private Map<ItemKey, ItemKey> swaps = Map.of();

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
        Pattern p = new Pattern(Kind.CRAFTING, in, outs, List.copyOf(grid), found.get(), null, false);
        p.alternatives = craftingAlternatives(found.get().value(), in.keySet());
        return p;
    }

    /** Most stand-ins kept for one ingredient. */
    private static final int MAX_ALTERNATIVES = 32;

    /** For each card item, the other items the recipe's ingredient in that spot takes. */
    private static Map<ItemKey, List<ItemKey>> craftingAlternatives(CraftingRecipe recipe, java.util.Set<ItemKey> inputs) {
        Map<ItemKey, List<ItemKey>> out = new LinkedHashMap<>();
        for (ItemKey k : inputs) {
            for (net.minecraft.world.item.crafting.Ingredient ing : recipe.getIngredients()) {
                if (ing.isEmpty() || !ing.test(k.stack())) {
                    continue;
                }
                List<ItemKey> alts = new ArrayList<>();
                for (ItemStack s : ing.getItems()) {
                    ItemKey a = ItemKey.of(s);
                    if (a != null && !a.equals(k) && !alts.contains(a)) {
                        alts.add(a);
                        if (alts.size() >= MAX_ALTERNATIVES) {
                            break;
                        }
                    }
                }
                if (!alts.isEmpty()) {
                    out.put(k, List.copyOf(alts));
                }
                break;
            }
        }
        return out.isEmpty() ? Map.of() : out;
    }

    /** Most variants one card turns into ("any planks" is up to this many patterns). */
    private static final int MAX_VARIANTS = 24;

    /**
     * A Recipe Card and, unless it is set to exact items, the same recipe with other items the recipe also accepts
     * (birch planks where the card shows oak). The card's own items come first. Each variant is a pattern of its
     * own, so the planner simply uses whichever one you have the items for.
     */
    public static List<Pattern> craftingVariants(Level level, ItemStack card) {
        Pattern base = crafting(level, card);
        if (base == null) {
            return List.of();
        }
        List<Pattern> out = new ArrayList<>();
        out.add(base);
        if (Boolean.TRUE.equals(card.get(Registration.RECIPE_EXACT.get()))) {
            // exactly the card's items: no stand-ins either
            base.alternatives = Map.of();
            return out;
        }
        CraftingRecipe recipe = base.recipe.value();
        // the card item with the most stand-ins is the one to swap (all of its slots at once, so the recipe still matches)
        ItemKey swap = null;
        List<ItemStack> options = List.of();
        for (ItemKey k : base.inputs.keySet()) {
            for (net.minecraft.world.item.crafting.Ingredient ing : recipe.getIngredients()) {
                if (!ing.isEmpty() && ing.test(k.stack())) {
                    ItemStack[] items = ing.getItems();
                    if (items.length > options.size()) {
                        swap = k;
                        options = java.util.Arrays.asList(items);
                    }
                    break;
                }
            }
        }
        if (swap == null || options.size() <= 1) {
            return out;
        }
        java.util.Set<ItemKey> seen = new java.util.HashSet<>();
        seen.add(swap);
        for (ItemStack opt : options) {
            ItemKey ok = ItemKey.of(opt);
            if (ok == null || !seen.add(ok)) {
                continue;
            }
            List<ItemStack> grid = new ArrayList<>(9);
            for (ItemStack s : base.grid) {
                grid.add(!s.isEmpty() && swap.equals(ItemKey.of(s)) ? ok.toStack(1) : s.copy());
            }
            CraftingInput input = CraftingInput.of(3, 3, grid);
            if (!recipe.matches(input, level)) {
                continue;
            }
            ItemStack result = recipe.assemble(input, level.registryAccess());
            if (!result.is(base.primary().item())) {
                continue;
            }
            Map<ItemKey, Long> in = new LinkedHashMap<>();
            for (ItemStack s : grid) {
                ItemKey k = ItemKey.of(s);
                if (k != null) {
                    in.merge(k, 1L, Long::sum);
                }
            }
            Map<ItemKey, Long> outs = new LinkedHashMap<>();
            outs.put(base.primary(), (long) result.getCount());
            Pattern v = new Pattern(Kind.CRAFTING, in, outs, List.copyOf(grid), base.recipe, null, false);
            v.alternatives = craftingAlternatives(recipe, in.keySet());
            out.add(v);
            if (out.size() >= MAX_VARIANTS) {
                break;
            }
        }
        return out;
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

    /** A machine recipe a Machine Bus learned, with any number of inputs. */
    public static Pattern learned(Map<ItemKey, Long> inputs, ItemKey output, long outCount, MachineLink machine) {
        return learned(inputs, output, outCount, machine, Map.of());
    }

    /** The same, also needing fluids or chemicals the network feeds the machine. */
    public static Pattern learned(Map<ItemKey, Long> inputs, ItemKey output, long outCount, MachineLink machine, Map<BulkKey, Long> bulk) {
        Map<ItemKey, Long> outs = new LinkedHashMap<>();
        outs.put(output, outCount);
        return learned(inputs, outs, bulk, Map.of(), machine);
    }

    /**
     * Any machine recipe: items, fluids and chemicals in, items, fluids and chemicals out (a Chemical Washer takes
     * a slurry and water and makes another slurry, no items at all). Needs at least one output.
     */
    public static Pattern learned(Map<ItemKey, Long> inputs, Map<ItemKey, Long> outputs, Map<BulkKey, Long> bulkIn,
                                  Map<BulkKey, Long> bulkOut, MachineLink machine) {
        Pattern p = new Pattern(Kind.PROCESSING, new LinkedHashMap<>(inputs), new LinkedHashMap<>(outputs), List.of(), null, machine, true);
        p.bulkInputs = bulkIn.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(bulkIn));
        p.bulkOutputs = bulkOut.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(bulkOut));
        return p;
    }

    public Map<BulkKey, Long> bulkInputs() {
        return bulkInputs;
    }

    /** Sets the stand-ins for each input (learned machine recipes: the other choices of each ingredient). */
    public Pattern withAlternatives(Map<ItemKey, List<ItemKey>> alts) {
        Map<ItemKey, List<ItemKey>> clean = new LinkedHashMap<>();
        alts.forEach((k, list) -> {
            List<ItemKey> l = new ArrayList<>();
            for (ItemKey a : list) {
                if (a != null && !a.equals(k) && !outputs.containsKey(a) && !l.contains(a) && l.size() < MAX_ALTERNATIVES) {
                    l.add(a);
                }
            }
            if (!l.isEmpty()) {
                clean.put(k, List.copyOf(l));
            }
        });
        alternatives = clean.isEmpty() ? Map.of() : clean;
        return this;
    }

    public Map<ItemKey, List<ItemKey>> alternatives() {
        return alternatives;
    }

    /** The pattern this one was made from by swapping inputs (itself if it wasn't). */
    public Pattern root() {
        return base == null ? this : base;
    }

    /** Inputs swapped for alternatives: original to used. Empty unless {@link #base} is set. */
    public Map<ItemKey, ItemKey> swaps() {
        return swaps;
    }

    /**
     * The same recipe with some inputs swapped for their alternatives (birch planks where the card has oak). Runs on
     * the same machines. Null if a crafting recipe doesn't take the swap after all.
     */
    @Nullable
    public Pattern substitute(Map<ItemKey, ItemKey> swap, @Nullable Level level) {
        Pattern root = root();
        Map<ItemKey, ItemKey> all = new LinkedHashMap<>(swaps);
        all.putAll(swap);
        Map<ItemKey, Long> in = new LinkedHashMap<>();
        root.inputs.forEach((k, c) -> in.merge(all.getOrDefault(k, k), c, Long::sum));
        List<ItemStack> grid = root.grid;
        if (kind == Kind.CRAFTING) {
            if (root.recipe == null || level == null) {
                return null;
            }
            List<ItemStack> g = new ArrayList<>(9);
            for (ItemStack s : root.grid) {
                ItemKey k = ItemKey.of(s);
                g.add(k != null && all.containsKey(k) ? all.get(k).toStack(1) : s.copy());
            }
            CraftingInput input = CraftingInput.of(3, 3, g);
            if (!root.recipe.value().matches(input, level)) {
                return null;
            }
            grid = List.copyOf(g);
        }
        Pattern p = new Pattern(kind, in, new LinkedHashMap<>(root.outputs), grid, root.recipe, null, root.learned);
        p.bulkInputs = root.bulkInputs;
        p.bulkOutputs = root.bulkOutputs;
        p.base = root;
        p.swaps = Collections.unmodifiableMap(all);
        return p;
    }

    public Map<BulkKey, Long> bulkOutputs() {
        return bulkOutputs;
    }

    /** True when only fluids or chemicals go in: the machine is filled straight from storage, many runs at once. */
    public boolean bulkOnlyInputs() {
        return inputs.isEmpty() && !bulkInputs.isEmpty();
    }

    /**
     * The main thing it makes: its first item, or (for a recipe that only makes fluids or chemicals) its first
     * fluid or chemical. An {@link ItemKey} or a {@link BulkKey}.
     */
    public Object primaryKey() {
        return outputs.isEmpty() ? bulkOutputs.keySet().iterator().next() : outputs.keySet().iterator().next();
    }

    /** How much of the main thing one run makes. */
    public long primaryAmount() {
        return Math.max(1, amountOf(primaryKey()));
    }

    /** How much one run makes of an item or a fluid / chemical. */
    public long amountOf(Object key) {
        return key instanceof BulkKey b ? bulkOutputs.getOrDefault(b, 0L) : key instanceof ItemKey k ? outputs.getOrDefault(k, 0L) : 0L;
    }

    /** An item to show for the main output (a bucket for a fluid, a cell for a chemical). */
    public ItemStack icon() {
        return iconOf(primaryKey());
    }

    public static ItemStack iconOf(Object key) {
        if (key instanceof ItemKey k) {
            return k.toStack(1);
        }
        BulkKey b = (BulkKey) key;
        if (b.kind() == BulkKind.FLUID) {
            ItemStack bucket = net.neoforged.neoforge.fluids.FluidUtil.getFilledBucket(b.fluid(1000));
            return bucket.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.BUCKET) : bucket;
        }
        var cells = Registration.BULK_CELLS.get(b.kind());
        return cells == null ? new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE) : new ItemStack(cells.get(CellTier.COPPER).get());
    }

    /** A name for the main output that also says it is a fluid or chemical. */
    public static net.minecraft.network.chat.Component nameOf(Object key) {
        return key instanceof ItemKey k ? k.stack().getHoverName() : ((BulkKey) key).name();
    }

    public boolean learned() {
        return learned;
    }

    /** Same kind, same ingredients, same results: two machines offering this can share the work. */
    public boolean sameRecipe(Pattern o) {
        return kind == o.kind && inputs.equals(o.inputs) && outputs.equals(o.outputs) && bulkInputs.equals(o.bulkInputs)
                && bulkOutputs.equals(o.bulkOutputs);
    }

    public void addMachines(Pattern o) {
        for (MachineLink m : o.machines) {
            if (!machines.contains(m)) {
                machines.add(m);
            }
        }
    }

    public List<MachineLink> machines() {
        return base != null ? base.machines() : machines;
    }

    /** The machines to try, starting after the one that was used last, so the work is spread out. */
    public List<MachineLink> machinesInTurn() {
        if (base != null) {
            return base.machinesInTurn();
        }
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
        if (base != null) {
            base.usedMachine(m);
            return;
        }
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

    /** The first item it makes (crafting patterns and processing cards always make one; see {@link #primaryKey()}). */
    public ItemKey primary() {
        return outputs.isEmpty() ? null : outputs.keySet().iterator().next();
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
        List<MachineLink> ms = machines();
        return ms.isEmpty() ? null : ms.get(0);
    }

    public CraftingInput craftingInput() {
        List<ItemStack> copy = new ArrayList<>(9);
        for (ItemStack s : grid) {
            copy.add(s.copy());
        }
        return CraftingInput.of(3, 3, copy);
    }
}
