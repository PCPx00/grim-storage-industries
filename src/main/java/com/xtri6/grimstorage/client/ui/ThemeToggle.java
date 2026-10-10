package com.xtri6.grimstorage.client.ui;

import com.xtri6.grimstorage.ClientConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** The little sun/moon button in each screen's corner. Flips dark mode and saves it to the client config. */
public class ThemeToggle extends AbstractButton {
    public ThemeToggle(int x, int y) {
        super(x, y, 11, 11, Component.translatable("gui.grimstorage.theme"));
        refreshTooltip();
    }

    private void refreshTooltip() {
        setTooltip(Tooltip.create(Component.translatable(ClientConfig.dark() ? "gui.grimstorage.theme.to_light" : "gui.grimstorage.theme.to_dark")));
    }

    @Override
    public void onPress() {
        ClientConfig.setDark(!ClientConfig.dark());
        refreshTooltip();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme t = Theme.get();
        int x = getX(), y = getY();
        boolean hot = isHoveredOrFocused();
        int c = hot ? t.accent() : t.textDim();
        if (t.dark()) {
            // crescent moon
            int[][] moon = {{4, 1, 7}, {3, 2, 6}, {2, 3, 5}, {2, 4, 4}, {2, 5, 4}, {2, 6, 5}, {3, 7, 6}, {4, 8, 8}, {5, 9, 7}};
            for (int[] r : moon) {
                g.fill(x + r[0], y + r[1], x + r[2] + 1, y + r[1] + 1, c);
            }
        } else {
            // sun
            g.fill(x + 4, y + 4, x + 7, y + 7, c);
            g.fill(x + 3, y + 5, x + 8, y + 6, c);
            g.fill(x + 5, y + 3, x + 6, y + 8, c);
            int[][] rays = {{5, 0}, {5, 10}, {0, 5}, {10, 5}, {2, 2}, {8, 2}, {2, 8}, {8, 8}};
            for (int[] r : rays) {
                g.fill(x + r[0], y + r[1], x + r[0] + 1, y + r[1] + 1, c);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
