package com.xtri6.grimstorage.client;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.client.ui.Theme;
import com.xtri6.grimstorage.client.ui.Ui;
import com.xtri6.grimstorage.menu.BlockMenus;
import com.xtri6.grimstorage.menu.CardMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The screens that are mostly slots plus a hint. */
public final class SimpleScreens {
    private SimpleScreens() {}

    public static class Drive extends GrimScreen<BlockMenus.Drive> {
        public Drive(BlockMenus.Drive menu, Inventory inv, Component title) {
            super(menu, inv, title, 176, 166);
        }

        @Override
        protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
            Theme t = t();
            // little status LEDs beside each cell bay
            for (int i = 0; i < 8; i++) {
                int x = leftPos + 53 + (i % 4) * 18;
                int y = topPos + 26 + (i / 4) * 18;
                boolean filled = menu.getSlot(i).hasItem();
                g.fill(x + 13, y - 3, x + 16, y - 2, filled ? t.ok() : t.textFaint());
            }
        }
    }

    public static class Assembler extends GrimScreen<BlockMenus.Assembler> {
        public Assembler(BlockMenus.Assembler menu, Inventory inv, Component title) {
            super(menu, inv, title, 176, 166);
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            Theme t = t();
            g.drawString(font, Component.translatable("gui.grimstorage.assembler.cards"), 8, 36, t.textDim(), false);
            g.drawString(font, Component.translatable("gui.grimstorage.assembler.speed"), 132, 16, t.textDim(), false);
            int ups = 0;
            for (int i = 9; i < 13; i++) {
                ups += menu.getSlot(i).hasItem() ? 1 : 0;
            }
            g.drawString(font, "x" + (1 << ups), 136, 66, ups > 0 ? t.accent() : t.textFaint(), false);
        }
    }

    public static class Processing extends GrimScreen<BlockMenus.Processing> {
        public Processing(BlockMenus.Processing menu, Inventory inv, Component title) {
            super(menu, inv, title, 176, 166);
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            for (int i = 1; i <= 4; i++) {
                hint(g, Component.translatable("gui.grimstorage.processing.hint" + i), 8, 22 + (i - 1) * 11);
            }
        }
    }

    public static class Filter extends GrimScreen<BlockMenus.Filter> {
        public Filter(BlockMenus.Filter menu, Inventory inv, Component title) {
            super(menu, inv, title, 176, 166);
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            boolean voids = Minecraft.getInstance().level != null && menu.pos != null
                    && Minecraft.getInstance().level.getBlockState(menu.pos).is(Registration.VOID_FILTER.get());
            String key = voids ? "gui.grimstorage.void.hint" : "gui.grimstorage.interface.hint";
            g.drawString(font, Component.translatable(key + "1"), 8, 22, voids ? t().purple() : t().textDim(), false);
            hint(g, Component.translatable(key + "2"), 8, 58);
        }
    }

    public static class Infuser extends GrimScreen<BlockMenus.Infuser> {
        public Infuser(BlockMenus.Infuser menu, Inventory inv, Component title) {
            super(menu, inv, title, 176, 166);
        }

        @Override
        protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
            Ui.arrow(g, t(), leftPos + 72, topPos + 38, menu.progress(), false);
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            if (menu.progress() > 0) {
                String pct = Math.round(menu.progress() * 100) + "%";
                g.drawString(font, pct, 83 - font.width(pct) / 2, 52, t().accent(), false);
            }
        }
    }

    public static class RecipeCard extends GrimScreen<CardMenus.Recipe> {
        public RecipeCard(CardMenus.Recipe menu, Inventory inv, Component title) {
            super(menu, inv, title, 176, 166);
        }

        @Override
        protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
            Ui.arrow(g, t(), leftPos + 92, topPos + 38, menu.getSlot(9).hasItem() ? 1 : 0, false);
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            hint(g, Component.translatable("gui.grimstorage.card.hint"), 64, 72);
        }
    }

    public static class ProcessingCard extends GrimScreen<CardMenus.Processing> {
        public ProcessingCard(CardMenus.Processing menu, Inventory inv, Component title) {
            super(menu, inv, title, 176, 166);
        }

        @Override
        protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
            Ui.arrow(g, t(), leftPos + 92, topPos + 38, 0, false);
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            hint(g, Component.translatable("gui.grimstorage.processing_card.hint"), 64, 72);
        }
    }
}
