package com.xtri6.grimstorage.item;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.BalanceConfig;
import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.entity.ControllerBlockEntity;
import com.xtri6.grimstorage.block.entity.WirelessDevice;
import com.xtri6.grimstorage.storage.NetworkAccess;
import com.xtri6.grimstorage.storage.WirelessDevices;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Copies a network's ID off its Network Controller. Right-click a controller to take its ID; the wrench then holds that
 * network until you take another one. Wireless devices bind to the ID the wrench holds.
 */
public class NetworkWrenchItem extends Item {
    public NetworkWrenchItem(Properties props) {
        super(props.stacksTo(1));
    }

    /** The network ID this wrench holds, or null if it has none yet. */
    @Nullable
    public static UUID held(ItemStack stack) {
        return stack.get(Registration.NETWORK_ID.get());
    }

    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof WirelessDevice device) {
            return bind(ctx, player, device);
        }
        if (!(ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof ControllerBlockEntity controller)) {
            return InteractionResult.PASS;
        }
        if (!ctx.getLevel().isClientSide && !NetworkAccess.check(player, ctx.getLevel(), ctx.getClickedPos())) {
            return InteractionResult.FAIL;
        }
        if (!ctx.getLevel().isClientSide) {
            UUID id = controller.networkId();
            ctx.getItemInHand().set(Registration.NETWORK_ID.get(), id);
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.grimstorage.network.copied", shortId(id)), true);
            }
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    /** Right-click a wireless device to bind it to the held network; crouch-right-click to unbind it. */
    private static InteractionResult bind(UseOnContext ctx, @Nullable Player player, WirelessDevice device) {
        Level level = ctx.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!NetworkAccess.check(player, level, ctx.getClickedPos())) {
            return InteractionResult.FAIL;
        }
        if (player != null && player.isShiftKeyDown()) {
            device.bindNetwork(null);
            message(player, Component.translatable("message.grimstorage.wireless_device.unbound"));
            return InteractionResult.SUCCESS;
        }
        UUID id = held(ctx.getItemInHand());
        if (id == null) {
            message(player, Component.translatable("message.grimstorage.wireless_device.no_network"));
            return InteractionResult.FAIL;
        }
        int limit = BalanceConfig.wirelessBindLimit();
        if (limit > 0 && !id.equals(device.boundNetwork()) && WirelessDevices.boundCount(id) >= limit) {
            message(player, Component.translatable("message.grimstorage.wireless_device.limit", limit));
            return InteractionResult.FAIL;
        }
        device.bindNetwork(id);
        message(player, Component.translatable("message.grimstorage.wireless_device.bound", shortId(id)));
        return InteractionResult.SUCCESS;
    }

    private static void message(@Nullable Player player, Component text) {
        if (player != null) {
            player.displayClientMessage(text, true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        UUID id = held(stack);
        tooltip.add(id == null
                ? Component.translatable("tooltip.grimstorage.network_wrench.none").withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.grimstorage.network_wrench.held", shortId(id)).withStyle(ChatFormatting.AQUA));
    }
}
