package com.xtri6.grimstorage.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.item.CardData;
import com.xtri6.grimstorage.item.StorageCellItem;
import com.xtri6.grimstorage.storage.CellContents;
import com.xtri6.grimstorage.storage.CellStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Holds up to 8 storage cells. Cells keep their contents when moved between drives. */
public class DriveBlockEntity extends NetworkBlockEntity implements DropsContents {
    public static final int SLOTS = 8;
    private final ItemStackHandler cells = handler(SLOTS, 1, true, s -> s.getItem() instanceof StorageCellItem);

    public DriveBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.DRIVE_ENTITY.get(), pos, state);
    }

    public ItemStackHandler cells() {
        return cells;
    }

    public List<ItemStack> cellStacks() {
        List<ItemStack> out = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            out.add(cells.getStackInSlot(i));
        }
        return out;
    }

    @Override
    protected void onSlotChanged(int slot) {
        ItemStack stack = cells.getStackInSlot(slot);
        if (level != null && !level.isClientSide && stack.getItem() instanceof StorageCellItem && !stack.has(Registration.CELL_ID.get())) {
            stack.set(Registration.CELL_ID.get(), UUID.randomUUID());
        }
    }

    /** Updates each cell's tooltip numbers from its stored contents. */
    public void refreshStats(CellStorage storage) {
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = cells.getStackInSlot(i);
            UUID id = stack.get(Registration.CELL_ID.get());
            if (id == null) {
                continue;
            }
            CellContents c = storage.contents(id);
            CardData.CellStats stats = new CardData.CellStats(c.total(), c.types());
            if (!stats.equals(stack.get(Registration.CELL_STATS.get()))) {
                stack.set(Registration.CELL_STATS.get(), stats);
                changed = true;
            }
        }
        if (changed) {
            setChanged();
        }
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        drop(level, pos, cells);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("cells", cells.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cells.deserializeNBT(registries, tag.getCompound("cells"));
    }
}
