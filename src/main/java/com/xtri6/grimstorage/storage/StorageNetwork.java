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
import com.xtri6.grimstorage.item.BulkCellItem;
import com.xtri6.grimstorage.item.CardData;
import com.xtri6.grimstorage.item.StorageCellItem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private static final int CRAFT_COST = 10;

    private record CellRef(CellTier tier, CellContents<ItemKey> contents, @Nullable ItemKey lock) {}

    private final ControllerBlockEntity controller;
    private MinecraftServer server;
    private long builtEpoch = -1;
    private boolean valid;
    /** True when the network hit {@link com.xtri6.grimstorage.ServerConfig#maxBlocks()} and some blocks were left out. */
    private boolean truncated;
    private Component problem = Component.empty();

    private final Set<GlobalPos> members = new HashSet<>();
    private final List<DriveBlockEntity> drives = new ArrayList<>();
    /** Chests (and any other inventory) linked into storage by Chest Links. */
    private final List<com.xtri6.grimstorage.block.entity.ChestLinkBlockEntity> links = new ArrayList<>();
    private Map<ItemKey, Long> linkSnapshot = Map.of();
    private long linkRevision;
    private long linkEpoch = -1;
    private final List<AssemblerBlockEntity> assemblers = new ArrayList<>();
    private final List<MachineLink> processors = new ArrayList<>();
    private final List<StockKeeperBlockEntity> keepers = new ArrayList<>();
    private final List<VoidFilterBlockEntity> voids = new ArrayList<>();
    private Map<ItemKey, List<Pattern>> patterns = Map.of();
    /** Machine recipes by the fluid or chemical they make. */
    private Map<BulkKey, List<Pattern>> bulkPatterns = Map.of();

    private final List<CraftingJob> jobs = new ArrayList<>();
    private final List<ItemStack> overflow = new ArrayList<>();
    private long craftBudget;
    private long tickCount;

    private Map<ItemKey, Long> cache = Map.of();
    private long cacheVersion = -1;
    private long cacheEpoch = -1;
    private long statsVersion = -1;

    /** Crafts the Soul Assemblers manage per second, all together. */
    public double craftsPerSecond() {
        long speed = 0;
        for (AssemblerBlockEntity a : assemblers) {
            if (!a.isRemoved()) {
                speed += a.speed();
            }
        }
        return speed * 20.0 / CRAFT_COST;
    }

    /** Tells the player who ordered a job that it is done (or stuck), with a toast. */
    private void alert(CraftingJob job, boolean done) {
        if (!done) {
            DebugLog.note("job stuck a minute: " + job.amount() + "x " + job.target() + " for " + job.source() + ": " + job.status().getString());
        }
        if (server == null) {
            return;
        }
        net.minecraft.server.level.ServerPlayer p = server.getPlayerList().getPlayerByName(job.source());
        if (p != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new com.xtri6.grimstorage.net.Payloads.CraftAlert(
                    job.target().toStack(1), job.amount(), done, done ? Component.empty() : job.status()));
        }
    }

    /** Whether this player may use the network (see {@link NetworkAccess}). */
    public boolean allows(net.minecraft.world.entity.player.Player player) {
        return controller.allows(player);
    }

    public String ownerName() {
        return controller.ownerName();
    }

    public ControllerBlockEntity controller() {
        return controller;
    }

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
        links.clear();

        int controllers = 0;
        record Node(ServerLevel level, BlockPos pos) {}
        Deque<Node> queue = new ArrayDeque<>();
        Set<GlobalPos> seen = new HashSet<>();
        BlockPos start = controller.getBlockPos();
        queue.add(new Node(origin, start));
        seen.add(GlobalPos.of(origin.dimension(), start));

        // Wireless devices bound to this network join it when a range card gives them reach to the controller.
        // Infinite reach goes any distance, and into other dimensions when the server allows it.
        for (BlockEntity device : WirelessDevices.bound(controller.networkId())) {
            if (!(device.getLevel() instanceof ServerLevel deviceLevel)
                    || !(device instanceof com.xtri6.grimstorage.block.entity.WirelessDevice wireless)) {
                continue;
            }
            int reach = wireless.wirelessRange();
            if (reach <= 0) {
                continue;
            }
            boolean sameDimension = deviceLevel.dimension() == controller.getLevel().dimension();
            if (sameDimension) {
                if (!wireless.infiniteReach() && device.getBlockPos().distSqr(start) > (double) reach * reach) {
                    continue;
                }
            } else if (!(wireless.infiniteReach() && com.xtri6.grimstorage.BalanceConfig.wirelessCrossDimension())) {
                continue;
            }
            GlobalPos key = GlobalPos.of(deviceLevel.dimension(), device.getBlockPos());
            if (seen.add(key)) {
                queue.add(new Node(deviceLevel, device.getBlockPos()));
            }
        }

        int limit = com.xtri6.grimstorage.ServerConfig.maxBlocks();
        while (!queue.isEmpty() && members.size() < limit) {
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
            } else if (be instanceof MachineLink p) {
                processors.add(p);
            } else if (be instanceof StockKeeperBlockEntity k) {
                keepers.add(k);
            } else if (be instanceof VoidFilterBlockEntity v) {
                voids.add(v);
            } else if (be instanceof com.xtri6.grimstorage.block.entity.ChestLinkBlockEntity c) {
                links.add(c);
            } else if (be instanceof QuantumLinkBlockEntity q) {
                GlobalPos partner = q.linkedPartner(server);
                if (partner != null && seen.add(partner)) {
                    queue.add(new Node(server.getLevel(partner.dimension()), partner.pos()));
                }
            }
            BlockState here = level.getBlockState(pos);
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                GlobalPos key = GlobalPos.of(level.dimension(), next);
                if (!seen.contains(key) && level.isLoaded(next) && NetworkMember.linked(here, dir, level.getBlockState(next))) {
                    seen.add(key);
                    queue.add(new Node(level, next));
                }
            }
        }

        truncated = !queue.isEmpty();
        valid = controllers == 1;
        if (controllers > 1) {
            problem = Component.translatable("gui.grimstorage.problem.controllers");
        } else if (truncated) {
            problem = Component.translatable("gui.grimstorage.problem.too_big", limit);
        } else {
            problem = Component.empty();
        }
        for (GlobalPos p : members) {
            NetworkManager.index(p, this);
        }
        rebuildPatterns();
        restoreSaved();
        cacheEpoch = -1;
        statsVersion = -1;
        builtEpoch = NetworkManager.epoch();
    }

    private void rebuildPatterns() {
        Map<ItemKey, List<Pattern>> map = new HashMap<>();
        for (AssemblerBlockEntity a : assemblers) {
            for (ItemStack card : a.cards()) {
                for (Pattern p : Pattern.craftingVariants(a.getLevel(), card)) {
                    map.computeIfAbsent(p.primary(), k -> new ArrayList<>()).add(p);
                }
            }
        }
        // Machine recipes: cards first, then what Machine Buses learned. The same recipe on several machines is one
        // pattern backed by all of them, so big jobs are shared out.
        List<Pattern> machine = new ArrayList<>();
        for (MachineLink proc : processors) {
            for (ItemStack card : proc.cards()) {
                Pattern p = Pattern.processing(card, proc);
                if (p != null) {
                    addMachinePattern(machine, p);
                }
            }
        }
        for (MachineLink proc : processors) {
            for (Pattern p : proc.learnedPatterns()) {
                addMachinePattern(machine, p);
            }
        }
        Set<ItemKey> explicit = new HashSet<>(map.keySet());
        Map<BulkKey, List<Pattern>> bulk = new HashMap<>();
        for (Pattern p : machine) {
            for (ItemKey out : p.outputs().keySet()) {
                map.computeIfAbsent(out, k -> new ArrayList<>()).add(p);
                if (!p.learned()) {
                    explicit.add(out);
                }
            }
            for (BulkKey out : p.bulkOutputs().keySet()) {
                bulk.computeIfAbsent(out, k -> new ArrayList<>()).add(p);
            }
        }
        patterns = map;
        bulkPatterns = bulk;
        explicitCraftables = explicit;
    }

    private Set<ItemKey> explicitCraftables = Set.of();

    private static void addMachinePattern(List<Pattern> list, Pattern p) {
        for (Pattern q : list) {
            if (q.sameRecipe(p)) {
                q.addMachines(p);
                return;
            }
        }
        list.add(p);
    }

    /** Things the network can make from Recipe Cards or Processing Cards (not only learned machine recipes). */
    public Set<ItemKey> explicitCraftables() {
        return explicitCraftables;
    }

    public List<Pattern> patternsFor(ItemKey key) {
        return patterns.getOrDefault(key, List.of());
    }

    /** Machine recipes that make this fluid or chemical. */
    public List<Pattern> patternsFor(BulkKey key) {
        return bulkPatterns.getOrDefault(key, List.of());
    }

    /** Fluids and chemicals some machine on the network can make. */
    public Set<BulkKey> bulkCraftables() {
        return bulkPatterns.keySet();
    }

    public Set<ItemKey> craftables() {
        return patterns.keySet();
    }

    // ------------------------------------------------------------------ storage

    /** Item cells in this network's drives, kept between rebuilds (a cell going in or out rebuilds the network). */
    private List<CellRef> cellCache = List.of();
    private final Map<BulkKind, List<BulkRef>> bulkCache = new java.util.EnumMap<>(BulkKind.class);
    private long cellCacheEpoch = -1;
    /** Goes up each time the cached cell list is rebuilt, so content versions never repeat across rebuilds. */
    private long cellListRevision;

    /** Drops the cached cell lists when the network was rebuilt (or is about to be). */
    private boolean cellCacheStale() {
        long epoch = NetworkManager.epoch();
        if (epoch != builtEpoch) {
            // a drive changed and the rebuild hasn't run yet: read the drives directly this time
            return true;
        }
        if (cellCacheEpoch != epoch) {
            cellCacheEpoch = epoch;
            cellCache = scanCells();
            bulkCache.clear();
            cellListRevision++;
        }
        return false;
    }

    private List<CellRef> cells() {
        return cellCacheStale() ? scanCells() : cellCache;
    }

    private List<CellRef> scanCells() {
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

    /** When each item last came in (network ticks), for the terminal's "recently added" sort. */
    private final java.util.LinkedHashMap<ItemKey, Long> added = new java.util.LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ItemKey, Long> e) {
            return size() > 4096;
        }
    };

    public long lastAdded(ItemKey key) {
        Long t = added.get(key);
        return t == null ? 0 : t;
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
        left -= insertLinks(key, left, simulate, true, true);
        left -= insertLinks(key, left, simulate, true, false);
        for (CellRef c : cells) {
            if (left > 0 && !c.tier().singular() && c.contents().count(key) > 0) {
                left -= c.contents().insert(key, left, c.tier().capacity(), simulate);
            }
        }
        left -= insertLinks(key, left, simulate, false, true);
        for (CellRef c : cells) {
            if (left > 0 && !c.tier().singular()) {
                left -= c.contents().insert(key, left, c.tier().capacity(), simulate);
            }
        }
        left -= insertLinks(key, left, simulate, false, false);
        if (!simulate && left < amount) {
            added.put(key, tickCount + 1);
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

    /** Chest Links set to fill first (or the rest), where the item already is (or anywhere). */
    private long insertLinks(ItemKey key, long amount, boolean simulate, boolean firstOnes, boolean onlyIfPresent) {
        long done = 0;
        for (com.xtri6.grimstorage.block.entity.ChestLinkBlockEntity l : links) {
            if (amount - done <= 0) {
                break;
            }
            if (l.fillFirst() == firstOnes) {
                done += l.insert(key, amount - done, simulate, onlyIfPresent);
            }
        }
        if (done > 0 && !simulate) {
            refreshLinks();
        }
        return done;
    }

    /** Looks inside every linked inventory again (they can change behind our back: hoppers, players). */
    private void refreshLinks() {
        Map<ItemKey, Long> map = new HashMap<>();
        for (com.xtri6.grimstorage.block.entity.ChestLinkBlockEntity l : links) {
            l.snapshot().forEach((k, v) -> map.merge(k, v, Long::sum));
        }
        if (!map.equals(linkSnapshot)) {
            linkSnapshot = map;
            linkRevision++;
        }
    }

    /** Items of this kind in chests linked as "only put in", which the network can see but not take. */
    public long lockedInLinks(ItemKey key) {
        long n = 0;
        for (com.xtri6.grimstorage.block.entity.ChestLinkBlockEntity l : links) {
            if (!l.isRemoved() && l.mode() == com.xtri6.grimstorage.block.entity.ChestLinkBlockEntity.INSERT_ONLY) {
                n += l.rawCount(key);
            }
        }
        return n;
    }

    /** Changes whenever anything stored changes, cells or linked chests. */
    public long contentVersion() {
        long v = cellListRevision * 0x9E3779B97F4A7C15L + linkRevision;
        for (CellRef c : cells()) {
            v = v * 31 + c.contents().version();
        }
        return v;
    }

    /** Same idea for everything this network holds, fluids, energy and the rest included (for cell tooltips). */
    private long allContentVersion() {
        long v = contentVersion();
        for (BulkKind k : BulkKind.values()) {
            for (BulkRef c : bulkCells(k)) {
                v = v * 31 + c.contents().version();
            }
        }
        return v;
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
        long fromLinks = 0;
        for (com.xtri6.grimstorage.block.entity.ChestLinkBlockEntity l : links) {
            if (left - fromLinks <= 0) {
                break;
            }
            fromLinks += l.extract(key, left - fromLinks, simulate);
        }
        left -= fromLinks;
        if (fromLinks > 0 && !simulate) {
            refreshLinks();
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
        long version = contentVersion();
        if (version != cacheVersion || builtEpoch != cacheEpoch) {
            Map<ItemKey, Long> map = new HashMap<>();
            if (isActive()) {
                for (CellRef c : cells()) {
                    c.contents().view().forEach((k, v) -> map.merge(k, v, Long::sum));
                }
                linkSnapshot.forEach((k, v) -> map.merge(k, v, Long::sum));
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
        for (com.xtri6.grimstorage.block.entity.ChestLinkBlockEntity l : links) {
            long[] u = l.usage();
            used += u[0];
            cap += u[1];
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

    // ------------------------------------------------------------------ fluids, chemicals, energy, Source

    private record BulkRef(BulkCellItem cell, CellContents<BulkKey> contents) {
        long capacity() {
            return cell.capacity();
        }
    }

    private List<BulkRef> bulkCells(BulkKind kind) {
        if (cellCacheStale()) {
            return scanBulkCells(kind);
        }
        return bulkCache.computeIfAbsent(kind, this::scanBulkCells);
    }

    private List<BulkRef> scanBulkCells(BulkKind kind) {
        List<BulkRef> out = new ArrayList<>();
        if (server == null) {
            return out;
        }
        CellStorage storage = CellStorage.get(server);
        for (DriveBlockEntity drive : drives) {
            if (drive.isRemoved()) {
                continue;
            }
            for (ItemStack stack : drive.cellStacks()) {
                if (stack.getItem() instanceof BulkCellItem cell && cell.kind() == kind) {
                    UUID id = stack.get(Registration.CELL_ID.get());
                    if (id != null) {
                        out.add(new BulkRef(cell, storage.bulk(id, kind)));
                    }
                }
            }
        }
        return out;
    }

    /** Puts a fluid/chemical/energy/Source amount in. Returns how much was accepted. */
    public long insertBulk(@Nullable BulkKey key, long amount, boolean simulate) {
        if (!isActive() || key == null || amount <= 0) {
            return 0;
        }
        long left = amount;
        List<BulkRef> cells = bulkCells(key.kind());
        for (BulkRef c : cells) {
            if (left > 0 && c.contents().count(key) > 0) {
                left -= c.contents().insert(key, left, c.capacity(), simulate);
            }
        }
        for (BulkRef c : cells) {
            if (left > 0) {
                left -= c.contents().insert(key, left, c.capacity(), simulate);
            }
        }
        return amount - left;
    }

    /** Takes a fluid/chemical/energy/Source amount out. Returns how much was taken. */
    public long extractBulk(@Nullable BulkKey key, long amount, boolean simulate) {
        if (!isActive() || key == null || amount <= 0) {
            return 0;
        }
        long left = amount;
        for (BulkRef c : bulkCells(key.kind())) {
            if (left > 0) {
                left -= c.contents().extract(key, left, simulate);
            }
        }
        return amount - left;
    }

    public long countBulk(@Nullable BulkKey key) {
        if (key == null || !isActive()) {
            return 0;
        }
        long n = 0;
        for (BulkRef c : bulkCells(key.kind())) {
            n += c.contents().count(key);
        }
        return n;
    }

    /** Everything of one kind, merged across cells. */
    public Map<BulkKey, Long> bulkContents(BulkKind kind) {
        Map<BulkKey, Long> map = new java.util.LinkedHashMap<>();
        if (isActive()) {
            for (BulkRef c : bulkCells(kind)) {
                c.contents().view().forEach((k, v) -> map.merge(k, v, Long::sum));
            }
        }
        return map;
    }

    /** {used, capacity} for one kind. */
    public long[] bulkStats(BulkKind kind) {
        long used = 0, cap = 0;
        if (isActive()) {
            for (BulkRef c : bulkCells(kind)) {
                used += c.contents().total();
                cap += c.capacity();
            }
        }
        return new long[] {used, cap};
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
        if (plan.ok() && canStartJob(source)) {
            CraftingJob job = new CraftingJob(key, amount, source, plan);
            jobs.add(job);
            sortJobs();
        }
        return plan;
    }

    public boolean canStartJob() {
        return jobs.size() < com.xtri6.grimstorage.ServerConfig.maxJobs();
    }

    /** Room on the network, and this player has not used up their share of it. */
    public boolean canStartJob(String player) {
        return canStartJob() && jobsOf(player) < com.xtri6.grimstorage.ServerConfig.maxJobsPerPlayer();
    }

    public int jobsOf(String player) {
        int n = 0;
        for (CraftingJob j : jobs) {
            if (j.source().equals(player)) {
                n++;
            }
        }
        return n;
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

    /** What the jobs list in a terminal can do to a job. */
    public enum JobAction { CANCEL, PAUSE, UP, DOWN, TOP }

    /**
     * Pauses / resumes a job, or moves it in the queue. Jobs higher in the queue get the Soul Assemblers and the
     * machines first; a job moved up gets a higher priority than the one it passes.
     */
    public void jobAction(UUID id, JobAction action) {
        int i = -1;
        for (int n = 0; n < jobs.size(); n++) {
            if (jobs.get(n).id().equals(id)) {
                i = n;
                break;
            }
        }
        if (i < 0) {
            return;
        }
        CraftingJob job = jobs.get(i);
        switch (action) {
            case CANCEL -> cancel(id);
            case PAUSE -> job.setPaused(!job.paused());
            case UP -> {
                if (i > 0) {
                    job.setPriority(Math.max(job.priority(), jobs.get(i - 1).priority() + 1));
                }
            }
            case DOWN -> {
                if (i + 1 < jobs.size()) {
                    job.setPriority(Math.min(job.priority(), jobs.get(i + 1).priority() - 1));
                }
            }
            case TOP -> job.setPriority(jobs.get(0).priority() + (i == 0 ? 0 : 1));
        }
        sortJobs();
    }

    /** Highest priority first; the same priority keeps the order they were asked for. */
    private void sortJobs() {
        jobs.sort(java.util.Comparator.comparingInt(CraftingJob::priority).reversed());
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

    /** Most runs of a fluid-and-chemical-only recipe sent to one machine at once. */
    private static final long MAX_BULK_RUNS = 4000;

    /** What one try at sending a machine recipe did: how many runs went, or why none did. */
    record Sent(long runs, @Nullable Component problem) {}

    /**
     * Sends a machine recipe to a machine: one run, or for a recipe that only takes fluids and chemicals (a Chemical
     * Washer, a Crystallizer), as many of the {@code want} runs as the machine's tanks hold.
     */
    Sent trySendProcessing(Pattern pattern, long want) {
        if (pattern.bulkOnlyInputs()) {
            return sendBulk(pattern, want);
        }
        Component problem = trySendProcessing(pattern);
        return problem == null ? new Sent(1, null) : new Sent(0, problem);
    }

    private Sent sendBulk(Pattern pattern, long want) {
        long runs = Math.min(want, MAX_BULK_RUNS);
        for (Map.Entry<BulkKey, Long> in : pattern.bulkInputs().entrySet()) {
            long per = Math.max(1, in.getValue());
            runs = Math.min(runs, countBulk(in.getKey()) / per);
            if (runs <= 0) {
                return new Sent(0, Component.translatable("gui.grimstorage.job.waiting", in.getKey().name()));
            }
        }
        boolean any = false;
        for (MachineLink link : pattern.machinesInTurn()) {
            if (link.linkRemoved()) {
                continue;
            }
            any = true;
            long fit = runs;
            for (Map.Entry<BulkKey, Long> in : pattern.bulkInputs().entrySet()) {
                long per = Math.max(1, in.getValue());
                fit = Math.min(fit, link.fillBulk(this, in.getKey(), per * fit, true) / per);
                if (fit <= 0) {
                    break;
                }
            }
            if (fit <= 0) {
                continue;
            }
            for (Map.Entry<BulkKey, Long> in : pattern.bulkInputs().entrySet()) {
                link.fillBulk(this, in.getKey(), Math.max(1, in.getValue()) * fit, false);
            }
            pattern.usedMachine(link);
            return new Sent(fit, null);
        }
        return new Sent(0, Component.translatable(any ? "gui.grimstorage.job.machine_busy" : "gui.grimstorage.job.no_machine"));
    }

    /** Sends one batch of a processing card into its machine. Returns null on success, or why it could not. */
    @Nullable
    Component trySendProcessing(Pattern pattern) {
        for (Map.Entry<ItemKey, Long> in : pattern.inputs().entrySet()) {
            if (extract(in.getKey(), in.getValue(), true) < in.getValue()) {
                return Component.translatable("gui.grimstorage.job.waiting", in.getKey().stack().getHoverName());
            }
        }
        for (Map.Entry<BulkKey, Long> in : pattern.bulkInputs().entrySet()) {
            if (countBulk(in.getKey()) <= 0) {
                return Component.translatable("gui.grimstorage.job.waiting", in.getKey().name());
            }
        }
        // the first machine (taking turns) that has room for every ingredient
        MachineLink chosen = null;
        IItemHandler machine = null;
        boolean any = false;
        for (MachineLink link : pattern.machinesInTurn()) {
            if (link.linkRemoved()) {
                continue;
            }
            IItemHandler h = link.machineInput();
            if (h == null) {
                continue;
            }
            any = true;
            boolean fits = true;
            for (Map.Entry<ItemKey, Long> in : pattern.inputs().entrySet()) {
                ItemStack test = in.getKey().toStack((int) Math.min(Integer.MAX_VALUE, in.getValue()));
                if (!ItemHandlerHelper.insertItemStacked(h, test, true).isEmpty()) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                machine = h;
                chosen = link;
                pattern.usedMachine(link);
                break;
            }
        }
        if (machine == null) {
            return Component.translatable(any ? "gui.grimstorage.job.machine_busy" : "gui.grimstorage.job.no_machine");
        }
        for (Map.Entry<ItemKey, Long> in : pattern.inputs().entrySet()) {
            long got = extract(in.getKey(), in.getValue(), false);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(machine, in.getKey().toStack((int) got), false);
            insertOrBuffer(rest);
        }
        if (!pattern.bulkInputs().isEmpty()) {
            chosen.onSent(pattern);
        }
        return null;
    }

    /** Called by a Machine Bus when a machine hands back a fluid or chemical it made. */
    public void onProcessingOutput(BulkKey key, long amount) {
        long left = amount;
        for (CraftingJob job : jobs) {
            if (left <= 0) {
                break;
            }
            left -= job.claim(key, left);
        }
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

    // ------------------------------------------------------------------ debug report

    /** Everything about this network worth seeing when something doesn't work, as plain text lines. */
    public List<String> debugReport() {
        List<String> out = new ArrayList<>();
        BlockPos c = controller.getBlockPos();
        out.add("Controller: " + controller.getLevel().dimension().location() + " " + c.getX() + " " + c.getY() + " " + c.getZ());
        out.add("Working: " + isActive() + (problem.getString().isEmpty() ? "" : "  problem: " + problem.getString()));
        out.add("Owner: " + (controller.owner() == null ? "nobody (open)" : controller.ownerName()) + "  open to all: " + controller.isOpen()
                + "  trusted: " + controller.trusted().values());
        // blocks by type
        Map<String, Integer> blocks = new java.util.TreeMap<>();
        for (GlobalPos p : members) {
            ServerLevel l = server == null ? null : server.getLevel(p.dimension());
            String id = l == null || !l.isLoaded(p.pos()) ? "(not loaded)"
                    : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(l.getBlockState(p.pos()).getBlock()).toString();
            blocks.merge(id, 1, Integer::sum);
        }
        out.add("Blocks: " + members.size() + "  " + blocks);
        long[] st = stats();
        out.add("Items: " + st[0] + " / " + st[1] + " in " + st[2] + " cells (+" + st[3] + " in singularity cells), "
                + contents().size() + " types");
        for (BulkKind k : BulkKind.values()) {
            long[] b = bulkStats(k);
            if (b[1] > 0) {
                out.add("  " + k.id() + ": " + b[0] + " / " + b[1] + "  " + bulkContents(k).size() + " types");
            }
        }
        out.add("Waiting for room (overflow): " + overflow.stream().mapToInt(ItemStack::getCount).sum() + " items");
        out.add("Soul Assemblers: " + assemblers.size() + "  (" + String.format(java.util.Locale.ROOT, "%.1f", craftsPerSecond()) + " crafts a second)");
        out.add("Chest Links: " + links.size());
        for (com.xtri6.grimstorage.block.entity.ChestLinkBlockEntity l : links) {
            out.add("  " + l.getBlockPos().toShortString() + "  mode " + l.mode() + (l.fillFirst() ? "  fills first" : ""));
        }
        out.add("Machine links: " + processors.size());
        for (MachineLink m : processors) {
            String where = m instanceof BlockEntity be ? be.getBlockPos().toShortString() : "?";
            Component name = m.machineName();
            out.add("  " + where + "  " + m.getClass().getSimpleName() + " -> " + (name == null ? "nothing" : name.getString())
                    + "  cards " + m.cards().size() + "  learned " + m.learnedCount());
            // what each machine makes, so a report shows whether a recipe (say obsidian dust) was learned at all
            int shown = 0;
            for (Pattern p : m.learnedPatterns()) {
                if (shown++ >= 120) {
                    out.add("    ...");
                    break;
                }
                out.add("    " + p.inputs().keySet().stream().map(k -> k.stack().getHoverName().getString()).toList()
                        + " -> " + p.outputs().keySet().stream().map(k -> k.stack().getHoverName().getString()).toList()
                        + (p.bulkOutputs().isEmpty() ? "" : " " + p.bulkOutputs().keySet()));
            }
        }
        // dust and grit recipes in the world, by recipe type: shows which type a machine would need to read
        out.add("Dust and grit recipes in this world (by recipe type, up to 4 samples each):");
        try {
            Map<String, List<String>> byType = new java.util.TreeMap<>();
            Map<String, Integer> counts = new java.util.TreeMap<>();
            for (RecipeHolder<?> h : controller.getLevel().getRecipeManager().getRecipes()) {
                ItemStack r = h.value().getResultItem(controller.getLevel().registryAccess());
                String name = r.getHoverName().getString();
                if (r.isEmpty() || !(name.toLowerCase(java.util.Locale.ROOT).contains("dust") || name.toLowerCase(java.util.Locale.ROOT).contains("grit"))) {
                    continue;
                }
                String type = BuiltInRegistries.RECIPE_TYPE.getKey(h.value().getType()) + "";
                counts.merge(type, 1, Integer::sum);
                List<String> samples = byType.computeIfAbsent(type, k -> new ArrayList<>());
                if (samples.size() < 4) {
                    samples.add(h.id() + " -> " + name);
                }
            }
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                out.add("  " + e.getKey() + ": " + e.getValue() + " recipes, e.g. " + byType.get(e.getKey()));
            }
        } catch (RuntimeException e) {
            out.add("  (could not read: " + e + ")");
        }
        int itemPatterns = 0;
        for (List<Pattern> l : patterns.values()) {
            itemPatterns += l.size();
        }
        out.add("Craftable: " + patterns.size() + " items (" + itemPatterns + " ways), " + bulkPatterns.size() + " fluids/chemicals");
        out.add("Jobs: " + jobs.size() + " (limit " + com.xtri6.grimstorage.ServerConfig.maxJobs() + ", "
                + com.xtri6.grimstorage.ServerConfig.maxJobsPerPlayer() + " a player)");
        for (CraftingJob j : jobs) {
            out.add("  " + j.amount() + "x " + j.target() + "  for " + j.source() + "  priority " + j.priority() + (j.paused() ? "  PAUSED" : "")
                    + "  " + Math.round(j.progress() * 100) + "%  status: " + j.status().getString());
            for (CraftingJob.StepState s : j.steps()) {
                Pattern p = s.pattern();
                String what = p == null ? "(recipe not found since the restart)"
                        : (p.kind() == Pattern.Kind.CRAFTING ? "craft " : "machine ") + p.inputs() + (p.bulkInputs().isEmpty() ? "" : " + " + bulkNames(p.bulkInputs()))
                        + " -> " + (p.primaryKey() instanceof BulkKey b ? b.name().getString() : p.primaryKey().toString())
                        + (p.swaps().isEmpty() ? "" : "  using " + p.swaps());
                out.add("    " + s.finished() + "/" + s.total() + " done, " + s.inMachines() + " in machines: " + what);
            }
        }
        return out;
    }

    private static String bulkNames(Map<BulkKey, Long> m) {
        StringBuilder sb = new StringBuilder("{");
        m.forEach((k, v) -> sb.append(sb.length() > 1 ? ", " : "").append(k.name().getString()).append('=').append(v));
        return sb.append('}').toString();
    }

    // ------------------------------------------------------------------ saving jobs with the world

    private boolean restored;
    private int lastSavedJobs = -1;

    /** Jobs and buffered items for the controller to save. */
    public net.minecraft.nbt.CompoundTag saveState(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (CraftingJob j : jobs) {
            if (!j.cancelled()) {
                list.add(j.save(registries));
            }
        }
        tag.put("jobs", list);
        net.minecraft.nbt.ListTag buffered = new net.minecraft.nbt.ListTag();
        for (ItemStack s : overflow) {
            if (!s.isEmpty()) {
                buffered.add(s.save(registries));
            }
        }
        tag.put("overflow", buffered);
        return tag;
    }

    /** The first time the network is built after the world loads: picks up the jobs the controller saved. */
    private void restoreSaved() {
        if (restored || server == null) {
            return;
        }
        restored = true;
        net.minecraft.nbt.CompoundTag saved = controller.takeSavedState();
        if (saved == null) {
            return;
        }
        for (net.minecraft.nbt.Tag t : saved.getList("jobs", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CraftingJob job = CraftingJob.load((net.minecraft.nbt.CompoundTag) t, server.registryAccess());
            if (job != null) {
                jobs.add(job);
            }
        }
        for (net.minecraft.nbt.Tag t : saved.getList("overflow", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            ItemStack.parse(server.registryAccess(), t).ifPresent(overflow::add);
        }
        sortJobs();
        resolveJobs();
    }

    private void resolveJobs() {
        for (CraftingJob j : jobs) {
            if (j.unresolved()) {
                j.resolve(this);
            }
        }
    }

    // ------------------------------------------------------------------ ticking

    public void tick(ServerLevel level) {
        if (!isActive()) {
            return;
        }
        tickCount++;
        if (!links.isEmpty() && (tickCount % 10 == 0 || builtEpoch != linkEpoch)) {
            linkEpoch = builtEpoch;
            refreshLinks();
        }

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

        if (tickCount % 20 == 0) {
            // jobs loaded with the world find their recipes once the machines around them have loaded
            resolveJobs();
        }
        // running jobs change all the time: keep the controller marked so they are saved with the world
        if ((!jobs.isEmpty() || !overflow.isEmpty() || lastSavedJobs != jobs.size()) && tickCount % 40 == 0) {
            lastSavedJobs = jobs.size();
            controller.setChanged();
        }

        Iterator<CraftingJob> it = jobs.iterator();
        while (it.hasNext()) {
            CraftingJob job = it.next();
            if (job.paused()) {
                continue;
            }
            job.tick(this);
            if (job.finished()) {
                it.remove();
                if (!job.cancelled()) {
                    alert(job, true);
                }
            } else if (job.shouldWarnStuck()) {
                alert(job, false);
            }
        }

        if (tickCount % 5 == 0) {
            for (MachineLink p : processors) {
                if (!p.linkRemoved()) {
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
            long version = allContentVersion();
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
