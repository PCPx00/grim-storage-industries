package com.xtri6.grimstorage.client;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.menu.BlockMenus;
import com.xtri6.grimstorage.menu.CardMenus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The screens that are mostly slots plus a hint line. */
public final class SimpleScreens {
    private SimpleScreens() {}

    public static class Drive extends GrimScreen<BlockMenus.Drive> {
        public Drive(BlockMenus.Drive menu, Inventory inv, Component title) {
            super(menu, inv, title, 176, 166);
        }

    }

    public static class Assembler extends GrimScreen<BlockMenus.Assembler> {
        public Assembler(BlockMenus.Assembler menu, Inventory inv, Component title) {
            super(menu, inv, title, 176, 166);
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            g.drawString(font, Component.translatable("gui.grimstorage.assembler.cards"), 8, 36, TEXT, false);
            g.drawString(font, Component.translatable("gui.grimstorage.assembler.speed"), 128, 16, TEXT, false);
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
                g.drawString(font, Component.translatable("gui.grimstorage.processing.hint" + i), 8, 18 + (i - 1) * 11, 0xFF707070, false);
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
            g.drawString(font, Component.translatable(key + "1"), 8, 18, 0xFF707070, false);
            g.drawString(font, Component.translatable(key + "2"), 8, 58, 0xFF707070, false);
        }
    }

    public static class Infuser extends GrimScreen<BlockMenus.Infuser> {
        public Infuser(BlockMenus.Infuser menu, Inventory inv, Component title) {
            super(menu, inv, title, 176, 166);
        }

        @Override
        protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
            arrow(g, leftPos + 72, topPos + 38, menu.progress());
        }
    }

    public static class RecipeCard extends GrimScreen<CardMenus.Recipe> {
        public RecipeCard(CardMenus.Recipe menu, Inventory inv, Component title) {
            super(menu, inv, title, 176, 166);
        }

        @Override
        protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
            arrow(g, leftPos + 92, topPos + 38, 0);
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            g.drawString(font, Component.translatable("gui.grimstorage.card.hint"), 64, 72, 0xFF707070, false);
        }
    }

    public static class ProcessingCard extends GrimScreen<CardMenus.Processing> {
        public ProcessingCard(CardMenus.Processing menu, Inventory inv, Component title) {
            super(menu, inv, title, 176, 166);
        }

        @Override
        protected void drawExtraBg(GuiGraphics g, int mouseX, int mouseY) {
            arrow(g, leftPos + 92, topPos + 38, 0);
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            g.drawString(font, Component.translatable("gui.grimstorage.processing_card.hint"), 64, 72, 0xFF707070, false);
        }
    }
}
