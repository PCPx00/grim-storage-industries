package com.xtri6.grimstorage.client;

import com.xtri6.grimstorage.block.entity.StockKeeperBlockEntity;
import com.xtri6.grimstorage.client.ui.GrimButton;
import com.xtri6.grimstorage.client.ui.Theme;
import com.xtri6.grimstorage.client.ui.Ui;
import com.xtri6.grimstorage.menu.BlockMenus;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.util.Fmt;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/** Pick items in the top row, click the number under one, type how many to keep, press Set. */
public class StockKeeperScreen extends GrimScreen<BlockMenus.StockKeeper> {
    private static final int NUM_Y = 40;
    private EditBox amount;
    private int selected = 0;
    private int shownTarget = -1;

    public StockKeeperScreen(BlockMenus.StockKeeper menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 166);
    }

    @Override
    protected void init() {
        super.init();
        amount = new EditBox(font, leftPos + 12, topPos + 58, 82, 9, Component.translatable("gui.grimstorage.stock.amount"));
        amount.setMaxLength(10);
        amount.setBordered(false);
        amount.setFilter(s -> s.isEmpty() || s.matches("\\d{0,10}"));
        addRenderableWidget(amount);
        addRenderableWidget(new GrimButton(leftPos + 102, topPos + 55, 30, 14, Component.translatable("gui.grimstorage.stock.set"), b -> apply(), true));
        addRenderableWidget(new GrimButton(leftPos + 135, topPos + 55, 33, 14, Component.translatable("gui.grimstorage.stock.clear"), b -> {
            amount.setValue("0");
            apply();
        }));
        select(0);
    }

    private void select(int rule) {
        selected = rule;
        shownTarget = menu.target(rule);
        amount.setValue(Integer.toString(shownTarget));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        amount.setTextColor(t().text() & 0xFFFFFF);
        if (menu.target(selected) != shownTarget && !amount.isFocused()) {
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
        PacketDistributor.sendToServer(new Payloads.SetStockTarget(menu.containerId, selected, (int) Math.min(StockKeeperBlockEntity.MAX_TARGET, v)));
        amount.setFocused(false);
    }

    @Override
    protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
        Theme t = t();
        for (int i = 0; i < StockKeeperBlockEntity.RULES; i++) {
            int x = leftPos + 8 + i * 18;
            int y = topPos + NUM_Y;
            boolean hover = mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 9;
            g.fill(x - 1, y - 1, x + 17, y + 9, i == selected ? t.accentDim() : hover ? t.buttonHover() : t.button());
            int state = menu.ruleState(i);
            if (menu.getSlot(i).hasItem() && menu.target(i) > 0) {
                int dot = state == 1 ? t.accent() : state == 2 ? t.danger() : t.ok();
                g.fill(x + 13, y - 4, x + 16, y - 1, dot);
            }
        }
        Ui.field(g, t, leftPos + 8, topPos + 55, 90, 14, amount.isFocused());
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        Theme t = t();
        for (int i = 0; i < StockKeeperBlockEntity.RULES; i++) {
            String n = Fmt.compact(menu.target(i));
            g.pose().pushPose();
            g.pose().translate(8 + i * 18 + 8, NUM_Y + 1, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, n, -font.width(n) / 2, 0, i == selected ? 0xFFFFFFFF : t.textDim(), false);
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
                amount.keyPressed(key, scan, mods);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }
}
