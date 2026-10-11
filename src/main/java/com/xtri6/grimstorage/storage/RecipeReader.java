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
 * methods. Recipes that also need a fluid or chemical are skipped: only item recipes are learned.
 */
public final class RecipeReader {
    /** One ingredient: any of {@code options}, {@code count} of them. */
    public record Input(List<ItemKey> options, long count) {}

    /** A machine recipe: these inputs make {@code outCount} of {@code output}. */
    public record Read(List<Input> inputs, ItemKey output, long outCount) {}

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
            if (needsFluidOrChemical(recipe)) {
                return null;
            }
            List<Input> inputs = inputs(recipe);
            if (inputs.isEmpty()) {
                return null;
            }
            ItemStack out = output(recipe, registries);
            ItemKey key = ItemKey.of(out);
            return key == null ? null : new Read(inputs, key, Math.max(1, out.getCount()));
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
        Object o = call(recipe, "getOutput", "output", "getResult", "result", "getResults", "getOutputs", "getRollableResults");
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

    /** A recipe that also takes a fluid, chemical or gas can't be fed with items alone. */
    private static boolean needsFluidOrChemical(Recipe<?> recipe) {
        for (Method m : recipe.getClass().getMethods()) {
            if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            String name = m.getName().toLowerCase(java.util.Locale.ROOT);
            if (!(name.contains("input") || name.contains("ingredient"))) {
                continue;
            }
            String type = m.getReturnType().getSimpleName().toLowerCase(java.util.Locale.ROOT);
            if (type.contains("fluid") || type.contains("chemical") || type.contains("gas")) {
                try {
                    Object v = m.invoke(recipe);
                    if (v != null && !(v instanceof Collection<?> c && c.isEmpty()) && !isEmptyStack(v)) {
                        return true;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // can't tell; treat it as item-only
                }
            }
        }
        return false;
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
