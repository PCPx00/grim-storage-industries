package com.xtri6.grimstorage.item;

import com.xtri6.grimstorage.block.CableBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Import / Export Bus item. Use it on the block your cable runs up to (or on the cable itself) and
 * the bus goes onto that side of the cable, facing the block. Anywhere else it places a bus block as usual.
 */
public class BusItem extends BlockItem {
    private final boolean export;

    public BusItem(Block block, boolean export, Properties props) {
        super(block, props);
        this.export = export;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        BlockPos cable;
        Direction side;
        BlockState clickedState = level.getBlockState(clicked);
        BlockState next = level.getBlockState(clicked.relative(face));
        if (clickedState.getBlock() instanceof CableBlock && !CableBlock.side(clickedState, face).isBus()) {
            // clicked the cable: the bus goes on that side of it
            cable = clicked;
            side = face;
        } else if (next.getBlock() instanceof CableBlock && !CableBlock.side(next, face.getOpposite()).isBus()) {
            // clicked the block a cable runs up to: the bus goes on the cable, facing what you clicked
            cable = clicked.relative(face);
            side = face.getOpposite();
        } else {
            InteractionResult placed = super.useOn(ctx);
            if (placed.consumesAction()) {
                hint(ctx);
            }
            return placed;
        }
        if (!CableBlock.attachBus(level, cable, side, export)) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            level.playSound(null, cable, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1f, 1f);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
        }
        hint(ctx);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Export Buses are easy to mix up with Import Buses, so say which way this one goes when it is placed. */
    private void hint(UseOnContext ctx) {
        if (export && !ctx.getLevel().isClientSide && ctx.getPlayer() != null) {
            ctx.getPlayer().displayClientMessage(Component.translatable("message.grimstorage.export_bus.placed").withStyle(ChatFormatting.GOLD), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, java.util.List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(export ? "tooltip.grimstorage.export_bus" : "tooltip.grimstorage.import_bus")
                .withStyle(export ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.grimstorage.bus.how").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.grimstorage.bus.switch").withStyle(ChatFormatting.DARK_GRAY));
    }
}
