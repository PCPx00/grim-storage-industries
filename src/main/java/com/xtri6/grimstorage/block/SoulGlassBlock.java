package com.xtri6.grimstorage.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Soulite Glass: decorative glass that also carries the network, so storage rooms can look good. */
public class SoulGlassBlock extends TransparentBlock implements NetworkMember {
    public static final MapCodec<SoulGlassBlock> CODEC = simpleCodec(SoulGlassBlock::new);

    public SoulGlassBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        NetworkMember.changed(state, oldState);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        NetworkMember.changed(state, newState);
        super.onRemove(state, level, pos, newState, moved);
    }
}
