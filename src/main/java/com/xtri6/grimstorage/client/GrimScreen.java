package com.xtri6.grimstorage.client;

import com.xtri6.grimstorage.menu.GrimMenu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Base screen. Backgrounds are drawn from code (no texture sheets), in vanilla's grey with a Soulite edge,
 * and every slot gets its frame automatically from the menu's slot list.
 */
public abstract class GrimScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    public static final int BODY = 0xFFC6C6C6;
    public static final int LIGHT = 0xFFFFFFFF;
    public static final int DARK = 0xFF555555;
    public static final int EDGE = 0xFF1B2430;
    public static final int ACCENT = 0xFF6FF2FF;
    public static final int ACCENT_DIM = 0xFF3F5872;
    public static final int TEXT = 0xFF404040;

    protected GrimScreen(T menu, Inventory inv, Component title, int width, int height) {
        super(menu, inv, title);
        this.imageWidth = width;
        this.imageHeight = height;
        this.inventoryLabelY = height - 94;
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, EDGE);
        g.fill(x, y + 1, x + w, y + h - 1, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BODY);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
        g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, DARK);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, DARK);
        g.fill(x + 4, y + 1, x + w - 4, y + 2, ACCENT_DIM);
    }

    /** A slot frame; (x, y) is the item position, like Slot.x/y. */
    public static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
        g.fill(x - 1, y - 1, x + 16, y, 0xFF373737);
        g.fill(x - 1, y - 1, x, y + 16, 0xFF373737);
        g.fill(x, y + 16, x + 17, y + 17, LIGHT);
        g.fill(x + 16, y, x + 17, y + 17, LIGHT);
    }

    /** A bigger frame for output slots. */
    public static void bigSlot(GuiGraphics g, int x, int y) {
        g.fill(x - 5, y - 5, x + 21, y + 21, 0xFF8B8B8B);
        g.fill(x - 5, y - 5, x + 20, y - 4, 0xFF373737);
        g.fill(x - 5, y - 5, x - 4, y + 20, 0xFF373737);
        g.fill(x - 4, y + 20, x + 21, y + 21, LIGHT);
        g.fill(x + 20, y - 4, x + 21, y + 21, LIGHT);
    }

    /** A right-pointing arrow filled to {@code progress} (0..1) with the Soulite glow. */
    public static void arrow(GuiGraphics g, int x, int y, float progress) {
        int len = 22;
        g.fill(x, y + 3, x + len - 6, y + 7, DARK);
        for (int i = 0; i < 6; i++) {
            g.fill(x + len - 6 + i, y + i, x + len - 5 + i, y + 10 - i, DARK);
        }
        int filled = Math.round(len * progress);
        if (filled > 0) {
            g.fill(x, y + 3, x + Math.min(filled, len - 6), y + 7, ACCENT);
            for (int i = 0; i < 6 && len - 6 + i < filled; i++) {
                g.fill(x + len - 6 + i, y + i, x + len - 5 + i, y + 10 - i, ACCENT);
            }
        }
    }

    public static void smallText(GuiGraphics g, Font font, String text, int rightX, int bottomY, int color) {
        smallText(g, font, text, rightX, bottomY, color, 2f / 3f);
    }

    public static void smallText(GuiGraphics g, Font font, String text, int rightX, int bottomY, int color, float s) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.pose().scale(s, s, 1f);
        g.drawString(font, text, Math.round(rightX / s) - font.width(text), Math.round(bottomY / s) - 8, color, true);
        g.pose().popPose();
    }

    protected void drawSlots(GuiGraphics g) {
        for (Slot s : menu.slots) {
            if (s.isActive()) {
                if (s instanceof GrimMenu.DisplaySlot) {
                    bigSlot(g, leftPos + s.x, topPos + s.y);
                } else {
                    slot(g, leftPos + s.x, topPos + s.y);
                }
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        panel(g, leftPos, topPos, imageWidth, imageHeight);
        drawSlots(g);
        drawExtraBg(g, mouseX, mouseY);
    }

    protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
