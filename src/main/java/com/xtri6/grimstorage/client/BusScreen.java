package com.xtri6.grimstorage.client;

import java.util.ArrayList;
import java.util.List;

import com.xtri6.grimstorage.block.entity.BusBlockEntity;
import com.xtri6.grimstorage.client.ui.GrimButton;
import com.xtri6.grimstorage.client.ui.Theme;
import com.xtri6.grimstorage.menu.BlockMenus;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.storage.BulkKind;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/** Import / Export Bus: 9 filter slots, whitelist or blacklist, exact match, and a switch per resource. */
public class BusScreen extends GrimScreen<BlockMenus.Bus> {
    private GrimButton direction;
    private GrimButton mode;
    private GrimButton match;
    private final List<Toggle> toggles = new ArrayList<>();

    public BusScreen(BlockMenus.Bus menu, Inventory inv, Component title) {
        super(menu, inv, title, 222, 222);
        this.inventoryLabelY = 124;
    }

    private record Toggle(GrimButton button, int bit) {}

    private void send(int button) {
        PacketDistributor.sendToServer(new Payloads.BusSetting(menu.containerId, button));
    }

    @Override
    protected void init() {
        super.init();
        toggles.clear();
        mode = addRenderableWidget(new GrimButton(leftPos + 8, topPos + 58, 78, 14, Component.empty(), b -> send(BlockMenus.Bus.BLACKLIST)));
        match = addRenderableWidget(new GrimButton(leftPos + 90, topPos + 58, 78, 14, Component.empty(), b -> send(BlockMenus.Bus.MATCH_COMPONENTS)));
        match.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.bus.match.tooltip")));
        direction = addRenderableWidget(new GrimButton(leftPos + 8, topPos + 76, 160, 12, Component.empty(), b -> send(BlockMenus.Bus.DIRECTION)));
        direction.setTooltip(Tooltip.create(Component.translatable("gui.grimstorage.bus.direction.tooltip")));
        int x = leftPos + 8;
        addToggle(x, BusBlockEntity.ITEMS_BIT, new ItemStack(Items.CHEST), Component.translatable("gui.grimstorage.kind.items"));
        x += 22;
        for (BulkKind kind : BulkKind.values()) {
            if (!kind.available()) {
                continue;
            }
            ItemStack icon = switch (kind) {
                case FLUID -> new ItemStack(Items.WATER_BUCKET);
                case EXPERIENCE -> new ItemStack(Items.EXPERIENCE_BOTTLE);
                default -> BulkRender.icon(kind);
            };
            addToggle(x, 1 + kind.ordinal(), icon, Component.translatable("gui.grimstorage.kind." + kind.id()));
            x += 22;
        }
        refreshLabels();
    }

    private void addToggle(int x, int bit, ItemStack icon, Component name) {
        GrimButton b = new GrimButton(x, topPos + 104, 20, 20, Component.empty(), btn -> send(BlockMenus.Bus.RESOURCE + bit)) {
            @Override
            protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
                super.renderWidget(g, mouseX, mouseY, partialTick);
                Theme t = t();
                boolean on = menu.moves(bit);
                g.renderItem(icon, getX() + 2, getY() + 1);
                if (!on) {
                    g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1, 0xA0000000 | (t.panelBottom() & 0xFFFFFF));
                }
                g.fill(getX() + 3, getY() + getHeight() - 3, getX() + getWidth() - 3, getY() + getHeight() - 2,
                        on ? t.ok() : Theme.withAlpha(t.textFaint(), 0x80));
            }
        };
        b.setTooltip(Tooltip.create(Component.translatable(menu.isExport() ? "gui.grimstorage.bus.toggle.export" : "gui.grimstorage.bus.toggle.import", name)));
        toggles.add(new Toggle(addRenderableWidget(b), bit));
    }

    private void refreshLabels() {
        direction.setMessage(Component.translatable(menu.isExport() ? "gui.grimstorage.bus.direction.export" : "gui.grimstorage.bus.direction.import"));
        mode.setMessage(Component.translatable(menu.blacklist() ? "gui.grimstorage.bus.blacklist" : "gui.grimstorage.bus.whitelist"));
        mode.setTooltip(Tooltip.create(Component.translatable(menu.blacklist() ? "gui.grimstorage.bus.blacklist.tooltip"
                : "gui.grimstorage.bus.whitelist.tooltip")));
        match.setMessage(Component.translatable(menu.matchComponents() ? "gui.grimstorage.bus.exact" : "gui.grimstorage.bus.any"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshLabels();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        Theme t = t();
        com.xtri6.grimstorage.block.entity.BusLogic.Status s = menu.status();
        int color = switch (s) {
            case WORKING -> t.ok();
            case IDLE -> t.textDim();
            case FULL, NO_FILTER -> t.warn();
            default -> t.danger();
        };
        Component text = Component.translatable("gui.grimstorage.bus.status." + s.name().toLowerCase(java.util.Locale.ROOT));
        int w = font.width(text);
        int x = imageWidth - 22 - w;
        g.fill(x - 7, 6, x - 3, 10, color);
        g.drawString(font, text, x, 4, color, false);
        // section labels for the grid and the upgrade column
        hint(g, Component.translatable("gui.grimstorage.bus.label.filters"), 8, 22);
        hint(g, Component.translatable("gui.grimstorage.bus.label.moves"), 8, 92);
        hint(g, Component.translatable("gui.grimstorage.bus.label.speed"), BlockMenus.Bus.SPEED_X, 22);
        int speedCount = menu.getSlot(BlockMenus.Bus.SPEED_SLOT_INDEX).getItem().getCount();
        g.drawString(font, "x" + (1 + speedCount), BlockMenus.Bus.SPEED_X, 52, speedCount > 0 ? t.accent() : t.textDim(), false);
        if (menu.wireless()) {
            hint(g, Component.translatable("gui.grimstorage.bus.label.range"), BlockMenus.Bus.RANGE_X, 68);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (hoveredSlot instanceof net.minecraft.world.inventory.Slot slot && !slot.hasItem() && menu.getCarried().isEmpty()) {
            if (slot.index < BlockMenus.Bus.SPEED_SLOT_INDEX) {
                g.renderTooltip(font, Component.translatable("gui.grimstorage.bus.slot"), mouseX, mouseY);
            } else if (slot.index == BlockMenus.Bus.SPEED_SLOT_INDEX) {
                g.renderTooltip(font, Component.translatable("gui.grimstorage.bus.speed_slot.tooltip"), mouseX, mouseY);
            } else if (slot.index == BlockMenus.Bus.RANGE_SLOT_INDEX) {
                g.renderTooltip(font, Component.translatable("gui.grimstorage.bus.range_slot.tooltip"), mouseX, mouseY);
            }
        }
    }
}
