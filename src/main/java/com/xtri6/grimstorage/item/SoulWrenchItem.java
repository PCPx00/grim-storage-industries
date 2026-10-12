package com.xtri6.grimstorage.item;

import java.util.List;

import com.xtri6.grimstorage.GrimStorage;
import com.xtri6.grimstorage.block.CableBlock;
import com.xtri6.grimstorage.block.NetworkMember;
import com.xtri6.grimstorage.storage.NetworkManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Soul Wrench.
 * <ul>
 *     <li>On a cable arm: cut that connection. On a cut side: join it again.</li>
 *     <li>On a bus mounted on cable: take the bus off.</li>
 *     <li>On a machine (this mod's or anyone's): turn it to face the side you clicked.</li>
 *     <li>Sneak-use on any Grim Storage block: pick it up.</li>
 * </ul>
 */
public class SoulWrenchItem extends Item {
    public SoulWrenchItem(Properties props) {
        super(props.stacksTo(1));
    }

    private static boolean ours(BlockState state) {
        return GrimStorage.MODID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = ctx.getPlayer();
        Direction face = ctx.getClickedFace();
        if (player != null && (ours(state) || state.getBlock() instanceof CableBlock)
                && !com.xtri6.grimstorage.storage.NetworkAccess.check(player, level, pos)) {
            return InteractionResult.FAIL;
        }

        if (player != null && player.isShiftKeyDown() && ours(state)) {
            if (!level.isClientSide) {
                level.destroyBlock(pos, true, player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (state.getBlock() instanceof CableBlock) {
            Direction bus = CableBlock.busAt(state, pos, ctx.getClickLocation());
            if (bus != null) {
                if (!level.isClientSide) {
                    CableBlock.removeBus(level, pos, bus, player == null || !player.isCreative());
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            Direction arm = CableBlock.armAt(state, pos, ctx.getClickLocation());
            Direction side = arm != null ? arm : face;
            if (!level.isClientSide) {
                boolean cut = CableBlock.toggleSide(level, pos, side);
                level.playSound(null, pos, cut ? SoundEvents.CHAIN_BREAK : SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.2f);
                if (player != null) {
                    player.displayClientMessage(Component.translatable(cut ? "message.grimstorage.wrench.cut" : "message.grimstorage.wrench.joined",
                            Component.translatable("direction.grimstorage." + side.getSerializedName())), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockState turned = rotate(state, face);
        if (turned == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, turned, Block.UPDATE_ALL);
            level.invalidateCapabilities(pos);
            if (state.getBlock() instanceof NetworkMember) {
                NetworkManager.markDirty();
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * The block turned to face {@code face}, or null if it cannot turn. Blocks that face any way point at the clicked
     * side (click the same side again to flip it round); blocks that only face sideways turn to a clicked side, or a
     * quarter turn when you click the top or bottom. Two-block things (chests, beds, doors) are left alone.
     */
    static BlockState rotate(BlockState state, Direction face) {
        Block b = state.getBlock();
        if (b instanceof ChestBlock || b instanceof BedBlock || b instanceof DoorBlock
                || state.getOptionalValue(BlockStateProperties.EXTENDED).orElse(false)) {
            return null;
        }
        for (Property<?> p : state.getProperties()) {
            if (!(p instanceof DirectionProperty dp) || !p.getName().equals("facing")) {
                continue;
            }
            Direction now = state.getValue(dp);
            Direction want;
            if (dp.getPossibleValues().contains(Direction.UP)) {
                want = face == now ? face.getOpposite() : face;
            } else if (face.getAxis().isHorizontal() && face != now) {
                want = face;
            } else {
                want = now.getClockWise();
            }
            return dp.getPossibleValues().contains(want) && want != now ? state.setValue(dp, want) : null;
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.grimstorage.wrench.cable").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.grimstorage.wrench.bus").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.grimstorage.wrench.rotate").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.grimstorage.wrench.pickup").withStyle(ChatFormatting.DARK_GRAY));
    }
}
