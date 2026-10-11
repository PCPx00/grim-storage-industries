package com.xtri6.grimstorage.compat.jei;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.client.GrimScreen;
import com.xtri6.grimstorage.menu.CardMenus;
import com.xtri6.grimstorage.menu.GrimMenu;
import com.xtri6.grimstorage.menu.TerminalMenu;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.storage.ItemKey;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * JEI recipe buttons and drag-and-drop for Grim Storage screens, so cards are written from recipes without
 * holding any of the items:
 * <ul>
 *     <li>Recipe Card: the + button on any crafting recipe writes it on the card.</li>
 *     <li>Processing Card: the + button on any recipe of any mod writes its items in and out.</li>
 *     <li>Terminal: the + button fills the crafting grid from the network (and your inventory).</li>
 *     <li>Every filter and card slot: drag an item from JEI onto it.</li>
 * </ul>
 */
public final class JeiTransfers {
    private JeiTransfers() {}

    /** The item a recipe slot shows, preferring one the player is carrying. */
    private static ItemStack pick(IRecipeSlotView slot, Player player) {
        List<ItemStack> all = slot.getItemStacks().toList();
        for (ItemStack s : all) {
            if (player.getInventory().contains(s)) {
                return s;
            }
        }
        return slot.getDisplayedItemStack().orElse(all.isEmpty() ? ItemStack.EMPTY : all.get(0));
    }

    // ------------------------------------------------------------------ recipe card

    public static final class RecipeCard implements IRecipeTransferHandler<CardMenus.Recipe, RecipeHolder<CraftingRecipe>> {
        @Override
        public Class<? extends CardMenus.Recipe> getContainerClass() {
            return CardMenus.Recipe.class;
        }

        @Override
        public Optional<MenuType<CardMenus.Recipe>> getMenuType() {
            return Optional.of(Registration.RECIPE_CARD_MENU.get());
        }

        @Override
        public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Nullable
        @Override
        public IRecipeTransferError transferRecipe(CardMenus.Recipe menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots,
                                                   Player player, boolean maxTransfer, boolean doTransfer) {
            if (doTransfer) {
                List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
                List<Integer> ids = new ArrayList<>();
                List<ItemStack> stacks = new ArrayList<>();
                for (int i = 0; i < Math.min(9, inputs.size()); i++) {
                    ids.add(i);
                    stacks.add(pick(inputs.get(i), player).copyWithCount(1));
                }
                PacketDistributor.sendToServer(new Payloads.SetGhosts(menu.containerId, true, ids, stacks));
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ processing card (any recipe, any mod)

    public static final class ProcessingCard implements IUniversalRecipeTransferHandler<CardMenus.Processing> {
        private final IRecipeTransferHandlerHelper helper;

        public ProcessingCard(IRecipeTransferHandlerHelper helper) {
            this.helper = helper;
        }

        @Override
        public Class<? extends CardMenus.Processing> getContainerClass() {
            return CardMenus.Processing.class;
        }

        @Override
        public Optional<MenuType<CardMenus.Processing>> getMenuType() {
            return Optional.of(Registration.PROCESSING_CARD_MENU.get());
        }

        @Nullable
        @Override
        public IRecipeTransferError transferRecipe(CardMenus.Processing menu, Object recipe, IRecipeSlotsView slots, Player player,
                                                   boolean maxTransfer, boolean doTransfer) {
            Map<ItemKey, Integer> in = collect(slots.getSlotViews(RecipeIngredientRole.INPUT), player, 9);
            Map<ItemKey, Integer> out = collect(slots.getSlotViews(RecipeIngredientRole.OUTPUT), player, 3);
            if (in.isEmpty() || out.isEmpty()) {
                return helper.createUserErrorWithTooltip(Component.translatable("jei.grimstorage.processing.items_only"));
            }
            if (doTransfer) {
                List<Integer> ids = new ArrayList<>();
                List<ItemStack> stacks = new ArrayList<>();
                int i = 0;
                for (Map.Entry<ItemKey, Integer> e : in.entrySet()) {
                    ids.add(i++);
                    stacks.add(e.getKey().toStack(Math.min(64, e.getValue())));
                }
                i = 9;
                for (Map.Entry<ItemKey, Integer> e : out.entrySet()) {
                    ids.add(i++);
                    stacks.add(e.getKey().toStack(Math.min(64, e.getValue())));
                }
                PacketDistributor.sendToServer(new Payloads.SetGhosts(menu.containerId, true, ids, stacks));
            }
            return null;
        }

        /** Items in these slots, the same item added up; fluids, chemicals and the like are left out. */
        private static Map<ItemKey, Integer> collect(List<IRecipeSlotView> views, Player player, int max) {
            Map<ItemKey, Integer> out = new LinkedHashMap<>();
            for (IRecipeSlotView v : views) {
                ItemStack s = pick(v, player);
                ItemKey k = ItemKey.of(s);
                if (k != null && (out.containsKey(k) || out.size() < max)) {
                    out.merge(k, s.getCount(), Integer::sum);
                }
            }
            return out;
        }
    }

    // ------------------------------------------------------------------ terminal crafting grid

    public static final class Terminal implements IRecipeTransferHandler<TerminalMenu, RecipeHolder<CraftingRecipe>> {
        @Override
        public Class<? extends TerminalMenu> getContainerClass() {
            return TerminalMenu.class;
        }

        @Override
        public Optional<MenuType<TerminalMenu>> getMenuType() {
            return Optional.of(Registration.TERMINAL_MENU.get());
        }

        @Override
        public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Nullable
        @Override
        public IRecipeTransferError transferRecipe(TerminalMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots,
                                                   Player player, boolean maxTransfer, boolean doTransfer) {
            List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
            Map<ItemKey, Long> stock = new LinkedHashMap<>();
            for (TerminalMenu.Entry e : menu.clientEntries) {
                stock.merge(e.key(), e.count(), Long::sum);
            }
            for (ItemStack s : player.getInventory().items) {
                ItemKey k = ItemKey.of(s);
                if (k != null) {
                    stock.merge(k, (long) s.getCount(), Long::sum);
                }
            }
            List<List<ItemStack>> options = new ArrayList<>();
            List<IRecipeSlotView> missing = new ArrayList<>();
            for (int i = 0; i < Math.min(9, inputs.size()); i++) {
                IRecipeSlotView v = inputs.get(i);
                List<ItemStack> opts = new ArrayList<>(v.getItemStacks().toList());
                // what we have most of goes first
                opts.sort((a, b) -> Long.compare(stock.getOrDefault(ItemKey.of(b), 0L), stock.getOrDefault(ItemKey.of(a), 0L)));
                if (opts.size() > 64) {
                    opts = opts.subList(0, 64);
                }
                boolean have = false;
                for (ItemStack o : opts) {
                    ItemKey k = ItemKey.of(o);
                    if (k != null && stock.getOrDefault(k, 0L) > 0) {
                        stock.merge(k, -1L, Long::sum);
                        have = true;
                        break;
                    }
                }
                if (!have && !opts.isEmpty()) {
                    missing.add(v);
                }
                options.add(opts);
            }
            if (doTransfer) {
                PacketDistributor.sendToServer(new Payloads.FillGrid(menu.containerId, options));
            }
            return missing.isEmpty() ? null : new Missing(missing);
        }
    }

    /** Some ingredients aren't in the network: the button still works, the missing ones are marked red. */
    private record Missing(List<IRecipeSlotView> slots) implements IRecipeTransferError {
        @Override
        public Type getType() {
            return Type.COSMETIC;
        }

        @Override
        public int getButtonHighlightColor() {
            return 0x80FFA000;
        }

        @Override
        public void showError(GuiGraphics g, int mouseX, int mouseY, IRecipeSlotsView view, int recipeX, int recipeY) {
            g.pose().pushPose();
            g.pose().translate(recipeX, recipeY, 0);
            for (IRecipeSlotView s : slots) {
                s.drawHighlight(g, 0x66FF0000);
            }
            g.pose().popPose();
        }

        @Override
        public List<Component> getTooltip() {
            return List.of(Component.translatable("jei.grimstorage.terminal.fill"),
                    Component.translatable("jei.grimstorage.terminal.missing", slots.size()).withStyle(ChatFormatting.RED));
        }
    }

    // ------------------------------------------------------------------ drag and drop onto filter / card slots

    @SuppressWarnings("rawtypes")
    static final class Ghosts implements IGhostIngredientHandler<GrimScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(GrimScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
            List<Target<I>> out = new ArrayList<>();
            Optional<ItemStack> item = ingredient.getIngredient(VanillaTypes.ITEM_STACK);
            if (item.isEmpty() || !(screen.getMenu() instanceof GrimMenu menu)) {
                return out;
            }
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot s = menu.slots.get(i);
                if (s instanceof GrimMenu.GhostSlot && s.isActive() && seen.add(i)) {
                    int id = i;
                    Rect2i area = new Rect2i(screen.getGuiLeft() + s.x, screen.getGuiTop() + s.y, 16, 16);
                    out.add(new Target<>() {
                        @Override
                        public Rect2i getArea() {
                            return area;
                        }

                        @Override
                        public void accept(I ignored) {
                            PacketDistributor.sendToServer(new Payloads.SetGhosts(menu.containerId, false, List.of(id),
                                    List.of(item.get().copyWithCount(1))));
                        }
                    });
                }
            }
            return out;
        }

        @Override
        public void onComplete() {}
    }
}
