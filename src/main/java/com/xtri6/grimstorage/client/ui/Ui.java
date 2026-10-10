package com.xtri6.grimstorage.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Drawing helpers shared by every screen. All shapes are plain fills, so no texture sheets are needed. */
public final class Ui {
    private Ui() {}

    /** A rounded, gradient panel with a soft drop shadow and a lit top edge. */
    public static void panel(GuiGraphics g, Theme t, int x, int y, int w, int h) {
        int a = t.panelAlpha();
        // shadow
        g.fill(x + 2, y + h, x + w, y + h + 2, 0x40000000);
        g.fill(x + w, y + 2, x + w + 2, y + h, 0x40000000);
        // border (corners cut for a rounded look)
        g.fill(x + 2, y, x + w - 2, y + 1, t.border());
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, t.border());
        g.fill(x, y + 2, x + 1, y + h - 2, t.border());
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, t.border());
        g.fill(x + 1, y + 1, x + 2, y + 2, t.border());
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, t.border());
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, t.border());
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, t.border());
        // body
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, a | (t.panelTop() & 0xFFFFFF), a | (t.panelBottom() & 0xFFFFFF));
        // lit top edge
        g.fill(x + 2, y + 1, x + w - 2, y + 2, Theme.withAlpha(t.borderLight(), t.dark() ? 0x70 : 0xC0));
    }

    /** A thin accent rule that fades out to the right, used under titles and section headings. */
    public static void rule(GuiGraphics g, Theme t, int x, int y, int w) {
        int steps = 8;
        int seg = Math.max(1, w / steps);
        for (int i = 0; i < steps; i++) {
            int alpha = 0xE0 - i * (0xE0 / steps);
            g.fill(x + i * seg, y, x + (i + 1) * seg, y + 1, Theme.withAlpha(t.accent(), alpha));
        }
    }

    /** An inset slot; (x, y) is the item position like Slot.x/y. */
    public static void slot(GuiGraphics g, Theme t, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, t.slot());
        g.fill(x - 1, y - 1, x + 17, y, t.slotShadow());
        g.fill(x - 1, y, x, y + 17, t.slotShadow());
        g.fill(x, y + 16, x + 17, y + 17, Theme.withAlpha(t.slotLight(), 0xB0));
        g.fill(x + 16, y, x + 17, y + 16, Theme.withAlpha(t.slotLight(), 0xB0));
    }

    /** A large framed slot for outputs, with an accent corner. */
    public static void bigSlot(GuiGraphics g, Theme t, int x, int y) {
        g.fill(x - 5, y - 5, x + 21, y + 21, t.slot());
        g.fill(x - 5, y - 5, x + 21, y - 4, t.slotShadow());
        g.fill(x - 5, y - 4, x - 4, y + 21, t.slotShadow());
        g.fill(x - 4, y + 20, x + 21, y + 21, Theme.withAlpha(t.slotLight(), 0xB0));
        g.fill(x + 20, y - 4, x + 21, y + 20, Theme.withAlpha(t.slotLight(), 0xB0));
        g.fill(x - 4, y - 4, x + 2, y - 3, t.accentDim());
        g.fill(x - 4, y - 4, x - 3, y + 2, t.accentDim());
    }

    /** Inset background for a text field. */
    public static void field(GuiGraphics g, Theme t, int x, int y, int w, int h, boolean focused) {
        g.fill(x, y, x + w, y + h, focused ? t.accentDim() : t.slotShadow());
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, t.field());
    }

    /** A rounded progress/usage bar. {@code fill} is 0..1. */
    public static void bar(GuiGraphics g, Theme t, int x, int y, int w, int h, float fill, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, t.slotShadow());
        g.fill(x, y + 1, x + w, y + h - 1, t.slotShadow());
        int fw = Math.round((w - 2) * Math.max(0, Math.min(1, fill)));
        if (fw > 0) {
            g.fill(x + 1, y + 1, x + 1 + fw, y + h - 1, color);
            g.fill(x + 1, y + 1, x + 1 + fw, y + 2, Theme.withAlpha(0xFFFFFF, 0x50));
        }
    }

    /** A vertical scrollbar; {@code pos} is 0..1, {@code active} false when everything fits. */
    public static void scrollbar(GuiGraphics g, Theme t, int x, int y, int w, int h, float pos, boolean active, boolean hover) {
        g.fill(x, y, x + w, y + h, t.slot());
        g.fill(x, y, x + w, y + 1, t.slotShadow());
        int thumbH = 15;
        int ty = y + 1 + Math.round((h - 2 - thumbH) * pos);
        int c = !active ? t.textFaint() : hover ? t.accent() : t.accentDim();
        g.fill(x + 1, ty + 1, x + w - 1, ty + thumbH - 1, c);
        g.fill(x + 2, ty, x + w - 2, ty + thumbH, c);
    }

    /** Arrow pointing right (or down), filled to {@code progress}. */
    public static void arrow(GuiGraphics g, Theme t, int x, int y, float progress, boolean down) {
        int len = 22;
        int empty = Theme.withAlpha(t.textFaint(), 0xA0);
        int filled = Math.round(len * progress);
        for (int i = 0; i < len; i++) {
            int col = i < filled ? t.accent() : empty;
            int half = i >= len - 6 ? (len - i) : 2;
            if (down) {
                g.fill(x + 5 - half, y + i, x + 5 + half, y + i + 1, col);
            } else {
                g.fill(x + i, y + 5 - half, x + i + 1, y + 5 + half, col);
            }
        }
    }

    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    /** Scaled text aligned to a right/bottom corner, drawn above items (stack counts). */
    public static void cornerText(GuiGraphics g, Font font, String text, int rightX, int bottomY, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, Math.round(rightX / scale) - font.width(text), Math.round(bottomY / scale) - 8, color, true);
        g.pose().popPose();
    }
}
