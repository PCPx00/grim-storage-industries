package com.xtri6.grimstorage.storage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.NetworkMember;
import com.xtri6.grimstorage.block.entity.AssemblerBlockEntity;
import com.xtri6.grimstorage.block.entity.ControllerBlockEntity;
import com.xtri6.grimstorage.block.entity.DriveBlockEntity;
import com.xtri6.grimstorage.block.entity.ProcessingInterfaceBlockEntity;
import com.xtri6.grimstorage.block.entity.QuantumLinkBlockEntity;
import com.xtri6.grimstorage.block.entity.StockKeeperBlockEntity;
import com.xtri6.grimstorage.block.entity.VoidFilterBlockEntity;
import com.xtri6.grimstorage.item.CardData;
import com.xtri6.grimstorage.item.StorageCellItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Everything connected to one controller: its storage, its crafting patterns and its running jobs. */
public final class StorageNetwork {
    private static final int MAX_BLOCKS = 16_384;
    private static final int MAX_JOBS = 32;
    private static final int CRAFT_COST = 10;

    private record CellRef(CellTier tier, CellContents contents, @Nullable ItemKey lock) {}

    private final ControllerBlockEntity controller;
    private MinecraftServer server;
    private long builtEpoch = -1;
    private boolean valid;
    private Component problem = Component.empty();

    private final Set<GlobalPos> members = new HashSet<>();
    private final List<DriveBlockEntity> drives = new ArrayList<>();
    private final List<AssemblerBlockEntity> assemblers = new ArrayList<>();
    private final List<ProcessingInterfaceBlockEntity> processors = new ArrayList<>();
    private final List<StockKeeperBlockEntity> keepers = new ArrayList<>();
    private final List<VoidFilterBlockEntity> voids = new ArrayList<>();
    private Map<ItemKey, List<Pattern>> patterns = Map.of();

    private final List<CraftingJob> jobs = new ArrayList<>();
    private final List<ItemStack> overflow = new ArrayList<>();
    private long craftBudget;
    private long tickCount;

    private Map<ItemKey, Long> cache = Map.of();
    private long cacheVersion = -1;
    private long cacheEpoch = -1;
    private long statsVersion = -1;

    public StorageNetwork(ControllerBlockEntity controller) {
        this.controller = controller;
    }

    // ------------------------------------------------------------------ structure

    public long builtEpoch() {
        return builtEpoch;
    }

    public boolean isActive() {
        return valid && !controller.isRemoved();
    }

    public Component problem() {
        return problem;
    }

    public int memberCount() {
        return members.size();
    }

    public int driveCount() {
        return drives.size();
    }

    public boolean hasAssemblers() {
        return !assemblers.isEmpty();
    }

    public MinecraftServer server() {
        return server;
    }

    public void dispose() {
        for (GlobalPos p : members) {
            NetworkManager.unindex(p, this);
        }
        members.clear();
        valid = false;
    }

    public void rebuild(ServerLevel origin) {
        this.server = origin.getServer();
        dispose();
        drives.clear();
        assemblers.clear();
        processors.clear();
        keepers.clear();
        voids.clear();

        int controllers = 0;
        record Node(ServerLevel level, BlockPos pos) {}
        Deque<Node> queue = new ArrayDeque<>();
        Set<GlobalPos> seen = new HashSet<>();
        BlockPos start = controller.getBlockPos();
        queue.add(new Node(origin, start));
        seen.add(GlobalPos.of(origin.dimension(), start));

        while (!queue.isEmpty() && members.size() < MAX_BLOCKS) {
            Node node = queue.poll();
            ServerLevel level = node.level();
            BlockPos pos = node.pos();
            if (!(level.getBlockState(pos).getBlock() instanceof NetworkMember)) {
                continue;
            }
            members.add(GlobalPos.of(level.dimension(), pos));
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ControllerBlockEntity) {
                controllers++;
            } else if (be instanceof DriveBlockEntity d) {
                drives.add(d);
            } else if (be instanceof AssemblerBlockEntity a) {
                assemblers.add(a);
            } else if (be instanceof ProcessingInterfaceBlockEntity p) {
                processors.add(p);
            } else if (be instanceof StockKeeperBlockEntity k) {
                keepers.add(k);
            } else if (be instanceof VoidFilterBlockEntity v) {
                voids.add(v);
            } else if (be instanceof QuantumLinkBlockEntity q) {
                GlobalPos partner = q.linkedPartner(server);
                if (partner != null && seen.add(partner)) {
                    queue.add(new Node(server.getLevel(partner.dimension()), partner.pos()));
                }
            }
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                GlobalPos key = GlobalPos.of(level.dimension(), next);
                if (!seen.contains(key) && level.isLoaded(next) && level.getBlockState(next).getBlock() instanceof NetworkMember) {
                    seen.add(key);
                    queue.add(new Node(level, next));
                }
            }
        }

        valid = controllers == 1;
        problem = controllers > 1 ? Component.translatable("gui.grimstorage.problem.controllers") : Component.empty();
        for (GlobalPos p : members) {
            NetworkManager.index(p, this);
        }
        rebuildPatterns();
        cacheEpoch = -1;
        statsVersion = -1;
        builtEpoch = NetworkManager.epoch();
    }

    private void rebuildPatterns() {
        Map<ItemKey, List<Pattern>> map = new HashMap<>();
        for (AssemblerBlockEntity a : assemblers) {
            for (ItemStack card : a.cards()) {
                Pattern p = Pattern.crafting(a.getLevel(), card);
                if (p != null) {
                    map.computeIfAbsent(p.primary(), k -> new ArrayList<>()).add(p);
                }
            }
        }
        for (ProcessingInterfaceBlockEntity proc : processors) {
            for (ItemStack card : proc.cards()) {
                Pattern p = Pattern.processing(card, proc);
                if (p != null) {
                    for (ItemKey out : p.outputs().keySet()) {
                        map.computeIfAbsent(out, k -> new ArrayList<>()).add(p);
                    }
                }
            }
        }
        patterns = map;
    }

    public List<Pattern> patternsFor(ItemKey key) {
        return patterns.getOrDefault(key, List.of());
    }

    public Set<ItemKey> craftables() {
        return patterns.keySet();
    }

    // ------------------------------------------------------------------ storage

    private List<CellRef> cells() {
        List<CellRef> out = new ArrayList<>();
        if (server == null) {
            return out;
        }
        CellStorage storage = CellStorage.get(server);
        for (DriveBlockEntity drive : drives) {
            if (drive.isRemoved()) {
                continue;
            }
            for (ItemStack stack : drive.cellStacks()) {
                if (stack.getItem() instanceof StorageCellItem cell) {
                    UUID id = stack.get(Registration.CELL_ID.get());
                    if (id != null) {
                        CardData.Locked lock = stack.get(Registration.CELL_LOCK.get());
                        out.add(new CellRef(cell.tier(), storage.contents(id), lock == null ? null : ItemKey.of(lock.stack())));
                    }
                }
            }
        }
        return out;
    }

    /** Puts items in. Returns how many were accepted. */
    public long insert(@Nullable ItemKey key, long amount, boolean simulate) {
        if (!isActive() || key == null || amount <= 0) {
            return 0;
        }
        long left = amount;
        List<CellRef> cells = cells();
        for (CellRef c : cells) {
            if (left > 0 && c.tier().singular() && key.equals(c.lock())) {
                left -= c.contents().insert(key, left, c.tier().capacity(), simulate);
            }
        }
        for (CellRef c : cells) {
            if (left > 0 && !c.tier().singular() && c.contents().count(key) > 0) {
                left -= c.contents().insert(key, left, c.tier().capacity(), simulate);
            }
        }
        for (CellRef c : cells) {
            if (left > 0 && !c.tier().singular()) {
                left -= c.contents().insert(key, left, c.tier().capacity(), simulate);
            }
        }
        if (left > 0) {
            for (VoidFilterBlockEntity v : voids) {
                if (!v.isRemoved() && v.voids(key)) {
                    left = 0;
                    break;
                }
            }
        }
        return amount - left;
    }

    public long insert(ItemStack stack, boolean simulate) {
        return insert(ItemKey.of(stack), stack.getCount(), simulate);
    }

    /** Takes items out. Returns how many were taken. */
    public long extract(@Nullable ItemKey key, long amount, boolean simulate) {
        if (!isActive() || key == null || amount <= 0) {
            return 0;
        }
        long left = amount;
        List<CellRef> cells = cells();
        for (CellRef c : cells) {
            if (left > 0 && !c.tier().singular()) {
                left -= c.contents().extract(key, left, simulate);
            }
        }
        for (CellRef c : cells) {
            if (left > 0 && c.tier().singular()) {
                left -= c.contents().extract(key, left, simulate);
            }
        }
        return amount - left;
    }

    public long count(@Nullable ItemKey key) {
        return key == null ? 0 : contents().getOrDefault(key, 0L);
    }

    /** Everything stored, merged across cells. Cached until something changes. */
    public Map<ItemKey, Long> contents() {
        long version = CellStorage.version();
        if (version != cacheVersion || builtEpoch != cacheEpoch) {
            Map<ItemKey, Long> map = new HashMap<>();
            if (isActive()) {
                for (CellRef c : cells()) {
                    c.contents().view().forEach((k, v) -> map.merge(k, v, Long::sum));
                }
            }
            cache = Collections.unmodifiableMap(map);
            cacheVersion = version;
            cacheEpoch = builtEpoch;
        }
        return cache;
    }

    /** {used, capacity, cells, singularity contents}. Singularity cells have no capacity, so they are counted apart. */
    public long[] stats() {
        long used = 0, cap = 0, count = 0, singular = 0;
        for (CellRef c : cells()) {
            if (c.tier().singular()) {
                singular += c.contents().total();
            } else {
                used += c.contents().total();
                cap += c.tier().capacity();
            }
            count++;
        }
        return new long[] {used, cap, count, singular};
    }

    /** Inserts, and keeps anything that does not fit to retry later rather than losing it. */
    public void insertOrBuffer(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        long in = insert(stack, false);
        if (in < stack.getCount()) {
            overflow.add(stack.copyWithCount(stack.getCount() - (int) in));
        }
    }

    public boolean hasOverflow() {
        return !overflow.isEmpty();
    }

    // ------------------------------------------------------------------ crafting

    public List<CraftingJob> jobs() {
        return Collections.unmodifiableList(jobs);
    }

    public long craftingAmount(ItemKey key) {
        long n = 0;
        for (CraftingJob j : jobs) {
            if (j.target().equals(key)) {
                n += j.amount();
            }
        }
        return n;
    }

    public CraftingPlanner.Plan plan(ItemKey key, long amount) {
        return CraftingPlanner.plan(this, key, amount);
    }

    /** Plans and starts a job; returns the plan so callers can report what is missing. */
    public CraftingPlanner.Plan request(ItemKey key, long amount, String source) {
        CraftingPlanner.Plan plan = plan(key, amount);
        if (plan.ok() && jobs.size() < MAX_JOBS) {
            jobs.add(new CraftingJob(key, amount, source, plan));
        }
        return plan;
    }

    public boolean canStartJob() {
        return jobs.size() < MAX_JOBS;
    }

    public void cancel(UUID id) {
        jobs.removeIf(j -> {
            if (j.id().equals(id)) {
                j.cancel();
                return true;
            }
            return false;
        });
    }

    boolean spendCraft() {
        if (craftBudget >= CRAFT_COST) {
            craftBudget -= CRAFT_COST;
            return true;
        }
        return false;
    }

    void refundCraft() {
        craftBudget += CRAFT_COST;
    }

    /** Runs one craft of a recipe card. Returns null on success, or why it could not run. */
    @Nullable
    Component tryCraft(Pattern pattern) {
        for (Map.Entry<ItemKey, Long> in : pattern.inputs().entrySet()) {
            if (extract(in.getKey(), in.getValue(), true) < in.getValue()) {
                return Component.translatable("gui.grimstorage.job.waiting", in.getKey().stack().getHoverName());
            }
        }
        long made = pattern.outputCount(pattern.primary());
        if (!overflow.isEmpty() || insert(pattern.primary(), made, true) < made) {
            return Component.translatable("gui.grimstorage.job.full");
        }
        for (Map.Entry<ItemKey, Long> in : pattern.inputs().entrySet()) {
            extract(in.getKey(), in.getValue(), false);
        }
        RecipeHolder<CraftingRecipe> recipe = pattern.recipe();
        CraftingInput input = pattern.craftingInput();
        ItemStack result = recipe.value().assemble(input, server.registryAccess());
        if (result.isEmpty()) {
            result = pattern.primary().toStack((int) pattern.outputCount(pattern.primary()));
        }
        insertOrBuffer(result);
        NonNullList<ItemStack> remaining = recipe.value().getRemainingItems(input);
        for (ItemStack r : remaining) {
            insertOrBuffer(r);
        }
        return null;
    }

    /** Sends one batch of a processing card into its machine. Returns null on success, or why it could not. */
    @Nullable
    Component trySendProcessing(Pattern pattern) {
        ProcessingInterfaceBlockEntity proc = pattern.processor();
        if (proc == null || proc.isRemoved()) {
            return Component.translatable("gui.grimstorage.job.no_machine");
        }
        IItemHandler machine = proc.machineInput();
        if (machine == null) {
            return Component.translatable("gui.grimstorage.job.no_machine");
        }
        for (Map.Entry<ItemKey, Long> in : pattern.inputs().entrySet()) {
            if (extract(in.getKey(), in.getValue(), true) < in.getValue()) {
                return Component.translatable("gui.grimstorage.job.waiting", in.getKey().stack().getHoverName());
            }
            ItemStack test = in.getKey().toStack((int) Math.min(Integer.MAX_VALUE, in.getValue()));
            if (!ItemHandlerHelper.insertItemStacked(machine, test, true).isEmpty()) {
                return Component.translatable("gui.grimstorage.job.machine_busy");
            }
        }
        for (Map.Entry<ItemKey, Long> in : pattern.inputs().entrySet()) {
            long got = extract(in.getKey(), in.getValue(), false);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(machine, in.getKey().toStack((int) got), false);
            insertOrBuffer(rest);
        }
        return null;
    }

    /** Called by processing interfaces when a machine hands back an output. */
    public void onProcessingOutput(ItemKey key, long amount) {
        long left = amount;
        for (CraftingJob job : jobs) {
            if (left <= 0) {
                break;
            }
            left -= job.claim(key, left);
        }
    }

    // ------------------------------------------------------------------ ticking

    public void tick(ServerLevel level) {
        if (!isActive()) {
            return;
        }
        tickCount++;

        if (!overflow.isEmpty()) {
            Iterator<ItemStack> it = overflow.iterator();
            List<ItemStack> keep = new ArrayList<>();
            while (it.hasNext()) {
                ItemStack s = it.next();
                long in = insert(s, false);
                if (in < s.getCount()) {
                    keep.add(s.copyWithCount(s.getCount() - (int) in));
                }
            }
            overflow.clear();
            overflow.addAll(keep);
        }

        long speed = 0;
        for (AssemblerBlockEntity a : assemblers) {
            if (!a.isRemoved()) {
                speed += a.speed();
            }
        }
        craftBudget = Math.min(craftBudget + speed, Math.max(CRAFT_COST, speed * 20));

        Iterator<CraftingJob> it = jobs.iterator();
        while (it.hasNext()) {
            CraftingJob job = it.next();
            job.tick(this);
            if (job.finished()) {
                it.remove();
            }
        }

        if (tickCount % 5 == 0) {
            for (ProcessingInterfaceBlockEntity p : processors) {
                if (!p.isRemoved()) {
                    p.pullOutputs(this);
                }
            }
        }
        if (tickCount % 20 == 0) {
            for (StockKeeperBlockEntity k : keepers) {
                if (!k.isRemoved()) {
                    k.check(this, level.getGameTime());
                }
            }
            long version = CellStorage.version();
            if (version != statsVersion) {
                statsVersion = version;
                CellStorage storage = CellStorage.get(server);
                for (DriveBlockEntity d : drives) {
                    if (!d.isRemoved()) {
                        d.refreshStats(storage);
                    }
                }
            }
        }
    }
}
