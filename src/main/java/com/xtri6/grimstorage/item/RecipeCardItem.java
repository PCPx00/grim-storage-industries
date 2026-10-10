package com.xtri6.grimstorage.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.menu.Menus;
import com.xtri6.grimstorage.storage.ItemKey;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Holds one 3x3 crafting recipe. Use it to edit; sneak-use to wipe it. */
public class RecipeCardItem extends Item {
    public RecipeCardItem(Properties props) {
        super(props);
    }

    public static boolean configured(ItemStack stack) {
        CardData.Recipe data = stack.get(Registration.RECIPE_CARD_DATA.get());
        return data != null && data.valid();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getCount() > 1) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.grimstorage.card.one_at_a_time"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                stack.remove(Registration.RECIPE_CARD_DATA.get());
                player.displayClientMessage(Component.translatable("message.grimstorage.card.cleared"), true);
            } else if (player instanceof ServerPlayer sp) {
                Menus.openRecipeCard(sp, hand);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        CardData.Recipe data = stack.get(Registration.RECIPE_CARD_DATA.get());
        if (data != null && data.valid()) {
            return Component.translatable("item.grimstorage.recipe_card.for", data.result().getHoverName());
        }
        return super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return configured(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        CardData.Recipe data = stack.get(Registration.RECIPE_CARD_DATA.get());
        if (data == null || !data.valid()) {
            tooltip.add(Component.translatable("tooltip.grimstorage.card.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.grimstorage.card.makes", data.result().getCount(), data.result().getHoverName())
                .withStyle(ChatFormatting.AQUA));
        Map<ItemKey, Integer> counts = new LinkedHashMap<>();
        for (ItemStack s : data.grid()) {
            ItemKey k = ItemKey.of(s);
            if (k != null) {
                counts.merge(k, 1, Integer::sum);
            }
        }
        counts.forEach((k, n) -> tooltip.add(Component.literal("  " + n + "x ").append(k.stack().getHoverName()).withStyle(ChatFormatting.GRAY)));
    }
}
