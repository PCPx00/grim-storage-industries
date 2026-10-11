package com.xtri6.grimstorage.client;

import java.util.List;

import com.xtri6.grimstorage.client.ui.Theme;
import com.xtri6.grimstorage.client.ui.Ui;
import com.xtri6.grimstorage.menu.ControllerMenu;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.util.Fmt;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Network overview: status, a storage gauge, stat chips and the crafting job list with cancel buttons. */
public class ControllerScreen extends GrimScreen<ControllerMenu> {
    private static final int JOB_Y = 140;
    private static final int JOB_H = 22;
    private static final int JOBS_SHOWN = 4;
    private int scroll;

    public ControllerScreen(ControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 210, 238);
        this.inventoryLabelY = 10_000;
    }

    private boolean onCancel(int i, double mx, double my) {
        int jy = topPos + JOB_Y + i * JOB_H;
        return mx >= leftPos + imageWidth - 24 && mx < leftPos + imageWidth - 12 && my >= jy + 5 && my < jy + 17;
    }

    private void chip(GuiGraphics g, Theme t, int x, int y, int w, String value, Component label) {
        g.fill(x, y, x + w, y + 24, t.slot());
        g.fill(x, y, x + 2, y + 24, t.accentDim());
        g.drawString(font, value, x + 6, y + 3, t.text(), false);
        g.pose().pushPose();
        g.pose().translate(x + 6, y + 14, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, label, 0, 0, t.textFaint(), false);
        g.pose().popPose();
    }

    @Override
    protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
        Theme t = t();
        Payloads.ControllerStatus s = menu.clientStatus;
        int x = leftPos + 8, w = imageWidth - 16;
        float fill = s == null || s.capacity() <= 0 ? 0 : (float) s.used() / s.capacity();
        int color = fill > 0.95f ? t.danger() : fill > 0.8f ? t.warn() : t.accent();
        Ui.bar(g, t, x, topPos + 32, w, 8, fill, color);
        if (s != null) {
            int cw = (w - 9) / 4;
            chip(g, t, x, topPos + 56, cw, Fmt.compact(s.types()), Component.translatable("gui.grimstorage.chip.types"));
            chip(g, t, x + (cw + 3), topPos + 56, cw, Integer.toString(s.cells()), Component.translatable("gui.grimstorage.chip.cells"));
            chip(g, t, x + 2 * (cw + 3), topPos + 56, cw, Integer.toString(s.drives()), Component.translatable("gui.grimstorage.chip.drives"));
            chip(g, t, x + 3 * (cw + 3), topPos + 56, cw, Integer.toString(s.members()), Component.translatable("gui.grimstorage.chip.blocks"));
        }
        // fluids / chemicals / energy / Source
        if (s != null) {
            int kinds = 0;
            for (BulkKind k : BulkKind.values()) {
                kinds += k.available() ? 1 : 0;
            }
            int cw = (w - 3 * (kinds - 1)) / Math.max(1, kinds);
            int i = 0;
            for (BulkKind k : BulkKind.values()) {
                if (!k.available()) {
                    continue;
                }
                int cx = x + i * (cw + 3);
                int cy = topPos + 86;
                long used = s.bulkStats().length > k.ordinal() * 2 + 1 ? s.bulkStats()[k.ordinal() * 2] : 0;
                long cap = s.bulkStats().length > k.ordinal() * 2 + 1 ? s.bulkStats()[k.ordinal() * 2 + 1] : 0;
                g.fill(cx, cy, cx + cw, cy + 36, t.slot());
                g.renderItem(BulkRender.icon(k), cx + 3, cy + 3);
                int kc = switch (k) {
                    case FLUID -> 0xFF3F8FFF;
                    case CHEMICAL -> 0xFF8FE04A;
                    case ENERGY -> 0xFFFF5A4A;
                    case SOURCE -> t.purple();
                    case EXPERIENCE -> 0xFF7FE03A;
                };
                Ui.bar(g, t, cx + 3, cy + 28, cw - 6, 5, cap <= 0 ? 0 : (float) used / cap, kc);
                i++;
            }
        }
        g.fill(x, topPos + JOB_Y - 1, x + w, topPos + JOB_Y + JOB_H * JOBS_SHOWN + 1, t.slot());
        if (s == null) {
            return;
        }
        List<Payloads.JobInfo> jobs = s.jobs();
        for (int i = 0; i < JOBS_SHOWN && i + scroll < jobs.size(); i++) {
            Payloads.JobInfo job = jobs.get(i + scroll);
            int jy = topPos + JOB_Y + i * JOB_H;
            g.fill(x + 1, jy + 1, x + w - 1, jy + JOB_H - 1, i % 2 == 0 ? t.button() : t.buttonHover());
            Ui.bar(g, t, x + 26, jy + JOB_H - 5, w - 56, 3, job.progress(), t.accent());
            boolean hover = onCancel(i, mouseX, mouseY);
            int cx = leftPos + imageWidth - 24, cy = jy + 5;
            g.fill(cx, cy, cx + 12, cy + 12, hover ? t.danger() : Theme.withAlpha(t.danger(), 0x60));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        Theme t = t();
        Payloads.ControllerStatus s = menu.clientStatus;
        if (s == null) {
            g.drawString(font, Component.translatable("gui.grimstorage.controller.loading"), 8, 20, t.textDim(), false);
            return;
        }
        Component state = !s.active()
                ? (s.problem().getString().isEmpty() ? Component.translatable("gui.grimstorage.controller.offline") : s.problem())
                : Component.translatable("gui.grimstorage.controller.online");
        int dot = s.active() ? t.ok() : t.danger();
        g.fill(8, 21, 12, 25, dot);
        g.drawString(font, state, 16, 19, s.active() ? t.text() : t.danger(), false);
        String used = Fmt.big(s.used()) + " / " + Fmt.big(s.capacity());
        g.drawString(font, used, 8, 43, t.textDim(), false);
        if (s.singular() > 0) {
            String sing = "+" + Fmt.compact(s.singular());
            g.drawString(font, sing, imageWidth - 8 - font.width(sing), 43, t.purple(), false);
        }
        int kinds = 0;
        for (BulkKind k : BulkKind.values()) {
            kinds += k.available() ? 1 : 0;
        }
        int cw = (imageWidth - 16 - 3 * (kinds - 1)) / Math.max(1, kinds);
        int chip = 0;
        for (BulkKind k : BulkKind.values()) {
            if (!k.available()) {
                continue;
            }
            int cx = 8 + chip * (cw + 3);
            long kUsed = s.bulkStats().length > k.ordinal() * 2 + 1 ? s.bulkStats()[k.ordinal() * 2] : 0;
            long kCap = s.bulkStats().length > k.ordinal() * 2 + 1 ? s.bulkStats()[k.ordinal() * 2 + 1] : 0;
            g.pose().pushPose();
            g.pose().translate(cx + 21, 86 + 5, 0);
            g.pose().scale(0.7f, 0.7f, 1f);
            g.drawString(font, kCap <= 0 ? "-" : Fmt.shortAmount(k, kUsed), 0, 0, t.text(), false);
            g.drawString(font, Component.translatable("gui.grimstorage.chip." + k.id()), 0, 10, t.textFaint(), false);
            g.pose().popPose();
            chip++;
        }
        g.drawString(font, Component.translatable("gui.grimstorage.controller.jobs", s.jobs().size()), 8, JOB_Y - 11, t.textDim(), false);
        List<Payloads.JobInfo> jobs = s.jobs();
        if (jobs.isEmpty()) {
            g.drawString(font, Component.translatable("gui.grimstorage.controller.no_jobs"), 14, JOB_Y + 7, t.textFaint(), false);
        }
        for (int i = 0; i < JOBS_SHOWN && i + scroll < jobs.size(); i++) {
            Payloads.JobInfo job = jobs.get(i + scroll);
            int jy = JOB_Y + i * JOB_H;
            g.renderItem(job.icon(), 11, jy + 3);
            g.drawString(font, font.plainSubstrByWidth(Fmt.compact(job.amount()) + "x " + job.icon().getHoverName().getString(), imageWidth - 64),
                    32, jy + 3, t.text(), false);
            g.pose().pushPose();
            g.pose().translate(32, jy + 12, 0);
            g.pose().scale(0.7f, 0.7f, 1f);
            g.drawString(font, job.status().copy().append(" · " + job.source()), 0, 0, t.textDim(), false);
            g.pose().popPose();
            g.drawString(font, "x", imageWidth - 21, jy + 7, 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Payloads.ControllerStatus s = menu.clientStatus;
        if (s != null) {
            for (int i = 0; i < JOBS_SHOWN && i + scroll < s.jobs().size(); i++) {
                if (onCancel(i, mx, my)) {
                    PacketDistributor.sendToServer(new Payloads.CancelJob(menu.containerId, s.jobs().get(i + scroll).id()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        Payloads.ControllerStatus s = menu.clientStatus;
        int max = s == null ? 0 : Math.max(0, s.jobs().size() - JOBS_SHOWN);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(sy)));
        return true;
    }
}
