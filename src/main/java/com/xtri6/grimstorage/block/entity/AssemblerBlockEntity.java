package com.xtri6.grimstorage.block.entity;

import java.util.ArrayList;
import java.util.List;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.item.RecipeCardItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Crafts the recipes on the Recipe Cards inside it. Speed Upgrades double its speed each. */
public class AssemblerBlockEntity extends NetworkBlockEntity implements DropsContents {
    public static final int CARD_SLOTS = 9;
    public static final int UPGRADE_SLOTS = 4;

    private final ItemStackHandler cards = handler(CARD_SLOTS, 1, true, s -> s.getItem() instanceof RecipeCardItem);
    private final ItemStackHandler upgrades = handler(UPGRADE_SLOTS, 1, false, s -> s.is(Registration.SPEED_UPGRADE.get()));

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.ASSEMBLER_ENTITY.get(), pos, state);
    }

    public ItemStackHandler cardHandler() {
        return cards;
    }

    public ItemStackHandler upgradeHandler() {
        return upgrades;
    }

    public List<ItemStack> cards() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < CARD_SLOTS; i++) {
            if (!cards.getStackInSlot(i).isEmpty()) {
                out.add(cards.getStackInSlot(i));
            }
        }
        return out;
    }

    /** Crafting points added per tick; a craft costs 10, so 1 = one craft every half second. */
    public int speed() {
        int n = 0;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            n += upgrades.getStackInSlot(i).isEmpty() ? 0 : 1;
        }
        return Math.max(1, (int) Math.round((1 << n) * com.xtri6.grimstorage.BalanceConfig.assemblerSpeed()));
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        drop(level, pos, cards);
        drop(level, pos, upgrades);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("cards", cards.serializeNBT(registries));
        tag.put("upgrades", upgrades.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cards.deserializeNBT(registries, tag.getCompound("cards"));
        upgrades.deserializeNBT(registries, tag.getCompound("upgrades"));
    }
}
