package com.xtri6.grimstorage.client;

import com.xtri6.grimstorage.storage.BulkKey;

import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;

/** Mekanism chemical icons and names. Only called when Mekanism is installed. */
final class ChemicalClient {
    private ChemicalClient() {}

    static void draw(GuiGraphics g, BulkKey key, int x, int y) {
        Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.get(key.id());
        if (chemical == null || chemical.isEmptyType()) {
            return;
        }
        BulkRender.drawSprite(g, Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(chemical.getIcon()),
                0xFF000000 | chemical.getTint(), x, y);
    }

    static Component name(BulkKey key) {
        Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.get(key.id());
        return chemical == null ? Component.literal(String.valueOf(key.id())) : chemical.getTextComponent();
    }
}
