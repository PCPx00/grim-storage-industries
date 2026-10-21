package com.xtri6.grimstorage.item;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.entity.ControllerBlockEntity;
import com.xtri6.grimstorage.storage.NetworkAccess;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        UUID id = held(stack);
        tooltip.add(id == null
                ? Component.translatable("tooltip.grimstorage.network_wrench.none").withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.grimstorage.network_wrench.held", shortId(id)).withStyle(ChatFormatting.AQUA));
    }
}
