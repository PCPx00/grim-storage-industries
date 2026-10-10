package com.xtri6.grimstorage.item;

import java.util.List;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.entity.ControllerBlockEntity;
import com.xtri6.grimstorage.menu.Menus;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** A terminal in your pocket. Sneak-use it on a Network Controller to bind it. */
public class WirelessTerminalItem extends Item {
    public static final int RANGE = 128;
    private final boolean unlimited;

    public WirelessTerminalItem(boolean unlimited, Properties props) {
        super(props.stacksTo(1));
        this.unlimited = unlimited;
    }

    public boolean unlimited() {
        return unlimited;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || !player.isShiftKeyDown() || !(ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof ControllerBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (!ctx.getLevel().isClientSide) {
            ctx.getItemInHand().set(Registration.BOUND_POS.get(), GlobalPos.of(ctx.getLevel().dimension(), ctx.getClickedPos()));
            player.displayClientMessage(Component.translatable("message.grimstorage.wireless.bound"), true);
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            Component problem = problem(sp, stack);
            if (problem != null) {
                sp.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true);
            } else {
                Menus.openWirelessTerminal(sp, hand);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Why this terminal cannot reach its network right now, or null if it can. */
    @Nullable
    public static Component problem(ServerPlayer player, ItemStack stack) {
        GlobalPos pos = stack.get(Registration.BOUND_POS.get());
        if (pos == null) {
            return Component.translatable("message.grimstorage.wireless.unbound");
        }
        boolean unlimited = stack.getItem() instanceof WirelessTerminalItem w && w.unlimited;
        if (!unlimited && (!pos.dimension().equals(player.level().dimension())
                || pos.pos().distSqr(player.blockPosition()) > (double) RANGE * RANGE)) {
            return Component.translatable("message.grimstorage.wireless.range");
        }
        return resolve(player, stack) == null ? Component.translatable("message.grimstorage.wireless.offline") : null;
    }

    @Nullable
    public static StorageNetwork resolve(ServerPlayer player, ItemStack stack) {
        GlobalPos pos = stack.get(Registration.BOUND_POS.get());
        if (pos == null) {
            return null;
        }
        ServerLevel level = player.server.getLevel(pos.dimension());
        if (level == null || !level.isLoaded(pos.pos())) {
            return null;
        }
        if (level.getBlockEntity(pos.pos()) instanceof ControllerBlockEntity c && c.ownNetwork() != null && c.ownNetwork().isActive()) {
            boolean unlimited = stack.getItem() instanceof WirelessTerminalItem w && w.unlimited;
            if (!unlimited && (!pos.dimension().equals(player.level().dimension())
                    || pos.pos().distSqr(player.blockPosition()) > (double) RANGE * RANGE)) {
                return null;
            }
            return c.ownNetwork();
        }
        return null;
    }

    /** The first wireless terminal the player is carrying that can reach a network. */
    @Nullable
    public static StorageNetwork findCarried(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof WirelessTerminalItem) {
                StorageNetwork n = resolve(player, s);
                if (n != null) {
                    return n;
                }
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos pos = stack.get(Registration.BOUND_POS.get());
        if (pos == null) {
            tooltip.add(Component.translatable("tooltip.grimstorage.wireless.unbound").withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("tooltip.grimstorage.wireless.bound",
                    pos.pos().getX(), pos.pos().getY(), pos.pos().getZ(), pos.dimension().location().getPath()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(unlimited ? "tooltip.grimstorage.wireless.unlimited" : "tooltip.grimstorage.wireless.range", RANGE)
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
