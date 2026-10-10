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

    public record Entry(ItemKey key, long count, boolean craftable) {}

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
        long version = net == null ? -1 : CellStorage.version();
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
    }

    private static List<Entry> snapshot(@Nullable StorageNetwork net) {
        List<Entry> out = new ArrayList<>();
        if (net == null) {
            return out;
        }
        Set<ItemKey> craftable = new HashSet<>(net.craftables());
        for (Map.Entry<ItemKey, Long> e : net.contents().entrySet()) {
            out.add(new Entry(e.getKey(), e.getValue(), craftable.remove(e.getKey())));
        }
        for (ItemKey k : craftable) {
            out.add(new Entry(k, 0, true));
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
