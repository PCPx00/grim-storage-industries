package com.xtri6.grimstorage.compat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.menu.CardMenus;
import com.xtri6.grimstorage.menu.GrimMenu;
import com.xtri6.grimstorage.menu.TerminalMenu;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.storage.ItemKey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What a recipe viewer's buttons do on Grim Storage screens, shared by the EMI and REI plugins (JEI has its own):
 * write a Recipe Card, write a Processing Card, fill the terminal's grid, or drop an item on a filter slot.
 */
public final class ViewerActions {
    private ViewerActions() {}

    /** The crafting recipe with this id, laid out as the 3x3 grid (each slot: the items that would do). */
    @Nullable
    public static List<List<ItemStack>> craftingGrid(@Nullable ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        if (id == null || mc.level == null) {
            return null;
        }
        Optional<RecipeHolder<?>> holder = mc.level.getRecipeManager().byKey(id);
        if (holder.isEmpty() || !(holder.get().value() instanceof CraftingRecipe recipe)) {
            return null;
        }
        List<List<ItemStack>> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(List.of());
        }
        List<Ingredient> ings = recipe.getIngredients();
        int width = recipe instanceof ShapedRecipe s ? s.getWidth() : 3;
        for (int i = 0; i < ings.size() && i < 9; i++) {
            int slot = recipe instanceof ShapedRecipe ? (i / width) * 3 + i % width : i;
            if (slot < 9) {
                grid.set(slot, List.of(ings.get(i).getItems()));
            }
        }
        return grid;
    }

    private static ItemStack pick(List<ItemStack> options, Player player) {
        for (ItemStack s : options) {
            if (player.getInventory().contains(s)) {
                return s;
            }
        }
        return options.isEmpty() ? ItemStack.EMPTY : options.get(0);
    }

    /** Writes a crafting recipe on the Recipe Card being edited. True if the screen was one. */
    public static boolean recipeCard(AbstractContainerScreen<?> screen, @Nullable ResourceLocation id) {
        List<List<ItemStack>> grid = craftingGrid(id);
        if (!(screen.getMenu() instanceof CardMenus.Recipe menu) || grid == null || Minecraft.getInstance().player == null) {
            return false;
        }
        List<Integer> ids = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ids.add(i);
            stacks.add(pick(grid.get(i), Minecraft.getInstance().player).copyWithCount(1));
        }
        PacketDistributor.sendToServer(new Payloads.SetGhosts(menu.containerId, true, ids, stacks));
        return true;
    }

    /** Writes any recipe's items in and out on the Processing Card being edited. */
    public static boolean processingCard(AbstractContainerScreen<?> screen, List<List<ItemStack>> inputs, List<ItemStack> outputs) {
        if (!(screen.getMenu() instanceof CardMenus.Processing menu) || Minecraft.getInstance().player == null) {
            return false;
        }
        Map<ItemKey, Integer> in = new LinkedHashMap<>();
        for (List<ItemStack> options : inputs) {
            ItemStack s = pick(options, Minecraft.getInstance().player);
            ItemKey k = ItemKey.of(s);
            if (k != null && (in.containsKey(k) || in.size() < 9)) {
                in.merge(k, Math.max(1, s.getCount()), Integer::sum);
            }
        }
        Map<ItemKey, Integer> out = new LinkedHashMap<>();
        for (ItemStack s : outputs) {
            ItemKey k = ItemKey.of(s);
            if (k != null && (out.containsKey(k) || out.size() < 3)) {
                out.merge(k, Math.max(1, s.getCount()), Integer::sum);
            }
        }
        if (in.isEmpty() || out.isEmpty()) {
            return false;
        }
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
        return true;
    }

    /** Fills the terminal's crafting grid from the network for a crafting recipe. */
    public static boolean terminalGrid(AbstractContainerScreen<?> screen, @Nullable ResourceLocation id) {
        List<List<ItemStack>> grid = craftingGrid(id);
        if (!(screen.getMenu() instanceof TerminalMenu menu) || grid == null) {
            return false;
        }
        PacketDistributor.sendToServer(new Payloads.FillGrid(menu.containerId, grid));
        return true;
    }

    /** Drops an item on the ghost (filter or card) slot under the mouse, if there is one. */
    public static boolean dropOnGhost(AbstractContainerScreen<?> screen, ItemStack stack, double mx, double my) {
        if (stack.isEmpty() || !(screen.getMenu() instanceof GrimMenu menu)) {
            return false;
        }
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int x = screen.getGuiLeft() + s.x, y = screen.getGuiTop() + s.y;
            if (s instanceof GrimMenu.GhostSlot && s.isActive() && mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17) {
                PacketDistributor.sendToServer(new Payloads.SetGhosts(menu.containerId, false, List.of(i), List.of(stack.copyWithCount(1))));
                return true;
            }
        }
        return false;
    }
}
