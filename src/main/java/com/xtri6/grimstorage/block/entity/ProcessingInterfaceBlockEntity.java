package com.xtri6.grimstorage.block.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.FacingDeviceBlock;
import com.xtri6.grimstorage.item.CardData;
import com.xtri6.grimstorage.item.ProcessingCardItem;
import com.xtri6.grimstorage.storage.ItemKey;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Faces a machine (any mod's). Sends Processing Card inputs into it and pulls the card's outputs back out
 * into the network, which works with every machine that exposes an item inventory.
 */
public class ProcessingInterfaceBlockEntity extends NetworkBlockEntity implements DropsContents {
    public static final int SLOTS = 9;
    private final ItemStackHandler cards = handler(SLOTS, 1, true, s -> s.getItem() instanceof ProcessingCardItem);

    public ProcessingInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.PROCESSING_INTERFACE_ENTITY.get(), pos, state);
    }

    public ItemStackHandler cardHandler() {
        return cards;
    }

    public List<ItemStack> cards() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (!cards.getStackInSlot(i).isEmpty()) {
                out.add(cards.getStackInSlot(i));
            }
        }
        return out;
    }

    private Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(FacingDeviceBlock.All.FACING) ? state.getValue(FacingDeviceBlock.All.FACING) : Direction.NORTH;
    }

    /** The machine's inventory as seen from the side this interface touches. */
    @Nullable
    public IItemHandler machineInput() {
        if (level == null) {
            return null;
        }
        Direction f = facing();
        return level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(f), f.getOpposite());
    }

    @Nullable
    private IItemHandler machineAll() {
        if (level == null) {
            return null;
        }
        Direction f = facing();
        BlockPos target = worldPosition.relative(f);
        IItemHandler all = level.getCapability(Capabilities.ItemHandler.BLOCK, target, null);
        if (all == null) {
            all = level.getCapability(Capabilities.ItemHandler.BLOCK, target, Direction.DOWN);
        }
        return all != null ? all : machineInput();
    }

    /** Moves any of this interface's card outputs that the machine has finished into the network. */
    public void pullOutputs(StorageNetwork network) {
        IItemHandler machine = machineAll();
        if (machine == null) {
            return;
        }
        Set<ItemKey> wanted = new HashSet<>();
        for (ItemStack card : cards()) {
            CardData.Processing data = card.get(Registration.PROCESSING_CARD_DATA.get());
            if (data != null) {
                for (ItemStack out : data.outputs()) {
                    ItemKey k = ItemKey.of(out);
                    if (k != null) {
                        wanted.add(k);
                    }
                }
            }
        }
        if (wanted.isEmpty()) {
            return;
        }
        for (int slot = 0; slot < machine.getSlots(); slot++) {
            ItemStack in = machine.getStackInSlot(slot);
            ItemKey key = ItemKey.of(in);
            if (key == null || !wanted.contains(key)) {
                continue;
            }
            ItemStack can = machine.extractItem(slot, in.getCount(), true);
            if (can.isEmpty()) {
                continue;
            }
            long fits = network.insert(key, can.getCount(), true);
            if (fits <= 0) {
                continue;
            }
            ItemStack got = machine.extractItem(slot, (int) fits, false);
            if (!got.isEmpty()) {
                network.insertOrBuffer(got);
                network.onProcessingOutput(key, got.getCount());
            }
        }
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        drop(level, pos, cards);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("cards", cards.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cards.deserializeNBT(registries, tag.getCompound("cards"));
    }
}
