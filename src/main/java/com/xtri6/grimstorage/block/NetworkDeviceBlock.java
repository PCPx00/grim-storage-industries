package com.xtri6.grimstorage.block;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.xtri6.grimstorage.block.entity.DropsContents;
import com.xtri6.grimstorage.block.entity.Ticking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A network block with an optional block entity and an optional screen. */
public class NetworkDeviceBlock extends Block implements EntityBlock, NetworkMember {
    @FunctionalInterface
    public interface Opener {
        void open(ServerPlayer player, BlockPos pos);
    }

    @Nullable
    private final Supplier<? extends BlockEntityType<?>> type;
    @Nullable
    private final Opener opener;

    public NetworkDeviceBlock(Properties props, @Nullable Supplier<? extends BlockEntityType<?>> type, @Nullable Opener opener) {
        super(props);
        this.type = type;
        this.opener = opener;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new NetworkDeviceBlock(p, type, opener));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return type == null ? null : type.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || type == null) {
            return null;
        }
        return (l, p, s, be) -> {
            if (be instanceof Ticking t) {
                t.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (opener == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            opener.open(sp, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        NetworkMember.changed(state, oldState);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DropsContents d) {
            d.dropContents(level, pos);
        }
        NetworkMember.changed(state, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Soul wisps drifting off the controller, quantum link and void filter. Client only, purely for looks. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        boolean controller = this == com.xtri6.grimstorage.Registration.CONTROLLER.get();
        boolean link = this == com.xtri6.grimstorage.Registration.QUANTUM_LINK.get();
        boolean voids = this == com.xtri6.grimstorage.Registration.VOID_FILTER.get();
        if (!(controller || link || voids) || random.nextInt(controller ? 3 : 2) != 0) {
            return;
        }
        net.minecraft.core.Direction side = net.minecraft.core.Direction.getRandom(random);
        BlockPos out = pos.relative(side);
        if (level.getBlockState(out).isSolidRender(level, out)) {
            return;
        }
        double x = pos.getX() + 0.5 + side.getStepX() * 0.55 + (side.getStepX() == 0 ? random.nextDouble() * 0.8 - 0.4 : 0);
        double y = pos.getY() + 0.5 + side.getStepY() * 0.55 + (side.getStepY() == 0 ? random.nextDouble() * 0.8 - 0.4 : 0);
        double z = pos.getZ() + 0.5 + side.getStepZ() * 0.55 + (side.getStepZ() == 0 ? random.nextDouble() * 0.8 - 0.4 : 0);
        if (voids) {
            // overflow being pulled into the void
            level.addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL, x, y, z,
                    (pos.getX() + 0.5 - x) * 2, (pos.getY() + 0.5 - y) * 2, (pos.getZ() + 0.5 - z) * 2);
        } else if (link) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, x, y, z, side.getStepX() * 0.02, 0.02, side.getStepZ() * 0.02);
        } else {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, x, y, z, side.getStepX() * 0.01, 0.015, side.getStepZ() * 0.01);
        }
    }
}
