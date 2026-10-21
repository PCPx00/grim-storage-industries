package com.xtri6.grimstorage.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.entity.GhostContainer;
import com.xtri6.grimstorage.item.CardData;
import com.xtri6.grimstorage.item.ProcessingCardItem;
import com.xtri6.grimstorage.item.RecipeCardItem;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Editing screens for the card in your hand. */
public final class CardMenus {
    private CardMenus() {}

    /** Card menus lock the slot holding the card so it cannot be moved while being edited. */
    abstract static class InHand extends GrimMenu {
        protected final Player player;
        protected final InteractionHand hand;
        private final int lockedSlot;

        InHand(net.minecraft.world.inventory.MenuType<?> type, int id, Inventory inv, InteractionHand hand) {
            super(type, id);
            this.player = inv.player;
            this.hand = hand;
            this.lockedSlot = hand == InteractionHand.MAIN_HAND ? inv.selected : -1;
        }

        protected ItemStack card() {
            return player.getItemInHand(hand);
        }

        @Override
        public void clicked(int slotId, int button, ClickType type, Player p) {
            if (type == ClickType.SWAP && (button == lockedSlot || button == 40)) {
                return;
            }
            if (slotId >= playerStart && slotId < playerEnd && lockedSlot >= 0) {
                int invIndex = slotId - playerStart < 27 ? slotId - playerStart + 9 : slotId - playerStart - 27;
                if (invIndex == lockedSlot) {
                    return;
                }
            }
            super.clicked(slotId, button, type, p);
        }
    }

    // ------------------------------------------------------------------ crafting recipe card
    public static class Recipe extends InHand {
        private final GhostContainer grid;
        private final SimpleContainer result = new SimpleContainer(1);

        public Recipe(int id, Inventory inv, InteractionHand hand) {
            super(Registration.RECIPE_CARD_MENU.get(), id, inv, hand);
            this.grid = new GhostContainer(9, false, () -> {});
            CardData.Recipe data = card().get(Registration.RECIPE_CARD_DATA.get());
            if (data != null) {
                for (int i = 0; i < 9; i++) {
                    grid.items().set(i, data.grid().get(i).copy());
                }
                result.setItem(0, data.result().copy());
            }
            for (int i = 0; i < 9; i++) {
                addSlot(new GhostSlot(grid, i, 30 + (i % 3) * 18, 17 + (i / 3) * 18, false));
            }
            addSlot(new DisplaySlot(result, 0, 124, 35));
            addPlayerInventory(inv, 8, 104);
        }

        public static Recipe client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
            return new Recipe(id, inv, buf.readEnum(InteractionHand.class));
        }

        @Override
        protected void onGhostChanged() {
            Level level = player.level();
            if (level.isClientSide) {
                return;
            }
            List<ItemStack> items = new ArrayList<>(grid.items());
            CraftingInput input = CraftingInput.of(3, 3, items);
            Optional<RecipeHolder<CraftingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
            ItemStack out = recipe.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
            result.setItem(0, out);
            ItemStack card = card();
            if (card.getItem() instanceof RecipeCardItem) {
                if (grid.isEmpty()) {
                    card.remove(Registration.RECIPE_CARD_DATA.get());
                } else {
                    card.set(Registration.RECIPE_CARD_DATA.get(), new CardData.Recipe(items, out));
                }
            }
            broadcastChanges();
        }

        /** Button 0: switch between "any variant" (any planks will do) and exact items. */
        @Override
        public boolean clickMenuButton(Player p, int id) {
            if (id == 0 && card().getItem() instanceof RecipeCardItem) {
                ItemStack card = card();
                if (exact()) {
                    card.remove(Registration.RECIPE_EXACT.get());
                } else {
                    card.set(Registration.RECIPE_EXACT.get(), true);
                }
                return true;
            }
            return false;
        }

        public boolean exact() {
            return Boolean.TRUE.equals(card().get(Registration.RECIPE_EXACT.get()));
        }

        @Override
        public boolean stillValid(Player p) {
            return card().getItem() instanceof RecipeCardItem;
        }
    }

    // ------------------------------------------------------------------ machine processing card
    public static class Processing extends InHand {
        private final GhostContainer inputs = new GhostContainer(9, true, () -> {});
        private final GhostContainer outputs = new GhostContainer(3, true, () -> {});

        public Processing(int id, Inventory inv, InteractionHand hand) {
            super(Registration.PROCESSING_CARD_MENU.get(), id, inv, hand);
            CardData.Processing data = card().get(Registration.PROCESSING_CARD_DATA.get());
            if (data != null) {
                for (int i = 0; i < 9; i++) {
                    inputs.items().set(i, data.inputs().get(i).copy());
                }
                for (int i = 0; i < 3; i++) {
                    outputs.items().set(i, data.outputs().get(i).copy());
                }
            }
            for (int i = 0; i < 9; i++) {
                addSlot(new GhostSlot(inputs, i, 30 + (i % 3) * 18, 17 + (i / 3) * 18, true));
            }
            for (int i = 0; i < 3; i++) {
                addSlot(new GhostSlot(outputs, i, 124, 17 + i * 18, true));
            }
            addPlayerInventory(inv, 8, 104);
        }

        public static Processing client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
            return new Processing(id, inv, buf.readEnum(InteractionHand.class));
        }

        @Override
        protected void onGhostChanged() {
            if (player.level().isClientSide) {
                return;
            }
            ItemStack card = card();
            if (card.getItem() instanceof ProcessingCardItem) {
                if (inputs.isEmpty() && outputs.isEmpty()) {
                    card.remove(Registration.PROCESSING_CARD_DATA.get());
                } else {
                    card.set(Registration.PROCESSING_CARD_DATA.get(), new CardData.Processing(new ArrayList<>(inputs.items()),
                            new ArrayList<>(outputs.items())));
                }
            }
            broadcastChanges();
        }

        @Override
        public boolean stillValid(Player p) {
            return card().getItem() instanceof ProcessingCardItem;
        }
    }
}
