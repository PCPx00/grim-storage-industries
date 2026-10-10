package com.xtri6.grimstorage.net;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.xtri6.grimstorage.GrimStorage;
import com.xtri6.grimstorage.item.WirelessTerminalItem;
import com.xtri6.grimstorage.menu.BlockMenus;
import com.xtri6.grimstorage.menu.ControllerMenu;
import com.xtri6.grimstorage.menu.TerminalMenu;
import com.xtri6.grimstorage.storage.BulkKey;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.ItemKey;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Every custom packet. Client-bound handlers only touch the player's open menu (a common class), so none of
 * this references client-only code and it is safe on a dedicated server.
 */
public final class Payloads {
    private Payloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(GrimStorage.MODID, name));
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(TerminalContents.TYPE, TerminalContents.CODEC, Payloads::onTerminalContents);
        r.playToClient(ControllerStatus.TYPE, ControllerStatus.CODEC, Payloads::onControllerStatus);
        r.playToServer(TerminalClick.TYPE, TerminalClick.CODEC, Payloads::onTerminalClick);
        r.playToServer(TerminalCraft.TYPE, TerminalCraft.CODEC, Payloads::onTerminalCraft);
        r.playToServer(CancelJob.TYPE, CancelJob.CODEC, Payloads::onCancelJob);
        r.playToServer(SetStockTarget.TYPE, SetStockTarget.CODEC, Payloads::onSetStockTarget);
        r.playToServer(DumpInventory.TYPE, DumpInventory.CODEC, Payloads::onDumpInventory);
        r.playToServer(ClearGrid.TYPE, ClearGrid.CODEC, Payloads::onClearGrid);
        r.playToServer(ToggleExport.TYPE, ToggleExport.CODEC, Payloads::onToggleExport);
        r.playToServer(TerminalBulkClick.TYPE, TerminalBulkClick.CODEC, Payloads::onTerminalBulkClick);
    }

    // ------------------------------------------------------------------ server -> client

    public record TerminalContents(int containerId, boolean online, long used, long capacity, List<TerminalMenu.Entry> entries,
                                   List<TerminalMenu.BulkEntry> bulk, long[] bulkStats) implements CustomPacketPayload {
        public static final Type<TerminalContents> TYPE = id("terminal_contents");
        private static final StreamCodec<RegistryFriendlyByteBuf, TerminalMenu.Entry> ENTRY = StreamCodec.composite(
                ItemKey.STREAM_CODEC, TerminalMenu.Entry::key, ByteBufCodecs.VAR_LONG, TerminalMenu.Entry::count,
                ByteBufCodecs.BOOL, TerminalMenu.Entry::craftable, TerminalMenu.Entry::new);
        private static final StreamCodec<RegistryFriendlyByteBuf, TerminalMenu.BulkEntry> BULK = StreamCodec.composite(
                BulkKey.STREAM_CODEC, TerminalMenu.BulkEntry::key, ByteBufCodecs.VAR_LONG, TerminalMenu.BulkEntry::amount,
                TerminalMenu.BulkEntry::new);
        public static final StreamCodec<RegistryFriendlyByteBuf, TerminalContents> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.containerId);
                    buf.writeBoolean(p.online);
                    buf.writeVarLong(p.used);
                    buf.writeVarLong(p.capacity);
                    ENTRY.apply(ByteBufCodecs.list()).encode(buf, p.entries);
                    BULK.apply(ByteBufCodecs.list()).encode(buf, p.bulk);
                    buf.writeVarInt(p.bulkStats.length);
                    for (long l : p.bulkStats) {
                        buf.writeVarLong(l);
                    }
                },
                buf -> {
                    int id = buf.readVarInt();
                    boolean online = buf.readBoolean();
                    long used = buf.readVarLong();
                    long cap = buf.readVarLong();
                    List<TerminalMenu.Entry> entries = ENTRY.apply(ByteBufCodecs.list()).decode(buf);
                    List<TerminalMenu.BulkEntry> bulk = BULK.apply(ByteBufCodecs.list()).decode(buf);
                    long[] stats = new long[buf.readVarInt()];
                    for (int i = 0; i < stats.length; i++) {
                        stats[i] = buf.readVarLong();
                    }
                    return new TerminalContents(id, online, used, cap, entries, bulk, stats);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record JobInfo(UUID id, ItemStack icon, long amount, Component status, float progress, String source) {
        public static final StreamCodec<RegistryFriendlyByteBuf, JobInfo> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, JobInfo::id, ItemStack.STREAM_CODEC, JobInfo::icon, ByteBufCodecs.VAR_LONG, JobInfo::amount,
                ComponentSerialization.STREAM_CODEC, JobInfo::status, ByteBufCodecs.FLOAT, JobInfo::progress,
                ByteBufCodecs.STRING_UTF8, JobInfo::source, JobInfo::new);
    }

    public record ControllerStatus(int containerId, boolean active, Component problem, long used, long capacity, long singular, int types,
                                   int cells, int drives, int members, List<JobInfo> jobs, long[] bulkStats) implements CustomPacketPayload {
        public static final Type<ControllerStatus> TYPE = id("controller_status");
        public static final StreamCodec<RegistryFriendlyByteBuf, ControllerStatus> CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeVarInt(s.containerId);
                    buf.writeBoolean(s.active);
                    ComponentSerialization.STREAM_CODEC.encode(buf, s.problem);
                    buf.writeVarLong(s.used);
                    buf.writeVarLong(s.capacity);
                    buf.writeVarLong(s.singular);
                    buf.writeVarInt(s.types);
                    buf.writeVarInt(s.cells);
                    buf.writeVarInt(s.drives);
                    buf.writeVarInt(s.members);
                    buf.writeVarInt(s.jobs.size());
                    for (JobInfo j : s.jobs) {
                        JobInfo.CODEC.encode(buf, j);
                    }
                    buf.writeVarInt(s.bulkStats.length);
                    for (long l : s.bulkStats) {
                        buf.writeVarLong(l);
                    }
                },
                buf -> {
                    int id = buf.readVarInt();
                    boolean active = buf.readBoolean();
                    Component problem = ComponentSerialization.STREAM_CODEC.decode(buf);
                    long used = buf.readVarLong();
                    long cap = buf.readVarLong();
                    long singular = buf.readVarLong();
                    int types = buf.readVarInt();
                    int cells = buf.readVarInt();
                    int drives = buf.readVarInt();
                    int members = buf.readVarInt();
                    int n = buf.readVarInt();
                    List<JobInfo> jobs = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        jobs.add(JobInfo.CODEC.decode(buf));
                    }
                    long[] bulk = new long[buf.readVarInt()];
                    for (int i = 0; i < bulk.length; i++) {
                        bulk[i] = buf.readVarLong();
                    }
                    return new ControllerStatus(id, active, problem, used, cap, singular, types, cells, drives, members, jobs, bulk);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void onTerminalContents(TerminalContents p, IPayloadContext ctx) {
        if (ctx.player().containerMenu instanceof TerminalMenu m && m.containerId == p.containerId()) {
            m.clientEntries = p.entries();
            m.clientOnline = p.online();
            m.clientUsed = p.used();
            m.clientCapacity = p.capacity();
            m.clientBulk = p.bulk();
            if (p.bulkStats().length == m.clientBulkStats.length) {
                m.clientBulkStats = p.bulkStats();
            }
            m.clientRevision++;
        }
    }

    private static void onControllerStatus(ControllerStatus p, IPayloadContext ctx) {
        if (ctx.player().containerMenu instanceof ControllerMenu m && m.containerId == p.containerId()) {
            m.clientStatus = p;
        }
    }

    // ------------------------------------------------------------------ client -> server

    public record TerminalClick(int containerId, ItemStack key, int action) implements CustomPacketPayload {
        public static final Type<TerminalClick> TYPE = id("terminal_click");
        public static final StreamCodec<RegistryFriendlyByteBuf, TerminalClick> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TerminalClick::containerId, ItemStack.OPTIONAL_STREAM_CODEC, TerminalClick::key,
                ByteBufCodecs.VAR_INT, TerminalClick::action, TerminalClick::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record TerminalCraft(int containerId, ItemStack key, int amount) implements CustomPacketPayload {
        public static final Type<TerminalCraft> TYPE = id("terminal_craft");
        public static final StreamCodec<RegistryFriendlyByteBuf, TerminalCraft> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TerminalCraft::containerId, ItemStack.STREAM_CODEC, TerminalCraft::key,
                ByteBufCodecs.VAR_INT, TerminalCraft::amount, TerminalCraft::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record CancelJob(int containerId, UUID job) implements CustomPacketPayload {
        public static final Type<CancelJob> TYPE = id("cancel_job");
        public static final StreamCodec<RegistryFriendlyByteBuf, CancelJob> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CancelJob::containerId, UUIDUtil.STREAM_CODEC, CancelJob::job, CancelJob::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetStockTarget(int containerId, int rule, int target) implements CustomPacketPayload {
        public static final Type<SetStockTarget> TYPE = id("set_stock_target");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetStockTarget> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetStockTarget::containerId, ByteBufCodecs.VAR_INT, SetStockTarget::rule,
                ByteBufCodecs.VAR_INT, SetStockTarget::target, SetStockTarget::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DumpInventory() implements CustomPacketPayload {
        public static final Type<DumpInventory> TYPE = id("dump_inventory");
        public static final StreamCodec<RegistryFriendlyByteBuf, DumpInventory> CODEC = StreamCodec.unit(new DumpInventory());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ToggleExport(int containerId, int kind) implements CustomPacketPayload {
        public static final Type<ToggleExport> TYPE = id("toggle_export");
        public static final StreamCodec<RegistryFriendlyByteBuf, ToggleExport> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ToggleExport::containerId, ByteBufCodecs.VAR_INT, ToggleExport::kind, ToggleExport::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void onToggleExport(ToggleExport p, IPayloadContext ctx) {
        if (ctx.player().containerMenu instanceof BlockMenus.Filter m && m.containerId == p.containerId()
                && p.kind() >= 0 && p.kind() < BulkKind.values().length) {
            m.toggleExport(BulkKind.values()[p.kind()]);
        }
    }

    /** A click in the terminal's fluid/chemical view, with whatever container is on the cursor. */
    public record TerminalBulkClick(int containerId, int kind, java.util.Optional<BulkKey> key) implements CustomPacketPayload {
        public static final Type<TerminalBulkClick> TYPE = id("terminal_bulk_click");
        public static final StreamCodec<RegistryFriendlyByteBuf, TerminalBulkClick> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TerminalBulkClick::containerId, ByteBufCodecs.VAR_INT, TerminalBulkClick::kind,
                ByteBufCodecs.optional(BulkKey.STREAM_CODEC), TerminalBulkClick::key, TerminalBulkClick::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void onTerminalBulkClick(TerminalBulkClick p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof TerminalMenu m && m.containerId == p.containerId()
                && p.kind() >= 0 && p.kind() < BulkKind.values().length) {
            m.handleBulkClick(sp, BulkKind.values()[p.kind()], p.key().orElse(null));
        }
    }

    public record ClearGrid(int containerId) implements CustomPacketPayload {
        public static final Type<ClearGrid> TYPE = id("clear_grid");
        public static final StreamCodec<RegistryFriendlyByteBuf, ClearGrid> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ClearGrid::containerId, ClearGrid::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void onClearGrid(ClearGrid p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof TerminalMenu m && m.containerId == p.containerId()) {
            m.clearGrid(sp);
        }
    }

    private static void onTerminalClick(TerminalClick p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof TerminalMenu m && m.containerId == p.containerId()) {
            m.handleClick(sp, ItemKey.of(p.key()), p.action());
        }
    }

    private static void onTerminalCraft(TerminalCraft p, IPayloadContext ctx) {
        ItemKey key = ItemKey.of(p.key());
        if (key != null && ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof TerminalMenu m
                && m.containerId == p.containerId()) {
            m.handleCraft(sp, key, p.amount());
        }
    }

    private static void onCancelJob(CancelJob p, IPayloadContext ctx) {
        if (ctx.player().containerMenu instanceof ControllerMenu m && m.containerId == p.containerId()) {
            StorageNetwork net = m.network();
            if (net != null) {
                net.cancel(p.job());
            }
        }
    }

    private static void onSetStockTarget(SetStockTarget p, IPayloadContext ctx) {
        if (ctx.player().containerMenu instanceof BlockMenus.StockKeeper m && m.containerId == p.containerId()) {
            m.setTarget(p.rule(), p.target());
        }
    }

    /** The "dump inventory" key: sends your main inventory (not hotbar or armor) into the network. */
    private static void onDumpInventory(DumpInventory p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) {
            return;
        }
        StorageNetwork net = WirelessTerminalItem.findCarried(sp);
        if (net == null) {
            sp.displayClientMessage(Component.translatable("message.grimstorage.dump.no_terminal").withStyle(ChatFormatting.RED), true);
            return;
        }
        int moved = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (s.isEmpty() || s.getItem() instanceof WirelessTerminalItem) {
                continue;
            }
            long in = net.insert(s, false);
            if (in > 0) {
                moved += (int) in;
                s.shrink((int) in);
            }
        }
        sp.getInventory().setChanged();
        sp.displayClientMessage(Component.translatable("message.grimstorage.dump.done", moved).withStyle(ChatFormatting.AQUA), true);
    }
}
