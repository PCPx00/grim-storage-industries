package com.xtri6.grimstorage.client;

import com.xtri6.grimstorage.block.BusBlock;
import com.xtri6.grimstorage.client.ui.GrimButton;
import com.xtri6.grimstorage.client.ui.Theme;
import com.xtri6.grimstorage.client.ui.Ui;
import com.xtri6.grimstorage.menu.BlockMenus;
import com.xtri6.grimstorage.util.Fmt;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;

/** Chest Link: what it is linked to, how full that is, and two switches. */
public class ChestLinkScreen extends GrimScreen<BlockMenus.ChestLink> {
    private static final String[] MODES = {"gui.grimstorage.chest_link.both", "gui.grimstorage.chest_link.insert", "gui.grimstorage.chest_link.extract"};
    private GrimButton mode;
    private GrimButton first;

    public ChestLinkScreen(BlockMenus.ChestLink menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 116);
        this.inventoryLabelY = 10_000;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void init() {
        super.init();
        mode = addRenderableWidget(new GrimButton(leftPos + 8, topPos + 72, 160, 14, Component.empty(), b -> press(BlockMenus.ChestLink.MODE)));
        mode.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.chest_link.mode.tooltip")));
        first = addRenderableWidget(new GrimButton(leftPos + 8, topPos + 90, 160, 14, Component.empty(), b -> press(BlockMenus.ChestLink.FIRST)));
        first.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.chest_link.first.tooltip")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        mode.setMessage(Component.translatable(MODES[Math.min(2, menu.mode())]));
        first.setMessage(Component.translatable(menu.fillFirst() ? "gui.grimstorage.chest_link.first.on" : "gui.grimstorage.chest_link.first.off"));
    }

    @Override
    protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
        Theme t = t();
        int cap = menu.capacity();
        float f = cap <= 0 ? 0 : (float) menu.used() / cap;
        if (menu.linked()) {
            Ui.bar(g, t, leftPos + 8, topPos + 48, 160, 6, f, f > 0.95f ? t.danger() : t.accent());
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        Theme t = t();
        Component target = Component.translatable("gui.grimstorage.machine_bus.none");
        if (minecraft != null && minecraft.level != null) {
            BlockState self = minecraft.level.getBlockState(menu.pos);
            if (self.hasProperty(BusBlock.FACING)) {
                BlockState there = minecraft.level.getBlockState(menu.pos.relative(self.getValue(BusBlock.FACING)));
                if (!there.isAir()) {
                    target = there.getBlock().getName();
                }
            }
        }
        g.drawString(font, Component.translatable("gui.grimstorage.chest_link.linked"), 8, 22, t.textDim(), false);
        g.drawString(font, font.plainSubstrByWidth(target.getString(), 160), 8, 32,
                menu.linked() ? t.accent() : t.danger(), false);
        if (!menu.linked()) {
            int ly = 46;
            for (net.minecraft.util.FormattedCharSequence line : font.split(Component.translatable("gui.grimstorage.chest_link.nothing"), 160)) {
                g.drawString(font, line, 8, ly, t.textFaint(), false);
                ly += 9;
            }
        } else {
            String used = Fmt.compact(menu.used()) + " / " + Fmt.compact(menu.capacity());
            g.drawString(font, used, 168 - font.width(used), 22, t.textDim(), false);
        }
    }
}
