package com.xtri6.grimstorage.item;

import java.util.List;

import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.CellTier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Creative-only energy battery. It sits in a Soul Drive like any energy cell, but the network treats it as an endless
 * supply of FE and an endless sink for FE. It has no recipe, so it can only come from the creative tab.
 */
public class CreativeEnergyBatteryItem extends BulkCellItem {
    /** Far more FE than any network can hold, so reading it never runs out. */
    public static final long SUPPLY = 1_000_000_000_000_000L;

    public CreativeEnergyBatteryItem(Item.Properties props) {
        super(BulkKind.ENERGY, CellTier.NETHERITE, props);
    }

    @Override
    public long capacity() {
        return SUPPLY;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.grimstorage.creative_battery").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.grimstorage.creative_battery.hint").withStyle(ChatFormatting.GRAY));
    }
}
