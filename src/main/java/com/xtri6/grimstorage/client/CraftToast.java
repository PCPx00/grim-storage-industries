package com.xtri6.grimstorage.client;

import com.xtri6.grimstorage.ClientConfig;
import com.xtri6.grimstorage.client.ui.Theme;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.util.Fmt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** "Crafting done: 64x Iron Ingot" (or "stuck: waiting for coal") in the corner, wherever you are. */
public final class CraftToast implements Toast {
    private static final long SHOW_FOR = 5000;
    private final ItemStack icon;
    private final Component title;
    private final Component line;
    private final boolean done;

    private CraftToast(Payloads.CraftAlert a) {
        this.icon = a.icon();
        this.done = a.done();
        this.title = Component.translatable(a.done() ? "toast.grimstorage.craft.done" : "toast.grimstorage.craft.stuck");
        Component what = Component.literal(Fmt.compact(a.amount()) + "x ").append(a.icon().getHoverName());
        this.line = a.done() ? what : a.detail();
    }

    public static void show(Payloads.CraftAlert alert) {
        if (ClientConfig.craftAlerts()) {
            Minecraft.getInstance().getToasts().addToast(new CraftToast(alert));
        }
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
        Theme t = Theme.get();
        Font font = Minecraft.getInstance().font;
        int w = width(), h = height();
        g.fill(0, 0, w, h, t.border());
        g.fill(1, 1, w - 1, h - 1, 0xF0000000 | (t.panelTop() & 0xFFFFFF));
        g.fill(1, 1, 3, h - 1, done ? t.ok() : t.warn());
        g.renderItem(icon, 8, 8);
        g.drawString(font, title, 30, 7, done ? t.ok() : t.warn(), false);
        g.drawString(font, font.plainSubstrByWidth(line.getString(), w - 36), 30, 18, t.text(), false);
        return time >= SHOW_FOR ? Visibility.HIDE : Visibility.SHOW;
    }
}
