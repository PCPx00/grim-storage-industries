package com.xtri6.grimstorage.item;

import java.util.List;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.menu.Menus;

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

/** Holds one machine recipe (inputs in, outputs back) for a Processing Interface. */
public class ProcessingCardItem extends Item {
    public ProcessingCardItem(Properties props) {
        super(props);
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
                stack.remove(Registration.PROCESSING_CARD_DATA.get());
                player.displayClientMessage(Component.translatable("message.grimstorage.card.cleared"), true);
            } else if (player instanceof ServerPlayer sp) {
                Menus.openProcessingCard(sp, hand);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static ItemStack firstOutput(CardData.Processing data) {
        return data.outputs().stream().filter(s -> !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
    }

    @Override
    public Component getName(ItemStack stack) {
        CardData.Processing data = stack.get(Registration.PROCESSING_CARD_DATA.get());
        if (data != null && data.valid()) {
            return Component.translatable("item.grimstorage.processing_card.for", firstOutput(data).getHoverName());
        }
        return super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CardData.Processing data = stack.get(Registration.PROCESSING_CARD_DATA.get());
        return data != null && data.valid();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        CardData.Processing data = stack.get(Registration.PROCESSING_CARD_DATA.get());
        if (data == null || !data.valid()) {
            tooltip.add(Component.translatable("tooltip.grimstorage.card.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.grimstorage.card.in").withStyle(ChatFormatting.GRAY));
        for (ItemStack s : data.inputs()) {
            if (!s.isEmpty()) {
                tooltip.add(Component.literal("  " + s.getCount() + "x ").append(s.getHoverName()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        tooltip.add(Component.translatable("tooltip.grimstorage.card.out").withStyle(ChatFormatting.AQUA));
        for (ItemStack s : data.outputs()) {
            if (!s.isEmpty()) {
                tooltip.add(Component.literal("  " + s.getCount() + "x ").append(s.getHoverName()).withStyle(ChatFormatting.DARK_AQUA));
            }
        }
    }
}
