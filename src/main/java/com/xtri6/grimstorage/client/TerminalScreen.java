package com.xtri6.grimstorage.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.client.ui.GrimButton;
import com.xtri6.grimstorage.client.ui.Theme;
import com.xtri6.grimstorage.client.ui.Ui;
import com.xtri6.grimstorage.menu.TerminalMenu;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.util.Fmt;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/**
 * The storage terminal (block and wireless). Left: filter tabs. Middle: the network's items, search and sort.
 * Right: a 3x3 crafting grid that refills itself from the network, plus a storage gauge.
 *
 * Click an item to take a stack, right-click for half, shift-click to send it to your inventory, click with
 * an item held to store it. Craftable items show "Craft": click (or ctrl/middle-click) to order a craft.
 */
public class TerminalScreen extends GrimScreen<TerminalMenu> {
    private static final int W = 272;
    private static final int H = 222;
    private static final int COLS = 9;
    private static final int ROWS = 6;
    private static final int GX = 8;
    private static final int GY = 18;
    private static final int BAR_X = 174;
    private static final int RIGHT_X = 192;
    private static final int TAB_W = 22;
    private static final int TAB_H = 20;
    private static final String[] SORTS = {"gui.grimstorage.sort.count", "gui.grimstorage.sort.name", "gui.grimstorage.sort.mod"};

    /** Remembered between openings so the terminal reopens the way you left it. */
    private static ItemCategories.Category lastCategory = ItemCategories.Category.ALL;
    private static int lastSort;

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
    protected void init() {
        super.init();
        ItemCategories.reset();
        Theme t = t();
        search = new EditBox(font, leftPos + 100, topPos + 6, 76, 9, Component.translatable("gui.grimstorage.search"));
        search.setMaxLength(50);
        search.setBordered(false);
        search.setTextColor(t.text() & 0xFFFFFF);
        search.setHint(Component.translatable("gui.grimstorage.search.hint").withColor(t.textFaint()));
        search.setValue(seenSearch);
        addRenderableWidget(search);
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

        craftAmount = new EditBox(font, 0, 0, 100, 9, Component.translatable("gui.grimstorage.craft.amount"));
        craftAmount.setMaxLength(7);
        craftAmount.setBordered(false);
        craftAmount.setFilter(s -> s.matches("\\d{0,7}"));
        craftGo = new GrimButton(0, 0, 60, 16, Component.translatable("gui.grimstorage.craft.go"), b -> confirmCraft(), true);
        craftCancel = new GrimButton(0, 0, 60, 16, Component.translatable("gui.cancel"), b -> closeCraft());
        refresh();
    }

    // ------------------------------------------------------------------ data

    private void refresh() {
        seenRevision = menu.clientRevision;
        seenSearch = search.getValue();
        String q = seenSearch.toLowerCase(Locale.ROOT).trim();
        boolean byMod = q.startsWith("@");
        String needle = byMod ? q.substring(1) : q;
        Arrays.fill(categoryCounts, 0);
        List<TerminalMenu.Entry> out = new ArrayList<>();
        for (TerminalMenu.Entry e : menu.clientEntries) {
            for (ItemCategories.Category c : ItemCategories.Category.values()) {
                if (ItemCategories.matches(c, e.key().stack())) {
                    categoryCounts[c.ordinal()]++;
                }
            }
            if (!ItemCategories.matches(category, e.key().stack())) {
                continue;
            }
            if (!needle.isEmpty()) {
                ItemStack s = e.key().stack();
                String hay = byMod ? BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace() : s.getHoverName().getString().toLowerCase(Locale.ROOT);
                if (!hay.contains(needle)) {
                    continue;
                }
            }
            out.add(e);
        }
        Comparator<TerminalMenu.Entry> byName = Comparator.comparing(e -> e.key().stack().getHoverName().getString());
        Comparator<TerminalMenu.Entry> cmp = switch (sort) {
            case 1 -> byName;
            case 2 -> Comparator.<TerminalMenu.Entry, String>comparing(e -> BuiltInRegistries.ITEM.getKey(e.key().item()).toString());
            default -> Comparator.comparingLong(TerminalMenu.Entry::count).reversed().thenComparing(byName);
        };
        out.sort(cmp);
        view = out;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        if (sortButton != null) {
            sortButton.setTooltip(Tooltip.create(Component.translatable(SORTS[sort])));
        }
    }

    private int maxScroll() {
        return Math.max(0, (view.size() + COLS - 1) / COLS - ROWS);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.clientRevision != seenRevision || !search.getValue().equals(seenSearch)) {
            refresh();
        }
        search.setTextColor(t().text() & 0xFFFFFF);
    }

    @Nullable
    private TerminalMenu.Entry entryAt(double mx, double my) {
        int col = (int) Math.floor((mx - leftPos - GX) / 18.0);
        int row = (int) Math.floor((my - topPos - GY) / 18.0);
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) {
            return null;
        }
        int idx = (scroll + row) * COLS + col;
        return idx < view.size() ? view.get(idx) : null;
    }

    private boolean inGrid(double mx, double my) {
        return mx >= leftPos + GX - 1 && mx < leftPos + GX + COLS * 18 - 1 && my >= topPos + GY - 1 && my < topPos + GY + ROWS * 18 - 1;
    }

    private boolean onBar(double mx, double my) {
        return mx >= leftPos + BAR_X && mx < leftPos + BAR_X + 10 && my >= topPos + GY - 1 && my < topPos + GY + ROWS * 18 - 1;
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
        if (mx < tabX() || mx >= leftPos) {
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
        g.renderItem(c.icon(), x + 4, y + 2);
        boolean empty = c != ItemCategories.Category.ALL && categoryCounts[c.ordinal()] == 0;
        if (empty && !selected) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            g.fill(x + 4, y + 2, x + 20, y + 18, Theme.withAlpha(t.panelBottom(), 0xA0));
            g.pose().popPose();
        }
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return tabAt(mx, my) == null && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Theme t = t();
        for (ItemCategories.Category c : ItemCategories.Category.values()) {
            if (c != category) {
                drawTab(g, t, c, mouseX, mouseY);
            }
        }
        super.renderBg(g, partialTick, mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(0, 0, 50);
        drawTab(g, t, category, mouseX, mouseY);
        g.pose().popPose();
    }

    @Override
    protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
        Theme t = t();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Ui.slot(g, t, leftPos + GX + c * 18, topPos + GY + r * 18);
            }
        }
        Ui.field(g, t, leftPos + 97, topPos + 4, 82, 12, search.isFocused());
        int max = maxScroll();
        Ui.scrollbar(g, t, leftPos + BAR_X, topPos + GY - 1, 10, ROWS * 18, max == 0 ? 0 : scroll / (float) max, max > 0,
                draggingBar || onBar(mouseX, mouseY));
        // right column divider and crafting arrow
        g.fill(leftPos + RIGHT_X - 4, topPos + 18, leftPos + RIGHT_X - 3, topPos + H - 8, Theme.withAlpha(t.border() & 0xFFFFFF, 0x90));
        Ui.arrow(g, t, leftPos + TerminalMenu.RESULT_X + 3, topPos + 86, 0, true);
        // storage gauge
        float fill = menu.clientCapacity <= 0 ? 0 : (float) menu.clientUsed / menu.clientCapacity;
        int color = fill > 0.95f ? t.danger() : fill > 0.8f ? t.warn() : t.accent();
        Ui.bar(g, t, leftPos + RIGHT_X, topPos + 152, W - RIGHT_X - 8, 6, fill, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        Theme t = t();
        String name = font.plainSubstrByWidth(title.getString(), 86);
        g.drawString(font, name, 8, 6, t.text(), false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, t.textDim(), false);
        g.drawString(font, Component.translatable("gui.grimstorage.crafting"), RIGHT_X, 18, t.textDim(), false);
        g.drawString(font, Component.translatable("gui.grimstorage.storage"), RIGHT_X, 141, t.textDim(), false);
        String used = Fmt.compact(menu.clientUsed) + " / " + Fmt.compact(menu.clientCapacity);
        g.drawString(font, used, RIGHT_X, 162, t.text(), false);
        g.drawString(font, Component.translatable("gui.grimstorage.types", menu.clientEntries.size()), RIGHT_X, 172, t.textFaint(), false);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable(category.langKey()).getString(), W - RIGHT_X - 8), RIGHT_X, 190,
                t.accent(), false);
        g.drawString(font, Component.translatable("gui.grimstorage.showing", view.size()), RIGHT_X, 200, t.textFaint(), false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Theme t = t();
        TerminalMenu.Entry hovered = null;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scroll + r) * COLS + c;
                if (idx >= view.size()) {
                    break;
                }
                TerminalMenu.Entry e = view.get(idx);
                int x = leftPos + GX + c * 18;
                int y = topPos + GY + r * 18;
                g.renderItem(e.key().stack(), x, y);
                if (e.count() > 0) {
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
        ItemCategories.Category tab = crafting == null ? tabAt(mouseX, mouseY) : null;
        if (crafting != null) {
            renderCraftPopup(g, t, mouseX, mouseY, partialTick);
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
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void renderCraftPopup(GuiGraphics g, Theme t, int mouseX, int mouseY, float pt) {
        int w = 160, h = 86;
        int x = leftPos + GX + (COLS * 18 - w) / 2, y = topPos + 26;
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
        g.drawString(font, font.plainSubstrByWidth(name, w - 40), x + 32, y + 19, t.accent(), false);
        Ui.rule(g, t, x + 8, y + 30, w - 16);
        Ui.field(g, t, x + 8, y + 37, w - 16, 14, true);
        craftAmount.setPosition(x + 12, y + 40);
        craftAmount.setWidth(w - 24);
        craftAmount.setTextColor(t.text() & 0xFFFFFF);
        craftGo.setPosition(x + 8, y + 60);
        craftCancel.setPosition(x + w - 68, y + 60);
        craftAmount.render(g, mouseX, mouseY, pt);
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
        if (crafting != null) {
            if (craftAmount.mouseClicked(mx, my, button)) {
                craftAmount.setFocused(true);
                return true;
            }
            craftGo.mouseClicked(mx, my, button);
            craftCancel.mouseClicked(mx, my, button);
            return true;
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
            } else if (e != null) {
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
            double f = (my - (topPos + GY - 1) - 7) / (ROWS * 18 - 15.0);
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
