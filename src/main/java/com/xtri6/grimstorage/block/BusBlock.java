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
    /** Which sides reach out to a cable or network block, so the bus draws a cable arm that way. */
    public static final Map<Direction, net.minecraft.world.level.block.state.properties.BooleanProperty> LINKS =
            net.minecraft.world.level.block.PipeBlock.PROPERTY_BY_DIRECTION;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    private static final Map<BlockState, VoxelShape> SHAPE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        for (Direction d : Direction.values()) {
            // plate, collar and a neck out to the middle of the block
            SHAPES.put(d, Shapes.or(box(d, 1, 1, 0, 15, 15, 2), box(d, 4, 4, 2, 12, 12, 5), box(d, 6, 6, 5, 10, 10, 10)));
            ARMS.put(d, box(d, 6, 6, 0, 10, 10, 6));
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
        BlockState st = defaultBlockState();
        for (var p : LINKS.values()) {
            st = st.setValue(p, false);
        }
        registerDefaultState(st);
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        LINKS.values().forEach(builder::add);
    }

    private static boolean links(BlockState self, Direction dir, BlockState neighbor) {
        return dir != self.getValue(FACING) && neighbor.getBlock() instanceof NetworkMember m && m.connectsTo(neighbor, dir.getOpposite());
    }

    /** Every side worked out from the neighbors: an arm wherever cable or a network block touches (never the front). */
    private static BlockState linked(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            state = state.setValue(LINKS.get(d), links(state, d, level.getBlockState(pos.relative(d))));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        BlockState st = super.getStateForPlacement(ctx);
        return st == null ? null : linked(st, ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, net.minecraft.world.level.LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return state.setValue(LINKS.get(dir), links(state, dir, neighbor));
    }

    /** Placed by commands or other mods, or turned with a wrench: work the arms out again. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide) {
            BlockState fixed = linked(state, level, pos);
            if (fixed != state) {
                level.setBlock(pos, fixed, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The front sits on the block being imported from or exported to, so the network never reaches through it. */
    @Override
    public boolean connectsTo(BlockState state, Direction side) {
        return side != state.getValue(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE_CACHE.computeIfAbsent(state, st -> {
            VoxelShape shape = SHAPES.get(st.getValue(FACING));
            for (Direction d : Direction.values()) {
                if (st.getValue(LINKS.get(d))) {
                    shape = Shapes.or(shape, ARMS.get(d));
                }
            }
            return shape.optimize();
        });
    }

    /**
     * Vanilla opens the GUI before an item gets its useOn, so a wrench on a wireless bus would open the menu instead of
     * binding. Hand the click to the wrench first and consume it; everything else keeps the normal GUI behaviour.
     */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
                                                                 BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                 net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (stack.getItem() instanceof com.xtri6.grimstorage.item.NetworkWrenchItem
                && level.getBlockEntity(pos) instanceof com.xtri6.grimstorage.block.entity.WirelessDevice) {
            stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, hand, hit));
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, from, moving);
        if (level.getBlockEntity(pos) instanceof BusBlockEntity bus) {
            bus.wake();
        } else if (level.getBlockEntity(pos) instanceof com.xtri6.grimstorage.block.entity.MachineBusBlockEntity mb) {
            mb.neighborChanged(from);
        }
    }
}
