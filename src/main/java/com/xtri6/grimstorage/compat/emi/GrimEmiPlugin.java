package com.xtri6.grimstorage.compat.emi;

import java.util.ArrayList;
import java.util.List;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.client.GrimScreen;
import com.xtri6.grimstorage.client.RecipeViewerSearch;
import com.xtri6.grimstorage.compat.ViewerActions;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** EMI: the + button writes cards and fills the terminal grid, items drag onto filter slots, and search syncs. */
@EmiEntrypoint
public final class GrimEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry reg) {
        reg.addRecipeHandler(Registration.RECIPE_CARD_MENU.get(), new Handler<>(true, (screen, r) -> ViewerActions.recipeCard(screen, r.getId())));
        reg.addRecipeHandler(Registration.TERMINAL_MENU.get(), new Handler<>(true, (screen, r) -> ViewerActions.terminalGrid(screen, r.getId())));
        reg.addRecipeHandler(Registration.PROCESSING_CARD_MENU.get(), new Handler<>(false, (screen, r) -> {
            List<List<ItemStack>> in = new ArrayList<>();
            for (EmiIngredient ing : r.getInputs()) {
                List<ItemStack> options = new ArrayList<>();
                for (EmiStack s : ing.getEmiStacks()) {
                    ItemStack st = s.getItemStack();
                    if (!st.isEmpty()) {
                        options.add(st.copyWithCount((int) Math.max(1, Math.min(64, ing.getAmount()))));
                    }
                }
                if (!options.isEmpty()) {
                    in.add(options);
                }
            }
            List<ItemStack> out = new ArrayList<>();
            for (EmiStack s : r.getOutputs()) {
                ItemStack st = s.getItemStack();
                if (!st.isEmpty()) {
                    out.add(st.copyWithCount((int) Math.max(1, Math.min(64, s.getAmount()))));
                }
            }
            return ViewerActions.processingCard(screen, in, out);
        }));
        reg.addDragDropHandler(GrimScreen.class, (screen, ingredient, x, y) -> {
            List<EmiStack> stacks = ingredient.getEmiStacks();
            return !stacks.isEmpty() && ViewerActions.dropOnGhost((AbstractContainerScreen<?>) screen, stacks.get(0).getItemStack(), x, y);
        });
        RecipeViewerSearch.connect(EmiApi::getSearchText, EmiApi::setSearchText);
        com.xtri6.grimstorage.GrimStorage.LOGGER.info("EMI found: recipe buttons, drag and drop and search sync enabled");
    }

    private interface Action {
        boolean run(AbstractContainerScreen<?> screen, EmiRecipe recipe);
    }

    private record Handler<T extends AbstractContainerMenu>(boolean craftingOnly, Action action) implements EmiRecipeHandler<T> {
        @Override
        public EmiPlayerInventory getInventory(AbstractContainerScreen<T> screen) {
            return new EmiPlayerInventory(List.of());
        }

        @Override
        public boolean supportsRecipe(EmiRecipe recipe) {
            return !craftingOnly || recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING;
        }

        @Override
        public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
            return true;
        }

        @Override
        public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
            return action.run(context.getScreen(), recipe);
        }
    }
}
