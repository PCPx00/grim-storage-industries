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
            return super.useOn(ctx);
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
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
