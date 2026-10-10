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
}
