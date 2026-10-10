package com.xtri6.grimstorage.item;

import java.util.List;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.ServerConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** The Grim Storage Guide: an in-game manual for the whole mod. */
public class GuideBookItem extends Item {
    private static final String GIVEN = "grimstorage_guide_given";

    public GuideBookItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            com.xtri6.grimstorage.client.guide.GuideScreen.open();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.grimstorage.guide").withStyle(ChatFormatting.GRAY));
    }

    /** Gives the guide the first time a player joins. Returns true if it was given. */
    public static boolean giveIfFirstJoin(Player player) {
        if (!ServerConfig.giveGuide()) {
            return false;
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN)) {
            return false;
        }
        persisted.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack book = new ItemStack(Registration.GUIDE_BOOK.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        return true;
    }
}
