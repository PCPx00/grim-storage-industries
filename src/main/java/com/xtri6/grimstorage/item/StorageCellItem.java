package com.xtri6.grimstorage.item;

import java.util.List;
import java.util.UUID;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.storage.CellStorage;
import com.xtri6.grimstorage.storage.CellTier;
import com.xtri6.grimstorage.util.Fmt;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class StorageCellItem extends Item {
    private final CellTier tier;

    public StorageCellItem(CellTier tier, Properties props) {
        super(props.stacksTo(1));
        this.tier = tier;
    }

    public CellTier tier() {
        return tier;
    }

    private static boolean isEmpty(Level level, ItemStack stack) {
        UUID id = stack.get(Registration.CELL_ID.get());
        return id == null || level.getServer() == null || CellStorage.get(level.getServer()).contents(id).total() == 0;
    }

    /** Singularity Cells: use with the item to lock to in your off hand. Sneak-use an empty cell to unlock. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!tier.singular() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!isEmpty(level, stack)) {
            player.displayClientMessage(Component.translatable("message.grimstorage.cell.not_empty").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        ItemStack off = player.getOffhandItem();
        if (player.isShiftKeyDown() || off.isEmpty()) {
            stack.remove(Registration.CELL_LOCK.get());
            player.displayClientMessage(Component.translatable("message.grimstorage.cell.unlocked"), true);
        } else {
            stack.set(Registration.CELL_LOCK.get(), new CardData.Locked(off));
            player.displayClientMessage(Component.translatable("message.grimstorage.cell.locked", off.getHoverName()), true);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return tier.glints() || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(tier.tooltipLine());
        CardData.CellStats stats = stack.get(Registration.CELL_STATS.get());
        long used = stats == null ? 0 : stats.used();
        int types = stats == null ? 0 : stats.types();
        if (tier.singular()) {
            CardData.Locked lock = stack.get(Registration.CELL_LOCK.get());
            if (lock == null) {
                tooltip.add(Component.translatable("tooltip.grimstorage.cell.unlocked").withStyle(ChatFormatting.GOLD));
                tooltip.add(Component.translatable("tooltip.grimstorage.cell.how_lock").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                tooltip.add(Component.translatable("tooltip.grimstorage.cell.locked", lock.stack().getHoverName()).withStyle(ChatFormatting.AQUA));
            }
            tooltip.add(Component.translatable("tooltip.grimstorage.cell.stored", Fmt.big(used)).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.grimstorage.cell.used", Fmt.big(used), Fmt.big(tier.capacity())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.grimstorage.cell.types", types).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
