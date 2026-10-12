package com.xtri6.grimstorage.compat;

import java.util.List;

import javax.annotation.Nullable;

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
                    java.util.Map.of(com.xtri6.grimstorage.storage.BulkKey.chemical(chems.get(0).getTypeRegistryName()), Math.max(1, amount)));
        }
        if (recipe instanceof CombinerRecipe r) {
            return of(List.of(r.getMainInput().getRepresentations(), r.getExtraInput().getRepresentations()), r.getOutputDefinition());
        }
        return null;
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
