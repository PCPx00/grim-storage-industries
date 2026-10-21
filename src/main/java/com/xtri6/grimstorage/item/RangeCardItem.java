package com.xtri6.grimstorage.item;

import java.util.List;

import com.xtri6.grimstorage.BalanceConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.items.IItemHandler;

/** A wireless range card. It sits in an upgrade slot of a wireless device and sets how far that device reaches. */
public class RangeCardItem extends Item {
    /** Reach used by the infinite card: far beyond any real controller distance. */
    public static final int INFINITE_REACH = 30_000_000;

    public enum Tier {
        LOW, MEDIUM, HIGH, INFINITE;

        /** Blocks this card reaches. The infinite card reaches as the high card when the server turns it off. */
        public int range() {
            return switch (this) {
                case LOW -> BalanceConfig.wirelessLowRange();
                case MEDIUM -> BalanceConfig.wirelessRange();
                case HIGH -> BalanceConfig.wirelessHighRange();
                case INFINITE -> infinite() ? INFINITE_REACH : BalanceConfig.wirelessHighRange();
            };
        }

        /** Whether this card reaches any distance, and any dimension if the server allows it. */
        public boolean infinite() {
            return this == INFINITE && BalanceConfig.infiniteWireless();
        }
    }

    private final Tier tier;

    public RangeCardItem(Tier tier, Item.Properties props) {
        super(props.stacksTo(16));
        this.tier = tier;
    }

    public Tier tier() {
        return tier;
    }

    /** The furthest-reaching range card in these slots, or null if there is none. */
    public static Tier best(IItemHandler slots) {
        Tier best = null;
        for (int i = 0; i < slots.getSlots(); i++) {
            if (slots.getStackInSlot(i).getItem() instanceof RangeCardItem card && (best == null || card.tier().ordinal() > best.ordinal())) {
                best = card.tier();
            }
        }
        return best;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        if (tier.infinite()) {
            tooltip.add(Component.translatable("tooltip.grimstorage.range_card.infinite").withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.grimstorage.range_card", tier.range()).withStyle(ChatFormatting.AQUA));
        }
    }
}
