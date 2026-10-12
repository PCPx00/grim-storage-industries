package com.xtri6.grimstorage.menu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.compat.Compat;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.storage.BulkKey;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.NetworkFluidHandler;
import com.xtri6.grimstorage.storage.CellStorage;
import com.xtri6.grimstorage.storage.CraftingPlanner;
import com.xtri6.grimstorage.storage.ItemKey;
import com.xtri6.grimstorage.storage.StorageNetwork;
import com.xtri6.grimstorage.storage.Experience;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.xtri6.grimstorage.util.Fmt;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;

/** The terminal: browse, take, store and order crafts. Block and wireless terminals share this menu. */
public class TerminalMenu extends GrimMenu {
    public static final int EXTRACT_STACK = 0;
    public static final int EXTRACT_HALF = 1;
    public static final int EXTRACT_TO_INVENTORY = 2;
    public static final int INSERT_ALL = 3;
    public static final int INSERT_ONE = 4;
    public static final int EXTRACT_ONE = 5;

    /** {@code learned}: craftable only through recipes a Machine Bus learned (listed in the Craftable tab, not everywhere). */
    public record Entry(ItemKey key, long count, boolean craftable, boolean learned, long added) {
        public Entry(ItemKey key, long count, boolean craftable, boolean learned) {
            this(key, count, craftable, learned, 0);
        }
    }

    /** A stored fluid / chemical / energy / Source amount, for the terminal's resource tabs. */
    public record BulkEntry(BulkKey key, long amount) {}

    /** Where the built-in crafting grid sits in the screen. */
    public static final int GRID_X = 196;
    public static final int GRID_Y = 30;
    public static final int RESULT_X = 214;
    public static final int RESULT_Y = 114;

    @Nullable
    private final Supplier<StorageNetwork> network;
    private final Predicate<Player> valid;
    private final Player player;
    private long sentVersion = -2;
    private long sentEpoch = -2;
    private int sinceSend;

    /** Client copy of the network contents, filled by {@link Payloads.TerminalContents}. */
    public List<Entry> clientEntries = List.of();
    public boolean clientOnline;
    public int clientRevision;
    public long clientUsed;
    public long clientCapacity;
    public List<BulkEntry> clientBulk = List.of();
    /** used, capacity for each {@link BulkKind}, in order. */
    public long[] clientBulkStats = new long[BulkKind.values().length * 2];
    /** Client copy of the running crafting jobs, filled by {@link Payloads.TerminalJobs}. */
    public List<Payloads.JobView> clientJobs = List.of();
    /** The last craft preview the server sent. */
    @Nullable
    public Payloads.Preview clientPreview;
    private int sinceJobs;
    private boolean sentJobs;

    private final TransientCraftingContainer craftGrid = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer craftResult = new ResultContainer();
    public final int gridStart;
    public final int resultIndex;
    private int shiftCrafted;

    public TerminalMenu(int id, Inventory inv, @Nullable Supplier<StorageNetwork> network, Predicate<Player> valid) {
        super(Registration.TERMINAL_MENU.get(), id);
        this.network = network;
        this.valid = valid;
        this.player = inv.player;
        addPlayerInventory(inv, 8, 140);
        gridStart = slots.size();
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(craftGrid, i, GRID_X + (i % 3) * 18, GRID_Y + (i / 3) * 18));
        }
        resultIndex = slots.size();
        addSlot(new RefillingResultSlot(inv.player, RESULT_X, RESULT_Y));
    }

    /** The crafting output: after each craft, any grid slot that ran out is refilled from the network. */
    private final class RefillingResultSlot extends ResultSlot {
        RefillingResultSlot(Player player, int x, int y) {
            super(player, craftGrid, craftResult, 0, x, y);
        }

        @Override
        public void onTake(Player p, ItemStack stack) {
            ItemStack[] before = new ItemStack[9];
            for (int i = 0; i < 9; i++) {
                before[i] = craftGrid.getItem(i).copy();
            }
            super.onTake(p, stack);
            StorageNetwork net = net();
            if (net == null) {
                return;
            }
            for (int i = 0; i < 9; i++) {
                ItemKey key = ItemKey.of(before[i]);
                if (key != null && craftGrid.getItem(i).isEmpty() && net.extract(key, 1, false) == 1) {
                    craftGrid.setItem(i, key.toStack(1));
                }
            }
        }
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == craftGrid && player instanceof ServerPlayer sp) {
            CraftingInput input = craftGrid.asCraftInput();
            Level level = sp.level();
            ItemStack out = ItemStack.EMPTY;
            Optional<RecipeHolder<CraftingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
            if (recipe.isPresent() && craftResult.setRecipeUsed(level, sp, recipe.get())) {
                ItemStack made = recipe.get().value().assemble(input, level.registryAccess());
                if (made.isItemEnabled(level.enabledFeatures())) {
                    out = made;
                }
            }
            craftResult.setItem(0, out);
            setRemoteSlot(resultIndex, out);
            sp.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), resultIndex, out));
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player p) {
        if (slotId == resultIndex && type == ClickType.QUICK_MOVE) {
            shiftCrafted = 0;
        }
        super.clicked(slotId, button, type, p);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != craftResult && super.canTakeItemForPickAll(stack, slot);
    }

    /** Server: sends everything in the crafting grid back into the network (or your inventory if it is full). */
    public void clearGrid(ServerPlayer sp) {
        StorageNetwork net = net();
        for (int i = 0; i < 9; i++) {
            ItemStack s = craftGrid.removeItemNoUpdate(i);
            if (s.isEmpty()) {
                continue;
            }
            if (net != null) {
                long in = net.insert(s, false);
                s.shrink((int) in);
            }
            if (!s.isEmpty()) {
                sp.getInventory().placeItemBackInInventory(s);
            }
        }
        slotsChanged(craftGrid);
        broadcastChanges();
    }

    /**
     * Fills the crafting grid for a recipe (JEI's + button): whatever is in the grid goes back first, then each
     * slot takes the first of its options the network has, or else one from the player's inventory. Slots with
     * nothing available stay empty.
     */
    public void fillGrid(ServerPlayer sp, List<List<ItemStack>> options) {
        clearGrid(sp);
        StorageNetwork net = net();
        for (int i = 0; i < Math.min(9, options.size()); i++) {
            for (ItemStack want : options.get(i)) {
                ItemKey key = ItemKey.of(want);
                if (key == null) {
                    continue;
                }
                if (net != null && net.extract(key, 1, false) == 1) {
                    craftGrid.setItem(i, key.toStack(1));
                    break;
                }
                int slot = findInInventory(sp, want);
                if (slot >= 0) {
                    craftGrid.setItem(i, sp.getInventory().removeItem(slot, 1));
                    break;
                }
            }
        }
        slotsChanged(craftGrid);
        broadcastChanges();
    }

    private static int findInInventory(ServerPlayer sp, ItemStack want) {
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            if (ItemStack.isSameItemSameComponents(inv.items.get(i), want)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Writes the recipe in the crafting grid onto a Recipe Card in the player's inventory (a blank one first),
     * so a card can be made from items in the network without taking them out.
     */
    public boolean writeCard(ServerPlayer sp) {
        Level level = sp.level();
        List<ItemStack> grid = new ArrayList<>(9);
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            ItemStack s = craftGrid.getItem(i);
            grid.add(s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1));
            any |= !s.isEmpty();
        }
        if (!any) {
            return false;
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isEmpty()) {
            sp.displayClientMessage(Component.translatable("gui.grimstorage.write_card.no_recipe").withStyle(ChatFormatting.RED), true);
            return false;
        }
        ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
        Inventory inv = sp.getInventory();
        int target = -1;
        for (int pass = 0; pass < 2 && target < 0; pass++) {
            for (int i = 0; i < inv.items.size(); i++) {
                ItemStack st = inv.items.get(i);
                if (st.is(Registration.RECIPE_CARD.get()) && (pass == 1 || !st.has(Registration.RECIPE_CARD_DATA.get()))) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0) {
            sp.displayClientMessage(Component.translatable("gui.grimstorage.write_card.no_card").withStyle(ChatFormatting.RED), true);
            return false;
        }
        ItemStack card = inv.items.get(target);
        if (card.getCount() > 1) {
            // one card off the stack gets the recipe
            card.shrink(1);
            ItemStack one = new ItemStack(Registration.RECIPE_CARD.get());
            one.set(Registration.RECIPE_CARD_DATA.get(), new com.xtri6.grimstorage.item.CardData.Recipe(grid, result));
            if (!inv.add(one)) {
                sp.drop(one, false);
            }
        } else {
            card.set(Registration.RECIPE_CARD_DATA.get(), new com.xtri6.grimstorage.item.CardData.Recipe(grid, result));
        }
        sp.displayClientMessage(Component.translatable("gui.grimstorage.write_card.done", result.getHoverName()).withStyle(ChatFormatting.AQUA), true);
        level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.2f);
        return true;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (p instanceof ServerPlayer sp) {
            boolean gone = !sp.isAlive() || sp.hasDisconnected();
            StorageNetwork net = net();
            for (int i = 0; i < 9; i++) {
                ItemStack s = craftGrid.removeItemNoUpdate(i);
                if (s.isEmpty()) {
                    continue;
                }
                if (net != null) {
                    s.shrink((int) net.insert(s, false));
                }
                if (!s.isEmpty()) {
                    if (gone) {
                        sp.drop(s, false);
                    } else {
                        sp.getInventory().placeItemBackInInventory(s);
                    }
                }
            }
        }
    }

    public static TerminalMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new TerminalMenu(id, inv, null, p -> true);
    }

    @Nullable
    private StorageNetwork net() {
        return network == null ? null : network.get();
    }

    /** The network this terminal shows (server side only). */
    @Nullable
    public StorageNetwork network() {
        return net();
    }

    /** What ordering {@code amount} of {@code key} would do: every step, what it takes from storage, what's missing. */
    @Nullable
    public Payloads.Preview preview(ItemKey key, int amount) {
        StorageNetwork net = net();
        if (net == null) {
            return null;
        }
        CraftingPlanner.Plan plan = net.plan(key, amount);
        List<Payloads.PreviewLine> steps = new ArrayList<>();
        long crafts = 0;
        long machineRuns = 0;
        for (CraftingPlanner.Step st : plan.steps()) {
            com.xtri6.grimstorage.storage.Pattern p = st.pattern();
            Component by;
            if (p.kind() == com.xtri6.grimstorage.storage.Pattern.Kind.CRAFTING) {
                by = Registration.ASSEMBLER.get().getName();
                crafts += st.times();
            } else {
                Component name = null;
                int machines = 0;
                for (com.xtri6.grimstorage.storage.MachineLink link : p.machines()) {
                    if (!link.linkRemoved()) {
                        machines++;
                        if (name == null) {
                            name = link.machineName();
                        }
                    }
                }
                by = name == null ? Component.translatable("gui.grimstorage.jobs.machine") : machines > 1 ? name.copy().append(" x" + machines) : name;
                machineRuns += (st.times() + Math.max(1, machines) - 1) / Math.max(1, machines);
            }
            Object made = p.primaryKey();
            if (made instanceof com.xtri6.grimstorage.storage.BulkKey b) {
                by = b.name().copy().append(" (" + (b.kind() == BulkKind.SOURCE ? "Source" : "mB") + ") · ").append(by);
            }
            steps.add(new Payloads.PreviewLine(p.icon(), st.times() * p.primaryAmount(), by));
        }
        // newest step last in the plan is the item asked for: show it first
        java.util.Collections.reverse(steps);
        List<Payloads.PreviewLine> used = new ArrayList<>();
        plan.used().forEach((k, n) -> used.add(new Payloads.PreviewLine(k.toStack(1), n, Component.empty())));
        used.sort((a, b) -> Long.compare(b.amount(), a.amount()));
        List<Payloads.PreviewLine> missing = new ArrayList<>();
        plan.missing().forEach((k, n) -> missing.add(new Payloads.PreviewLine(k.toStack(1), n, Component.empty())));
        plan.missingBulk().forEach((k, n) -> missing.add(new Payloads.PreviewLine(ItemStack.EMPTY, n,
                k.name().copy().append(k.kind() == BulkKind.SOURCE ? "" : " (mB)"))));
        double cps = net.craftsPerSecond();
        // machines: about 10 seconds a run (a furnace), shared between machines
        long seconds = (long) Math.ceil((cps > 0 ? crafts / cps : 0) + machineRuns * 10.0);
        return new Payloads.Preview(containerId, amount, plan.ok(), limit(steps, 40), limit(used, 40), limit(missing, 40),
                (int) Math.min(Integer.MAX_VALUE, seconds));
    }

    private static <T> List<T> limit(List<T> list, int max) {
        return list.size() <= max ? list : new ArrayList<>(list.subList(0, max));
    }

    /**
     * Moves the player inventory to a new height (client side; the screen can be made taller). Slot positions are
     * final, so the slots are swapped for copies at the new spot. The server never looks at slot positions.
     */
    public void movePlayerInventory(int y) {
        for (int i = playerStart; i < playerEnd; i++) {
            Slot old = slots.get(i);
            int row = (i - playerStart) / 9;
            int ny = row < 3 ? y + row * 18 : y + 58;
            if (old.y == ny) {
                continue;
            }
            Slot moved = new Slot(old.container, old.getContainerSlot(), old.x, ny);
            moved.index = old.index;
            slots.set(i, moved);
        }
    }

    @Override
    public boolean stillValid(Player p) {
        return valid.test(p);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (network == null || !(player instanceof ServerPlayer sp)) {
            return;
        }
        sinceSend++;
        StorageNetwork net = net();
        long version = net == null ? -1 : net.contentVersion();
        long epoch = net == null ? -1 : net.builtEpoch();
        boolean changed = version != sentVersion || epoch != sentEpoch;
        if ((changed && sinceSend >= 4) || sinceSend >= 100) {
            sentVersion = version;
            sentEpoch = epoch;
            sinceSend = 0;
            long[] stats = net == null ? new long[] {0, 0} : net.stats();
            List<BulkEntry> bulk = new ArrayList<>();
            long[] bulkStats = new long[BulkKind.values().length * 2];
            if (net != null) {
                for (BulkKind kind : BulkKind.values()) {
                    net.bulkContents(kind).forEach((k, v) -> bulk.add(new BulkEntry(k, v)));
                    long[] st = net.bulkStats(kind);
                    bulkStats[kind.ordinal() * 2] = st[0];
                    bulkStats[kind.ordinal() * 2 + 1] = st[1];
                }
            }
            PacketDistributor.sendToPlayer(sp, new Payloads.TerminalContents(containerId, net != null, stats[0], stats[1], snapshot(net),
                    bulk, bulkStats));
        }
        // running jobs, twice a second while there are any (and once more when the last one ends)
        if (++sinceJobs >= 10) {
            sinceJobs = 0;
            List<Payloads.JobView> jobs = jobViews(net);
            if (!jobs.isEmpty() || sentJobs) {
                sentJobs = !jobs.isEmpty();
                PacketDistributor.sendToPlayer(sp, new Payloads.TerminalJobs(containerId, jobs));
            }
        }
    }

    public static List<Payloads.JobView> jobViews(@Nullable StorageNetwork net) {
        List<Payloads.JobView> out = new ArrayList<>();
        if (net == null) {
            return out;
        }
        for (com.xtri6.grimstorage.storage.CraftingJob job : net.jobs()) {
            List<Payloads.StepView> steps = new ArrayList<>();
            for (com.xtri6.grimstorage.storage.CraftingJob.StepState st : job.steps()) {
                com.xtri6.grimstorage.storage.Pattern p = st.pattern();
                boolean processing = p.kind() == com.xtri6.grimstorage.storage.Pattern.Kind.PROCESSING;
                Component machine;
                if (!processing) {
                    machine = Registration.ASSEMBLER.get().getName();
                } else {
                    Component name = null;
                    int count = 0;
                    for (com.xtri6.grimstorage.storage.MachineLink link : p.machines()) {
                        if (!link.linkRemoved()) {
                            count++;
                            if (name == null) {
                                name = link.machineName();
                            }
                        }
                    }
                    machine = name == null ? Component.translatable("gui.grimstorage.jobs.machine") : count > 1
                            ? name.copy().append(" x" + count) : name;
                }
                if (p.primaryKey() instanceof com.xtri6.grimstorage.storage.BulkKey b) {
                    machine = b.name().copy().append(" · ").append(machine);
                }
                steps.add(new Payloads.StepView(p.icon(), processing, st.total(), st.finished(), st.inMachines(), machine));
            }
            out.add(new Payloads.JobView(new Payloads.JobInfo(job.id(), job.target().toStack(1), job.amount(), job.status(), job.progress(),
                    job.source(), job.priority(), job.paused()), steps));
        }
        return out;
    }

    private static List<Entry> snapshot(@Nullable StorageNetwork net) {
        List<Entry> out = new ArrayList<>();
        if (net == null) {
            return out;
        }
        Set<ItemKey> craftable = new HashSet<>(net.craftables());
        Set<ItemKey> explicit = net.explicitCraftables();
        for (Map.Entry<ItemKey, Long> e : net.contents().entrySet()) {
            boolean c = craftable.remove(e.getKey());
            out.add(new Entry(e.getKey(), e.getValue(), c, c && !explicit.contains(e.getKey()), net.lastAdded(e.getKey())));
        }
        for (ItemKey k : craftable) {
            out.add(new Entry(k, 0, true, !explicit.contains(k)));
        }
        return out;
    }

    /** Server: a click on an item (or the empty grid) in the terminal screen. */
    public void handleClick(ServerPlayer sp, @Nullable ItemKey key, int action) {
        StorageNetwork net = net();
        if (net == null) {
            return;
        }
        ItemStack carried = getCarried();
        switch (action) {
            case EXTRACT_STACK, EXTRACT_HALF -> {
                if (key == null || !carried.isEmpty()) {
                    return;
                }
                long want = Math.min(key.maxStackSize(), net.count(key));
                if (action == EXTRACT_HALF) {
                    want = (want + 1) / 2;
                }
                long got = net.extract(key, want, false);
                if (got > 0) {
                    setCarried(key.toStack((int) got));
                }
            }
            case EXTRACT_ONE -> {
                if (key == null || (!carried.isEmpty() && (!key.matches(carried) || carried.getCount() >= carried.getMaxStackSize()))) {
                    return;
                }
                if (net.extract(key, 1, false) == 1) {
                    setCarried(carried.isEmpty() ? key.toStack(1) : carried.copyWithCount(carried.getCount() + 1));
                }
            }
            case EXTRACT_TO_INVENTORY -> {
                if (key == null) {
                    return;
                }
                long got = net.extract(key, Math.min(key.maxStackSize(), net.count(key)), false);
                if (got > 0) {
                    ItemStack stack = key.toStack((int) got);
                    sp.getInventory().add(stack);
                    if (!stack.isEmpty()) {
                        net.insertOrBuffer(stack);
                    }
                }
            }
            case INSERT_ALL -> {
                if (!carried.isEmpty()) {
                    long in = net.insert(carried, false);
                    carried.shrink((int) in);
                    setCarried(carried);
                }
            }
            case INSERT_ONE -> {
                if (!carried.isEmpty() && net.insert(ItemKey.of(carried), 1, false) == 1) {
                    carried.shrink(1);
                    setCarried(carried);
                }
            }
            default -> {
            }
        }
        broadcastChanges();
    }

    /**
     * Server: a click in the fluid/chemical view. A full bucket or tank on the cursor is emptied into the network;
     * an empty one is filled with the clicked fluid or chemical.
     */
    public void handleBulkClick(ServerPlayer sp, BulkKind kind, @Nullable BulkKey key) {
        StorageNetwork net = net();
        ItemStack carried = getCarried();
        if (net == null || carried.isEmpty()) {
            return;
        }
        if (kind == BulkKind.FLUID) {
            IItemHandler inv = new PlayerMainInvWrapper(sp.getInventory());
            FluidActionResult result;
            if (FluidUtil.getFluidContained(carried).isPresent()) {
                result = FluidUtil.tryEmptyContainerAndStow(carried, new NetworkFluidHandler(() -> net, () -> true, null), inv,
                        Integer.MAX_VALUE, sp, true);
            } else if (key != null && key.kind() == BulkKind.FLUID) {
                result = FluidUtil.tryFillContainerAndStow(carried, new NetworkFluidHandler(() -> net, () -> true, key), inv,
                        Integer.MAX_VALUE, sp, true);
            } else {
                return;
            }
            if (result.isSuccess()) {
                setCarried(result.getResult());
            }
        } else if (kind == BulkKind.CHEMICAL && Compat.chemicals() != null) {
            setCarried(Compat.chemicals().click(sp, net, carried, key).copy());
        }
        broadcastChanges();
    }

    /** Server: take levels out of the network's XP cells, or store all of the player's XP. */
    public void handleXp(ServerPlayer sp, int action) {
        StorageNetwork network = net();
        if (network == null) {
            return;
        }
        if (action == 2) {
            long have = Experience.points(sp);
            long stored = network.insertBulk(Experience.KEY, have, false);
            if (stored > 0) {
                Experience.setPoints(sp, have - stored);
                sp.level().playSound(null, sp.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.PLAYERS, 0.4f, 0.8f);
            }
        } else if (action == 0 || action == 1) {
            long want = Experience.pointsToGain(sp, action == 0 ? 1 : 10);
            long got = network.extractBulk(Experience.KEY, Math.max(0, want), false);
            if (got > 0) {
                Experience.setPoints(sp, Experience.points(sp) + got);
                sp.level().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                        SoundSource.PLAYERS, 0.5f, 1.2f);
            }
        }
        broadcastChanges();
    }

    /** Server: the player asked for something to be crafted. */
    public void handleCraft(ServerPlayer sp, ItemKey key, int amount) {
        StorageNetwork net = net();
        if (net == null || amount <= 0) {
            return;
        }
        if (!net.canStartJob()) {
            sp.displayClientMessage(Component.translatable("message.grimstorage.craft.busy").withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!net.canStartJob(sp.getGameProfile().getName())) {
            sp.displayClientMessage(Component.translatable("message.grimstorage.craft.your_limit",
                    com.xtri6.grimstorage.ServerConfig.maxJobsPerPlayer()).withStyle(ChatFormatting.RED), false);
            return;
        }
        CraftingPlanner.Plan plan = net.request(key, Math.min(amount, 1_000_000), sp.getGameProfile().getName());
        if (plan.ok()) {
            sp.displayClientMessage(Component.translatable("message.grimstorage.craft.started", Fmt.big(amount), key.stack().getHoverName())
                    .withStyle(ChatFormatting.AQUA), true);
            return;
        }
        MutableComponent msg = Component.translatable(plan.loop() ? "message.grimstorage.craft.loop" : "message.grimstorage.craft.missing",
                key.stack().getHoverName()).withStyle(ChatFormatting.RED);
        int shown = 0;
        for (Map.Entry<ItemKey, Long> m : plan.missing().entrySet()) {
            if (shown++ >= 6) {
                break;
            }
            msg.append(Component.literal("\n  " + Fmt.big(m.getValue()) + "x ").withStyle(ChatFormatting.GRAY))
                    .append(m.getKey().stack().getHoverName().copy().withStyle(ChatFormatting.GRAY));
        }
        for (Map.Entry<com.xtri6.grimstorage.storage.BulkKey, Long> m : plan.missingBulk().entrySet()) {
            if (shown++ >= 6) {
                break;
            }
            msg.append(Component.literal("\n  " + Fmt.big(m.getValue()) + " " + (m.getKey().kind() == BulkKind.SOURCE ? "" : "mB ")).withStyle(ChatFormatting.GRAY))
                    .append(m.getKey().name().copy().withStyle(ChatFormatting.GRAY));
        }
        sp.displayClientMessage(msg, false);
    }

    /**
     * Shift-click: your inventory goes into the network, the crafting grid goes back into the network, and the
     * crafting result crafts up to one full stack into your inventory (refilling the grid as it goes).
     */
    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (index == resultIndex) {
            ItemStack stack = slot.getItem();
            ItemStack copy = stack.copy();
            if (shiftCrafted >= copy.getMaxStackSize()) {
                return ItemStack.EMPTY;
            }
            stack.getItem().onCraftedBy(stack, p.level(), p);
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copy);
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == copy.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(p, stack);
            shiftCrafted += copy.getCount();
            return copy;
        }
        StorageNetwork net = net();
        if (index >= gridStart && index < resultIndex) {
            ItemStack stack = slot.getItem();
            if (net != null) {
                stack.shrink((int) net.insert(stack, false));
            }
            if (!stack.isEmpty()) {
                moveItemStackTo(stack, playerStart, playerEnd, false);
            }
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        if (net == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        long in = net.insert(stack, false);
        if (in > 0) {
            stack.shrink((int) in);
            slot.setChanged();
        }
        return ItemStack.EMPTY;
    }
}
