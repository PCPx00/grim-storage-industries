package com.xtri6.grimstorage.block.entity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.BusBlock;
import com.xtri6.grimstorage.compat.Compat;
import com.xtri6.grimstorage.storage.BulkKey;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.Experience;
import com.xtri6.grimstorage.storage.ItemKey;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Import Bus and Export Bus. Sits on the face of any block and moves items, fluids, chemicals, energy, Source and
 * XP between that block and the network.
 *
 * <p>Built to stay cheap with hundreds placed: the block's capabilities are cached (NeoForge tells us when they
 * change), a bus that moved something runs again next tick, and one that found nothing to do backs off up to once a
 * second until something nearby changes. Each run has a fixed budget, and big inventories are scanned a slice at a
 * time instead of all at once.
 */
public class BusBlockEntity extends FilterBlockEntity implements Ticking {
    /** Resource switches: bit 0 is items, then one bit per {@link BulkKind}. */
    public static final int ITEMS_BIT = 0;
    public static final int RESOURCES = 1 + BulkKind.values().length;

    // Per-run budgets. With one run per tick while busy, that is 2,560 items/s or 640 B of fluid/s per bus.
    private static final int ITEM_BUDGET = 128;
    private static final int SLOTS_PER_RUN = 27;
    private static final int FLUID_BUDGET = 32_000;
    private static final long CHEMICAL_BUDGET = 32_000;
    private static final long SOURCE_BUDGET = 10_000;
    private static final long XP_BUDGET = 10_000;
    private static final int MAX_SLEEP = 20;

    public enum Status { WORKING, IDLE, NO_NETWORK, NOTHING_ATTACHED, OWN_NETWORK, FULL }

    private final boolean export;
    private boolean blacklist;
    private boolean matchComponents = true;
    private int resources;

    // runtime only
    private int sleep;
    private int wait;
    private int slotCursor;
    private int keyCursor;
    private int age;
    private Status status = Status.IDLE;
    @Nullable
    private Direction linkedFacing;
    @Nullable
    private BlockCapabilityCache<IItemHandler, Direction> items;
    @Nullable
    private BlockCapabilityCache<IFluidHandler, Direction> fluids;
    @Nullable
    private BlockCapabilityCache<IEnergyStorage, Direction> energy;
    @Nullable
    private Compat.BulkLink chemicals;
    @Nullable
    private Compat.BulkLink source;
    private List<ItemKey> exportSnapshot = List.of();
    private int snapshotAge = Integer.MAX_VALUE;

    public BusBlockEntity(BlockPos pos, BlockState state) {
        super(state.is(Registration.EXPORT_BUS.get()) ? Registration.EXPORT_BUS_ENTITY.get() : Registration.IMPORT_BUS_ENTITY.get(), pos, state);
        this.export = state.is(Registration.EXPORT_BUS.get());
        // An Import Bus takes everything by default; an Export Bus only sends out the items you pick.
        this.blacklist = !export;
        this.resources = export ? 1 << ITEMS_BIT : (1 << RESOURCES) - 1;
    }

    public boolean isExport() {
        return export;
    }

    public boolean blacklist() {
        return blacklist;
    }

    public boolean matchComponents() {
        return matchComponents;
    }

    public int resources() {
        return resources;
    }

    public boolean moves(int bit) {
        return (resources & (1 << bit)) != 0;
    }

    public boolean moves(BulkKind kind) {
        return moves(1 + kind.ordinal());
    }

    public Status status() {
        return status;
    }

    public void toggleBlacklist() {
        blacklist = !blacklist;
        changedSettings();
    }

    public void toggleMatchComponents() {
        matchComponents = !matchComponents;
        changedSettings();
    }

    public void toggleResource(int bit) {
        if (bit >= 0 && bit < RESOURCES) {
            resources ^= 1 << bit;
            changedSettings();
        }
    }

    private void changedSettings() {
        snapshotAge = Integer.MAX_VALUE;
        setChanged();
        wake();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        snapshotAge = Integer.MAX_VALUE;
        wake();
    }

    /** Something nearby changed: run on the next tick. */
    public void wake() {
        sleep = 0;
        wait = 0;
    }

    private Direction facing() {
        return getBlockState().getValue(BusBlock.FACING);
    }

    private void link(ServerLevel level) {
        Direction facing = facing();
        if (facing == linkedFacing && items != null) {
            return;
        }
        linkedFacing = facing;
        BlockPos target = worldPosition.relative(facing);
        Direction side = facing.getOpposite();
        items = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level, target, side, () -> !isRemoved(), this::wake);
        fluids = BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, level, target, side, () -> !isRemoved(), this::wake);
        energy = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, level, target, side, () -> !isRemoved(), this::wake);
        chemicals = Compat.chemicals() == null ? null : Compat.chemicals().link(level, target, side, () -> !isRemoved(), this::wake);
        source = Compat.source() == null ? null : Compat.source().link(level, target, side, () -> !isRemoved(), this::wake);
    }

    // ------------------------------------------------------------------ ticking

    /** Dev checks only: total time spent in bus ticks. */
    private static final boolean PROFILE = Boolean.getBoolean("grimstorage.systemcheck");
    public static long profileNanos;
    public static long profileTicks;

    @Override
    public void serverTick() {
        if (!PROFILE) {
            tick();
            return;
        }
        long t = System.nanoTime();
        tick();
        profileNanos += System.nanoTime() - t;
        profileTicks++;
    }

    private void tick() {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        age++;
        snapshotAge++;
        if (wait > 0) {
            wait--;
            return;
        }
        boolean moved = run(sl);
        if (moved) {
            sleep = 0;
        } else {
            sleep = sleep == 0 ? 1 : Math.min(MAX_SLEEP, sleep * 2);
        }
        wait = sleep;
    }

    /** One run. Returns true if anything moved. */
    private boolean run(ServerLevel level) {
        StorageNetwork net = network();
        if (net == null) {
            status = Status.NO_NETWORK;
            return false;
        }
        link(level);
        BlockPos target = worldPosition.relative(facing());
        if (level.getBlockEntity(target) instanceof NetworkBlockEntity other && other.network() == net) {
            status = Status.OWN_NETWORK;
            return false;
        }
        boolean attached = false;
        boolean full = false;
        long moved = 0;

        if (moves(ITEMS_BIT)) {
            IItemHandler h = items.getCapability();
            if (h != null) {
                attached = true;
                long n = export ? exportItems(net, h) : importItems(net, h);
                full |= n < 0;
                moved += Math.max(0, n);
            }
        }
        if (moves(BulkKind.FLUID) || moves(BulkKind.EXPERIENCE)) {
            IFluidHandler h = fluids.getCapability();
            if (h != null) {
                attached = true;
                moved += export ? exportFluids(net, h) : importFluids(net, h);
            }
        }
        if (moves(BulkKind.ENERGY)) {
            IEnergyStorage h = energy.getCapability();
            if (h != null) {
                attached = true;
                moved += export ? exportEnergy(net, h) : importEnergy(net, h);
            }
        }
        if (moves(BulkKind.CHEMICAL) && chemicals != null) {
            long n = export ? chemicals.push(net, CHEMICAL_BUDGET, chemicalCandidates(net)) : chemicals.pull(net, CHEMICAL_BUDGET, this::allowChemical);
            attached |= n >= 0;
            moved += Math.max(0, n);
        }
        if (moves(BulkKind.SOURCE) && source != null) {
            long n = export ? source.push(net, SOURCE_BUDGET, List.of()) : source.pull(net, SOURCE_BUDGET, k -> true);
            attached |= n >= 0;
            moved += Math.max(0, n);
        }
        // An Import Bus facing open space sweeps up dropped items and XP orbs there (great under a mob farm).
        if (!export && !attached && level.getBlockState(target).getCollisionShape(level, target).isEmpty()) {
            attached = true;
            moved += vacuum(level, net, target);
        }
        status = !attached ? Status.NOTHING_ATTACHED : moved > 0 ? Status.WORKING : full ? Status.FULL : Status.IDLE;
        return moved > 0;
    }

    // ------------------------------------------------------------------ filters

    private boolean listed(ItemStack stack) {
        for (ItemStack f : filters.items()) {
            if (!f.isEmpty() && (matchComponents ? ItemStack.isSameItemSameComponents(f, stack) : f.is(stack.getItem()))) {
                return true;
            }
        }
        return false;
    }

    public boolean allowItem(ItemStack stack) {
        return blacklist != listed(stack);
    }

    private List<FluidStack> fluidFilters() {
        List<FluidStack> out = new ArrayList<>();
        for (ItemStack f : filters.items()) {
            FluidUtil.getFluidContained(f).ifPresent(out::add);
        }
        return out;
    }

    public boolean allowFluid(FluidStack stack) {
        boolean listed = false;
        for (FluidStack f : fluidFilters()) {
            if (matchComponents ? FluidStack.isSameFluidSameComponents(f, stack) : f.getFluid().isSame(stack.getFluid())) {
                listed = true;
                break;
            }
        }
        return blacklist != listed;
    }

    private List<BulkKey> chemicalFilters() {
        List<BulkKey> out = new ArrayList<>();
        Compat.Chemicals c = Compat.chemicals();
        if (c != null) {
            for (ItemStack f : filters.items()) {
                BulkKey k = c.chemicalIn(f);
                if (k != null) {
                    out.add(k);
                }
            }
        }
        return out;
    }

    private boolean allowChemical(BulkKey key) {
        return blacklist != chemicalFilters().contains(key);
    }

    private Iterable<BulkKey> chemicalCandidates(StorageNetwork net) {
        if (!blacklist) {
            return chemicalFilters();
        }
        List<BulkKey> out = new ArrayList<>();
        for (BulkKey k : net.bulkContents(BulkKind.CHEMICAL).keySet()) {
            if (allowChemical(k)) {
                out.add(k);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ items

    /** Returns items moved, or -1 if something was waiting but the network had no room. */
    private long importItems(StorageNetwork net, IItemHandler h) {
        int slots = h.getSlots();
        if (slots <= 0) {
            return 0;
        }
        int budget = ITEM_BUDGET;
        boolean blocked = false;
        int scan = Math.min(slots, SLOTS_PER_RUN);
        for (int n = 0; n < scan && budget > 0; n++) {
            int slot = (slotCursor + n) % slots;
            ItemStack peek = h.extractItem(slot, Math.min(budget, 64), true);
            if (peek.isEmpty() || !allowItem(peek)) {
                continue;
            }
            long fits = net.insert(peek, true);
            if (fits <= 0) {
                blocked = true;
                continue;
            }
            ItemStack got = h.extractItem(slot, (int) fits, false);
            if (!got.isEmpty()) {
                long in = net.insert(got, false);
                if (in < got.getCount()) {
                    net.insertOrBuffer(got.copyWithCount(got.getCount() - (int) in));
                }
                budget -= got.getCount();
            }
        }
        slotCursor = (slotCursor + scan) % slots;
        int moved = ITEM_BUDGET - budget;
        return moved == 0 && blocked ? -1 : moved;
    }

    private long exportItems(StorageNetwork net, IItemHandler h) {
        List<ItemKey> keys = exportKeys(net);
        if (keys.isEmpty()) {
            return 0;
        }
        int budget = ITEM_BUDGET;
        int tries = Math.min(keys.size(), 16);
        for (int n = 0; n < tries && budget > 0; n++) {
            ItemKey key = keys.get((keyCursor + n) % keys.size());
            int want = (int) net.extract(key, Math.min(budget, key.maxStackSize()), true);
            if (want <= 0) {
                continue;
            }
            int accepted = want - insert(h, key.toStack(want), true).getCount();
            if (accepted <= 0) {
                continue;
            }
            int got = (int) net.extract(key, accepted, false);
            ItemStack left = insert(h, key.toStack(got), false);
            if (!left.isEmpty()) {
                net.insertOrBuffer(left);
            }
            budget -= got - left.getCount();
        }
        keyCursor = (keyCursor + tries) % keys.size();
        return ITEM_BUDGET - budget;
    }

    private static ItemStack insert(IItemHandler h, ItemStack stack, boolean simulate) {
        for (int i = 0; i < h.getSlots() && !stack.isEmpty(); i++) {
            stack = h.insertItem(i, stack, simulate);
        }
        return stack;
    }

    /** What an Export Bus may send: the picked items, or with a blacklist everything stored except them. */
    private List<ItemKey> exportKeys(StorageNetwork net) {
        if (!blacklist && matchComponents) {
            List<ItemKey> out = new ArrayList<>();
            for (ItemStack f : filters.items()) {
                ItemKey k = ItemKey.of(f);
                if (k != null && !out.contains(k)) {
                    out.add(k);
                }
            }
            return out;
        }
        // Anything else needs the network's contents; take a fresh look at most once a second.
        if (snapshotAge >= 20) {
            snapshotAge = 0;
            List<ItemKey> out = new ArrayList<>();
            for (ItemKey k : net.contents().keySet()) {
                if (allowItem(k.stack())) {
                    out.add(k);
                }
            }
            exportSnapshot = out;
        }
        return exportSnapshot;
    }

    // ------------------------------------------------------------------ fluids and liquid XP

    private long importFluids(StorageNetwork net, IFluidHandler h) {
        long moved = 0;
        int budget = FLUID_BUDGET;
        for (int t = 0; t < Math.min(h.getTanks(), 16) && budget > 0; t++) {
            FluidStack inside = h.getFluidInTank(t);
            if (inside.isEmpty()) {
                continue;
            }
            boolean xp = Experience.isXpFluid(inside);
            boolean asXp = xp && moves(BulkKind.EXPERIENCE);
            boolean asFluid = moves(BulkKind.FLUID) && allowFluid(inside);
            if (!asXp && !asFluid) {
                continue;
            }
            FluidStack can = h.drain(inside.copyWithAmount(Math.min(budget, inside.getAmount())), IFluidHandler.FluidAction.SIMULATE);
            if (can.isEmpty()) {
                continue;
            }
            int fits = 0;
            long points = 0;
            if (asXp) {
                points = net.insertBulk(Experience.KEY, can.getAmount() / Experience.MB_PER_POINT, true);
                fits = (int) (points * Experience.MB_PER_POINT);
            }
            if (fits <= 0 && asFluid) {
                points = 0;
                fits = (int) net.insertBulk(BulkKey.fluid(can), can.getAmount(), true);
            }
            if (fits <= 0) {
                continue;
            }
            FluidStack got = h.drain(can.copyWithAmount(fits), IFluidHandler.FluidAction.EXECUTE);
            if (got.isEmpty()) {
                continue;
            }
            if (points > 0) {
                net.insertBulk(Experience.KEY, got.getAmount() / Experience.MB_PER_POINT, false);
            } else {
                net.insertBulk(BulkKey.fluid(got), got.getAmount(), false);
            }
            budget -= got.getAmount();
            moved += got.getAmount();
        }
        return moved;
    }

    private long exportFluids(StorageNetwork net, IFluidHandler h) {
        long moved = 0;
        int budget = FLUID_BUDGET;
        if (moves(BulkKind.FLUID)) {
            List<BulkKey> keys = new ArrayList<>();
            if (!blacklist) {
                for (FluidStack f : fluidFilters()) {
                    if (matchComponents) {
                        keys.add(BulkKey.fluid(f));
                    } else {
                        for (BulkKey k : net.bulkContents(BulkKind.FLUID).keySet()) {
                            if (k.fluid().getFluid().isSame(f.getFluid())) {
                                keys.add(k);
                            }
                        }
                    }
                }
            } else {
                for (BulkKey k : net.bulkContents(BulkKind.FLUID).keySet()) {
                    if (allowFluid(k.fluid())) {
                        keys.add(k);
                    }
                }
            }
            for (BulkKey k : keys) {
                if (budget <= 0) {
                    break;
                }
                int have = (int) net.extractBulk(k, budget, true);
                int accepted = have <= 0 ? 0 : h.fill(k.fluid(have), IFluidHandler.FluidAction.SIMULATE);
                if (accepted <= 0) {
                    continue;
                }
                int got = (int) net.extractBulk(k, accepted, false);
                int in = h.fill(k.fluid(got), IFluidHandler.FluidAction.EXECUTE);
                if (in < got) {
                    net.insertBulk(k, got - in, false);
                }
                budget -= in;
                moved += in;
            }
        }
        if (moves(BulkKind.EXPERIENCE) && budget > 0) {
            // Stored XP goes out as whichever liquid XP the tank accepts.
            long have = net.extractBulk(Experience.KEY, Math.min(XP_BUDGET, budget / Experience.MB_PER_POINT), true);
            if (have > 0) {
                for (Fluid f : Experience.xpFluids()) {
                    int accepted = h.fill(new FluidStack(f, (int) (have * Experience.MB_PER_POINT)), IFluidHandler.FluidAction.SIMULATE);
                    long points = accepted / Experience.MB_PER_POINT;
                    if (points <= 0) {
                        continue;
                    }
                    long got = net.extractBulk(Experience.KEY, points, false);
                    int in = h.fill(new FluidStack(f, (int) (got * Experience.MB_PER_POINT)), IFluidHandler.FluidAction.EXECUTE);
                    long back = got - in / Experience.MB_PER_POINT;
                    if (back > 0) {
                        net.insertBulk(Experience.KEY, back, false);
                    }
                    moved += in;
                    break;
                }
            }
        }
        return moved;
    }

    // ------------------------------------------------------------------ energy

    private static long importEnergy(StorageNetwork net, IEnergyStorage h) {
        if (!h.canExtract()) {
            return 0;
        }
        int can = h.extractEnergy(Integer.MAX_VALUE, true);
        long fits = can <= 0 ? 0 : net.insertBulk(BulkKey.flat(BulkKind.ENERGY), can, true);
        if (fits <= 0) {
            return 0;
        }
        int got = h.extractEnergy((int) fits, false);
        net.insertBulk(BulkKey.flat(BulkKind.ENERGY), got, false);
        return got;
    }

    private static long exportEnergy(StorageNetwork net, IEnergyStorage h) {
        if (!h.canReceive()) {
            return 0;
        }
        BulkKey key = BulkKey.flat(BulkKind.ENERGY);
        long have = net.extractBulk(key, Integer.MAX_VALUE, true);
        int accepted = have <= 0 ? 0 : h.receiveEnergy((int) have, true);
        if (accepted <= 0) {
            return 0;
        }
        long got = net.extractBulk(key, accepted, false);
        int in = h.receiveEnergy((int) got, false);
        if (in < got) {
            net.insertBulk(key, got - in, false);
        }
        return in;
    }

    // ------------------------------------------------------------------ open space

    private long vacuum(ServerLevel level, StorageNetwork net, BlockPos target) {
        AABB box = new AABB(target);
        long moved = 0;
        if (moves(ITEMS_BIT)) {
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.getItem().isEmpty())) {
                ItemStack stack = e.getItem();
                if (!allowItem(stack)) {
                    continue;
                }
                long in = net.insert(stack, false);
                if (in > 0) {
                    moved += in;
                    if (in >= stack.getCount()) {
                        e.discard();
                    } else {
                        e.setItem(stack.copyWithCount(stack.getCount() - (int) in));
                    }
                }
                if (moved >= ITEM_BUDGET * 4L) {
                    break;
                }
            }
        }
        if (moves(BulkKind.EXPERIENCE)) {
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box, ExperienceOrb::isAlive)) {
                // Orbs that merged carry a count; store all of it or leave the orb alone.
                long value = (long) orb.getValue() * orbCount(orb);
                if (net.insertBulk(Experience.KEY, value, true) >= value) {
                    net.insertBulk(Experience.KEY, value, false);
                    orb.discard();
                    moved += value;
                }
            }
        }
        return moved;
    }

    private static java.lang.reflect.Field orbCount;

    /** Nearby orbs merge into one with a count; vanilla keeps that private. */
    private static int orbCount(ExperienceOrb orb) {
        try {
            if (orbCount == null) {
                orbCount = ExperienceOrb.class.getDeclaredField("count");
                orbCount.setAccessible(true);
            }
            return Math.max(1, orbCount.getInt(orb));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1;
        }
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("blacklist", blacklist);
        tag.putBoolean("matchComponents", matchComponents);
        tag.putInt("resources", resources);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("resources")) {
            blacklist = tag.getBoolean("blacklist");
            matchComponents = tag.getBoolean("matchComponents");
            resources = tag.getInt("resources");
        }
    }
}
