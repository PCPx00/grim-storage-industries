package com.xtri6.grimstorage.block;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.entity.CableBusBlockEntity;
import com.xtri6.grimstorage.menu.Menus;
import com.xtri6.grimstorage.storage.NetworkManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Soul Cable: a thin connector that links network blocks that are not touching. Each of its six
 * sides can also carry an Import or Export Bus, facing the block next to it. A side with a bus on it does not carry
 * the network, so a cable bus can face another network's blocks without merging the two.
 */
public class CableBlock extends Block implements NetworkMember, EntityBlock {
    public static final MapCodec<CableBlock> CODEC = simpleCodec(CableBlock::new);

    /** What is on one side of a cable. */
    public enum Side implements StringRepresentable {
        NONE, CABLE, IMPORT, EXPORT;

        public boolean isBus() {
            return this == IMPORT || this == EXPORT;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final Map<Direction, EnumProperty<Side>> SIDES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.values()) {
            SIDES.put(d, EnumProperty.create(d.getSerializedName(), Side.class));
        }
    }

    // Cable is 4 pixels thick, the same as the neck of a bus.
    private static final VoxelShape CORE = box(6, 6, 6, 10, 10, 10);
    private static final VoxelShape[] ARMS = new VoxelShape[6];
    private static final VoxelShape[] BUSES = new VoxelShape[6];
    private static final VoxelShape[] SHAPES = new VoxelShape[4096];

    static {
        for (Direction d : Direction.values()) {
            ARMS[d.ordinal()] = facing(d, 6, 6, 0, 10, 10, 6);
            BUSES[d.ordinal()] = Shapes.or(facing(d, 1, 1, 0, 15, 15, 2), facing(d, 4, 4, 2, 12, 12, 5), facing(d, 6, 6, 5, 10, 10, 6));
        }
    }

    /** A box given as if it sits on the north side (z = 0 is the outside), turned to side {@code d}. */
    private static VoxelShape facing(Direction d, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (d) {
            case NORTH -> Block.box(x1, y1, z1, x2, y2, z2);
            case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
            case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
            case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
            case UP -> Block.box(x1, 16 - z2, y1, x2, 16 - z1, y2);
            case DOWN -> Block.box(x1, z1, 16 - y2, x2, z2, 16 - y1);
        };
    }

    public CableBlock(Properties props) {
        super(props);
        BlockState state = stateDefinition.any();
        for (EnumProperty<Side> p : SIDES.values()) {
            state = state.setValue(p, Side.NONE);
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

    public static Side side(BlockState state, Direction d) {
        return state.getValue(SIDES.get(d));
    }

    public static boolean hasBus(BlockState state) {
        for (Direction d : Direction.values()) {
            if (side(state, d).isBus()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ connections

    /** The network never passes through a side that has a bus on it. */
    @Override
    public boolean connectsTo(BlockState state, Direction side) {
        return !side(state, side).isBus();
    }

    private static boolean connects(Direction dir, BlockState neighbor) {
        return neighbor.getBlock() instanceof NetworkMember m && m.connectsTo(neighbor, dir.getOpposite());
    }

    /** Every side without a bus worked out from its neighbors. */
    private static BlockState connected(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (!side(state, d).isBus()) {
                state = state.setValue(SIDES.get(d), connects(d, level.getBlockState(pos.relative(d))) ? Side.CABLE : Side.NONE);
            }
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return connected(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (side(state, dir).isBus()) {
            return state;
        }
        return state.setValue(SIDES.get(dir), connects(dir, neighbor) ? Side.CABLE : Side.NONE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int key = 0;
        for (Direction d : Direction.values()) {
            key = key * 4 + side(state, d).ordinal();
        }
        VoxelShape shape = SHAPES[key];
        if (shape == null) {
            shape = CORE;
            for (Direction d : Direction.values()) {
                Side s = side(state, d);
                if (s == Side.CABLE) {
                    shape = Shapes.or(shape, ARMS[d.ordinal()]);
                } else if (s.isBus()) {
                    shape = Shapes.or(shape, BUSES[d.ordinal()]);
                }
            }
            SHAPES[key] = shape = shape.optimize();
        }
        return shape;
    }

    // ------------------------------------------------------------------ buses on the cable

    /** Which bus (if any) a point on the block is on, for clicking and breaking. */
    @Nullable
    public static Direction busAt(BlockState state, BlockPos pos, Vec3 hit) {
        Vec3 local = hit.subtract(pos.getX(), pos.getY(), pos.getZ());
        for (Direction d : Direction.values()) {
            if (side(state, d).isBus()) {
                for (var box : BUSES[d.ordinal()].toAabbs()) {
                    if (box.inflate(0.02).contains(local)) {
                        return d;
                    }
                }
            }
        }
        return null;
    }

    /** Puts a bus on {@code side} of the cable at {@code pos}. Returns false if that side already has one. */
    public static boolean attachBus(Level level, BlockPos pos, Direction side, boolean export) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CableBlock) || side(state, side).isBus()) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        level.setBlock(pos, state.setValue(SIDES.get(side), export ? Side.EXPORT : Side.IMPORT), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof CableBusBlockEntity be) {
            be.addBus(side, export);
        }
        NetworkManager.markDirty();
        return true;
    }

    /** Takes the bus off {@code side} and drops it (unless {@code drop} is false). */
    public static void removeBus(Level level, BlockPos pos, Direction side, boolean drop) {
        BlockState state = level.getBlockState(pos);
        Side s = side(state, side);
        if (!s.isBus() || level.isClientSide) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof CableBusBlockEntity be) {
            be.removeBus(side);
        }
        BlockState next = state.setValue(SIDES.get(side), connects(side, level.getBlockState(pos.relative(side))) ? Side.CABLE : Side.NONE);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        if (!hasBus(next)) {
            level.removeBlockEntity(pos);
        }
        if (drop) {
            popResource(level, pos, busItem(s));
        }
        level.playSound(null, pos, SoundEvents.METAL_BREAK, SoundSource.BLOCKS, 0.8f, 1.1f);
        NetworkManager.markDirty();
    }

    private static ItemStack busItem(Side s) {
        return new ItemStack(s == Side.EXPORT ? Registration.EXPORT_BUS.get() : Registration.IMPORT_BUS.get());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction bus = busAt(state, pos, hit.getLocation());
        if (bus == null) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer sp) {
            Menus.openCableBus(sp, pos, bus);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Breaking a bus on a cable takes off just that bus; break the cable itself to remove the whole thing. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (hasBus(state)) {
            HitResult hit = player.pick(player.blockInteractionRange(), 1f, false);
            Direction bus = hit instanceof BlockHitResult bh && bh.getBlockPos().equals(pos) ? busAt(state, pos, hit.getLocation()) : null;
            if (bus != null) {
                removeBus(level, pos, bus, !player.isCreative());
                return false;
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, net.minecraft.world.level.LevelReader level, BlockPos pos, Player player) {
        Direction bus = busAt(state, pos, target.getLocation());
        return bus != null ? busItem(side(state, bus)) : super.getCloneItemStack(state, target, level, pos, player);
    }

    // ------------------------------------------------------------------ block entity (only when a bus is on it)

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return hasBus(state) ? new CableBusBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != Registration.CABLE_BUS_ENTITY.get()) {
            return null;
        }
        return (l, p, s, be) -> ((CableBusBlockEntity) be).serverTick();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, from, moving);
        if (level.getBlockEntity(pos) instanceof CableBusBlockEntity be) {
            be.wake();
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        // Cable set by commands, structures or other mods skips getStateForPlacement; connect it up anyway.
        if (!level.isClientSide && !oldState.is(this)) {
            BlockState fixed = connected(state, level, pos);
            if (fixed != state) {
                level.setBlock(pos, fixed, Block.UPDATE_CLIENTS);
            }
        }
        NetworkMember.changed(state, oldState);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!newState.is(this) && !level.isClientSide) {
            // the buses come off with the cable
            for (Direction d : Direction.values()) {
                if (side(state, d).isBus()) {
                    popResource(level, pos, busItem(side(state, d)));
                }
            }
        }
        NetworkMember.changed(state, newState);
        super.onRemove(state, level, pos, newState, moved);
    }
}
