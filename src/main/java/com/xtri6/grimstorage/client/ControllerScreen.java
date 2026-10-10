package com.xtri6.grimstorage.client;

import java.util.List;

import com.xtri6.grimstorage.menu.ControllerMenu;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.util.Fmt;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Network overview: storage bar, counts, and the crafting job list with cancel buttons. */
public class ControllerScreen extends GrimScreen<ControllerMenu> {
    private static final int JOB_Y = 86;
    private static final int JOB_H = 20;
    private static final int JOBS_SHOWN = 4;
    private int scroll;

    public ControllerScreen(ControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 196, 186);
        this.inventoryLabelY = 10_000;
    }

    @Override
    protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
        Payloads.ControllerStatus s = menu.clientStatus;
        int x = leftPos + 8, y = topPos + 30, w = imageWidth - 16;
        g.fill(x, y, x + w, y + 8, 0xFF373737);
        if (s != null && s.capacity() > 0) {
            int fill = (int) Math.min(w, (double) s.used() / s.capacity() * w);
            g.fill(x, y, x + fill, y + 8, fill > w * 0.9 ? 0xFFE04040 : ACCENT);
        }
        g.fill(leftPos + 7, topPos + JOB_Y - 1, leftPos + imageWidth - 7, topPos + JOB_Y + JOB_H * JOBS_SHOWN + 1, 0xFF8B8B8B);
        if (s == null) {
            return;
        }
        List<Payloads.JobInfo> jobs = s.jobs();
        for (int i = 0; i < JOBS_SHOWN && i + scroll < jobs.size(); i++) {
            Payloads.JobInfo job = jobs.get(i + scroll);
            int jy = topPos + JOB_Y + i * JOB_H;
            g.fill(leftPos + 8, jy, leftPos + imageWidth - 8, jy + JOB_H - 1, i % 2 == 0 ? 0xFF9A9A9A : 0xFF929292);
            g.fill(leftPos + 8, jy + JOB_H - 3, leftPos + 8 + (int) ((imageWidth - 16) * job.progress()), jy + JOB_H - 1, ACCENT);
            boolean hover = mouseX >= leftPos + imageWidth - 22 && mouseX < leftPos + imageWidth - 10 && mouseY >= jy + 3 && mouseY < jy + 15;
            g.fill(leftPos + imageWidth - 22, jy + 3, leftPos + imageWidth - 10, jy + 15, hover ? 0xFFE04040 : 0xFF703030);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, TEXT, false);
        Payloads.ControllerStatus s = menu.clientStatus;
        if (s == null) {
            g.drawString(font, Component.translatable("gui.grimstorage.controller.loading"), 8, 18, TEXT, false);
            return;
        }
        if (!s.active()) {
            Component why = s.problem().getString().isEmpty() ? Component.translatable("gui.grimstorage.controller.offline") : s.problem();
            g.drawString(font, why, 8, 18, 0xFFB02020, false);
        } else {
            g.drawString(font, Component.translatable("gui.grimstorage.controller.online"), 8, 18, 0xFF207020, false);
        }
        g.drawString(font, Component.translatable("gui.grimstorage.controller.storage", Fmt.big(s.used()), Fmt.big(s.capacity())), 8, 41, TEXT, false);
        if (s.singular() > 0) {
            g.drawString(font, Component.translatable("gui.grimstorage.controller.singular", Fmt.big(s.singular())), 8, 51, 0xFF5A3FA0, false);
        }
        g.drawString(font, Component.translatable("gui.grimstorage.controller.counts", s.types(), s.cells(), s.drives(), s.members()), 8, 61, TEXT, false);
        g.drawString(font, Component.translatable("gui.grimstorage.controller.jobs", s.jobs().size()), 8, 75, TEXT, false);

        List<Payloads.JobInfo> jobs = s.jobs();
        if (jobs.isEmpty()) {
            g.drawString(font, Component.translatable("gui.grimstorage.controller.no_jobs"), 12, JOB_Y + 6, 0xFF505050, false);
        }
        for (int i = 0; i < JOBS_SHOWN && i + scroll < jobs.size(); i++) {
            Payloads.JobInfo job = jobs.get(i + scroll);
            int jy = JOB_Y + i * JOB_H;
            g.renderItem(job.icon(), 10, jy + 1);
            g.drawString(font, Fmt.compact(job.amount()) + "x " + job.icon().getHoverName().getString(), 30, jy + 2, 0xFF202020, false);
            g.pose().pushPose();
            g.pose().translate(30, jy + 11, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, job.status().copy().append(" - " + job.source()), 0, 0, 0xFF303030, false);
            g.pose().popPose();
            g.drawString(font, "x", imageWidth - 19, jy + 5, 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Payloads.ControllerStatus s = menu.clientStatus;
        if (s != null) {
            for (int i = 0; i < JOBS_SHOWN && i + scroll < s.jobs().size(); i++) {
                int jy = topPos + JOB_Y + i * JOB_H;
                if (mx >= leftPos + imageWidth - 22 && mx < leftPos + imageWidth - 10 && my >= jy + 3 && my < jy + 15) {
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
