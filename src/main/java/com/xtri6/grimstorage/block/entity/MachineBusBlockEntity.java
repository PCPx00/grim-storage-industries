package com.xtri6.grimstorage.block.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.GrimStorage;
import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.BusBlock;
import com.xtri6.grimstorage.item.CardData;
import com.xtri6.grimstorage.item.ProcessingCardItem;
import com.xtri6.grimstorage.storage.ItemKey;
import com.xtri6.grimstorage.storage.MachineLink;
import com.xtri6.grimstorage.storage.NetworkManager;
import com.xtri6.grimstorage.storage.Pattern;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Machine Bus: clips onto any machine so the network can use it for autocrafting.
 * <ul>
 *     <li>Holds up to 9 Processing Cards for any machine from any mod.</li>
 *     <li>Facing a furnace, blast furnace or smoker (or any modded furnace built on them), it learns every recipe that
 *     machine can make, with no cards at all, and only takes results from the output slot.</li>
 *     <li>Keeps a furnace fuelled from the network while it has work.</li>
 *     <li>Several Machine Buses on the same kind of machine share big jobs.</li>
 * </ul>
 */
public class MachineBusBlockEntity extends NetworkBlockEntity implements MachineLink, DropsContents {
    public static final int SLOTS = 9;
    private static final int FURNACE_INPUT = 0;
    private static final int FURNACE_FUEL = 1;
    private static final int FURNACE_RESULT = 2;
    private static final java.util.List<net.minecraft.world.item.Item> FUELS = List.of(Items.COAL, Items.CHARCOAL, Items.COAL_BLOCK,
            Items.BLAZE_ROD, Items.DRIED_KELP_BLOCK);

    private final ItemStackHandler cards = handler(SLOTS, 1, true, s -> s.getItem() instanceof ProcessingCardItem);
    private boolean learn = true;
    private boolean fuel = true;

    // cached per network rebuild
    private List<Pattern> learned = List.of();
    @Nullable
    private Component machineName;

    public MachineBusBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.MACHINE_BUS_ENTITY.get(), pos, state);
    }

    public ItemStackHandler cardHandler() {
        return cards;
    }

    public boolean learns() {
        return learn;
    }

    public boolean fuels() {
        return fuel;
    }

    public void toggleLearn() {
        learn = !learn;
        setChanged();
        NetworkManager.markDirty();
    }

    public void toggleFuel() {
        fuel = !fuel;
        setChanged();
    }

    /** Recipes it currently offers (cards plus learned). */
    public int recipeCount() {
        return cards().size() + (learn ? learned.size() : 0);
    }

    private Direction facing() {
        BlockState st = getBlockState();
        return st.hasProperty(BusBlock.FACING) ? st.getValue(BusBlock.FACING) : Direction.NORTH;
    }

    private BlockPos target() {
        return worldPosition.relative(facing());
    }

    @Nullable
    private AbstractFurnaceBlockEntity furnace() {
        return level != null && level.getBlockEntity(target()) instanceof AbstractFurnaceBlockEntity f ? f : null;
    }

    @Override
    public boolean linkRemoved() {
        return isRemoved();
    }

    @Override
    public List<ItemStack> cards() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (!cards.getStackInSlot(i).isEmpty()) {
                out.add(cards.getStackInSlot(i));
            }
        }
        return out;
    }

    /** The whole machine inventory (furnaces: input, fuel and result slots), falling back to the face we touch. */
    @Nullable
    private IItemHandler machineAll() {
        if (level == null) {
            return null;
        }
        Direction f = facing();
        IItemHandler all = level.getCapability(Capabilities.ItemHandler.BLOCK, target(), null);
        return all != null ? all : level.getCapability(Capabilities.ItemHandler.BLOCK, target(), f.getOpposite());
    }

    /** Ingredients go into a furnace's input slot whatever side the bus is on; other machines get them on our face. */
    @Nullable
    @Override
    public IItemHandler machineInput() {
        if (level == null) {
            return null;
        }
        if (furnace() != null) {
            IItemHandler all = machineAll();
            return all == null ? null : new OneSlot(all, FURNACE_INPUT);
        }
        Direction f = facing();
        return level.getCapability(Capabilities.ItemHandler.BLOCK, target(), f.getOpposite());
    }

    // ------------------------------------------------------------------ learning

    @Override
    public List<Pattern> learnedPatterns() {
        learned = learn ? learn() : List.of();
        return learned;
    }

    private List<Pattern> learn() {
        AbstractFurnaceBlockEntity f = furnace();
        if (f == null || level == null) {
            machineName = level == null ? null : level.getBlockState(target()).getBlock().getName();
            return List.of();
        }
        machineName = f.getBlockState().getBlock().getName();
        lastMachine = f.getBlockState().getBlock();
        RecipeType<? extends AbstractCookingRecipe> type = recipeType(f);
        if (type == null) {
            return List.of();
        }
        List<Pattern> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<RecipeHolder<? extends AbstractCookingRecipe>> recipes = (List) level.getRecipeManager().getAllRecipesFor((RecipeType) type);
        for (RecipeHolder<? extends AbstractCookingRecipe> holder : recipes) {
            AbstractCookingRecipe r = holder.value();
            ItemStack result = r.getResultItem(level.registryAccess());
            ItemKey outKey = ItemKey.of(result);
            if (outKey == null || r.getIngredients().isEmpty()) {
                continue;
            }
            Ingredient ing = r.getIngredients().get(0);
            for (ItemStack in : ing.getItems()) {
                ItemKey inKey = ItemKey.of(in);
                if (inKey != null && seen.add(inKey + ">" + outKey)) {
                    out.add(Pattern.learned(inKey, 1, outKey, result.getCount(), this));
                }
            }
        }
        return out;
    }

    /** Which recipes a furnace-like block cooks, read from the block entity itself so modded furnaces work too. */
    @Nullable
    @SuppressWarnings("unchecked")
    private static RecipeType<? extends AbstractCookingRecipe> recipeType(AbstractFurnaceBlockEntity f) {
        for (Class<?> c = f.getClass(); c != null && c != BlockEntity.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field field : c.getDeclaredFields()) {
                if (RecipeType.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        return (RecipeType<? extends AbstractCookingRecipe>) field.get(f);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        GrimStorage.LOGGER.debug("Machine Bus could not read the recipe type of {}", f, e);
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    public Component machineName() {
        if (machineName == null && level != null) {
            machineName = level.getBlockState(target()).getBlock().getName();
        }
        return machineName;
    }

    // ------------------------------------------------------------------ results and fuel

    @Override
    public void pullOutputs(StorageNetwork network) {
        IItemHandler machine = machineAll();
        if (machine == null) {
            return;
        }
        if (furnace() != null) {
            // only the result slot: an ingredient waiting in the input slot can be another recipe's output
            pull(network, machine, FURNACE_RESULT, null);
            if (fuel) {
                refuel(network, machine);
            }
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
            pull(network, machine, slot, wanted);
        }
    }

    private static void pull(StorageNetwork network, IItemHandler machine, int slot, @Nullable Set<ItemKey> wanted) {
        if (slot >= machine.getSlots()) {
            return;
        }
        ItemStack in = machine.getStackInSlot(slot);
        ItemKey key = ItemKey.of(in);
        if (key == null || (wanted != null && !wanted.contains(key))) {
            return;
        }
        ItemStack can = machine.extractItem(slot, in.getCount(), true);
        long fits = can.isEmpty() ? 0 : network.insert(key, can.getCount(), true);
        if (fits <= 0) {
            return;
        }
        ItemStack got = machine.extractItem(slot, (int) fits, false);
        if (!got.isEmpty()) {
            network.insertOrBuffer(got);
            network.onProcessingOutput(key, got.getCount());
        }
    }

    /**
     * A furnace with something to cook and an empty fuel slot gets one piece of fuel from the network. One at a
     * time, so fuel is shared fairly between furnaces and never piles up in one of them.
     */
    private void refuel(StorageNetwork network, IItemHandler machine) {
        if (machine.getSlots() <= FURNACE_FUEL || machine.getStackInSlot(FURNACE_INPUT).isEmpty()
                || !machine.getStackInSlot(FURNACE_FUEL).isEmpty()) {
            return;
        }
        AbstractFurnaceBlockEntity f = furnace();
        if (f == null) {
            return;
        }
        for (net.minecraft.world.item.Item item : FUELS) {
            ItemKey k = ItemKey.of(new ItemStack(item));
            long have = network.extract(k, 1, true);
            if (have <= 0) {
                continue;
            }
            ItemStack offer = k.toStack((int) have);
            int took = offer.getCount() - machine.insertItem(FURNACE_FUEL, offer, true).getCount();
            if (took > 0) {
                long got = network.extract(k, took, false);
                ItemStack left = machine.insertItem(FURNACE_FUEL, k.toStack((int) got), false);
                network.insertOrBuffer(left);
                return;
            }
        }
    }

    @Nullable
    private net.minecraft.world.level.block.Block lastMachine;

    /** A different machine (or none) in front: learn its recipes again. A furnace lighting up does not count. */
    public void neighborChanged(BlockPos from) {
        if (level == null || level.isClientSide || !from.equals(target())) {
            return;
        }
        net.minecraft.world.level.block.Block now = level.getBlockState(from).getBlock();
        if (now != lastMachine) {
            lastMachine = now;
            machineName = null;
            NetworkManager.markDirty();
        }
    }

    @Override
    public void dropContents(Level lvl, BlockPos pos) {
        drop(lvl, pos, cards);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("cards", cards.serializeNBT(registries));
        tag.putBoolean("learn", learn);
        tag.putBoolean("fuel", fuel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cards.deserializeNBT(registries, tag.getCompound("cards"));
        if (tag.contains("learn")) {
            learn = tag.getBoolean("learn");
            fuel = tag.getBoolean("fuel");
        }
    }

    /** One slot of a bigger inventory, so ingredients only ever go into a furnace's input slot. */
    private record OneSlot(IItemHandler inner, int slot) implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int s) {
            return inner.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int s, ItemStack stack, boolean simulate) {
            return inner.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int s, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int s) {
            return inner.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int s, ItemStack stack) {
            return inner.isItemValid(slot, stack);
        }
    }
}
