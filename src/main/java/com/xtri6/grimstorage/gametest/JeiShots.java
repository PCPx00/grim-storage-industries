package com.xtri6.grimstorage.gametest;

import java.util.Optional;

import com.xtri6.grimstorage.GrimStorage;
import com.xtri6.grimstorage.compat.jei.GrimJeiPlugin;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Dev-only: presses JEI's + button on a recipe, through JEI's own transfer manager, for the screenshot check. */
final class JeiShots {
    private JeiShots() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <R> void transfer(String recipeId, RecipeType<R> type) {
        Minecraft mc = Minecraft.getInstance();
        IJeiRuntime rt = GrimJeiPlugin.runtime;
        if (rt == null || mc.player == null) {
            GrimStorage.LOGGER.error("[Shots] JEI not ready");
            return;
        }
        Optional<RecipeHolder<?>> holder = mc.level.getRecipeManager().byKey(ResourceLocation.parse(recipeId));
        if (holder.isEmpty()) {
            GrimStorage.LOGGER.error("[Shots] no recipe {}", recipeId);
            return;
        }
        R recipe = (R) holder.get();
        IRecipeCategory<R> category = rt.getRecipeManager().getRecipeCategory(type);
        IFocusGroup none = rt.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        Optional<IRecipeLayoutDrawable<R>> layout = rt.getRecipeManager().createRecipeLayoutDrawable(category, recipe, none);
        var handler = rt.getRecipeTransferManager().getRecipeTransferHandler(mc.player.containerMenu, category);
        if (layout.isEmpty() || handler.isEmpty()) {
            GrimStorage.LOGGER.error("[Shots] transfer for {}: layout={} handler={}", recipeId, layout.isPresent(), handler.isPresent());
            return;
        }
        var h = (mezz.jei.api.recipe.transfer.IRecipeTransferHandler) handler.get();
        var view = layout.get().getRecipeSlotsView();
        IRecipeTransferError check = h.transferRecipe(mc.player.containerMenu, recipe, view, mc.player, false, false);
        IRecipeTransferError done = h.transferRecipe(mc.player.containerMenu, recipe, view, mc.player, false, true);
        GrimStorage.LOGGER.info("[Shots] JEI transfer {} via {}: check={} done={} inputs={}", recipeId, h.getClass().getSimpleName(),
                check == null ? "ok" : check.getType(), done == null ? "ok" : done.getType(), view.getSlotViews(RecipeIngredientRole.INPUT).size());
    }

    static void show(String itemId) {
        IJeiRuntime rt = GrimJeiPlugin.runtime;
        if (rt == null) {
            return;
        }
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        var focus = rt.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, new net.minecraft.world.item.ItemStack(item));
        rt.getRecipesGui().show(focus);
    }
}
