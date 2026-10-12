package com.xtri6.grimstorage.block;

import java.util.function.Supplier;

import com.xtri6.grimstorage.block.entity.RedstoneEmitterBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** A network block that gives a redstone signal on every side (see {@link RedstoneEmitterBlockEntity}). */
public class RedstoneEmitterBlock extends NetworkDeviceBlock {
    public RedstoneEmitterBlock(Properties props, Supplier<? extends BlockEntityType<?>> type, Opener opener) {
        super(props, type, opener);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return level.getBlockEntity(pos) instanceof RedstoneEmitterBlockEntity e && e.emitting() ? 15 : 0;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @javax.annotation.Nullable Direction dir) {
        return true;
    }
}
