package com.xtri6.grimstorage.block;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import com.xtri6.grimstorage.block.entity.BusBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Import / Export Bus: a thin plate that sticks onto the face of the block you place it against, with a short neck
 * on the back for cable to reach.
 */
public class BusBlock extends FacingDeviceBlock.All {
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.values()) {
            SHAPES.put(d, Shapes.or(box(d, 1, 1, 0, 15, 15, 2), box(d, 4, 4, 2, 12, 12, 5), box(d, 6, 6, 5, 10, 10, 16)));
        }
    }

    /** A box given as if the bus faces north (plate at z = 0), turned to face {@code d}. */
    private static VoxelShape box(Direction d, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (d) {
            case NORTH -> Block.box(x1, y1, z1, x2, y2, z2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case UP -> Block.box(x1, 16 - z2, y1, x2, 16 - z1, y2);
            case DOWN -> Block.box(x1, z1, 16 - y2, x2, z2, 16 - y1);
        };
    }

    public BusBlock(Properties props, Supplier<? extends BlockEntityType<?>> type, Opener opener) {
        super(props, type, opener);
    }

    /** The front sits on the block being imported from or exported to, so the network never reaches through it. */
    @Override
    public boolean connectsTo(BlockState state, Direction side) {
        return side != state.getValue(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, from, moving);
        if (level.getBlockEntity(pos) instanceof BusBlockEntity bus) {
            bus.wake();
        }
    }
}
