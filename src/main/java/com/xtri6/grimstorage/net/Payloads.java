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
        r.playToClient(TerminalJobs.TYPE, TerminalJobs.CODEC, Payloads::onTerminalJobs);
        r.playToServer(TerminalClick.TYPE, TerminalClick.CODEC, Payloads::onTerminalClick);
        r.playToServer(TerminalCraft.TYPE, TerminalCraft.CODEC, Payloads::onTerminalCraft);
        r.playToServer(CancelJob.TYPE, CancelJob.CODEC, Payloads::onCancelJob);
        r.playToServer(SetStockTarget.TYPE, SetStockTarget.CODEC, Payloads::onSetStockTarget);
        r.playToServer(DumpInventory.TYPE, DumpInventory.CODEC, Payloads::onDumpInventory);
        r.playToServer(ClearGrid.TYPE, ClearGrid.CODEC, Payloads::onClearGrid);
        r.playToServer(ToggleExport.TYPE, ToggleExport.CODEC, Payloads::onToggleExport);
        r.playToServer(TerminalBulkClick.TYPE, TerminalBulkClick.CODEC, Payloads::onTerminalBulkClick);
        r.playToServer(TerminalXp.TYPE, TerminalXp.CODEC, Payloads::onTerminalXp);
        r.playToServer(BusSetting.TYPE, BusSetting.CODEC, Payloads::onBusSetting);
        r.playToServer(SetGhosts.TYPE, SetGhosts.CODEC, Payloads::onSetGhosts);
        r.playToServer(FillGrid.TYPE, FillGrid.CODEC, Payloads::onFillGrid);
        r.playToServer(WriteCard.TYPE, WriteCard.CODEC, Payloads::onWriteCard);
    }

    // ------------------------------------------------------------------ server -> client

    public record TerminalContents(int containerId, boolean online, long used, long capacity, List<TerminalMenu.Entry> entries,
                                   List<TerminalMenu.BulkEntry> bulk, long[] bulkStats) implements CustomPacketPayload {
        public static final Type<TerminalContents> TYPE = id("terminal_contents");
        private static final StreamCodec<RegistryFriendlyByteBuf, TerminalMenu.Entry> ENTRY = StreamCodec.composite(
                ItemKey.STREAM_CODEC, TerminalMenu.Entry::key, ByteBufCodecs.VAR_LONG, TerminalMenu.Entry::count,
                ByteBufCodecs.BOOL, TerminalMenu.Entry::craftable, ByteBufCodecs.BOOL, TerminalMenu.Entry::learned, TerminalMenu.Entry::new);
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

    /** One step of a running job, for the terminal's crafting tab. */
    public record StepView(ItemStack output, boolean processing, long total, long finished, long inMachines, Component machine) {
        public static final StreamCodec<RegistryFriendlyByteBuf, StepView> CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, StepView::output, ByteBufCodecs.BOOL, StepView::processing, ByteBufCodecs.VAR_LONG, StepView::total,
                ByteBufCodecs.VAR_LONG, StepView::finished, ByteBufCodecs.VAR_LONG, StepView::inMachines,
                ComponentSerialization.STREAM_CODEC, StepView::machine, StepView::new);
    }

    /** A running job with every step, for the terminal's crafting tab. */
    public record JobView(JobInfo info, List<StepView> steps) {
        public static final StreamCodec<RegistryFriendlyByteBuf, JobView> CODEC = StreamCodec.composite(
                JobInfo.CODEC, JobView::info, StepView.CODEC.apply(ByteBufCodecs.list()), JobView::steps, JobView::new);
    }

    public record TerminalJobs(int containerId, List<JobView> jobs) implements CustomPacketPayload {
        public static final Type<TerminalJobs> TYPE = id("terminal_jobs");
        public static final StreamCodec<RegistryFriendlyByteBuf, TerminalJobs> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TerminalJobs::containerId, JobView.CODEC.apply(ByteBufCodecs.list()), TerminalJobs::jobs, TerminalJobs::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
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

    private static void onTerminalJobs(TerminalJobs p, IPayloadContext ctx) {
        if (ctx.player().containerMenu instanceof TerminalMenu m && m.containerId == p.containerId()) {
            m.clientJobs = p.jobs();
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

    /** Experience view: 0 = take one level, 1 = take ten levels, 2 = store all of your XP. */
    public record TerminalXp(int containerId, int action) implements CustomPacketPayload {
        public static final Type<TerminalXp> TYPE = id("terminal_xp");
        public static final StreamCodec<RegistryFriendlyByteBuf, TerminalXp> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TerminalXp::containerId, ByteBufCodecs.VAR_INT, TerminalXp::action, TerminalXp::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void onTerminalXp(TerminalXp p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof TerminalMenu m && m.containerId == p.containerId()) {
            m.handleXp(sp, p.action());
        }
    }

    /** Import/Export Bus screen: a button was pressed (see {@link com.xtri6.grimstorage.menu.BlockMenus.Bus#press}). */
    public record BusSetting(int containerId, int button) implements CustomPacketPayload {
        public static final Type<BusSetting> TYPE = id("bus_setting");
        public static final StreamCodec<RegistryFriendlyByteBuf, BusSetting> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BusSetting::containerId, ByteBufCodecs.VAR_INT, BusSetting::button, BusSetting::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void onBusSetting(BusSetting p, IPayloadContext ctx) {
        if (ctx.player().containerMenu instanceof BlockMenus.Bus m && m.containerId == p.containerId()) {
            m.press(p.button(), (net.minecraft.server.level.ServerPlayer) ctx.player());
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

    /** JEI: set ghost slots (filters, card editors). */
    public record SetGhosts(int containerId, boolean clear, List<Integer> slots, List<ItemStack> stacks) implements CustomPacketPayload {
        public static final Type<SetGhosts> TYPE = id("set_ghosts");
        public static final StreamCodec<RegistryFriendlyByteBuf, SetGhosts> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetGhosts::containerId, ByteBufCodecs.BOOL, SetGhosts::clear,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)), SetGhosts::slots,
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(64)), SetGhosts::stacks, SetGhosts::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** JEI's + button on the terminal: fill the crafting grid; each slot lists the items that would do. */
    public record FillGrid(int containerId, List<List<ItemStack>> options) implements CustomPacketPayload {
        public static final Type<FillGrid> TYPE = id("fill_grid");
        public static final StreamCodec<RegistryFriendlyByteBuf, FillGrid> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FillGrid::containerId,
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(64)).apply(ByteBufCodecs.list(9)), FillGrid::options, FillGrid::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Terminal: write the crafting grid's recipe onto a Recipe Card from the player's inventory. */
    public record WriteCard(int containerId) implements CustomPacketPayload {
        public static final Type<WriteCard> TYPE = id("write_card");
        public static final StreamCodec<RegistryFriendlyByteBuf, WriteCard> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, WriteCard::containerId, WriteCard::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void onSetGhosts(SetGhosts p, IPayloadContext ctx) {
        if (ctx.player().containerMenu instanceof com.xtri6.grimstorage.menu.GrimMenu m && m.containerId == p.containerId()) {
            m.setGhosts(p.clear(), p.slots(), p.stacks());
        }
    }

    private static void onFillGrid(FillGrid p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof TerminalMenu m && m.containerId == p.containerId()) {
            m.fillGrid(sp, p.options());
        }
    }

    private static void onWriteCard(WriteCard p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof TerminalMenu m && m.containerId == p.containerId()) {
            m.writeCard(sp);
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
        StorageNetwork net = null;
        if (ctx.player().containerMenu instanceof ControllerMenu m && m.containerId == p.containerId()) {
            net = m.network();
        } else if (ctx.player().containerMenu instanceof TerminalMenu m && m.containerId == p.containerId()) {
            net = m.network();
        }
        if (net != null) {
            net.cancel(p.job());
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
