package com.xtri6.grimstorage.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.xtri6.grimstorage.GrimStorage;
import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.client.SimpleScreens;
import com.xtri6.grimstorage.item.BulkCellItem;
import com.xtri6.grimstorage.recipe.InfusingRecipe;
import com.xtri6.grimstorage.storage.BulkKind;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/** JEI support: Soul Infuser recipes get their own page, and cells for mods that are not installed are hidden. */
@JeiPlugin
public final class GrimJeiPlugin implements IModPlugin {
    public static final RecipeType<RecipeHolder<InfusingRecipe>> INFUSING =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(GrimStorage.MODID, "infusing"));

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(GrimStorage.MODID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration reg) {
        reg.addRecipeCategories(new InfusingCategory(reg.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        RecipeManager rm = Minecraft.getInstance().level.getRecipeManager();
        reg.addRecipes(INFUSING, new ArrayList<>(rm.getAllRecipesFor(Registration.INFUSING_TYPE.get())));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration reg) {
        reg.addRecipeCatalyst(Registration.SOUL_INFUSER.get(), INFUSING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration reg) {
        reg.addRecipeClickArea(SimpleScreens.Infuser.class, 70, 34, 26, 18, INFUSING);
    }

    /** The live JEI runtime (used by the dev screenshot check to open the infusing page). */
    public static IJeiRuntime runtime;

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        GrimJeiPlugin.runtime = runtime;
        // Chemical and Source cells do nothing without Mekanism / Ars Nouveau, so keep them out of the list too.
        List<ItemStack> hide = new ArrayList<>();
        for (BulkKind kind : BulkKind.values()) {
            if (!kind.available()) {
                Registration.BULK_CELLS.get(kind).values().forEach(c -> hide.add(new ItemStack(c.get())));
            }
        }
        if (!hide.isEmpty()) {
            runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hide);
        }
    }

    private static final class InfusingCategory implements IRecipeCategory<RecipeHolder<InfusingRecipe>> {
        private final IDrawable icon;
        private final IDrawableAnimated arrow;

        InfusingCategory(IGuiHelper gui) {
            this.icon = gui.createDrawableItemLike(Registration.SOUL_INFUSER.get());
            this.arrow = gui.createAnimatedRecipeArrow(100);
        }

        @Override
        public RecipeType<RecipeHolder<InfusingRecipe>> getRecipeType() {
            return INFUSING;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("block.grimstorage.soul_infuser");
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public int getWidth() {
            return 104;
        }

        @Override
        public int getHeight() {
            return 56;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<InfusingRecipe> holder, IFocusGroup focuses) {
            InfusingRecipe r = holder.value();
            for (int i = 0; i < InfusingRecipe.SLOTS; i++) {
                var slot = builder.addSlot(RecipeIngredientRole.INPUT, 1 + i * 18, 1).setStandardSlotBackground();
                if (i < r.ingredients().size()) {
                    slot.addIngredients(r.ingredients().get(i));
                }
            }
            builder.addSlot(RecipeIngredientRole.OUTPUT, 83, 20).setOutputSlotBackground().addItemStack(r.result());
        }

        @Override
        public void draw(RecipeHolder<InfusingRecipe> holder, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
            arrow.draw(g, 50, 20);
            String time = Component.translatable("jei.grimstorage.infusing.time", String.format("%.1f", holder.value().time() / 20.0)).getString();
            g.drawString(Minecraft.getInstance().font, time, 1, 26, 0xFF808080, false);
            g.drawString(Minecraft.getInstance().font, Component.translatable("jei.grimstorage.infusing.any_order")
                    .withStyle(ChatFormatting.ITALIC), 1, 44, 0xFF808080, false);
        }
    }
}
