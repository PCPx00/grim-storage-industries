package com.xtri6.grimstorage.block;

import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Soul Cable: a thin connector that links network blocks that are not touching. */
public class CableBlock extends Block implements NetworkMember {
    public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);
    private static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape CORE = box(5, 5, 5, 11, 11, 11);
    private static final VoxelShape[] ARMS = {
            box(5, 0, 5, 11, 5, 11), box(5, 11, 5, 11, 16, 11),
            box(5, 5, 0, 11, 11, 5), box(5, 5, 11, 11, 11, 16),
            box(0, 5, 5, 5, 11, 11), box(11, 5, 5, 16, 11, 11)};
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = CORE;
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.ordinal())) != 0) {
                    shape = Shapes.or(shape, ARMS[d.ordinal()]);
                }
            }
            SHAPES[mask] = shape.optimize();
        }
    }

    public CableBlock(Properties props) {
        super(props);
        BlockState state = stateDefinition.any();
        for (BooleanProperty p : SIDES.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        SIDES.values().forEach(builder::add);
    }

    private static boolean connects(BlockState neighbor) {
        return neighbor.getBlock() instanceof NetworkMember;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = defaultBlockState();
        for (Direction d : Direction.values()) {
            state = state.setValue(SIDES.get(d), connects(ctx.getLevel().getBlockState(ctx.getClickedPos().relative(d))));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(SIDES.get(dir), connects(neighbor));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (state.getValue(SIDES.get(d))) {
                mask |= 1 << d.ordinal();
            }
        }
        return SHAPES[mask];
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
