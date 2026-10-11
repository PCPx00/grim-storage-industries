package com.xtri6.grimstorage.client;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.client.ui.GrimButton;
import com.xtri6.grimstorage.client.ui.Theme;
import com.xtri6.grimstorage.client.ui.Ui;
import com.xtri6.grimstorage.menu.BlockMenus;
import com.xtri6.grimstorage.menu.CardMenus;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.storage.BulkKind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

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
        private boolean voids;

        public Filter(BlockMenus.Filter menu, Inventory inv, Component title) {
            super(menu, inv, title, 176, 166);
        }

        @Override
        protected void init() {
            super.init();
            voids = Minecraft.getInstance().level != null && menu.pos != null
                    && Minecraft.getInstance().level.getBlockState(menu.pos).is(Registration.VOID_FILTER.get());
            if (voids) {
                return;
            }
            // Export switches: may pipes pull fluids / chemicals / energy / Source out of this interface?
            int shown = 0;
            for (BulkKind kind : BulkKind.values()) {
                shown += kind.available() ? 1 : 0;
            }
            int step = Math.min(35, 136 / Math.max(1, shown));
            int x = leftPos + 32;
            for (BulkKind kind : BulkKind.values()) {
                if (!kind.available()) {
                    continue;
                }
                GrimButton b = new GrimButton(x, topPos + 55, step - 2, 13, Component.translatable("gui.grimstorage.export." + kind.id()),
                        btn -> PacketDistributor.sendToServer(new Payloads.ToggleExport(menu.containerId, kind.ordinal()))) {
                    @Override
                    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
                        super.renderWidget(g, mouseX, mouseY, partialTick);
                        Theme t = t();
                        g.fill(getX() + 2, getY() + getHeight() - 3, getX() + getWidth() - 2, getY() + getHeight() - 2,
                                menu.exports(kind) ? t.ok() : Theme.withAlpha(t.textFaint(), 0x80));
                    }
                };
                b.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.export.tooltip",
                        Component.translatable("gui.grimstorage.kind." + kind.id()))));
                addRenderableWidget(b);
                x += step;
            }
        }

        @Override
        protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
            super.renderLabels(g, mouseX, mouseY);
            String key = voids ? "gui.grimstorage.void.hint" : "gui.grimstorage.interface.hint";
            g.drawString(font, Component.translatable(key + "1"), 8, 22, voids ? t().purple() : t().textDim(), false);
            if (voids) {
                hint(g, Component.translatable(key + "2"), 8, 58);
            } else {
                hint(g, Component.translatable("gui.grimstorage.export.label"), 8, 58);
            }
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
