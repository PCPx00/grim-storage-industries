package com.xtri6.grimstorage.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.menu.TerminalMenu;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.util.Fmt;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/**
 * The storage terminal. Click to take a stack, right-click for half, shift-click straight to your inventory,
 * click with an item held to store it. Items you can craft show up too: click one with 0 stored (or
 * ctrl/middle-click any craftable item) to order a craft.
 */
public class TerminalScreen extends GrimScreen<TerminalMenu> {
    private static final int COLS = 9;
    private static final int ROWS = 6;
    private static final int GX = 8;
    private static final int GY = 18;
    private static final int BAR_X = 174;
    private static final String[] SORTS = {"gui.grimstorage.sort.count", "gui.grimstorage.sort.name", "gui.grimstorage.sort.mod"};

    private EditBox search;
    private Button sortButton;
    private int sort;
    private int scroll;
    private boolean draggingBar;
    private int seenRevision = -1;
    private String seenSearch = "";
    private List<TerminalMenu.Entry> view = new ArrayList<>();

    // Craft amount popup
    @Nullable
    private TerminalMenu.Entry crafting;
    private EditBox craftAmount;
    private Button craftGo;
    private Button craftCancel;

    public TerminalScreen(TerminalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 194, 222);
        this.inventoryLabelY = 129;
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 80, topPos + 5, 72, 10, Component.translatable("gui.grimstorage.search"));
        search.setMaxLength(50);
        search.setBordered(false);
        search.setTextColor(0xFFFFFF);
        search.setValue(seenSearch);
        addRenderableWidget(search);
        sortButton = Button.builder(Component.literal("⇅"), b -> {
            sort = (sort + 1) % SORTS.length;
            refresh();
        }).bounds(leftPos + 156, topPos + 3, 14, 12).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable(SORTS[0]))).build();
        addRenderableWidget(sortButton);

        craftAmount = new EditBox(font, 0, 0, 60, 14, Component.translatable("gui.grimstorage.craft.amount"));
        craftAmount.setMaxLength(7);
        craftAmount.setFilter(s -> s.matches("\\d{0,7}"));
        craftGo = Button.builder(Component.translatable("gui.grimstorage.craft.go"), b -> confirmCraft()).bounds(0, 0, 50, 16).build();
        craftCancel = Button.builder(Component.translatable("gui.cancel"), b -> closeCraft()).bounds(0, 0, 50, 16).build();
        refresh();
    }

    // ------------------------------------------------------------------ data

    private void refresh() {
        seenRevision = menu.clientRevision;
        seenSearch = search.getValue();
        String q = seenSearch.toLowerCase(Locale.ROOT).trim();
        boolean byMod = q.startsWith("@");
        String needle = byMod ? q.substring(1) : q;
        List<TerminalMenu.Entry> out = new ArrayList<>();
        for (TerminalMenu.Entry e : menu.clientEntries) {
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
            sortButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(SORTS[sort])));
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

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                slot(g, leftPos + GX + c * 18, topPos + GY + r * 18);
            }
        }
        // search field background
        g.fill(leftPos + 78, topPos + 3, leftPos + 154, topPos + 15, 0xFF000000);
        g.fill(leftPos + 79, topPos + 4, leftPos + 153, topPos + 14, 0xFF26303A);
        // scrollbar
        int barTop = topPos + GY - 1;
        int barH = ROWS * 18;
        g.fill(leftPos + BAR_X, barTop, leftPos + BAR_X + 12, barTop + barH, 0xFF8B8B8B);
        int max = maxScroll();
        int thumbH = 15;
        int thumbY = max == 0 ? barTop : barTop + (int) ((barH - thumbH) * (scroll / (float) max));
        g.fill(leftPos + BAR_X + 1, thumbY, leftPos + BAR_X + 11, thumbY + thumbH, max == 0 ? 0xFFA0A0A0 : 0xFFE0E0E0);
        g.fill(leftPos + BAR_X + 1, thumbY + thumbH - 1, leftPos + BAR_X + 11, thumbY + thumbH, DARK);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
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
                    smallText(g, font, Fmt.compact(e.count()), x + 17, y + 16, 0xFFFFFF);
                } else {
                    smallText(g, font, Component.translatable("gui.grimstorage.craft.short").getString(), x + 17, y + 16, 0x6FF2FF);
                }
                if (crafting == null && mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    hovered = e;
                    g.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), x, y, x + 16, y + 16, 0x80FFFFFF, 0x80FFFFFF, 0);
                }
            }
        }
        if (!menu.clientOnline) {
            Component off = Component.translatable("gui.grimstorage.terminal.offline");
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.drawString(font, off, leftPos + GX + (COLS * 18 - font.width(off)) / 2, topPos + GY + 46, 0xFFE04040, true);
            g.pose().popPose();
        }
        if (crafting != null) {
            renderCraftPopup(g, mouseX, mouseY, partialTick);
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

    private void renderCraftPopup(GuiGraphics g, int mouseX, int mouseY, float pt) {
        int w = 150, h = 78;
        int x = leftPos + (imageWidth - w) / 2, y = topPos + 30;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0x90000000);
        panel(g, x, y, w, h);
        g.renderItem(crafting.key().stack(), x + 8, y + 8);
        g.drawString(font, Component.translatable("gui.grimstorage.craft.title"), x + 28, y + 8, TEXT, false);
        String name = crafting.key().stack().getHoverName().getString();
        g.drawString(font, font.plainSubstrByWidth(name, w - 36), x + 28, y + 17, 0xFF2A6A7A, false);
        craftAmount.setPosition(x + 8, y + 32);
        craftAmount.setWidth(w - 16);
        craftGo.setPosition(x + 8, y + 54);
        craftCancel.setPosition(x + w - 58, y + 54);
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
            if (craftGo.mouseClicked(mx, my, button) || craftCancel.mouseClicked(mx, my, button)) {
                return true;
            }
            return true;
        }
        if (mx >= leftPos + BAR_X && mx < leftPos + BAR_X + 12 && my >= topPos + GY - 1 && my < topPos + GY + ROWS * 18) {
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
            double t = (my - (topPos + GY - 1) - 7) / (ROWS * 18 - 15.0);
            scroll = (int) Math.round(Math.max(0, Math.min(1, t)) * max);
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
        if (inGrid(mx, my) || (mx >= leftPos + BAR_X && mx < leftPos + BAR_X + 12)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(sy)));
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

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, font.plainSubstrByWidth(title.getString(), 68), 8, 6, TEXT, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, TEXT, false);
    }
}
