package com.xtri6.grimstorage.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.recipe.InfusingRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** The Soul Infuser: 3 input slots, 1 output, no fuel. Pipes can insert from any side and pull from below. */
public class SoulInfuserBlockEntity extends BlockEntity implements Ticking, DropsContents {
    public static final int OUTPUT = 3;

    private final ItemStackHandler items = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot != OUTPUT) {
                checkRecipe = true;
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot != OUTPUT;
        }
    };
    private final IItemHandler automation = new Automation();
    private int progress;
    private int total;
    private boolean checkRecipe = true;
    private RecipeHolder<InfusingRecipe> current;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? progress : total;
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                progress = value;
            } else {
                total = value;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public SoulInfuserBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.SOUL_INFUSER_ENTITY.get(), pos, state);
    }

    public ItemStackHandler items() {
        return items;
    }

    public IItemHandler automation() {
        return automation;
    }

    public ContainerData data() {
        return data;
    }

    private InfusingRecipe.Input input() {
        List<ItemStack> list = new ArrayList<>(InfusingRecipe.SLOTS);
        for (int i = 0; i < InfusingRecipe.SLOTS; i++) {
            list.add(items.getStackInSlot(i));
        }
        return new InfusingRecipe.Input(list);
    }

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        if (checkRecipe) {
            checkRecipe = false;
            InfusingRecipe.Input in = input();
            Optional<RecipeHolder<InfusingRecipe>> found = sl.getRecipeManager().getRecipeFor(Registration.INFUSING_TYPE.get(), in, sl);
            if (current == null || found.isEmpty() || !found.get().id().equals(current.id())) {
                progress = 0;
            }
            current = found.orElse(null);
            total = current == null ? 0 : current.value().time();
        }
        if (current == null) {
            return;
        }
        ItemStack result = current.value().result();
        if (!outputFits(result)) {
            return;
        }
        progress++;
        if (progress % 20 == 0) {
            sl.sendParticles(ParticleTypes.SOUL, worldPosition.getX() + 0.5, worldPosition.getY() + 1.05, worldPosition.getZ() + 0.5,
                    2, 0.2, 0.05, 0.2, 0.01);
        }
        if (progress >= total) {
            int[] slots = current.value().assign(input());
            if (slots == null) {
                checkRecipe = true;
                progress = 0;
                return;
            }
            for (int s : slots) {
                items.extractItem(s, 1, false);
            }
            ItemStack out = items.getStackInSlot(OUTPUT);
            items.setStackInSlot(OUTPUT, out.isEmpty() ? result.copy() : out.copyWithCount(out.getCount() + result.getCount()));
            progress = 0;
            checkRecipe = true;
            sl.playSound(null, worldPosition, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.8f, 1.2f);
        }
    }

    /** The output slot refuses inserts from pipes, so the machine checks room itself. */
    private boolean outputFits(ItemStack result) {
        ItemStack out = items.getStackInSlot(OUTPUT);
        return out.isEmpty() || (ItemStack.isSameItemSameComponents(out, result)
                && out.getCount() + result.getCount() <= Math.min(out.getMaxStackSize(), items.getSlotLimit(OUTPUT)));
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < items.getSlots(); i++) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, items.getStackInSlot(i).copy());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.deserializeNBT(registries, tag.getCompound("items"));
        progress = tag.getInt("progress");
        checkRecipe = true;
    }

    /** What pipes and hoppers see: inputs accept items, only the output can be pulled. */
    private final class Automation implements IItemHandler {
        @Override
        public int getSlots() {
            return 4;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot == OUTPUT) {
                return stack;
            }
            // Keep one item type per slot so recipes stay matchable.
            for (int i = 0; i < InfusingRecipe.SLOTS; i++) {
                if (i != slot && ItemStack.isSameItemSameComponents(items.getStackInSlot(i), stack)) {
                    return slot < i ? stack : items.insertItem(i, stack, simulate);
                }
            }
            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == OUTPUT ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot != OUTPUT;
        }
    }
}
