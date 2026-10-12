package com.xtri6.grimstorage.compat;

import java.util.List;

import javax.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import com.xtri6.grimstorage.storage.BulkKey;
import com.xtri6.grimstorage.storage.ItemKey;
import com.xtri6.grimstorage.storage.RecipeReader;

import mekanism.api.recipes.CombinerRecipe;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.SawmillRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Mekanism's item machines (Enrichment Chamber, Crusher, Precision Sawmill, Combiner and their factories), read
 * through Mekanism's recipe API, plus the machines that also take a chemical (the Machine Bus feeds it). Only
 * loaded when Mekanism is installed.
 */
public final class MekanismRecipeReader {
    private MekanismRecipeReader() {}

    public static boolean isMekanism(Recipe<?> recipe) {
        return recipe instanceof MekanismRecipe<?>;
    }

    @Nullable
    public static RecipeReader.Read read(Recipe<?> recipe) {
        if (recipe instanceof ItemStackToItemStackRecipe r) {
            return of(List.of(r.getInput().getRepresentations()), r.getOutputDefinition());
        }
        if (recipe instanceof SawmillRecipe r) {
            // the main output; a chance-based extra (sawdust) is pulled out too but not counted
            return of(List.of(r.getInput().getRepresentations()), r.getMainOutputDefinition());
        }
        if (recipe instanceof mekanism.api.recipes.ItemStackChemicalToItemStackRecipe r) {
            // Osmium Compressor, Purification Chamber, Chemical Injection Chamber, Metallurgic Infuser: an item and a
            // chemical. Chemicals used every tick are counted for a whole run (200 ticks without upgrades).
            RecipeReader.Read base = of(List.of(r.getItemInput().getRepresentations()), r.getOutputDefinition());
            List<mekanism.api.chemical.ChemicalStack> chems = r.getChemicalInput().getRepresentations();
            if (base == null || chems.isEmpty()) {
                return null;
            }
            long amount = r.getChemicalInput().amount() * (r.perTickUsage() ? 200 : 1);
            return new RecipeReader.Read(base.inputs(), base.output(), base.outCount(),
                    java.util.Map.of(BulkKey.chemical(chems.get(0).getTypeRegistryName()), Math.max(1, amount)));
        }
        if (recipe instanceof CombinerRecipe r) {
            return of(List.of(r.getMainInput().getRepresentations(), r.getExtraInput().getRepresentations()), r.getOutputDefinition());
        }
        // ---- the chemical machines: ore processing chains and the gas lines that feed them
        if (recipe instanceof mekanism.api.recipes.ChemicalDissolutionRecipe r) {
            // ore + sulfuric acid (used every tick, a whole run counted) -> dirty slurry
            Map<BulkKey, Long> in = new LinkedHashMap<>();
            if (!chem(r.getChemicalInput(), r.perTickUsage() ? 100 : 1, in)) {
                return null;
            }
            return bulkOut(items(r.getItemInput().getRepresentations()), in, chemOut(r.getOutputDefinition()));
        }
        if (recipe instanceof mekanism.api.recipes.FluidChemicalToChemicalRecipe r) {
            // Chemical Washer: water + dirty slurry -> clean slurry
            Map<BulkKey, Long> in = new LinkedHashMap<>();
            if (!fluid(r.getFluidInput(), in) || !chem(r.getChemicalInput(), 1, in)) {
                return null;
            }
            return bulkOut(List.of(), in, chemOut(r.getOutputDefinition()));
        }
        if (recipe instanceof mekanism.api.recipes.ChemicalCrystallizerRecipe r) {
            Map<BulkKey, Long> in = new LinkedHashMap<>();
            if (!chem(r.getInput(), 1, in) || r.getOutputDefinition().isEmpty()) {
                return null;
            }
            ItemStack out = r.getOutputDefinition().get(0);
            ItemKey key = ItemKey.of(out);
            return key == null ? null : new RecipeReader.Read(List.of(), key, Math.max(1, out.getCount()), in, Map.of());
        }
        if (recipe instanceof mekanism.api.recipes.ItemStackToChemicalRecipe r) {
            // Chemical Oxidizer, Pigment Extractor
            return bulkOut(items(r.getInput().getRepresentations()), Map.of(), chemOut(r.getOutputDefinition()));
        }
        if (recipe instanceof mekanism.api.recipes.ChemicalToChemicalRecipe r) {
            // Chemical Activator, Isotopic Centrifuge
            Map<BulkKey, Long> in = new LinkedHashMap<>();
            if (!chem(r.getInput(), 1, in)) {
                return null;
            }
            return bulkOut(List.of(), in, chemOut(r.getOutputDefinition()));
        }
        if (recipe instanceof mekanism.api.recipes.ChemicalChemicalToChemicalRecipe r) {
            // Chemical Infuser, Pigment Mixer
            Map<BulkKey, Long> in = new LinkedHashMap<>();
            if (!chem(r.getLeftInput(), 1, in) || !chem(r.getRightInput(), 1, in)) {
                return null;
            }
            return bulkOut(List.of(), in, chemOut(r.getOutputDefinition()));
        }
        if (recipe instanceof mekanism.api.recipes.ElectrolysisRecipe r) {
            // Electrolytic Separator: water -> hydrogen and oxygen
            Map<BulkKey, Long> in = new LinkedHashMap<>();
            if (!fluid(r.getInput(), in) || r.getOutputDefinition().isEmpty()) {
                return null;
            }
            var o = r.getOutputDefinition().get(0);
            Map<BulkKey, Long> out = new LinkedHashMap<>();
            addChem(o.left(), out);
            addChem(o.right(), out);
            return bulkOut(List.of(), in, out);
        }
        if (recipe instanceof mekanism.api.recipes.PressurizedReactionRecipe r) {
            // item + fluid + gas -> item and / or gas
            Map<BulkKey, Long> in = new LinkedHashMap<>();
            if (!fluid(r.getInputFluid(), in) || !chem(r.getInputChemical(), 1, in) || r.getOutputDefinition().isEmpty()) {
                return null;
            }
            var o = r.getOutputDefinition().get(0);
            Map<BulkKey, Long> out = new LinkedHashMap<>();
            addChem(o.chemical(), out);
            List<RecipeReader.Input> items = items(r.getInputSolid().getRepresentations());
            ItemKey key = o.item().isEmpty() ? null : ItemKey.of(o.item());
            if (items == null || (key == null && out.isEmpty())) {
                return null;
            }
            return new RecipeReader.Read(items, key, key == null ? 0 : Math.max(1, o.item().getCount()), in, out);
        }
        return null;
    }

    /** A recipe whose results are all fluids or chemicals. */
    @Nullable
    private static RecipeReader.Read bulkOut(@Nullable List<RecipeReader.Input> items, Map<BulkKey, Long> in, Map<BulkKey, Long> out) {
        if (items == null || out.isEmpty() || (items.isEmpty() && in.isEmpty())) {
            return null;
        }
        return new RecipeReader.Read(items, null, 0, in, out);
    }

    @Nullable
    private static List<RecipeReader.Input> items(List<ItemStack> options) {
        RecipeReader.Input i = RecipeReader.input(options);
        return i == null ? null : List.of(i);
    }

    private static boolean chem(mekanism.api.recipes.ingredients.ChemicalStackIngredient ing, long times, Map<BulkKey, Long> out) {
        List<mekanism.api.chemical.ChemicalStack> all = ing.getRepresentations();
        if (all.isEmpty()) {
            return false;
        }
        out.merge(BulkKey.chemical(all.get(0).getTypeRegistryName()), Math.max(1, ing.amount() * times), Long::sum);
        return true;
    }

    private static boolean fluid(mekanism.api.recipes.ingredients.FluidStackIngredient ing, Map<BulkKey, Long> out) {
        List<net.neoforged.neoforge.fluids.FluidStack> all = ing.getRepresentations();
        if (all.isEmpty() || all.get(0).isEmpty()) {
            return false;
        }
        out.merge(BulkKey.fluid(all.get(0)), (long) Math.max(1, all.get(0).getAmount()), Long::sum);
        return true;
    }

    private static Map<BulkKey, Long> chemOut(List<mekanism.api.chemical.ChemicalStack> outs) {
        Map<BulkKey, Long> out = new LinkedHashMap<>();
        if (!outs.isEmpty()) {
            addChem(outs.get(0), out);
        }
        return out;
    }

    private static void addChem(mekanism.api.chemical.ChemicalStack stack, Map<BulkKey, Long> out) {
        if (stack != null && !stack.isEmpty()) {
            out.merge(BulkKey.chemical(stack.getTypeRegistryName()), Math.max(1, stack.getAmount()), Long::sum);
        }
    }

    @Nullable
    private static RecipeReader.Read of(List<List<ItemStack>> ins, List<ItemStack> outs) {
        if (outs.isEmpty()) {
            return null;
        }
        ItemStack out = outs.get(0);
        ItemKey key = ItemKey.of(out);
        if (key == null) {
            return null;
        }
        java.util.List<RecipeReader.Input> inputs = new java.util.ArrayList<>();
        for (List<ItemStack> in : ins) {
            RecipeReader.Input i = RecipeReader.input(in);
            if (i == null) {
                return null;
            }
            inputs.add(i);
        }
        return new RecipeReader.Read(inputs, key, Math.max(1, out.getCount()));
    }
}
