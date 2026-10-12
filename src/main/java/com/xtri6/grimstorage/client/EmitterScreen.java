package com.xtri6.grimstorage.client;

import com.xtri6.grimstorage.block.entity.RedstoneEmitterBlockEntity;
import com.xtri6.grimstorage.client.ui.GrimButton;
import com.xtri6.grimstorage.client.ui.Theme;
import com.xtri6.grimstorage.client.ui.Ui;
import com.xtri6.grimstorage.menu.BlockMenus;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.util.Fmt;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/** Redstone Emitter: pick an item, a rule and an amount. */
public class EmitterScreen extends GrimScreen<BlockMenus.Emitter> {
    private static final String[] MODES = {"gui.grimstorage.emitter.at_least", "gui.grimstorage.emitter.below", "gui.grimstorage.emitter.craft"};
    private GrimButton mode;
    private EditBox amount;
    private int shown = -1;

    public EmitterScreen(BlockMenus.Emitter menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 166);
    }

    @Override
    protected void init() {
        super.init();
        mode = addRenderableWidget(new GrimButton(leftPos + 50, topPos + 22, 118, 14, Component.empty(), b -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BlockMenus.Emitter.MODE);
            }
        }));
        mode.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.emitter.mode.tooltip")));
        amount = new EditBox(font, leftPos + 76, topPos + 44, 52, 9, Component.translatable("gui.grimstorage.stock.amount"));
        amount.setMaxLength(10);
        amount.setBordered(false);
        amount.setFilter(s -> s.isEmpty() || s.matches("\\d{0,10}"));
        addRenderableWidget(amount);
        addRenderableWidget(new GrimButton(leftPos + 136, topPos + 41, 32, 14, Component.translatable("gui.grimstorage.stock.set"), b -> apply(), true));
    }

    private void apply() {
        long v;
        try {
            v = amount.getValue().isEmpty() ? 0 : Long.parseLong(amount.getValue());
        } catch (NumberFormatException e) {
            v = 0;
        }
        PacketDistributor.sendToServer(new Payloads.SetStockTarget(menu.containerId, 0, (int) Math.min(RedstoneEmitterBlockEntity.MAX, v)));
        amount.setFocused(false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        mode.setMessage(Component.translatable(MODES[Math.min(2, menu.mode())]));
        amount.setTextColor(t().text() & 0xFFFFFF);
        if (menu.amount() != shown && !amount.isFocused()) {
            shown = menu.amount();
            amount.setValue(Integer.toString(shown));
        }
    }

    @Override
    protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
        Theme t = t();
        Ui.field(g, t, leftPos + 72, topPos + 41, 60, 14, amount.isFocused());
        // the signal lamp
        int lx = leftPos + 8, ly = topPos + 44;
        boolean craft = menu.mode() == RedstoneEmitterBlockEntity.CRAFT;
        int lamp = craft ? t.accentDim() : menu.emitting() ? 0xFFFF3030 : 0xFF401010;
        g.fill(lx, ly, lx + 14, ly + 10, t.border());
        g.fill(lx + 1, ly + 1, lx + 13, ly + 9, lamp);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        Theme t = t();
        boolean craft = menu.mode() == RedstoneEmitterBlockEntity.CRAFT;
        String now = menu.stored() < 0 ? Component.translatable("gui.grimstorage.emitter.pick").getString()
                : Component.translatable("gui.grimstorage.emitter.now", Fmt.compact(menu.stored())).getString();
        g.drawString(font, now, 8, 60, t.textDim(), false);
        Component state = craft ? Component.translatable("gui.grimstorage.emitter.craft_hint")
                : Component.translatable(menu.emitting() ? "gui.grimstorage.emitter.on" : "gui.grimstorage.emitter.off");
        // under the lamp, small
        g.pose().pushPose();
        g.pose().translate(26, 44, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, state, 0, 0, craft ? t.accent() : menu.emitting() ? 0xFFFF5050 : t.textFaint(), false);
        g.pose().popPose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (amount.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                apply();
                return true;
            }
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                amount.keyPressed(key, scan, mods);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }
}
