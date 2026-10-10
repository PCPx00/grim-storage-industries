package com.xtri6.grimstorage.item;

import java.util.List;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.entity.QuantumLinkBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/** Pairs two Quantum Links: use on the first, then on the second. Sneak-use on a link to unpair it. */
public class SoulTetherItem extends Item {
    public SoulTetherItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof QuantumLinkBlockEntity link)) {
            return InteractionResult.PASS;
        }
        if (!(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        Player player = ctx.getPlayer();
        ItemStack tether = ctx.getItemInHand();
        GlobalPos here = GlobalPos.of(level.dimension(), ctx.getClickedPos());

        if (player != null && player.isShiftKeyDown()) {
            unpair(level, link);
            tether.remove(Registration.BOUND_POS.get());
            message(player, Component.translatable("message.grimstorage.tether.unlinked"));
            return InteractionResult.CONSUME;
        }

        GlobalPos first = tether.get(Registration.BOUND_POS.get());
        if (first == null || first.equals(here)) {
            tether.set(Registration.BOUND_POS.get(), here);
            message(player, Component.translatable("message.grimstorage.tether.first"));
            return InteractionResult.CONSUME;
        }
        ServerLevel otherLevel = level.getServer().getLevel(first.dimension());
        if (otherLevel == null || !otherLevel.isLoaded(first.pos())
                || !(otherLevel.getBlockEntity(first.pos()) instanceof QuantumLinkBlockEntity other)) {
            tether.remove(Registration.BOUND_POS.get());
            message(player, Component.translatable("message.grimstorage.tether.lost").withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }
        unpair(level, link);
        unpair(otherLevel, other);
        other.setPartner(here);
        link.setPartner(first);
        tether.remove(Registration.BOUND_POS.get());
        message(player, Component.translatable("message.grimstorage.tether.linked"));
        return InteractionResult.CONSUME;
    }

    private static void unpair(ServerLevel level, QuantumLinkBlockEntity link) {
        GlobalPos old = link.partner();
        if (old != null) {
            ServerLevel l = level.getServer().getLevel(old.dimension());
            if (l != null && l.isLoaded(old.pos()) && l.getBlockEntity(old.pos()) instanceof QuantumLinkBlockEntity o) {
                o.setPartner(null);
            }
            link.setPartner(null);
        }
    }

    private static void message(Player player, Component text) {
        if (player != null) {
            player.displayClientMessage(text, true);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(Registration.BOUND_POS.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(stack.has(Registration.BOUND_POS.get())
                ? "tooltip.grimstorage.tether.holding" : "tooltip.grimstorage.tether.empty").withStyle(ChatFormatting.GRAY));
    }
}
