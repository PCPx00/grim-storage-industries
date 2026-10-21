package com.xtri6.grimstorage.client;

import com.xtri6.grimstorage.block.BusBlock;
import com.xtri6.grimstorage.client.ui.GrimButton;
import com.xtri6.grimstorage.client.ui.Theme;
import com.xtri6.grimstorage.menu.BlockMenus;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;

/** Machine Bus: 9 Processing Card slots, what machine it is on, how many recipes it offers, and two switches. */
public class MachineBusScreen extends GrimScreen<BlockMenus.MachineBus> {
    private GrimButton learn;
    private GrimButton fuel;

    public MachineBusScreen(BlockMenus.MachineBus menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 210);
        this.inventoryLabelY = 114;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void init() {
        super.init();
        learn = addRenderableWidget(new GrimButton(leftPos + 70, topPos + 62, 98, 14, Component.empty(), b -> press(BlockMenus.MachineBus.LEARN)));
        learn.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.machine_bus.learn.tooltip")));
        fuel = addRenderableWidget(new GrimButton(leftPos + 70, topPos + 80, 98, 14, Component.empty(), b -> press(BlockMenus.MachineBus.FUEL)));
        fuel.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.machine_bus.fuel.tooltip")));
        refresh();
    }

    private void refresh() {
        learn.setMessage(Component.translatable(menu.learns() ? "gui.grimstorage.machine_bus.learn.on" : "gui.grimstorage.machine_bus.learn.off"));
        fuel.setMessage(Component.translatable(menu.fuels() ? "gui.grimstorage.machine_bus.fuel.on" : "gui.grimstorage.machine_bus.fuel.off"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (hoveredSlot instanceof net.minecraft.world.inventory.Slot slot && !slot.hasItem() && menu.getCarried().isEmpty()
                && slot.index == com.xtri6.grimstorage.block.entity.MachineBusBlockEntity.SLOTS) {
            g.renderTooltip(font, Component.translatable("gui.grimstorage.machine_bus.range_slot.tooltip"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        Theme t = t();
        Component machine = Component.translatable("gui.grimstorage.machine_bus.none");
        if (minecraft != null && minecraft.level != null) {
            BlockState self = minecraft.level.getBlockState(menu.pos);
            if (self.hasProperty(BusBlock.FACING)) {
                BlockPos target = menu.pos.relative(self.getValue(BusBlock.FACING));
                BlockState there = minecraft.level.getBlockState(target);
                if (!there.isAir()) {
                    machine = there.getBlock().getName();
                }
            }
        }
        hint(g, Component.translatable("gui.grimstorage.machine_bus.label.cards"), 8, 22);
        if (menu.wireless()) {
            hint(g, Component.translatable("gui.grimstorage.machine_bus.label.range"), 8, 100);
        }
        g.drawString(font, Component.translatable("gui.grimstorage.machine_bus.on"), 70, 22, t.textDim(), false);
        // long names (Enrichment Chamber, Precision Sawmill) shrink to fit rather than get cut off
        String name = machine.getString();
        float scale = Math.max(0.6f, Math.min(1f, 98f / Math.max(1, font.width(name))));
        g.pose().pushPose();
        g.pose().translate(70, 32 + (1 - scale) * 4, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, font.plainSubstrByWidth(name, (int) (98 / scale)), 0, 0, t.accent(), false);
        g.pose().popPose();
        Component recipes = Component.translatable("gui.grimstorage.machine_bus.recipes", menu.recipes());
        g.drawString(font, recipes, 70, 46, menu.recipes() > 0 ? t.ok() : t.textFaint(), false);
    }
}
