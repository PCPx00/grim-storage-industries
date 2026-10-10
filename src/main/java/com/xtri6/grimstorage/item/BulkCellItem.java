package com.xtri6.grimstorage.item;

import java.util.List;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.CellTier;
import com.xtri6.grimstorage.util.Fmt;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A storage cell for fluids, chemicals, energy or Source. Goes in the same Soul Drive as item cells. */
public class BulkCellItem extends Item {
    private final BulkKind kind;
    private final CellTier tier;

    public BulkCellItem(BulkKind kind, CellTier tier, Properties props) {
        super(props.stacksTo(1));
        this.kind = kind;
        this.tier = tier;
    }

    public BulkKind kind() {
        return kind;
    }

    public CellTier tier() {
        return tier;
    }

    public long capacity() {
        return kind.capacity(tier);
    }

    public static String id(BulkKind kind, CellTier tier) {
        return kind.id() + "_cell_" + tier.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        CardData.CellStats stats = stack.get(Registration.CELL_STATS.get());
        long used = stats == null ? 0 : stats.used();
        tooltip.add(Component.translatable("tooltip.grimstorage.bulk.used", Fmt.amount(kind, used), Fmt.amount(kind, capacity()))
                .withStyle(ChatFormatting.GRAY));
        if (kind.typed()) {
            tooltip.add(Component.translatable("tooltip.grimstorage.cell.types", stats == null ? 0 : stats.types()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!kind.available()) {
            tooltip.add(Component.translatable("tooltip.grimstorage.bulk.needs_mod", kind.requiredMod()).withStyle(ChatFormatting.RED));
        }
    }
}
