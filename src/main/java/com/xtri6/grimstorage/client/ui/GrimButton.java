package com.xtri6.grimstorage.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A flat, themed button. Accent outline on hover; optional "primary" style filled with the accent colour. */
public class GrimButton extends Button {
    private final boolean primary;

    public GrimButton(int x, int y, int w, int h, Component label, OnPress onPress, boolean primary) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
        this.primary = primary;
    }

    public GrimButton(int x, int y, int w, int h, Component label, OnPress onPress) {
        this(x, y, w, h, label, onPress, false);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = Theme.get();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hot = isHoveredOrFocused() && active;
        int fill = primary ? (hot ? t.accent() : t.accentDim()) : (hot ? t.buttonHover() : t.button());
        int edge = hot ? t.accent() : t.border();
        g.fill(x + 1, y, x + w - 1, y + h, edge);
        g.fill(x, y + 1, x + w, y + h - 1, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, Theme.withAlpha(0xFFFFFF, primary ? 0x40 : 0x18));
        Font font = Minecraft.getInstance().font;
        int color = !active ? t.textFaint() : primary ? (t.dark() ? 0xFF061016 : 0xFFFFFFFF) : t.text();
        int tw = font.width(getMessage());
        g.drawString(font, getMessage(), x + (w - tw) / 2, y + (h - 8) / 2 + 1, color, false);
    }
}
