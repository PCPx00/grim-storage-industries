package com.xtri6.grimstorage.client;

import com.xtri6.grimstorage.client.ui.Theme;
import com.xtri6.grimstorage.client.ui.ThemeToggle;
import com.xtri6.grimstorage.client.ui.Ui;
import com.xtri6.grimstorage.menu.GrimMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Base for every Grim Storage screen: a translucent rounded panel in the current theme, inset slot frames
 * drawn from the menu's slot list, an accent rule under the title and the dark/light toggle in the corner.
 */
public abstract class GrimScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    protected GrimScreen(T menu, Inventory inv, Component title, int width, int height) {
        super(menu, inv, title);
        this.imageWidth = width;
        this.imageHeight = height;
        this.inventoryLabelY = height - 94;
        this.titleLabelY = 4;
    }

    protected static Theme t() {
        return Theme.get();
    }

    /** Space needed above the panel (for tabs along the top edge). */
    protected int topExtra() {
        return 0;
    }

    @Override
    protected void init() {
        super.init();
        int extra = topExtra();
        if (extra > 0) {
            topPos = Math.max(extra, (height - imageHeight - extra) / 2 + extra);
        }
        addRenderableWidget(new ThemeToggle(leftPos + imageWidth - 16, topPos + 5));
    }

    protected void drawSlots(GuiGraphics g) {
        Theme t = t();
        for (Slot s : menu.slots) {
            if (s.isActive()) {
                if (s instanceof GrimMenu.DisplaySlot) {
                    Ui.bigSlot(g, t, leftPos + s.x, topPos + s.y);
                } else {
                    Ui.slot(g, t, leftPos + s.x, topPos + s.y);
                }
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Ui.panel(g, t(), leftPos, topPos, imageWidth, imageHeight);
        drawSlots(g);
        drawExtraBg(g, mouseX, mouseY);
    }

    protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {}

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        Theme t = t();
        g.drawString(font, title, titleLabelX, titleLabelY, t.text(), false);
        Ui.rule(g, t, titleLabelX, titleLabelY + 10, Math.min(imageWidth - 16, font.width(title) + 40));
        if (inventoryLabelY < imageHeight) {
            g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, t.textDim(), false);
        }
    }

    /** Small grey helper text. */
    protected void hint(GuiGraphics g, Component text, int x, int y) {
        g.drawString(font, text, x, y, t().textFaint(), false);
    }

    @Override
    public int getSlotColor(int index) {
        return t().hover();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
