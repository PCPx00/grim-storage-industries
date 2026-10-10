package com.xtri6.grimstorage.client;

import com.xtri6.grimstorage.block.entity.StockKeeperBlockEntity;
import com.xtri6.grimstorage.menu.BlockMenus;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.util.Fmt;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/** Pick items in the top row, click the number under one, type how many to keep, press Set. */
public class StockKeeperScreen extends GrimScreen<BlockMenus.StockKeeper> {
    private static final int NUM_Y = 40;
    private EditBox amount;
    private Button set;
    private int selected = 0;

    public StockKeeperScreen(BlockMenus.StockKeeper menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 166);
    }

    @Override
    protected void init() {
        super.init();
        amount = new EditBox(font, leftPos + 8, topPos + 55, 90, 12, Component.translatable("gui.grimstorage.stock.amount"));
        amount.setMaxLength(10);
        amount.setFilter(s -> s.isEmpty() || s.matches("\\d{0,10}"));
        addRenderableWidget(amount);
        set = Button.builder(Component.translatable("gui.grimstorage.stock.set"), b -> apply())
                .bounds(leftPos + 102, topPos + 54, 30, 14).build();
        addRenderableWidget(set);
        Button clear = Button.builder(Component.translatable("gui.grimstorage.stock.clear"), b -> {
            amount.setValue("0");
            apply();
        }).bounds(leftPos + 136, topPos + 54, 32, 14).build();
        addRenderableWidget(clear);
        select(0);
    }

    private int shownTarget = -1;

    private void select(int rule) {
        selected = rule;
        shownTarget = menu.target(rule);
        amount.setValue(Integer.toString(shownTarget));
    }

    /** Keep the box in step with the server (the saved value arrives a tick after the screen opens). */
    @Override
    protected void containerTick() {
        super.containerTick();
        int now = menu.target(selected);
        if (now != shownTarget && !amount.isFocused()) {
            select(selected);
        }
    }

    private void apply() {
        long v;
        try {
            v = amount.getValue().isEmpty() ? 0 : Long.parseLong(amount.getValue());
        } catch (NumberFormatException e) {
            v = 0;
        }
        int target = (int) Math.min(StockKeeperBlockEntity.MAX_TARGET, v);
        PacketDistributor.sendToServer(new Payloads.SetStockTarget(menu.containerId, selected, target));
    }

    @Override
    protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < StockKeeperBlockEntity.RULES; i++) {
            int x = leftPos + 8 + i * 18;
            int y = topPos + NUM_Y;
            int bg = i == selected ? ACCENT_DIM : 0xFF8B8B8B;
            g.fill(x - 1, y - 1, x + 17, y + 9, bg);
            int state = menu.ruleState(i);
            int dot = state == 1 ? ACCENT : state == 2 ? 0xFFE04040 : 0xFF40C040;
            if (menu.getSlot(i).hasItem() && menu.target(i) > 0) {
                g.fill(x + 13, y - 4, x + 16, y - 1, dot);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        for (int i = 0; i < StockKeeperBlockEntity.RULES; i++) {
            String n = Fmt.compact(menu.target(i));
            g.pose().pushPose();
            g.pose().translate(8 + i * 18 + 8, NUM_Y + 1, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, n, -font.width(n) / 2, 0, i == selected ? 0xFFFFFFFF : 0xFF202020, false);
            g.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < StockKeeperBlockEntity.RULES; i++) {
            int x = leftPos + 8 + i * 18;
            int y = topPos + NUM_Y;
            if (mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 9) {
                select(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (amount.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                apply();
                return true;
            }
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                return amount.keyPressed(key, scan, mods) || true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }
}
