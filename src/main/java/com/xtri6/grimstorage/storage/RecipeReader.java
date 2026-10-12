package com.xtri6.grimstorage.storage;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.compat.MekanismRecipeReader;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * Reads what any mod's machine recipe takes and makes, so a Machine Bus can learn it. Uses the standard recipe
 * methods most mods fill in, Mekanism's own recipe API, and a careful look for the usual getInput / getOutput
 * methods. Fluids a recipe needs are read too (and chemicals, for Mekanism); the Machine Bus feeds them from the
 * network. Recipes that need something else it can't give are skipped.
 */
public final class RecipeReader {
    /** One ingredient: any of {@code options}, {@code count} of them. */
    public record Input(List<ItemKey> options, long count) {}

    /**
     * A machine recipe: these inputs (and fluids / chemicals / Source) make {@code outCount} of {@code output}, and
     * the fluids or chemicals in {@code bulkOut}. {@code output} is null for a recipe that only makes fluids or
     * chemicals; {@code inputs} is empty for one that only takes them.
     */
    public record Read(List<Input> inputs, @Nullable ItemKey output, long outCount, Map<BulkKey, Long> bulk, Map<BulkKey, Long> bulkOut) {
        public Read(List<Input> inputs, ItemKey output, long outCount) {
            this(inputs, output, outCount, Map.of(), Map.of());
        }

        public Read(List<Input> inputs, ItemKey output, long outCount, Map<BulkKey, Long> bulk) {
            this(inputs, output, outCount, bulk, Map.of());
        }

        /** It makes something and needs something. */
        public boolean usable() {
            return (output != null || !bulkOut.isEmpty()) && (!inputs.isEmpty() || !bulk.isEmpty());
        }
    }

    private static final int MAX_OPTIONS = 16;
    private static final boolean MEKANISM = ModList.get() != null && ModList.get().isLoaded("mekanism");

    private RecipeReader() {}

    @Nullable
    public static Read read(Recipe<?> recipe, HolderLookup.Provider registries) {
        try {
            if (MEKANISM) {
                Read mek = MekanismRecipeReader.read(recipe);
                if (mek != null || MekanismRecipeReader.isMekanism(recipe)) {
                    return mek;
                }
            }
            if (hasPedestalItems(recipe)) {
                // items that go on pedestals around the machine (Ars Nouveau): the network can't place those
                return null;
            }
            Map<BulkKey, Long> bulk = new LinkedHashMap<>();
            if (!fluidInputs(recipe, bulk)) {
                return null;
            }
            source(recipe, bulk);
            List<Input> inputs = inputs(recipe);
            if (inputs.isEmpty()) {
                return null;
            }
            ItemStack out = output(recipe, registries);
            ItemKey key = ItemKey.of(out);
            if (key != null) {
                return new Read(inputs, key, Math.max(1, out.getCount()), bulk);
            }
            // a recipe that makes a fluid instead of an item (melting, squeezing, mixing)
            Map<BulkKey, Long> fluidOut = new LinkedHashMap<>();
            fluidOutputs(recipe, fluidOut);
            return fluidOut.isEmpty() ? null : new Read(inputs, null, 0, bulk, fluidOut);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ inputs

    private static List<Input> inputs(Recipe<?> recipe) {
        // a recipe with one main input (Ars Nouveau's imbuement, many simple machines) says so directly; other
        // ingredients such as pedestal items are not put in the machine
        Object main = call(recipe, "getInput", "input", "getIngredient", "ingredient");
        if (main instanceof Ingredient ing) {
            Input in = input(ing, 1);
            return in == null ? List.of() : List.of(in);
        }
        if (main instanceof SizedIngredient sized) {
            Input in = input(sized.ingredient(), sized.count());
            return in == null ? List.of() : List.of(in);
        }
        // the standard list; the same ingredient twice means two of it
        Map<String, Input> grouped = new LinkedHashMap<>();
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) {
                continue;
            }
            Input in = input(ing, 1);
            if (in == null) {
                return List.of();
            }
            grouped.merge(in.options().toString(), in, (a, b) -> new Input(a.options(), a.count() + b.count()));
        }
        if (!grouped.isEmpty()) {
            return new ArrayList<>(grouped.values());
        }
        // some mods keep sized ingredients in their own list
        Object list = call(recipe, "getInputs", "inputs", "getIngredientsWithCount", "getSizedIngredients");
        List<Input> out = new ArrayList<>();
        if (list instanceof Collection<?> c) {
            for (Object o : c) {
                Input in = o instanceof SizedIngredient s ? input(s.ingredient(), s.count()) : o instanceof Ingredient i ? input(i, 1) : null;
                if (in == null) {
                    return List.of();
                }
                out.add(in);
            }
        }
        return out;
    }

    @Nullable
    private static Input input(Ingredient ing, long count) {
        List<ItemKey> options = new ArrayList<>();
        for (ItemStack s : ing.getItems()) {
            ItemKey k = ItemKey.of(s);
            if (k != null && !options.contains(k)) {
                options.add(k);
                if (options.size() >= MAX_OPTIONS) {
                    break;
                }
            }
        }
        return options.isEmpty() ? null : new Input(options, Math.max(1, count));
    }

    public static Input input(List<ItemStack> stacks) {
        List<ItemKey> options = new ArrayList<>();
        long count = 1;
        for (ItemStack s : stacks) {
            ItemKey k = ItemKey.of(s);
            if (k != null && !options.contains(k)) {
                options.add(k);
                count = Math.max(count, s.getCount());
                if (options.size() >= MAX_OPTIONS) {
                    break;
                }
            }
        }
        return options.isEmpty() ? null : new Input(options, count);
    }

    // ------------------------------------------------------------------ outputs

    private static ItemStack output(Recipe<?> recipe, HolderLookup.Provider registries) {
        ItemStack out = recipe.getResultItem(registries);
        if (!out.isEmpty()) {
            return out;
        }
        Object o = call(recipe, "getOutput", "output", "getResult", "result", "getResults", "results", "getOutputs", "outputs",
                "getRollableResults", "getMainOutput", "mainOutput");
        return stackOf(o);
    }

    private static ItemStack stackOf(@Nullable Object o) {
        if (o instanceof ItemStack s) {
            return s;
        }
        if (o instanceof Collection<?> c && !c.isEmpty()) {
            return stackOf(c.iterator().next());
        }
        if (o != null) {
            Object inner = call(o, "getStack", "stack", "getItemStack");
            if (inner instanceof ItemStack s) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ fluids and chemicals

    /**
     * Adds the fluids the recipe takes to {@code out}. Returns false if it also takes something the network can't
     * feed a machine (a chemical or gas outside Mekanism's API, say), so the recipe is left out.
     */
    private static boolean fluidInputs(Recipe<?> recipe, Map<BulkKey, Long> out) {
        for (Method m : recipe.getClass().getMethods()) {
            if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            String name = m.getName().toLowerCase(java.util.Locale.ROOT);
            if (!(name.contains("input") || name.contains("ingredient"))) {
                continue;
            }
            String type = m.getReturnType().getSimpleName().toLowerCase(java.util.Locale.ROOT);
            boolean collection = Collection.class.isAssignableFrom(m.getReturnType());
            if (!(type.contains("fluid") || type.contains("chemical") || type.contains("gas") || collection)) {
                continue;
            }
            Object v;
            try {
                v = m.invoke(recipe);
            } catch (ReflectiveOperationException | RuntimeException e) {
                continue;
            }
            if (v == null || isEmptyStack(v)) {
                continue;
            }
            for (Object o : v instanceof Collection<?> c ? c : List.of(v)) {
                if (o == null || o instanceof Ingredient || o instanceof SizedIngredient || o instanceof ItemStack) {
                    continue;
                }
                if (!fluid(o, out)) {
                    String cls = o.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
                    if (cls.contains("fluid") || cls.contains("chemical") || cls.contains("gas")) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** One fluid ingredient (sized ingredient, plain ingredient or stack) into {@code out}; false if it isn't one. */
    private static boolean fluid(Object o, Map<BulkKey, Long> out) {
        net.neoforged.neoforge.fluids.FluidStack stack = null;
        long amount = 1000;
        if (o instanceof net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient sized) {
            net.neoforged.neoforge.fluids.FluidStack[] all = sized.getFluids();
            stack = all.length == 0 ? null : all[0];
            amount = sized.amount();
        } else if (o instanceof net.neoforged.neoforge.fluids.crafting.FluidIngredient ing) {
            net.neoforged.neoforge.fluids.FluidStack[] all = ing.getStacks();
            stack = all.length == 0 ? null : all[0];
        } else if (o instanceof net.neoforged.neoforge.fluids.FluidStack fs) {
            stack = fs;
            amount = fs.getAmount();
        } else {
            return false;
        }
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        out.merge(BulkKey.fluid(stack), Math.max(1, amount), Long::sum);
        return true;
    }

    /** A recipe that costs Source (Ars Nouveau's Imbuement Chamber): the Machine Bus feeds it from Source cells. */
    private static void source(Recipe<?> recipe, Map<BulkKey, Long> out) {
        if (!recipe.getClass().getName().contains("arsnouveau")) {
            return;
        }
        Object cost = call(recipe, "getSource", "source");
        if (cost instanceof Number n && n.longValue() > 0) {
            out.put(BulkKey.flat(BulkKind.SOURCE), n.longValue());
        }
    }

    private static boolean hasPedestalItems(Recipe<?> recipe) {
        Object pedestals = call(recipe, "getPedestalItems", "pedestalItems");
        return pedestals instanceof Collection<?> c && !c.isEmpty();
    }

    /** Fluids the recipe makes, from the usual result methods. */
    private static void fluidOutputs(Recipe<?> recipe, Map<BulkKey, Long> out) {
        for (String n : new String[] {"getFluidResult", "getResultFluid", "getFluidOutput", "getOutputFluid", "fluidResult", "fluidOutput",
                "getFluidResults", "getFluidOutputs", "getResult", "result", "getOutput", "output", "getResults", "getOutputs"}) {
            Object o = call(recipe, n);
            Collection<?> values;
            if (o instanceof Collection<?> c) {
                values = c;
            } else {
                values = o == null ? List.of() : List.of(o);
            }
            for (Object v : values) {
                if (v instanceof net.neoforged.neoforge.fluids.FluidStack fs && !fs.isEmpty()) {
                    out.merge(BulkKey.fluid(fs), (long) Math.max(1, fs.getAmount()), Long::sum);
                }
            }
            if (!out.isEmpty()) {
                return;
            }
        }
    }

    private static boolean isEmptyStack(Object v) {
        Object empty = call(v, "isEmpty");
        return empty instanceof Boolean b && b;
    }

    /** The first of these public no-argument methods (or public fields) the object has, or null. */
    @Nullable
    private static Object call(Object target, String... names) {
        Class<?> c = target.getClass();
        for (String n : names) {
            try {
                Method m = c.getMethod(n);
                if (!Modifier.isStatic(m.getModifiers())) {
                    return m.invoke(target);
                }
            } catch (NoSuchMethodException ignored) {
                // try a field of the same name
                try {
                    java.lang.reflect.Field f = c.getField(n);
                    if (!Modifier.isStatic(f.getModifiers())) {
                        return f.get(target);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored2) {
                    // not there either
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // a getter that needs world state: skip it
            }
        }
        return null;
    }
}
