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
        List<IItemHandler> sides = sideHandlers();
        if (sides.isEmpty()) {
            return null;
        }
        // machines with side settings (Mekanism and friends) often don't take items on every face: try the
        // face we touch first, then the machine's other sides, so it works whatever its sides are set to
        return sides.size() == 1 ? sides.get(0) : new EitherInput(sides);
    }

    /** The machine's inventory as seen from each side, the face we touch first, then the rest, then no side. */
    private List<IItemHandler> sideHandlers() {
        List<IItemHandler> out = new ArrayList<>(7);
        if (level == null) {
            return out;
        }
        Direction f = facing().getOpposite();
        List<Direction> order = new ArrayList<>();
        order.add(f);
        for (Direction d : Direction.values()) {
            if (d != f) {
                order.add(d);
            }
        }
        order.add(null);
        for (Direction d : order) {
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, target(), d);
            if (h != null && h.getSlots() > 0 && out.stream().noneMatch(o -> o == h)) {
                out.add(h);
            }
        }
        return out;
    }

    /** Several views of one machine, in order: an item goes in through the first that takes it. */
    private static final class EitherInput implements IItemHandler {
        private final List<IItemHandler> parts;
        private final int[] starts;
        private final int slots;

        EitherInput(List<IItemHandler> parts) {
            this.parts = parts;
            this.starts = new int[parts.size()];
            int n = 0;
            for (int i = 0; i < parts.size(); i++) {
                starts[i] = n;
                n += parts.get(i).getSlots();
            }
            this.slots = n;
        }

        @Override
        public int getSlots() {
            return slots;
        }

        private int part(int slot) {
            int p = 0;
            while (p + 1 < starts.length && slot >= starts[p + 1]) {
                p++;
            }
            return p;
        }

        private IItemHandler of(int slot) {
            return parts.get(part(slot));
        }

        private int local(int slot) {
            return slot - starts[part(slot)];
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return of(slot).getStackInSlot(local(slot));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return of(slot).insertItem(local(slot), stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return of(slot).getSlotLimit(local(slot));
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return of(slot).isItemValid(local(slot), stack);
        }
    }

    // ------------------------------------------------------------------ learning

    @Override
    public List<Pattern> learnedPatterns() {
        learned = learn ? learn() : List.of();
        java.util.Set<com.xtri6.grimstorage.storage.BulkKey> outs = new java.util.HashSet<>();
        java.util.Set<com.xtri6.grimstorage.storage.BulkKey> ins = new java.util.HashSet<>();
        for (Pattern p : learned) {
            outs.addAll(p.bulkOutputs().keySet());
            ins.addAll(p.bulkInputs().keySet());
        }
        // a tank that is both (a slurry one recipe makes and another uses) is left alone: it may be an input
        outs.removeAll(ins);
        bulkResults = outs;
        java.util.Set<ItemKey> itemIns = new java.util.HashSet<>();
        java.util.Set<ItemKey> itemOuts = new java.util.HashSet<>();
        for (Pattern p : learned) {
            itemIns.addAll(p.inputs().keySet());
            itemOuts.addAll(p.outputs().keySet());
        }
        itemIns.removeAll(itemOuts);
        ingredientsOnly = itemIns;
        return learned;
    }

    /**
     * Items this machine's recipes only ever use, never make: they are never pulled back out, whatever the slot
     * says (a one-slot machine like the Imbuement Chamber reports its full slot as not taking items).
     */
    private java.util.Set<ItemKey> ingredientsOnly = java.util.Set.of();

    /** Fluids and chemicals this machine makes and never takes: pulled out of it into the network. */
    private java.util.Set<com.xtri6.grimstorage.storage.BulkKey> bulkResults = java.util.Set.of();

    // ------------------------------------------------------------------ fluids and chemicals for the machine

    /** Fluids and chemicals recent jobs need here, with when they were last needed. */
    private final java.util.Map<com.xtri6.grimstorage.storage.BulkKey, Long> feeding = new java.util.LinkedHashMap<>();
    /** How long to keep a machine supplied after the last job that needed it was sent (1 minute). */
    private static final long FEED_FOR = 1200;
    private static final long FEED_PER_CHECK = 4000;
    private final java.util.Map<Direction, com.xtri6.grimstorage.compat.Compat.BulkLink> chemicalLinks = new java.util.EnumMap<>(Direction.class);

    /**
     * Source still to give the machine for recipes already sent. Source machines hold far more than a recipe needs
     * (an Imbuement Chamber takes millions), so they get exactly what each recipe costs and no more.
     */
    private long sourceOwed;

    @Override
    public void onSent(Pattern pattern) {
        if (level != null) {
            for (java.util.Map.Entry<com.xtri6.grimstorage.storage.BulkKey, Long> e : pattern.bulkInputs().entrySet()) {
                if (e.getKey().kind() == com.xtri6.grimstorage.storage.BulkKind.SOURCE) {
                    sourceOwed += e.getValue();
                } else {
                    feeding.put(e.getKey(), level.getGameTime());
                }
            }
        }
    }

    /** Tops the machine's tanks up with what its jobs need, from the network. */
    private void feed(StorageNetwork network) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) {
            return;
        }
        if (sourceOwed > 0) {
            com.xtri6.grimstorage.storage.BulkKey src = com.xtri6.grimstorage.storage.BulkKey.flat(com.xtri6.grimstorage.storage.BulkKind.SOURCE);
            for (Direction d : sidesFaceFirst()) {
                com.xtri6.grimstorage.compat.Compat.BulkLink link = bulkLink(sl, com.xtri6.grimstorage.storage.BulkKind.SOURCE, d);
                long moved = link == null ? 0 : link.offer(network, src, Math.min(sourceOwed, FEED_PER_CHECK), false);
                if (moved > 0) {
                    sourceOwed -= moved;
                    break;
                }
            }
        }
        if (feeding.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        feeding.values().removeIf(t -> now - t > FEED_FOR);
        for (com.xtri6.grimstorage.storage.BulkKey k : feeding.keySet()) {
            if (k.kind() == com.xtri6.grimstorage.storage.BulkKind.FLUID) {
                feedFluid(network, k);
            } else if (k.kind() == com.xtri6.grimstorage.storage.BulkKind.CHEMICAL) {
                for (Direction d : sidesFaceFirst()) {
                    com.xtri6.grimstorage.compat.Compat.BulkLink link = bulkLink(sl, k.kind(), d);
                    if (link != null && link.push(network, FEED_PER_CHECK, List.of(k)) > 0) {
                        break;
                    }
                }
            }
        }
    }

    private final java.util.Map<Direction, com.xtri6.grimstorage.compat.Compat.BulkLink> sourceLinks = new java.util.EnumMap<>(Direction.class);

    /** The machine's chemical tanks or Source buffer as seen from one side (null without Mekanism / Ars Nouveau). */
    @Nullable
    private com.xtri6.grimstorage.compat.Compat.BulkLink bulkLink(net.minecraft.server.level.ServerLevel sl,
            com.xtri6.grimstorage.storage.BulkKind kind, Direction d) {
        if (kind == com.xtri6.grimstorage.storage.BulkKind.CHEMICAL) {
            com.xtri6.grimstorage.compat.Compat.Chemicals chem = com.xtri6.grimstorage.compat.Compat.chemicals();
            return chem == null ? null : chemicalLinks.computeIfAbsent(d, side -> chem.link(sl, target(), side, () -> !isRemoved(), () -> {}));
        }
        if (kind == com.xtri6.grimstorage.storage.BulkKind.SOURCE) {
            com.xtri6.grimstorage.compat.Compat.Source src = com.xtri6.grimstorage.compat.Compat.source();
            return src == null ? null : sourceLinks.computeIfAbsent(d, side -> src.link(sl, target(), side, () -> !isRemoved(), () -> {}));
        }
        return null;
    }

    /** Fills the machine's tanks with exactly this much, for recipes that only take fluids and chemicals. */
    @Override
    public long fillBulk(StorageNetwork network, com.xtri6.grimstorage.storage.BulkKey key, long amount, boolean simulate) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl) || amount <= 0) {
            return 0;
        }
        long done = 0;
        for (Direction d : sidesFaceFirst()) {
            if (done >= amount) {
                break;
            }
            long want = amount - done;
            if (key.kind() == com.xtri6.grimstorage.storage.BulkKind.FLUID) {
                net.neoforged.neoforge.fluids.capability.IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, target(), d);
                if (h == null) {
                    continue;
                }
                int fits = h.fill(key.fluid((int) Math.min(want, Integer.MAX_VALUE)), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
                if (fits <= 0) {
                    continue;
                }
                if (simulate) {
                    done += fits;
                    continue;
                }
                long got = network.extractBulk(key, fits, false);
                int took = h.fill(key.fluid((int) got), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                if (took < got) {
                    network.insertBulk(key, got - took, false);
                }
                done += took;
            } else {
                com.xtri6.grimstorage.compat.Compat.BulkLink link = bulkLink(sl, key.kind(), d);
                long moved = link == null ? 0 : link.offer(network, key, want, simulate);
                if (moved > 0) {
                    done += moved;
                }
            }
            if (simulate && done > 0) {
                // every side is the same tank on most machines: one side's answer is the answer
                break;
            }
        }
        return done;
    }

    /** Most of a fluid or chemical pulled out of the machine each check. */
    private static final long PULL_PER_CHECK = 64_000;

    /** Moves the fluids and chemicals this machine made into the network, and tells waiting jobs. */
    private void pullBulk(StorageNetwork network) {
        if (bulkResults.isEmpty() || !(level instanceof net.minecraft.server.level.ServerLevel sl)) {
            return;
        }
        boolean chemicals = false;
        for (com.xtri6.grimstorage.storage.BulkKey k : bulkResults) {
            if (k.kind() == com.xtri6.grimstorage.storage.BulkKind.FLUID) {
                pullFluid(network, k);
            } else if (k.kind() == com.xtri6.grimstorage.storage.BulkKind.CHEMICAL) {
                chemicals = true;
            }
        }
        if (!chemicals) {
            return;
        }
        for (Direction d : sidesFaceFirst()) {
            com.xtri6.grimstorage.compat.Compat.BulkLink link = bulkLink(sl, com.xtri6.grimstorage.storage.BulkKind.CHEMICAL, d);
            if (link == null) {
                return;
            }
            java.util.Set<com.xtri6.grimstorage.storage.BulkKey> seen = new java.util.LinkedHashSet<>();
            java.util.Map<com.xtri6.grimstorage.storage.BulkKey, Long> before = new java.util.HashMap<>();
            long moved = link.pull(network, PULL_PER_CHECK, k -> {
                if (!bulkResults.contains(k)) {
                    return false;
                }
                if (seen.add(k)) {
                    before.put(k, network.countBulk(k));
                }
                return true;
            });
            if (moved > 0) {
                for (com.xtri6.grimstorage.storage.BulkKey k : seen) {
                    long got = network.countBulk(k) - before.get(k);
                    if (got > 0) {
                        network.onProcessingOutput(k, got);
                    }
                }
                return;
            }
        }
    }

    private void pullFluid(StorageNetwork network, com.xtri6.grimstorage.storage.BulkKey k) {
        for (Direction d : sidesFaceFirst()) {
            net.neoforged.neoforge.fluids.capability.IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, target(), d);
            if (h == null) {
                continue;
            }
            net.neoforged.neoforge.fluids.FluidStack can = h.drain(k.fluid((int) PULL_PER_CHECK), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
            long fits = can.isEmpty() ? 0 : network.insertBulk(k, can.getAmount(), true);
            if (fits <= 0) {
                continue;
            }
            net.neoforged.neoforge.fluids.FluidStack got = h.drain(k.fluid((int) fits), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            if (!got.isEmpty()) {
                network.insertBulk(k, got.getAmount(), false);
                network.onProcessingOutput(k, got.getAmount());
            }
            return;
        }
    }

    private void feedFluid(StorageNetwork network, com.xtri6.grimstorage.storage.BulkKey k) {
        for (Direction d : sidesFaceFirst()) {
            net.neoforged.neoforge.fluids.capability.IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, target(), d);
            if (h == null) {
                continue;
            }
            long have = network.extractBulk(k, FEED_PER_CHECK, true);
            if (have <= 0) {
                return;
            }
            int fits = h.fill(k.fluid((int) have), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
            if (fits <= 0) {
                continue;
            }
            long got = network.extractBulk(k, fits, false);
            int took = h.fill(k.fluid((int) got), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            if (took < got) {
                network.insertBulk(k, got - took, false);
            }
            return;
        }
    }

    private List<Direction> sidesFaceFirst() {
        Direction f = facing().getOpposite();
        List<Direction> order = new ArrayList<>(6);
        order.add(f);
        for (Direction d : Direction.values()) {
            if (d != f) {
                order.add(d);
            }
        }
        return order;
    }

    private static final boolean MEKANISM = net.neoforged.fml.ModList.get() != null && net.neoforged.fml.ModList.get().isLoaded("mekanism");
    private int sideChecks;

    /** Most patterns one bus offers, so a machine with a huge recipe list can't slow the network down. */
    private static final int MAX_LEARNED = 4000;

    private List<Pattern> learn() {
        AbstractFurnaceBlockEntity f = furnace();
        if (level == null) {
            return List.of();
        }
        if (f == null) {
            BlockState st = level.getBlockState(target());
            machineName = st.getBlock().getName();
            lastMachine = st.getBlock();
            return st.isAir() ? List.of() : learnFromRecipes(st);
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

    /**
     * Any other machine: its recipe types come from the datapack table (Mekanism, Create, Ender IO, Immersive
     * Engineering, Create, Ars Nouveau and more ship with the mod) or a matching name, and each recipe is read by
     * {@link com.xtri6.grimstorage.storage.RecipeReader}.
     */
    private List<Pattern> learnFromRecipes(BlockState st) {
        List<Pattern> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RecipeType<?> type : com.xtri6.grimstorage.storage.MachineRecipes.INSTANCE.typesFor(st)) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            List<RecipeHolder<?>> recipes = (List) level.getRecipeManager().getAllRecipesFor((RecipeType) type);
            for (RecipeHolder<?> holder : recipes) {
                com.xtri6.grimstorage.storage.RecipeReader.Read read =
                        com.xtri6.grimstorage.storage.RecipeReader.read(holder.value(), level.registryAccess());
                if (read == null || !read.usable()) {
                    continue;
                }
                java.util.Map<ItemKey, Long> outs = new java.util.LinkedHashMap<>();
                if (read.output() != null) {
                    outs.put(read.output(), read.outCount());
                }
                if (read.inputs().isEmpty()) {
                    // only fluids and chemicals go in (a Chemical Washer): one pattern
                    if (seen.add(read.bulk() + ">" + outs + read.bulkOut())) {
                        out.add(Pattern.learned(java.util.Map.of(), outs, read.bulk(), read.bulkOut(), this));
                    }
                    if (out.size() >= MAX_LEARNED) {
                        return out;
                    }
                    continue;
                }
                // one pattern per choice of the ingredient with the most choices ("any log"), the rest take their first
                int vary = 0;
                for (int i = 1; i < read.inputs().size(); i++) {
                    if (read.inputs().get(i).options().size() > read.inputs().get(vary).options().size()) {
                        vary = i;
                    }
                }
                for (ItemKey choice : read.inputs().get(vary).options()) {
                    java.util.Map<ItemKey, Long> in = new java.util.LinkedHashMap<>();
                    for (int i = 0; i < read.inputs().size(); i++) {
                        com.xtri6.grimstorage.storage.RecipeReader.Input input = read.inputs().get(i);
                        in.merge(i == vary ? choice : input.options().get(0), input.count(), Long::sum);
                    }
                    if ((read.output() != null && in.containsKey(read.output())) || !seen.add(in + ">" + outs + read.bulkOut())) {
                        continue;
                    }
                    out.add(Pattern.learned(in, outs, read.bulk(), read.bulkOut(), this));
                    if (out.size() >= MAX_LEARNED) {
                        return out;
                    }
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
    @Override
    public Component machineName() {
        if (machineName == null && level != null) {
            machineName = level.getBlockState(target()).getBlock().getName();
        }
        return machineName;
    }

    // ------------------------------------------------------------------ results and fuel

    @Override
    public void pullOutputs(StorageNetwork network) {
        if (MEKANISM && level != null && (++sideChecks & 7) == 0) {
            com.xtri6.grimstorage.compat.MekanismSides.open(level, target(), facing().getOpposite());
        }
        IItemHandler machine = machineAll();
        if (machine == null) {
            if (fuel) {
                power(network);
            }
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
        if (fuel) {
            power(network);
        }
        feed(network);
        if (learn) {
            pullBulk(network);
        }
        if (learn && !learned.isEmpty()) {
            for (IItemHandler side : sideHandlers()) {
                pullFinished(network, side, ingredientsOnly);
            }
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
        for (IItemHandler side : sideHandlers()) {
            for (int slot = 0; slot < side.getSlots(); slot++) {
                pull(network, side, slot, wanted);
            }
        }
    }

    /**
     * A machine whose recipes were learned: everything in its output slots comes back, results and extras alike
     * (sawdust, slag, a chance output) so nothing clogs it. Slots that still take items are its inputs and are left
     * alone, even when they hold something another recipe makes.
     */
    private static void pullFinished(StorageNetwork network, IItemHandler machine, java.util.Set<ItemKey> ingredientsOnly) {
        for (int slot = 0; slot < machine.getSlots(); slot++) {
            ItemStack in = machine.getStackInSlot(slot);
            if (in.isEmpty() || ingredientsOnly.contains(ItemKey.of(in))) {
                continue;
            }
            boolean full = in.getCount() >= Math.min(machine.getSlotLimit(slot), in.getMaxStackSize());
            boolean takesMore = !full && machine.insertItem(slot, in.copyWithCount(1), true).isEmpty();
            boolean inputSlot = takesMore || (full && machine.isItemValid(slot, in));
            if (!inputSlot) {
                pull(network, machine, slot, null);
            }
        }
    }

    /** Machines that run on power get it from the network's energy cells, so a job never stalls on an empty buffer. */
    private void power(StorageNetwork network) {
        if (level == null) {
            return;
        }
        net.neoforged.neoforge.energy.IEnergyStorage energy = null;
        Direction face = facing().getOpposite();
        List<Direction> order = new ArrayList<>();
        order.add(face);
        for (Direction d : Direction.values()) {
            if (d != face) {
                order.add(d);
            }
        }
        order.add(null);
        for (Direction d : order) {
            net.neoforged.neoforge.energy.IEnergyStorage e = level.getCapability(Capabilities.EnergyStorage.BLOCK, target(), d);
            if (e != null && e.canReceive()) {
                energy = e;
                break;
            }
        }
        if (energy == null) {
            return;
        }
        int want = energy.receiveEnergy(MAX_POWER, true);
        if (want <= 0) {
            return;
        }
        com.xtri6.grimstorage.storage.BulkKey fe = com.xtri6.grimstorage.storage.BulkKey.flat(com.xtri6.grimstorage.storage.BulkKind.ENERGY);
        long got = network.extractBulk(fe, want, false);
        if (got > 0) {
            int took = energy.receiveEnergy((int) got, false);
            if (took < got) {
                network.insertBulk(fe, got - took, false);
            }
        }
    }

    /** Energy a Machine Bus can hand its machine each time it checks (every 5 ticks). */
    private static final int MAX_POWER = 200_000;

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
        tag.putLong("sourceOwed", sourceOwed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cards.deserializeNBT(registries, tag.getCompound("cards"));
        if (tag.contains("learn")) {
            learn = tag.getBoolean("learn");
            fuel = tag.getBoolean("fuel");
        }
        sourceOwed = tag.getLong("sourceOwed");
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
