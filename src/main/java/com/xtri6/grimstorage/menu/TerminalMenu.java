package com.xtri6.grimstorage.menu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.net.Payloads;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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

    public TerminalMenu(int id, Inventory inv, @Nullable Supplier<StorageNetwork> network, Predicate<Player> valid) {
        super(Registration.TERMINAL_MENU.get(), id);
        this.network = network;
        this.valid = valid;
        this.player = inv.player;
        addPlayerInventory(inv, 8, 140);
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
            PacketDistributor.sendToPlayer(sp, new Payloads.TerminalContents(containerId, net != null, snapshot(net)));
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

    /** Shift-clicking your own inventory sends that stack into the network. */
    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        StorageNetwork net = net();
        if (net == null || !slot.hasItem()) {
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
