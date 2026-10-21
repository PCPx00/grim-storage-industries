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

    // the access window: who owns the network and who else may use it
    private boolean accessOpen;
    private com.xtri6.grimstorage.client.ui.GrimButton accessButton;
    private com.xtri6.grimstorage.client.ui.GrimButton modeButton;
    private com.xtri6.grimstorage.client.ui.GrimButton claimButton;
    private com.xtri6.grimstorage.client.ui.GrimButton addButton;
    private com.xtri6.grimstorage.client.ui.GrimButton doneButton;
    private net.minecraft.client.gui.components.EditBox nameBox;
    private static final int AW = 180;
    private static final int AH = 196;
    private static final int TRUSTED_SHOWN = 6;

    public ControllerScreen(ControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 210, 238);
        this.inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        accessButton = addRenderableWidget(new com.xtri6.grimstorage.client.ui.GrimButton(leftPos + imageWidth - 34, topPos + 4, 15, 12,
                Component.literal("\u26bf"), b -> accessOpen = !accessOpen));
        accessButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.grimstorage.access")));
        int x = ax(), y = ay();
        modeButton = new com.xtri6.grimstorage.client.ui.GrimButton(x + 8, y + 42, AW - 16, 14, Component.empty(),
                b -> edit(Payloads.AccessEdit.TOGGLE_OPEN, ""));
        claimButton = new com.xtri6.grimstorage.client.ui.GrimButton(x + 8, y + 42, AW - 16, 14, Component.translatable("gui.grimstorage.access.claim"),
                b -> edit(Payloads.AccessEdit.CLAIM, ""), true);
        nameBox = new net.minecraft.client.gui.components.EditBox(font, x + 11, y + AH - 42, AW - 64, 10, Component.translatable("gui.grimstorage.access.name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.translatable("gui.grimstorage.access.name").withColor(t().textFaint()));
        addButton = new com.xtri6.grimstorage.client.ui.GrimButton(x + AW - 48, y + AH - 45, 40, 14, Component.translatable("gui.grimstorage.access.add"), b -> {
            if (!nameBox.getValue().isBlank()) {
                edit(Payloads.AccessEdit.TRUST, nameBox.getValue().trim());
                nameBox.setValue("");
            }
        });
        doneButton = new com.xtri6.grimstorage.client.ui.GrimButton(x + 8, y + AH - 24, AW - 16, 16, Component.translatable("gui.done"),
                b -> accessOpen = false, true);
    }

    private int ax() {
        return leftPos + (imageWidth - AW) / 2;
    }

    private int ay() {
        return topPos + 20;
    }

    private void edit(int action, String name) {
        PacketDistributor.sendToServer(new Payloads.AccessEdit(menu.containerId, action, name));
    }

    private int trustedRowY(int i) {
        return ay() + 74 + i * 12;
    }

    private void renderAccess(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Theme t = t();
        Payloads.AccessInfo a = menu.clientAccess;
        int x = ax(), y = ay();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0x80000000);
        Ui.panel(g, t, x, y, AW, AH);
        g.fill(x + 1, y + 2, x + AW - 1, y + AH - 1, Theme.withAlpha(t.panelTop(), 0xF0));
        g.pose().translate(0, 0, 10);
        g.drawString(font, Component.translatable("gui.grimstorage.access"), x + 8, y + 8, t.text(), false);
        Ui.rule(g, t, x + 8, y + 18, AW - 16);
        if (a == null) {
            g.drawString(font, Component.translatable("gui.grimstorage.controller.loading"), x + 8, y + 26, t.textDim(), false);
            g.pose().popPose();
            return;
        }
        boolean owned = !a.owner().isEmpty();
        g.drawString(font, owned ? Component.translatable("gui.grimstorage.access.owner", a.owner())
                : Component.translatable("gui.grimstorage.access.no_owner"), x + 8, y + 26, owned ? t.accent() : t.warn(), false);
        com.xtri6.grimstorage.client.ui.GrimButton top = owned ? modeButton : claimButton;
        modeButton.setMessage(Component.translatable(a.open() ? "gui.grimstorage.access.open" : "gui.grimstorage.access.private"));
        modeButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.grimstorage.access.mode.tooltip")));
        top.active = a.canManage();
        top.render(g, mouseX, mouseY, pt);
        g.drawString(font, Component.translatable("gui.grimstorage.access.trusted_list", a.trusted().size()), x + 8, y + 62, t.textDim(), false);
        if (a.trusted().isEmpty()) {
            small(g, Component.translatable("gui.grimstorage.access.none").getString(), x + 10, y + 76, t.textFaint());
        }
        for (int i = 0; i < Math.min(TRUSTED_SHOWN, a.trusted().size()); i++) {
            int ry = trustedRowY(i);
            g.fill(x + 8, ry, x + AW - 8, ry + 11, i % 2 == 0 ? t.button() : t.buttonHover());
            g.drawString(font, a.trusted().get(i), x + 11, ry + 2, t.text(), false);
            if (a.canManage()) {
                boolean hot = mouseX >= x + AW - 20 && mouseX < x + AW - 9 && mouseY >= ry && mouseY < ry + 11;
                g.drawString(font, "\u00d7", x + AW - 17, ry + 2, hot ? t.danger() : t.textFaint(), false);
            }
        }
        if (a.trusted().size() > TRUSTED_SHOWN) {
            small(g, "+" + (a.trusted().size() - TRUSTED_SHOWN), x + 10, trustedRowY(TRUSTED_SHOWN) + 1, t.textFaint());
        }
        if (a.canManage() && owned) {
            Ui.field(g, t, x + 8, y + AH - 45, AW - 60, 14, nameBox.isFocused());
            nameBox.setTextColor(t.text() & 0xFFFFFF);
            nameBox.render(g, mouseX, mouseY, pt);
            addButton.render(g, mouseX, mouseY, pt);
        } else if (owned) {
            small(g, Component.translatable("gui.grimstorage.access.owner_only").getString(), x + 10, y + AH - 40, t.textFaint());
        }
        doneButton.render(g, mouseX, mouseY, pt);
        g.pose().popPose();
    }

    private void small(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        if (accessOpen) {
            renderAccess(g, mouseX, mouseY, pt);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (accessOpen) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                accessOpen = false;
                return true;
            }
            if (nameBox.isFocused()) {
                if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                    addButton.onPress();
                    return true;
                }
                nameBox.keyPressed(key, scan, mods);
                return true;
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (accessOpen) {
            return nameBox.isFocused() && nameBox.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
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
        boolean warn = !s.problem().getString().isEmpty();
        Component state = warn
                ? s.problem()
                : Component.translatable(s.active() ? "gui.grimstorage.controller.online" : "gui.grimstorage.controller.offline");
        int dot = warn ? t.danger() : (s.active() ? t.ok() : t.danger());
        g.fill(8, 21, 12, 25, dot);
        g.drawString(font, state, 16, 19, warn ? t.danger() : (s.active() ? t.text() : t.danger()), false);
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
            // the amount sits beside the icon, the name under it; each shrinks to fit its tile so it never reaches the next one
            String amount = kCap <= 0 ? "-" : Fmt.shortAmount(k, kUsed);
            Component name = Component.translatable("gui.grimstorage.chip." + k.id());
            float as = Math.min(0.7f, (cw - 22) / (float) Math.max(1, font.width(amount)));
            float ns = Math.min(0.7f, (cw - 4) / (float) Math.max(1, font.width(name)));
            g.pose().pushPose();
            g.pose().translate(cx + 21, 86 + 3, 0);
            g.pose().scale(as, as, 1f);
            g.drawString(font, amount, 0, 0, t.text(), false);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(cx + 3, 86 + 21, 0);
            g.pose().scale(ns, ns, 1f);
            g.drawString(font, name, 0, 0, t.textFaint(), false);
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
        if (accessOpen) {
            Payloads.AccessInfo a = menu.clientAccess;
            if (a != null) {
                boolean owned = !a.owner().isEmpty();
                if (a.canManage()) {
                    if ((owned ? modeButton : claimButton).mouseClicked(mx, my, button)) {
                        return true;
                    }
                    for (int i = 0; i < Math.min(TRUSTED_SHOWN, a.trusted().size()); i++) {
                        int ry = trustedRowY(i);
                        if (mx >= ax() + AW - 20 && mx < ax() + AW - 9 && my >= ry && my < ry + 11) {
                            edit(Payloads.AccessEdit.UNTRUST, a.trusted().get(i));
                            return true;
                        }
                    }
                    if (owned) {
                        boolean inBox = nameBox.mouseClicked(mx, my, button);
                        nameBox.setFocused(inBox);
                        if (inBox || addButton.mouseClicked(mx, my, button)) {
                            return true;
                        }
                    }
                }
            }
            doneButton.mouseClicked(mx, my, button);
            if (mx < ax() || mx >= ax() + AW || my < ay() || my >= ay() + AH) {
                accessOpen = false;
            }
            return true;
        }
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
