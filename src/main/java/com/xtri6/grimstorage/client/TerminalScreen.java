package com.xtri6.grimstorage.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.ClientConfig;
import com.xtri6.grimstorage.client.ui.GrimButton;
import com.xtri6.grimstorage.client.ui.Theme;
import com.xtri6.grimstorage.client.ui.Ui;
import com.xtri6.grimstorage.menu.TerminalMenu;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.ItemKey;
import com.xtri6.grimstorage.storage.StorageNetwork;
import com.xtri6.grimstorage.util.Fmt;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/**
 * The storage terminal (block and wireless). Left: filter tabs. Middle: the network's items, search and sort.
 * Right: a 3x3 crafting grid that refills itself from the network, plus a storage gauge. Along the top: Everything,
 * items, each resource and the crafting jobs. The gear opens settings (JEI search sync, search focus and memory, size).
 *
 * Click an item to take a stack, right-click for half, shift-click to send it to your inventory, click with
 * an item held to store it. Craftable items show "Craft": click (or ctrl/middle-click) to order a craft.
 */
public class TerminalScreen extends GrimScreen<TerminalMenu> {
    private static final int W = 272;
    private static final int H = 222;
    private static final int COLS = 9;
    /** Rows of items at the normal size; the terminal can be made taller in its settings. */
    private static final int BASE_ROWS = 6;
    private static final int MAX_ROWS = 16;
    private static final int GX = 8;
    private static final int GY = 18;
    private static final int BAR_X = 174;
    private static final int RIGHT_X = 192;
    private static final int TAB_W = 22;
    private static final int TAB_H = 18;
    private static final String[] SORTS = {"gui.grimstorage.sort.count", "gui.grimstorage.sort.name", "gui.grimstorage.sort.mod", "gui.grimstorage.sort.recent"};

    /** Remembered between openings so the terminal reopens the way you left it. */
    private static ItemCategories.Category lastCategory = ItemCategories.Category.ALL;
    private static int lastSort;

    /** Which resource the terminal shows: null = items, otherwise fluids / chemicals / energy / Source. */
    private static BulkKind lastMode;
    private static final int TOP_W = 24;
    private static final int TOP_H = 21;

    /** The "Everything" tab: items and every other resource in one grid. */
    private static boolean lastAll;

    @Nullable
    private BulkKind mode = lastMode != null && lastMode.available() ? lastMode : null;
    private boolean all = lastAll;
    private List<TerminalMenu.BulkEntry> bulkView = new ArrayList<>();
    private ItemCategories.Category category = lastCategory;
    private final int[] categoryCounts = new int[ItemCategories.Category.values().length];
    private EditBox search;
    private GrimButton sortButton;
    private int sort = lastSort;
    private int scroll;
    private boolean draggingBar;
    private int seenRevision = -1;
    private String seenSearch = "";
    private List<TerminalMenu.Entry> view = new ArrayList<>();

    /** Item rows shown, from the size setting. */
    private int rows = BASE_ROWS;
    /** The search, remembered between openings when "keep search" is on. */
    private static String lastSearch = "";
    /** JEI's search as we last saw it, to notice when it changes. */
    @Nullable
    private String lastJei;
    private boolean opened;

    /** The crafting jobs tab (last tab along the top). */
    private static boolean lastJobs;
    private boolean jobs = lastJobs;

    private boolean settingsOpen;
    private GrimButton settingsButton;
    private final List<GrimButton> settingButtons = new ArrayList<>();

    @Nullable
    private TerminalMenu.Entry crafting;
    private EditBox craftAmount;
    private GrimButton craftGo;
    private GrimButton craftCancel;

    public TerminalScreen(TerminalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
        this.inventoryLabelY = 129;
    }

    @Override
    protected int topExtra() {
        return TOP_H - 3;
    }

    /** How many item rows fit on this screen. */
    private int rowsThatFit() {
        int free = height - topExtra() - 4 - H;
        return Math.max(BASE_ROWS, Math.min(MAX_ROWS, BASE_ROWS + free / 18));
    }

    @Override
    protected void init() {
        ClientConfig.TerminalSize size = ClientConfig.terminalSize();
        int fit = rowsThatFit();
        rows = size == ClientConfig.TerminalSize.AUTO ? fit : Math.max(BASE_ROWS, Math.min(size.rows, fit));
        int extra = (rows - BASE_ROWS) * 18;
        imageHeight = H + extra;
        inventoryLabelY = 129 + extra;
        menu.movePlayerInventory(140 + extra);
        super.init();
        ItemCategories.reset();
        Theme t = t();
        if (!opened) {
            opened = true;
            seenSearch = ClientConfig.keepSearch() ? lastSearch : "";
            if (syncing()) {
                RecipeViewerSearch.set(seenSearch);
            }
            lastJei = seenSearch;
        }
        search = new EditBox(font, leftPos + 100, topPos + 6, 76, 9, Component.translatable("gui.grimstorage.search"));
        search.setMaxLength(50);
        search.setBordered(false);
        search.setTextColor(t.text() & 0xFFFFFF);
        search.setHint(Component.translatable("gui.grimstorage.search.hint").withColor(t.textFaint()));
        search.setValue(seenSearch);
        addRenderableWidget(search);
        if (ClientConfig.focusSearch() && crafting == null && !settingsOpen) {
            search.setFocused(true);
            setFocused(search);
        }
        settingsButton = new GrimButton(leftPos + 240, topPos + 4, 13, 12, Component.literal("\u2699"), b -> toggleSettings());
        settingsButton.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.settings")));
        addRenderableWidget(settingsButton);
        buildSettings();
        sortButton = new GrimButton(leftPos + 182, topPos + 4, 14, 12, Component.literal("⇅"), b -> {
            sort = (sort + 1) % SORTS.length;
            lastSort = sort;
            refresh();
        });
        addRenderableWidget(sortButton);
        GrimButton clear = new GrimButton(leftPos + RIGHT_X + 60, topPos + 17, 11, 10, Component.literal("x"),
                b -> PacketDistributor.sendToServer(new Payloads.ClearGrid(menu.containerId)));
        clear.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.craft.clear")));
        addRenderableWidget(clear);
        GrimButton write = new GrimButton(leftPos + RIGHT_X + 47, topPos + 17, 11, 10, Component.literal("\u270e"),
                b -> PacketDistributor.sendToServer(new Payloads.WriteCard(menu.containerId)));
        write.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.write_card")));
        addRenderableWidget(write);

        craftAmount = new EditBox(font, 0, 0, 100, 9, Component.translatable("gui.grimstorage.craft.amount"));
        craftAmount.setMaxLength(7);
        craftAmount.setBordered(false);
        craftAmount.setFilter(s -> s.matches("\\d{0,7}"));
        craftGo = new GrimButton(0, 0, 60, 16, Component.translatable("gui.grimstorage.craft.go"), b -> confirmCraft(), true);
        craftCancel = new GrimButton(0, 0, 60, 16, Component.translatable("gui.cancel"), b -> closeCraft());
        refresh();
    }

    private static boolean syncing() {
        return ClientConfig.jeiSync() && RecipeViewerSearch.available();
    }

    @Override
    public void removed() {
        super.removed();
        lastSearch = search == null ? "" : search.getValue();
    }

    // ------------------------------------------------------------------ settings

    private void toggleSettings() {
        settingsOpen = !settingsOpen;
        if (settingsOpen) {
            closeCraft();
            search.setFocused(false);
        }
    }

    private static Component onOff(String key, boolean on) {
        return Component.translatable(key, Component.translatable(on ? "gui.grimstorage.on" : "gui.grimstorage.off"));
    }

    private void buildSettings() {
        settingButtons.clear();
        boolean jei = RecipeViewerSearch.available();
        GrimButton sync = new GrimButton(0, 0, 150, 16, onOff("gui.grimstorage.settings.jei", jei && ClientConfig.jeiSync()), b -> {
            ClientConfig.set(ClientConfig.JEI_SYNC, !ClientConfig.jeiSync());
            if (syncing()) {
                RecipeViewerSearch.set(search.getValue());
                lastJei = search.getValue();
            }
            b.setMessage(onOff("gui.grimstorage.settings.jei", ClientConfig.jeiSync()));
        });
        sync.active = jei;
        sync.setTooltip(Tooltip.create(Component.translatable(jei ? "gui.grimstorage.settings.jei.tooltip" : "gui.grimstorage.settings.jei.missing")));
        GrimButton focus = new GrimButton(0, 0, 150, 16, onOff("gui.grimstorage.settings.focus", ClientConfig.focusSearch()), b -> {
            ClientConfig.set(ClientConfig.FOCUS_SEARCH, !ClientConfig.focusSearch());
            b.setMessage(onOff("gui.grimstorage.settings.focus", ClientConfig.focusSearch()));
        });
        focus.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.settings.focus.tooltip")));
        GrimButton keep = new GrimButton(0, 0, 150, 16, onOff("gui.grimstorage.settings.keep", ClientConfig.keepSearch()), b -> {
            ClientConfig.set(ClientConfig.KEEP_SEARCH, !ClientConfig.keepSearch());
            b.setMessage(onOff("gui.grimstorage.settings.keep", ClientConfig.keepSearch()));
        });
        keep.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.settings.keep.tooltip")));
        GrimButton alerts = new GrimButton(0, 0, 150, 16, onOff("gui.grimstorage.settings.alerts", ClientConfig.craftAlerts()), b -> {
            ClientConfig.set(ClientConfig.CRAFT_ALERTS, !ClientConfig.craftAlerts());
            b.setMessage(onOff("gui.grimstorage.settings.alerts", ClientConfig.craftAlerts()));
        });
        alerts.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.settings.alerts.tooltip")));
        ClientConfig.TerminalSize now = ClientConfig.terminalSize();
        GrimButton size = new GrimButton(0, 0, 150, 16, Component.translatable("gui.grimstorage.settings.size",
                Component.translatable("gui.grimstorage.size." + now.name().toLowerCase(Locale.ROOT))), b -> {
            ClientConfig.TerminalSize[] all = ClientConfig.TerminalSize.values();
            ClientConfig.setTerminalSize(all[(now.ordinal() + 1) % all.length]);
            scroll = 0;
            init(minecraft, width, height);
        });
        size.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.settings.size.tooltip", rows, rowsThatFit())));
        GrimButton done = new GrimButton(0, 0, 150, 16, Component.translatable("gui.done"), b -> settingsOpen = false, true);
        settingButtons.addAll(List.of(sync, focus, keep, alerts, size, done));
    }

    private int settingsX() {
        return leftPos + (imageWidth - 166) / 2;
    }

    private int settingsY() {
        return topPos + 10;
    }

    private static final int SETTINGS_H = 26 + 6 * 20;

    private void renderSettings(GuiGraphics g, Theme t, int mouseX, int mouseY, float pt) {
        int w = 166, x = settingsX(), y = settingsY();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0x80000000);
        Ui.panel(g, t, x, y, w, SETTINGS_H);
        g.fill(x + 1, y + 2, x + w - 1, y + SETTINGS_H - 1, Theme.withAlpha(t.panelTop(), 0xF0));
        g.pose().translate(0, 0, 10);
        g.drawString(font, Component.translatable("gui.grimstorage.settings"), x + 8, y + 8, t.text(), false);
        Ui.rule(g, t, x + 8, y + 18, w - 16);
        for (int i = 0; i < settingButtons.size(); i++) {
            GrimButton b = settingButtons.get(i);
            b.setPosition(x + 8, y + 24 + i * 20 + (i == settingButtons.size() - 1 ? 2 : 0));
            b.render(g, mouseX, mouseY, pt);
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ data

    /** Item order from the last refresh, kept while shift is held. */
    @Nullable
    private List<ItemKey> frozen;
    /** When each item last had stock: an item a craft just used up stays listed for a few seconds, so it does not flicker. */
    private final java.util.Map<ItemKey, Long> lastStocked = new java.util.HashMap<>();
    private static final long STOCK_GRACE_MS = 4000;

    private boolean recentlyStocked(ItemKey key, long now) {
        Long t = lastStocked.get(key);
        return t != null && now - t < STOCK_GRACE_MS;
    }
    private boolean holdOrder;
    private boolean held;
    private int storedTypes;
    /** The item under the mouse in the grid this frame, for the R key (recipes). */
    @Nullable
    private TerminalMenu.Entry hoverEntry;

    /** True when one of the stack's enchantments (on an enchanted book or an enchanted item) contains the search text. */
    private boolean enchantmentsMatch(ItemStack s, String needle) {
        ItemEnchantments ench = s.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (ench.isEmpty()) {
            ench = s.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        }
        for (Holder<Enchantment> h : ench.keySet()) {
            if (SearchMatch.matches(h.value().description().getString().toLowerCase(Locale.ROOT), needle)) {
                return true;
            }
        }
        return false;
    }

    private void refresh() {
        refresh(false);
    }

    private void refresh(boolean keepOrder) {
        holdOrder = keepOrder;
        seenRevision = menu.clientRevision;
        seenSearch = search.getValue();
        String q = seenSearch.toLowerCase(Locale.ROOT).trim();
        boolean byMod = q.startsWith("@");
        String needle = byMod ? q.substring(1) : q;
        Arrays.fill(categoryCounts, 0);
        List<TerminalMenu.Entry> out = new ArrayList<>();
        storedTypes = 0;
        long now = System.currentTimeMillis();
        for (TerminalMenu.Entry e : menu.clientEntries) {
            if (e.count() > 0) {
                lastStocked.put(e.key(), now);
            }
        }
        for (TerminalMenu.Entry e : menu.clientEntries) {
            boolean hiddenLearned = e.count() == 0 && e.learned() && !recentlyStocked(e.key(), now);
            if (e.count() > 0) {
                storedTypes++;
            }
            for (ItemCategories.Category c : ItemCategories.Category.values()) {
                if (c == ItemCategories.Category.CRAFTABLE ? e.craftable() : !hiddenLearned && ItemCategories.matches(c, e.key().stack())) {
                    categoryCounts[c.ordinal()]++;
                }
            }
            boolean craftTab = !all && category == ItemCategories.Category.CRAFTABLE;
            if (craftTab) {
                if (!e.craftable()) {
                    continue;
                }
            } else {
                if (!all && !ItemCategories.matches(category, e.key().stack())) {
                    continue;
                }
                // a furnace's whole recipe book would flood the list: learned recipes only show in the Craftable tab
                if (e.count() == 0 && e.learned() && !recentlyStocked(e.key(), now)) {
                    continue;
                }
            }
            if (!needle.isEmpty()) {
                ItemStack s = e.key().stack();
                String hay = byMod ? BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace() : s.getHoverName().getString().toLowerCase(Locale.ROOT);
                if (!SearchMatch.matches(hay, needle) && (byMod || !enchantmentsMatch(s, needle))) {
                    continue;
                }
            }
            out.add(e);
        }
        Comparator<TerminalMenu.Entry> byName = Comparator.comparing(e -> e.key().stack().getHoverName().getString());
        Comparator<TerminalMenu.Entry> cmp = switch (sort) {
            case 1 -> byName;
            case 2 -> Comparator.<TerminalMenu.Entry, String>comparing(e -> BuiltInRegistries.ITEM.getKey(e.key().item()).toString());
            case 3 -> Comparator.comparingLong(TerminalMenu.Entry::added).reversed().thenComparing(byName);
            default -> Comparator.comparingLong(TerminalMenu.Entry::count).reversed().thenComparing(byName);
        };
        // pinned items (Alt-click) always come first, in the order they were pinned
        java.util.List<String> pins = ClientConfig.pinned();
        cmp = Comparator.<TerminalMenu.Entry>comparingInt(e -> {
            int i = pins.indexOf(BuiltInRegistries.ITEM.getKey(e.key().item()).toString());
            return i < 0 ? Integer.MAX_VALUE : i;
        }).thenComparing(cmp);
        out.sort(cmp);
        // Holding shift keeps every item where it is, so the same spot can be shift-clicked again and again.
        if (holdOrder && frozen != null) {
            java.util.Map<ItemKey, TerminalMenu.Entry> byKey = new java.util.LinkedHashMap<>();
            for (TerminalMenu.Entry e : out) {
                byKey.put(e.key(), e);
            }
            List<TerminalMenu.Entry> kept = new ArrayList<>(Math.max(frozen.size(), out.size()));
            for (ItemKey k : frozen) {
                TerminalMenu.Entry e = byKey.remove(k);
                // gone for now: keep its spot, shown faded
                kept.add(e != null ? e : new TerminalMenu.Entry(k, 0, false, false));
            }
            kept.addAll(byKey.values());
            out = kept;
        }
        frozen = new ArrayList<>(out.size());
        for (TerminalMenu.Entry e : out) {
            frozen.add(e.key());
        }
        view = out;
        List<TerminalMenu.BulkEntry> bulk = new ArrayList<>();
        if (mode != null || all) {
            for (TerminalMenu.BulkEntry e : menu.clientBulk) {
                if (!all && e.key().kind() != mode) {
                    continue;
                }
                if (all && !e.key().kind().available()) {
                    continue;
                }
                if (!needle.isEmpty() && !BulkRender.name(e.key()).getString().toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                bulk.add(e);
            }
            Comparator<TerminalMenu.BulkEntry> bulkByName = Comparator.comparing(e -> BulkRender.name(e.key()).getString());
            Comparator<TerminalMenu.BulkEntry> bulkCmp = sort == 0
                    ? Comparator.comparingLong(TerminalMenu.BulkEntry::amount).reversed().thenComparing(bulkByName) : bulkByName;
            // In the Everything tab, resources come first, grouped by kind (fluids, chemicals, energy, Source, XP).
            bulk.sort(all ? Comparator.<TerminalMenu.BulkEntry>comparingInt(e -> e.key().kind().ordinal()).thenComparing(bulkCmp) : bulkCmp);
        }
        bulkView = bulk;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        if (sortButton != null) {
            sortButton.setTooltip(Tooltip.create(Component.translatable(SORTS[sort])));
        }
    }

    private int shownCount() {
        return all ? bulkView.size() + view.size() : mode == null ? view.size() : mode.typed() ? bulkView.size() : 0;
    }

    private int maxScroll() {
        if (jobs) {
            return Math.max(0, jobLines().size() - rows * 18 / LINE);
        }
        return Math.max(0, (shownCount() + COLS - 1) / COLS - rows);
    }

    @Nullable
    private TerminalMenu.BulkEntry bulkAt(double mx, double my) {
        int col = (int) Math.floor((mx - leftPos - GX) / 18.0);
        int row = (int) Math.floor((my - topPos - GY) / 18.0);
        if (col < 0 || col >= COLS || row < 0 || row >= rows) {
            return null;
        }
        int idx = (scroll + row) * COLS + col;
        return idx < bulkView.size() ? bulkView.get(idx) : null;
    }

    // ------------------------------------------------------------------ resource tabs (top edge)

    /** Index 0 is items; after that, every resource kind whose mod is installed. */
    private List<BulkKind> modes() {
        List<BulkKind> out = new ArrayList<>();
        out.add(null);
        for (BulkKind k : BulkKind.values()) {
            if (k.available()) {
                out.add(k);
            }
        }
        return out;
    }

    private int topX(int i) {
        return leftPos + 8 + i * (TOP_W + 2);
    }

    /** Display index of the crafting jobs tab: after every resource tab. */
    private int jobsTab() {
        return modes().size() + 1;
    }

    /**
     * Display index of the top tab under the mouse: 0 is Everything, then items, then each resource (modes() + 1),
     * and last the crafting jobs.
     */
    private int topAt(double mx, double my) {
        if (my < topPos - TOP_H + 3 || my >= topPos) {
            return -1;
        }
        List<BulkKind> modes = modes();
        for (int i = 0; i <= modes.size() + 1; i++) {
            if (mx >= topX(i) && mx < topX(i) + TOP_W) {
                return i;
            }
        }
        return -1;
    }

    private void drawTop(GuiGraphics g, Theme t, int i, @Nullable BulkKind kind, int mouseX, int mouseY) {
        boolean everything = i == 0;
        boolean jobTab = i == jobsTab();
        boolean selected = jobTab ? jobs : !jobs && (everything ? all : !all && kind == mode);
        int x = topX(i);
        int y = topPos - TOP_H + 3 - (selected ? 2 : 0);
        int bottom = topPos + 1;
        boolean hover = !selected && topAt(mouseX, mouseY) == i;
        g.fill(x, y + 1, x + TOP_W, bottom, t.border());
        g.fill(x + 1, y, x + TOP_W - 1, bottom, t.border());
        int fill = selected ? t.panelAlpha() | (t.panelTop() & 0xFFFFFF) : hover ? t.buttonHover() : Theme.withAlpha(t.panelBottom(), 0xD0);
        g.fill(x + 1, y + 1, x + TOP_W - 1, bottom, fill);
        if (selected) {
            g.fill(x + 2, y + 1, x + TOP_W - 2, y + 2, t.accent());
        }
        g.renderItem(jobTab ? new ItemStack(com.xtri6.grimstorage.Registration.ASSEMBLER.get())
                : everything ? new ItemStack(com.xtri6.grimstorage.Registration.DRIVE.get())
                : kind == null ? new ItemStack(net.minecraft.world.item.Items.CHEST) : BulkRender.icon(kind), x + 4, y + 3);
        if (jobTab && !menu.clientJobs.isEmpty()) {
            Ui.cornerText(g, font, String.valueOf(menu.clientJobs.size()), x + 21, y + 19, t.accent(), 2f / 3f);
        }
    }

    private Component topName(int display) {
        if (display == jobsTab()) {
            return Component.translatable("gui.grimstorage.kind.jobs");
        }
        return display == 0 ? Component.translatable("gui.grimstorage.kind.all") : modeName(modes().get(display - 1));
    }

    private Component modeName(@Nullable BulkKind kind) {
        return Component.translatable(kind == null ? "gui.grimstorage.kind.items" : "gui.grimstorage.kind." + kind.id());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        tickCount++;
        updatePreview();
        boolean shift = hasShiftDown();
        String typed = search.getValue();
        if (syncing() && typed.equals(seenSearch)) {
            // typed into JEI's search: follow it
            String j = RecipeViewerSearch.get();
            if (j != null && !j.equals(lastJei)) {
                lastJei = j;
                if (!j.equals(typed)) {
                    search.setValue(j);
                    typed = j;
                }
            }
        }
        if (!typed.equals(seenSearch)) {
            if (syncing() && !typed.equals(lastJei)) {
                RecipeViewerSearch.set(typed);
                lastJei = typed;
            }
            refresh();
        } else if (menu.clientRevision != seenRevision) {
            // counts changed (a craft, a job step): keep the order, so the item under the cursor does not jump
            refresh(true);
        } else if (held && !shift) {
            // let go of shift: sort again
            refresh();
        }
        held = shift;
        search.setTextColor(t().text() & 0xFFFFFF);
    }

    @Nullable
    private TerminalMenu.Entry entryAt(double mx, double my) {
        int col = (int) Math.floor((mx - leftPos - GX) / 18.0);
        int row = (int) Math.floor((my - topPos - GY) / 18.0);
        if (col < 0 || col >= COLS || row < 0 || row >= rows) {
            return null;
        }
        int idx = (scroll + row) * COLS + col - (all ? bulkView.size() : 0);
        return idx >= 0 && idx < view.size() ? view.get(idx) : null;
    }

    private boolean inGrid(double mx, double my) {
        return mx >= leftPos + GX - 1 && mx < leftPos + GX + COLS * 18 - 1 && my >= topPos + GY - 1 && my < topPos + GY + rows * 18 - 1;
    }

    private boolean onBar(double mx, double my) {
        return mx >= leftPos + BAR_X && mx < leftPos + BAR_X + 10 && my >= topPos + GY - 1 && my < topPos + GY + rows * 18 - 1;
    }

    // ------------------------------------------------------------------ tabs

    private int tabX() {
        return leftPos - TAB_W + 3;
    }

    private int tabY(int i) {
        return topPos + 6 + i * (TAB_H + 1);
    }

    @Nullable
    private ItemCategories.Category tabAt(double mx, double my) {
        if (jobs || mode != null || all || mx < tabX() || mx >= leftPos) {
            return null;
        }
        for (ItemCategories.Category c : ItemCategories.Category.values()) {
            int y = tabY(c.ordinal());
            if (my >= y && my < y + TAB_H) {
                return c;
            }
        }
        return null;
    }

    private void drawTab(GuiGraphics g, Theme t, ItemCategories.Category c, int mouseX, int mouseY) {
        boolean selected = c == category;
        int x = tabX() - (selected ? 2 : 0);
        int y = tabY(c.ordinal());
        int right = leftPos + 1;
        boolean hover = !selected && tabAt(mouseX, mouseY) == c;
        g.fill(x + 1, y, right, y + TAB_H, t.border());
        g.fill(x, y + 1, right, y + TAB_H - 1, t.border());
        int fill = selected ? t.panelAlpha() | (t.panelTop() & 0xFFFFFF) : hover ? t.buttonHover() : Theme.withAlpha(t.panelBottom(), 0xD0);
        g.fill(x + 1, y + 1, right, y + TAB_H - 1, fill);
        if (selected) {
            g.fill(x + 1, y + 2, x + 2, y + TAB_H - 2, t.accent());
        }
        g.renderItem(c.icon(), x + 4, y + 1);
        boolean empty = c != ItemCategories.Category.ALL && categoryCounts[c.ordinal()] == 0;
        if (empty && !selected) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            g.fill(x + 4, y + 1, x + 20, y + 17, Theme.withAlpha(t.panelBottom(), 0xA0));
            g.pose().popPose();
        }
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return tabAt(mx, my) == null && topAt(mx, my) < 0 && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Theme t = t();
        List<BulkKind> modes = modes();
        int selectedTop = jobs ? jobsTab() : all ? 0 : modes.indexOf(mode) + 1;
        for (int i = 0; i <= modes.size() + 1; i++) {
            if (i != selectedTop) {
                drawTop(g, t, i, i == 0 || i > modes.size() ? null : modes.get(i - 1), mouseX, mouseY);
            }
        }
        if (mode == null && !all && !jobs) {
            for (ItemCategories.Category c : ItemCategories.Category.values()) {
                if (c != category) {
                    drawTab(g, t, c, mouseX, mouseY);
                }
            }
        }
        super.renderBg(g, partialTick, mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(0, 0, 50);
        if (mode == null && !all && !jobs) {
            drawTab(g, t, category, mouseX, mouseY);
        }
        drawTop(g, t, selectedTop, all || jobs ? null : mode, mouseX, mouseY);
        g.pose().popPose();
    }

    @Override
    protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
        Theme t = t();
        if (jobs) {
            g.fill(leftPos + GX - 1, topPos + GY - 1, leftPos + GX + COLS * 18 - 1, topPos + GY + rows * 18 - 1, t.slot());
        } else {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < COLS; c++) {
                    Ui.slot(g, t, leftPos + GX + c * 18, topPos + GY + r * 18);
                }
            }
        }
        Ui.field(g, t, leftPos + 97, topPos + 4, 82, 12, search.isFocused());
        int max = maxScroll();
        Ui.scrollbar(g, t, leftPos + BAR_X, topPos + GY - 1, 10, rows * 18, max == 0 ? 0 : scroll / (float) max, max > 0,
                draggingBar || onBar(mouseX, mouseY));
        // right column divider and crafting arrow
        g.fill(leftPos + RIGHT_X - 4, topPos + 18, leftPos + RIGHT_X - 3, topPos + imageHeight - 8, Theme.withAlpha(t.border() & 0xFFFFFF, 0x90));
        Ui.arrow(g, t, leftPos + TerminalMenu.RESULT_X + 3, topPos + 86, 0, true);
        // storage gauge
        long used = mode == null ? menu.clientUsed : menu.clientBulkStats[mode.ordinal() * 2];
        long cap = mode == null ? menu.clientCapacity : menu.clientBulkStats[mode.ordinal() * 2 + 1];
        float fill = cap <= 0 ? 0 : (float) used / cap;
        int color = fill > 0.95f ? t.danger() : fill > 0.8f ? t.warn() : t.accent();
        Ui.bar(g, t, leftPos + RIGHT_X, topPos + 152, W - RIGHT_X - 8, 6, fill, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        Theme t = t();
        String name = Ui.fit(font, title.getString(), 86);
        g.drawString(font, name, 8, 6, t.text(), false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, t.textDim(), false);
        g.drawString(font, Component.translatable("gui.grimstorage.crafting"), RIGHT_X, 18, t.textDim(), false);
        g.drawString(font, Component.translatable("gui.grimstorage.storage"), RIGHT_X, 141, t.textDim(), false);
        if (mode == null) {
            String used = Fmt.compact(menu.clientUsed) + " / " + Fmt.compact(menu.clientCapacity);
            g.drawString(font, used, RIGHT_X, 162, t.text(), false);
            g.drawString(font, Component.translatable("gui.grimstorage.types", storedTypes), RIGHT_X, 172, t.textFaint(), false);
            if (!jobs) {
                g.drawString(font, Ui.fit(font, (all ? Component.translatable("gui.grimstorage.kind.all")
                        : Component.translatable(category.langKey())).getString(), W - RIGHT_X - 8), RIGHT_X, 190, t.accent(), false);
            }
        } else {
            long used = menu.clientBulkStats[mode.ordinal() * 2];
            long cap = menu.clientBulkStats[mode.ordinal() * 2 + 1];
            g.drawString(font, Fmt.amount(mode, used), RIGHT_X, 162, t.text(), false);
            g.drawString(font, Component.translatable("gui.grimstorage.of", Fmt.amount(mode, cap)), RIGHT_X, 172, t.textFaint(), false);
            if (!jobs) {
                g.drawString(font, modeName(mode), RIGHT_X, 190, t.accent(), false);
            }
        }
        if (jobs) {
            g.drawString(font, Ui.fit(font, Component.translatable("gui.grimstorage.kind.jobs").getString(), W - RIGHT_X - 8),
                    RIGHT_X, 190, t.accent(), false);
            g.drawString(font, Component.translatable("gui.grimstorage.jobs.running", menu.clientJobs.size()), RIGHT_X, 200, t.textFaint(), false);
        } else if (mode == null || mode.typed()) {
            g.drawString(font, Component.translatable("gui.grimstorage.showing", shownCount()), RIGHT_X, 200, t.textFaint(), false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Theme t = t();
        TerminalMenu.Entry hovered = null;
        TerminalMenu.BulkEntry bulkHovered = null;
        List<Component> jobTip = null;
        if (jobs) {
            jobTip = drawJobs(g, t, mouseX, mouseY);
        } else if (all || (mode != null && mode.typed())) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < COLS; c++) {
                    int idx = (scroll + r) * COLS + c;
                    if (idx >= bulkView.size()) {
                        break;
                    }
                    TerminalMenu.BulkEntry e = bulkView.get(idx);
                    int x = leftPos + GX + c * 18;
                    int y = topPos + GY + r * 18;
                    BulkRender.draw(g, e.key(), x, y);
                    Ui.cornerText(g, font, Fmt.shortAmount(e.key().kind(), e.amount()), x + 17, y + 16, 0xFFFFFFFF, 2f / 3f);
                    if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) {
                        bulkHovered = e;
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 310);
                        g.fill(x, y, x + 16, y + 16, t.hover());
                        g.pose().popPose();
                    }
                }
            }
            if (!all && bulkView.isEmpty() && menu.clientOnline) {
                Component none = Component.translatable("gui.grimstorage.bulk.empty", modeName(mode));
                g.drawString(font, none, leftPos + GX + (COLS * 18 - font.width(none)) / 2, topPos + GY + 46, t.textFaint(), false);
            }
        } else if (mode != null) {
            drawPool(g, t, mode);
        }
        int offset = all ? bulkView.size() : 0;
        java.util.List<String> pins = ClientConfig.pinned();
        for (int r = 0; r < rows && mode == null && !jobs; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scroll + r) * COLS + c - offset;
                if (idx < 0) {
                    continue;
                }
                if (idx >= view.size()) {
                    break;
                }
                TerminalMenu.Entry e = view.get(idx);
                int x = leftPos + GX + c * 18;
                int y = topPos + GY + r * 18;
                g.renderItem(e.key().stack(), x, y);
                if (!pins.isEmpty() && pins.contains(BuiltInRegistries.ITEM.getKey(e.key().item()).toString())) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 300);
                    g.fill(x - 1, y - 1, x + 3, y + 3, t.accent());
                    g.pose().popPose();
                }
                boolean ghost = e.count() == 0 && !e.craftable();
                if (ghost) {
                    // ran out while shift is held: its spot stays, faded, until shift is let go
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 300);
                    g.fill(x, y, x + 16, y + 16, Theme.withAlpha(t.panelBottom() & 0xFFFFFF, 0xB0));
                    g.pose().popPose();
                } else if (e.count() > 0) {
                    Ui.cornerText(g, font, Fmt.compact(e.count()), x + 17, y + 16, 0xFFFFFFFF, 2f / 3f);
                } else {
                    Ui.cornerText(g, font, Component.translatable("gui.grimstorage.craft.short").getString(), x + 17, y + 16, t.accent(), 0.5f);
                }
                if (crafting == null && mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) {
                    hovered = e;
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 310);
                    g.fill(x, y, x + 16, y + 16, t.hover());
                    g.pose().popPose();
                }
            }
        }
        if (!menu.clientOnline) {
            Component off = Component.translatable("gui.grimstorage.terminal.offline");
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            int tx = leftPos + GX + (COLS * 18 - font.width(off)) / 2;
            g.fill(tx - 6, topPos + GY + 40, tx + font.width(off) + 6, topPos + GY + 58, Theme.withAlpha(0x000000, 0xB0));
            g.drawString(font, off, tx, topPos + GY + 45, t.danger(), false);
            g.pose().popPose();
        }
        if (settingsOpen) {
            renderSettings(g, t, mouseX, mouseY, partialTick);
            return;
        }
        hoverEntry = hovered;
        ItemCategories.Category tab = crafting == null ? tabAt(mouseX, mouseY) : null;
        int top = crafting == null ? topAt(mouseX, mouseY) : -1;
        if (crafting != null) {
            renderCraftPopup(g, t, mouseX, mouseY, partialTick);
        } else if (top >= 0) {
            g.renderTooltip(font, topName(top), mouseX, mouseY);
        } else if (bulkHovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(BulkRender.name(bulkHovered.key()));
            BulkKind k = bulkHovered.key().kind();
            lines.add(Component.literal(Fmt.amount(k, bulkHovered.amount())).withStyle(ChatFormatting.GRAY));
            String hint = switch (k) {
                case FLUID -> "gui.grimstorage.bulk.hint_fluid";
                case CHEMICAL -> "gui.grimstorage.bulk.hint_chemical";
                case EXPERIENCE -> "gui.grimstorage.bulk.hint_xp";
                default -> "gui.grimstorage.bulk.hint_pool";
            };
            lines.add(Component.translatable(hint).withStyle(ChatFormatting.DARK_AQUA));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (jobTip != null) {
            g.renderComponentTooltip(font, jobTip, mouseX, mouseY);
        } else if (tab != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(tab.langKey()));
            lines.add(Component.translatable("gui.grimstorage.tab.count", categoryCounts[tab.ordinal()]).withStyle(ChatFormatting.GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else if (hovered != null && menu.getCarried().isEmpty()) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, hovered.key().stack()));
            lines.add(Component.translatable("gui.grimstorage.terminal.stored", Fmt.big(hovered.count())).withStyle(ChatFormatting.GRAY));
            if (hovered.craftable()) {
                lines.add(Component.translatable(hovered.count() > 0 ? "gui.grimstorage.terminal.craft_more" : "gui.grimstorage.terminal.craft_click")
                        .withStyle(ChatFormatting.AQUA));
            }
            boolean pinned = ClientConfig.pinned().contains(BuiltInRegistries.ITEM.getKey(hovered.key().item()).toString());
            lines.add(Component.translatable(pinned ? "gui.grimstorage.terminal.pinned" : "gui.grimstorage.terminal.pin_hint")
                    .withStyle(pinned ? ChatFormatting.DARK_AQUA : ChatFormatting.DARK_GRAY));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** Energy and Source are one big pool each, so they get a gauge instead of a grid. */
    private void drawPool(GuiGraphics g, Theme t, BulkKind kind) {
        int x = leftPos + GX - 1, y = topPos + GY - 1, w = COLS * 18, h = rows * 18;
        boolean xp = kind == BulkKind.EXPERIENCE;
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.fill(x, y, x + w, y + h, 0xFF000000 | (t.panelBottom() & 0xFFFFFF));
        g.fill(x, y, x + w, y + h, t.slot());
        long used = menu.clientBulkStats[kind.ordinal() * 2];
        long cap = menu.clientBulkStats[kind.ordinal() * 2 + 1];
        float f = cap <= 0 ? 0 : (float) used / cap;
        // XP needs room for its click hints, so it skips the name (the tab says it) and sits higher.
        int iconY = xp ? 8 : 14, barY = xp ? 46 : 66, amountY = xp ? 62 : 82, pctY = xp ? 73 : 93;
        g.pose().pushPose();
        g.pose().translate(x + w / 2f - 16, y + iconY, 0);
        g.pose().scale(2f, 2f, 1f);
        g.renderItem(BulkRender.icon(kind), 0, 0);
        g.pose().popPose();
        if (!xp) {
            Component name = modeName(kind);
            g.drawString(font, name, x + (w - font.width(name)) / 2, y + 52, t.text(), false);
        }
        int color = kind == BulkKind.ENERGY ? 0xFFFF5A4A : xp ? 0xFF7FE03A : t.purple();
        Ui.bar(g, t, x + 12, y + barY, w - 24, 10, f, color);
        String amount = Fmt.amount(kind, used) + " / " + Fmt.amount(kind, cap);
        g.drawString(font, amount, x + (w - font.width(amount)) / 2, y + amountY, t.textDim(), false);
        String pct = cap <= 0 ? Component.translatable("gui.grimstorage.bulk.no_cells").getString() : Math.round(f * 100) + "%";
        if (xp && cap > 0) {
            int lv = 0;
            while (lv < 21863 && com.xtri6.grimstorage.storage.Experience.pointsForLevel(lv + 1) <= used) {
                lv++;
            }
            pct += "  \u00b7  " + Component.translatable("gui.grimstorage.xp.levels", lv).getString();
        }
        g.drawString(font, pct, x + (w - font.width(pct)) / 2, y + pctY, cap <= 0 ? t.warn() : xp ? color : t.accent(), false);
        if (xp && cap > 0) {
            for (int i = 0; i < 2; i++) {
                Component hint = Component.translatable("gui.grimstorage.xp.hint" + (i + 1));
                g.pose().pushPose();
                g.pose().translate(x + w / 2f - font.width(hint) * 0.375f, y + 88 + i * 8, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, hint, 0, 0, t.textFaint(), false);
                g.pose().popPose();
            }
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ crafting jobs tab

    /** Height of one line in the jobs list; a job's header takes two lines, each of its steps one. */
    private static final int LINE = 10;

    /** One line of the jobs list: a job's header (part 0 and 1) or one of its steps. */
    private record JobLine(Payloads.JobView job, @Nullable Payloads.StepView step, int part) {}

    private List<JobLine> jobLines() {
        List<JobLine> out = new ArrayList<>();
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        for (Payloads.JobView job : menu.clientJobs) {
            if (!q.isEmpty() && !q.startsWith("@") && !job.info().icon().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            out.add(new JobLine(job, null, 0));
            out.add(new JobLine(job, null, 1));
            for (Payloads.StepView st : job.steps()) {
                out.add(new JobLine(job, st, 0));
            }
        }
        return out;
    }

    private void small(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private int jobsLeft() {
        return leftPos + GX - 1;
    }

    private int jobsRight() {
        return leftPos + GX + COLS * 18 - 1;
    }

    /** Controls on a job's header, right to left: cancel, pause / resume, move down, move up. */
    private static final StorageNetwork.JobAction[] CONTROLS = {StorageNetwork.JobAction.CANCEL, StorageNetwork.JobAction.PAUSE,
            StorageNetwork.JobAction.DOWN, StorageNetwork.JobAction.UP};
    private static final int CONTROL_W = 10;

    /** Which control of the job whose header starts at {@code y} the mouse is on, or -1. */
    private int controlAt(double mx, double my, int y) {
        if (my < y + 1 || my >= y + 11) {
            return -1;
        }
        for (int c = 0; c < CONTROLS.length; c++) {
            int right = jobsRight() - 1 - c * CONTROL_W;
            if (mx >= right - CONTROL_W + 1 && mx < right) {
                return c;
            }
        }
        return -1;
    }

    private record JobControl(Payloads.JobView job, StorageNetwork.JobAction action) {}

    /** The job control under the mouse, or null. */
    @Nullable
    private JobControl jobControlAt(double mx, double my) {
        List<JobLine> lines = jobLines();
        int top = topPos + GY - 1;
        for (int i = Math.max(0, scroll - 1); i < lines.size() && i - scroll < rows * 18 / LINE; i++) {
            JobLine l = lines.get(i);
            if (l.step() == null && l.part() == 0) {
                int c = controlAt(mx, my, top + (i - scroll) * LINE);
                if (c >= 0) {
                    return new JobControl(l.job(), CONTROLS[c]);
                }
            }
        }
        return null;
    }

    private void drawControl(GuiGraphics g, Theme t, StorageNetwork.JobAction a, boolean paused, int x, int y, boolean hot) {
        int c = hot ? (a == StorageNetwork.JobAction.CANCEL ? t.danger() : t.accent()) : t.textFaint();
        int cx = x + CONTROL_W / 2;
        switch (a) {
            case CANCEL -> g.drawString(font, "\u00d7", cx - 3, y + 2, c, false);
            case PAUSE -> {
                if (paused) {
                    // play: a small triangle pointing right
                    for (int i = 0; i < 4; i++) {
                        g.fill(cx - 2 + i, y + 3 + i, cx - 1 + i, y + 10 - i, c);
                    }
                } else {
                    g.fill(cx - 3, y + 3, cx - 1, y + 9, c);
                    g.fill(cx + 1, y + 3, cx + 3, y + 9, c);
                }
            }
            case UP -> {
                for (int i = 0; i < 4; i++) {
                    g.fill(cx - i, y + 4 + i, cx + i + 1, y + 5 + i, c);
                }
            }
            case DOWN -> {
                for (int i = 0; i < 4; i++) {
                    g.fill(cx - 3 + i, y + 4 + i, cx + 4 - i, y + 5 + i, c);
                }
            }
            default -> {}
        }
    }

    /** Draws the running jobs; returns a tooltip for what the mouse is over. */
    @Nullable
    private List<Component> drawJobs(GuiGraphics g, Theme t, int mouseX, int mouseY) {
        int x0 = jobsLeft(), x1 = jobsRight(), y0 = topPos + GY - 1, y1 = y0 + rows * 18;
        List<JobLine> lines = jobLines();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        if (lines.isEmpty()) {
            Component none = Component.translatable(menu.clientJobs.isEmpty() ? "gui.grimstorage.jobs.empty" : "gui.grimstorage.jobs.no_match");
            g.drawString(font, none, x0 + (x1 - x0 - font.width(none)) / 2, y0 + 40, t.textDim(), false);
            if (menu.clientJobs.isEmpty()) {
                for (int i = 0; i < 2; i++) {
                    String hint = Component.translatable("gui.grimstorage.jobs.empty_hint" + (i + 1)).getString();
                    small(g, hint, x0 + (x1 - x0 - (int) (font.width(hint) * 0.75f)) / 2, y0 + 54 + i * 8, t.textFaint());
                }
            }
            return null;
        }
        List<Component> tip = null;
        g.enableScissor(x0, y0, x1, y1);
        int visible = rows * 18 / LINE;
        for (int i = Math.max(0, scroll - 1); i < lines.size() && i - scroll <= visible; i++) {
            JobLine l = lines.get(i);
            int y = y0 + (i - scroll) * LINE;
            Payloads.JobInfo info = l.job().info();
            if (l.step() == null) {
                if (l.part() != 0) {
                    continue;
                }
                // header: icon, amount and name, status, progress bar, cancel cross
                g.fill(x0, y, x1, y + 2 * LINE - 1, Theme.withAlpha(t.panelTop() & 0xFFFFFF, 0xC0));
                g.renderItem(info.icon(), x0 + 2, y + 2);
                if (info.paused()) {
                    g.fill(x0, y, x0 + 2, y + 2 * LINE - 1, t.warn());
                }
                String name = Fmt.compact(info.amount()) + "x " + info.icon().getHoverName().getString();
                g.drawString(font, Ui.fit(font, name, x1 - x0 - 24 - CONTROLS.length * CONTROL_W), x0 + 21, y + 2,
                        info.paused() ? t.textDim() : t.text(), false);
                small(g, Ui.fit(font, info.status().getString(), (int) ((x1 - x0 - 50) / 0.75f)), x0 + 21, y + 10, t.textDim());
                String pct = Math.round(info.progress() * 100) + "%";
                small(g, pct, x1 - 3 - (int) (font.width(pct) * 0.75f), y + 10, t.accent());
                Ui.bar(g, t, x0 + 21, y + 16, x1 - x0 - 24, 3, info.progress(), t.accent());
                int hotControl = mouseY >= y0 && mouseY < y1 ? controlAt(mouseX, mouseY, y) : -1;
                for (int c = 0; c < CONTROLS.length; c++) {
                    drawControl(g, t, CONTROLS[c], info.paused(), x1 - 1 - (c + 1) * CONTROL_W, y, c == hotControl);
                }
                if (hotControl >= 0) {
                    String key = switch (CONTROLS[hotControl]) {
                        case CANCEL -> "gui.grimstorage.jobs.cancel";
                        case PAUSE -> info.paused() ? "gui.grimstorage.jobs.resume" : "gui.grimstorage.jobs.pause";
                        case UP -> "gui.grimstorage.jobs.up";
                        case DOWN -> "gui.grimstorage.jobs.down";
                        default -> "gui.grimstorage.jobs.top";
                    };
                    tip = new ArrayList<>(List.of(Component.translatable(key)));
                    if (CONTROLS[hotControl] == StorageNetwork.JobAction.UP) {
                        tip.add(Component.translatable("gui.grimstorage.jobs.up_hint").withStyle(ChatFormatting.GRAY));
                    }
                } else if (mouseX >= x0 && mouseX < x1 && mouseY >= Math.max(y, y0) && mouseY < Math.min(y + 2 * LINE, y1)) {
                    tip = List.of(info.icon().getHoverName(), Component.literal(info.status().getString()).withStyle(ChatFormatting.GRAY),
                            Component.translatable("gui.grimstorage.jobs.ordered_by", info.source()).withStyle(ChatFormatting.DARK_GRAY));
                }
            } else {
                Payloads.StepView st = l.step();
                boolean done = st.finished() >= st.total();
                boolean busy = st.inMachines() > 0;
                g.pose().pushPose();
                g.pose().translate(x0 + 6, y + 1, 0);
                g.pose().scale(0.5f, 0.5f, 1f);
                g.renderItem(st.output(), 0, 0);
                g.pose().popPose();
                String text = Fmt.compact(st.finished()) + "/" + Fmt.compact(st.total()) + " \u00b7 " + st.machine().getString();
                if (busy) {
                    text += " \u00b7 " + Component.translatable("gui.grimstorage.jobs.in_machines", st.inMachines()).getString();
                }
                int color = done ? t.textFaint() : busy ? t.purple() : t.textDim();
                small(g, Ui.fit(font, done ? "\u2714 " + text : text, (int) ((x1 - x0 - 20) / 0.75f)), x0 + 17, y + 2, color);
                if (mouseX >= x0 && mouseX < x1 && mouseY >= Math.max(y, y0) && mouseY < Math.min(y + LINE, y1)) {
                    List<Component> lines2 = new ArrayList<>();
                    lines2.add(st.output().getHoverName());
                    lines2.add(Component.translatable(st.processing() ? "gui.grimstorage.jobs.step_machine" : "gui.grimstorage.jobs.step_craft",
                            st.machine()).withStyle(ChatFormatting.GRAY));
                    lines2.add(Component.translatable("gui.grimstorage.jobs.done", st.finished(), st.total()).withStyle(ChatFormatting.GRAY));
                    if (busy) {
                        lines2.add(Component.translatable("gui.grimstorage.jobs.in_machines", st.inMachines()).withStyle(ChatFormatting.LIGHT_PURPLE));
                    }
                    tip = lines2;
                }
            }
        }
        g.disableScissor();
        return tip;
    }

    private static final int CW = 228;
    private static final int CH = 196;
    private static final int PREVIEW_ROWS = 9;
    private int previewScroll;
    private int sentAmount = -1;
    private int amountChangedAt;
    private int tickCount;

    private int craftX() {
        return leftPos + (imageWidth - CW) / 2;
    }

    private int craftY() {
        return topPos + 14;
    }

    private int typedAmount() {
        try {
            return Math.max(0, Integer.parseInt(craftAmount.getValue()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** Asks the server what the craft would do, a moment after the amount stops changing. */
    private void updatePreview() {
        if (crafting == null) {
            return;
        }
        int n = typedAmount();
        if (n != sentAmount && n > 0 && tickCount - amountChangedAt >= 4) {
            sentAmount = n;
            PacketDistributor.sendToServer(new Payloads.PreviewRequest(menu.containerId, crafting.key().stack(), n));
        }
    }

    /** One preview line: a small icon, the amount and name, and what makes it. */
    private record PreviewRow(@Nullable Payloads.PreviewLine line, @Nullable Component header, int color) {}

    private List<PreviewRow> previewRows(Theme t) {
        List<PreviewRow> rows = new ArrayList<>();
        Payloads.Preview p = menu.clientPreview;
        if (p == null || p.amount() != typedAmount()) {
            return rows;
        }
        if (!p.missing().isEmpty()) {
            rows.add(new PreviewRow(null, Component.translatable("gui.grimstorage.preview.missing"), t.danger()));
            p.missing().forEach(l -> rows.add(new PreviewRow(l, null, l.amount() < 0 ? t.textDim() : t.danger())));
        }
        if (!p.steps().isEmpty()) {
            rows.add(new PreviewRow(null, Component.translatable("gui.grimstorage.preview.steps"), t.accent()));
            p.steps().forEach(l -> rows.add(new PreviewRow(l, null, t.text())));
        }
        if (!p.used().isEmpty()) {
            rows.add(new PreviewRow(null, Component.translatable("gui.grimstorage.preview.used"), t.textDim()));
            p.used().forEach(l -> rows.add(new PreviewRow(l, null, t.textDim())));
        }
        return rows;
    }

    private void renderCraftPopup(GuiGraphics g, Theme t, int mouseX, int mouseY, float pt) {
        int w = CW, h = CH;
        int x = craftX(), y = craftY();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0x80000000);
        Ui.panel(g, t, x, y, w, h);
        g.fill(x + 1, y + 2, x + w - 1, y + h - 1, Theme.withAlpha(t.panelTop(), 0xF0));
        g.pose().translate(0, 0, 10);
        Ui.slot(g, t, x + 9, y + 9);
        g.renderItem(crafting.key().stack(), x + 9, y + 9);
        g.drawString(font, Component.translatable("gui.grimstorage.craft.title"), x + 32, y + 9, t.text(), false);
        String name = crafting.key().stack().getHoverName().getString();
        g.drawString(font, Ui.fit(font, name, w - 100), x + 32, y + 19, t.accent(), false);
        // amount box, top right
        Ui.field(g, t, x + w - 64, y + 9, 56, 14, true);
        craftAmount.setPosition(x + w - 60, y + 12);
        craftAmount.setWidth(50);
        craftAmount.setTextColor(t.text() & 0xFFFFFF);
        craftAmount.render(g, mouseX, mouseY, pt);
        Ui.rule(g, t, x + 8, y + 30, w - 16);
        // the plan
        int listY = y + 36, listH = PREVIEW_ROWS * 12;
        g.fill(x + 8, listY - 1, x + w - 8, listY + listH + 1, t.slot());
        List<PreviewRow> rows = previewRows(t);
        Payloads.Preview p = menu.clientPreview;
        boolean fresh = p != null && p.amount() == typedAmount();
        int max = Math.max(0, rows.size() - PREVIEW_ROWS);
        previewScroll = Math.max(0, Math.min(previewScroll, max));
        if (!fresh) {
            small(g, Component.translatable("gui.grimstorage.preview.working").getString(), x + 12, listY + 4, t.textFaint());
        }
        for (int i = 0; i < PREVIEW_ROWS && i + previewScroll < rows.size(); i++) {
            PreviewRow r = rows.get(i + previewScroll);
            int ry = listY + i * 12;
            if (r.header() != null) {
                small(g, r.header().getString(), x + 11, ry + 3, r.color());
                continue;
            }
            Payloads.PreviewLine l = r.line();
            if (l.amount() < 0) {
                // why the item above is missing, wrapped onto a second row only if it has to be cut
                small(g, Ui.fit(font, "\u21b3 " + l.by().getString(), (int) ((w - 30) / 0.75f)), x + 18, ry + 3, r.color());
                continue;
            }
            g.pose().pushPose();
            g.pose().translate(x + 13, ry + 2, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.renderItem(l.item(), 0, 0);
            g.pose().popPose();
            String text = Fmt.compact(l.amount()) + "x " + l.item().getHoverName().getString();
            if (l.item().isEmpty()) {
                // a fluid or chemical: the name is in "by"
                text = Fmt.compact(l.amount()) + " " + l.by().getString();
            } else if (!l.by().getString().isEmpty()) {
                text += "  \u00b7  " + l.by().getString();
            }
            small(g, Ui.fit(font, text, (int) ((w - 36) / 0.75f)), x + 24, ry + 3, r.color());
        }
        if (max > 0) {
            int barH = Math.max(8, listH * PREVIEW_ROWS / rows.size());
            int barY = listY + (listH - barH) * previewScroll / max;
            g.fill(x + w - 11, barY, x + w - 9, barY + barH, t.accentDim());
        }
        // summary and buttons
        int fy = listY + listH + 6;
        if (fresh) {
            Component summary = !p.ok()
                    ? Component.translatable(p.missing().isEmpty() ? "gui.grimstorage.preview.cant" : "gui.grimstorage.preview.short",
                            p.missing().stream().filter(l -> l.amount() >= 0).count())
                    : Component.translatable("gui.grimstorage.preview.time", Fmt.duration(p.seconds()));
            small(g, summary.getString(), x + 10, fy, p.ok() ? t.ok() : t.danger());
        }
        craftGo.active = fresh && p.ok();
        craftGo.setPosition(x + 8, y + h - 24);
        craftCancel.setPosition(x + w - 68, y + h - 24);
        craftGo.render(g, mouseX, mouseY, pt);
        craftCancel.render(g, mouseX, mouseY, pt);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ input

    private void send(@Nullable TerminalMenu.Entry e, int action) {
        PacketDistributor.sendToServer(new Payloads.TerminalClick(menu.containerId, e == null ? ItemStack.EMPTY : e.key().stack(), action));
    }

    private void openCraft(TerminalMenu.Entry e) {
        crafting = e;
        craftAmount.setValue("1");
        menu.clientPreview = null;
        sentAmount = -1;
        previewScroll = 0;
        amountChangedAt = tickCount - 10;
        craftAmount.setResponder(v -> amountChangedAt = tickCount);
        craftAmount.setFocused(true);
        craftAmount.moveCursorToEnd(false);
        craftAmount.setHighlightPos(0);
        search.setFocused(false);
        setFocused(craftAmount);
    }

    private void closeCraft() {
        crafting = null;
        craftAmount.setFocused(false);
        setFocused(null);
    }

    private void confirmCraft() {
        if (crafting == null) {
            return;
        }
        int n;
        try {
            n = Integer.parseInt(craftAmount.getValue());
        } catch (NumberFormatException ex) {
            n = 0;
        }
        if (n > 0) {
            PacketDistributor.sendToServer(new Payloads.TerminalCraft(menu.containerId, crafting.key().stack(), n));
        }
        closeCraft();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (settingsOpen) {
            for (GrimButton b : new ArrayList<>(settingButtons)) {
                if (b.mouseClicked(mx, my, button)) {
                    return true;
                }
            }
            int x = settingsX(), y = settingsY();
            if (mx < x || mx >= x + 166 || my < y || my >= y + SETTINGS_H) {
                settingsOpen = false;
            }
            return true;
        }
        if (crafting != null) {
            if (craftAmount.mouseClicked(mx, my, button)) {
                craftAmount.setFocused(true);
                return true;
            }
            craftGo.mouseClicked(mx, my, button);
            craftCancel.mouseClicked(mx, my, button);
            return true;
        }
        int top = topAt(mx, my);
        if (top >= 0) {
            if (top == jobsTab()) {
                if (!jobs) {
                    jobs = true;
                    lastJobs = true;
                    scroll = 0;
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.6f));
                }
                return true;
            }
            boolean nextAll = top == 0;
            BulkKind next = nextAll ? null : modes().get(top - 1);
            if (jobs || next != mode || nextAll != all) {
                jobs = false;
                lastJobs = false;
                mode = next;
                lastMode = next;
                all = nextAll;
                lastAll = nextAll;
                scroll = 0;
                refresh();
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.6f));
            }
            return true;
        }
        if (jobs) {
            if (inGrid(mx, my)) {
                search.setFocused(false);
                JobControl control = jobControlAt(mx, my);
                if (control != null) {
                    StorageNetwork.JobAction a = control.action();
                    if (a == StorageNetwork.JobAction.UP && hasShiftDown()) {
                        a = StorageNetwork.JobAction.TOP;
                    }
                    PacketDistributor.sendToServer(new Payloads.JobCommand(menu.containerId, control.job().info().id(), a.ordinal()));
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f,
                            a == StorageNetwork.JobAction.CANCEL ? 0.5f : 0.8f));
                }
                return true;
            }
            if (onBar(mx, my)) {
                draggingBar = true;
                dragBar(my);
                return true;
            }
            if (button == 1 && search.isMouseOver(mx, my)) {
                search.setValue("");
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        if (mode != null && inGrid(mx, my)) {
            search.setFocused(false);
            if (mode == BulkKind.EXPERIENCE && menu.getCarried().isEmpty()) {
                int action = button == 1 ? 2 : hasShiftDown() ? 1 : 0;
                PacketDistributor.sendToServer(new Payloads.TerminalXp(menu.containerId, action));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.4f));
            } else if (mode.typed() && !menu.getCarried().isEmpty()) {
                TerminalMenu.BulkEntry e = bulkAt(mx, my);
                PacketDistributor.sendToServer(new Payloads.TerminalBulkClick(menu.containerId, mode.ordinal(),
                        java.util.Optional.ofNullable(e == null ? null : e.key())));
            }
            return true;
        }
        if (all && inGrid(mx, my)) {
            // Everything tab: resources act like they do in their own tab, items fall through to the item clicks below.
            TerminalMenu.BulkEntry b = bulkAt(mx, my);
            if (b != null) {
                search.setFocused(false);
                BulkKind k = b.key().kind();
                if (k == BulkKind.EXPERIENCE && menu.getCarried().isEmpty()) {
                    int action = button == 1 ? 2 : hasShiftDown() ? 1 : 0;
                    PacketDistributor.sendToServer(new Payloads.TerminalXp(menu.containerId, action));
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.4f));
                } else if (k.typed() && !menu.getCarried().isEmpty()) {
                    PacketDistributor.sendToServer(new Payloads.TerminalBulkClick(menu.containerId, k.ordinal(), java.util.Optional.of(b.key())));
                }
                return true;
            }
        }
        ItemCategories.Category tab = tabAt(mx, my);
        if (tab != null) {
            if (tab != category) {
                category = tab;
                lastCategory = tab;
                scroll = 0;
                refresh();
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.6f));
            }
            return true;
        }
        if (onBar(mx, my)) {
            draggingBar = true;
            dragBar(my);
            return true;
        }
        if (inGrid(mx, my)) {
            search.setFocused(false);
            TerminalMenu.Entry e = entryAt(mx, my);
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                send(null, button == 1 ? TerminalMenu.INSERT_ONE : TerminalMenu.INSERT_ALL);
            } else if (e != null && hasAltDown()) {
                // pin it to the top, or unpin it
                java.util.List<String> pins = ClientConfig.pinned();
                String id = BuiltInRegistries.ITEM.getKey(e.key().item()).toString();
                if (!pins.remove(id)) {
                    pins.add(id);
                }
                ClientConfig.setPinned(pins);
                refresh();
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.2f));
            } else if (e != null && (e.count() > 0 || e.craftable())) {
                boolean wantsCraft = e.craftable() && (e.count() == 0 || hasControlDown() || button == 2);
                if (wantsCraft) {
                    openCraft(e);
                } else if (hasShiftDown()) {
                    send(e, TerminalMenu.EXTRACT_TO_INVENTORY);
                } else if (button == 1) {
                    send(e, TerminalMenu.EXTRACT_HALF);
                } else if (button == 0) {
                    send(e, TerminalMenu.EXTRACT_STACK);
                }
            }
            return true;
        }
        if (button == 1 && search.isMouseOver(mx, my)) {
            search.setValue("");
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void dragBar(double my) {
        int max = maxScroll();
        if (max > 0) {
            double f = (my - (topPos + GY - 1) - 7) / (rows * 18 - 15.0);
            scroll = (int) Math.round(Math.max(0, Math.min(1, f)) * max);
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingBar) {
            dragBar(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingBar = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (crafting != null) {
            previewScroll = Math.max(0, previewScroll - (int) Math.signum(sy));
            return true;
        }
        if (settingsOpen) {
            return true;
        }
        if (inGrid(mx, my) || onBar(mx, my)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(sy)));
            return true;
        }
        if (tabAt(mx, my) != null) {
            int n = ItemCategories.Category.values().length;
            category = ItemCategories.Category.values()[Math.floorMod(category.ordinal() - (int) Math.signum(sy), n)];
            lastCategory = category;
            scroll = 0;
            refresh();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (settingsOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                settingsOpen = false;
            }
            return true;
        }
        if (crafting != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeCraft();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                confirmCraft();
                return true;
            }
            craftAmount.keyPressed(key, scan, mods);
            return true;
        }
        if (search.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(key, scan, mods);
            return true;
        }
        // R over an item: open its recipes in the installed viewer (JEI, EMI or REI)
        if (key == GLFW.GLFW_KEY_R && hoverEntry != null && ViewerShow.available()) {
            ViewerShow.show(hoverEntry.key().stack());
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (crafting != null) {
            return craftAmount.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
    }
}
