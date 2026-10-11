package com.xtri6.grimstorage.client;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.storage.BulkKey;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.CellTier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** Draws and names stored fluids, chemicals, energy and Source in the terminal. */
public final class BulkRender {
    private BulkRender() {}

    /** The copper cell of a kind, used as its tab icon and for the single-pool kinds. */
    public static ItemStack icon(BulkKind kind) {
        return new ItemStack(Registration.BULK_CELLS.get(kind).get(CellTier.COPPER).get());
    }

    public static void draw(GuiGraphics g, BulkKey key, int x, int y) {
        switch (key.kind()) {
            case FLUID -> drawSprite(g, fluidSprite(key.fluid()), IClientFluidTypeExtensions.of(key.fluid().getFluid()).getTintColor(key.fluid()), x, y);
            case CHEMICAL -> {
                if (ModList.get().isLoaded("mekanism")) {
                    ChemicalClient.draw(g, key, x, y);
                }
            }
            default -> g.renderItem(icon(key.kind()), x, y);
        }
    }

    public static Component name(BulkKey key) {
        return switch (key.kind()) {
            case FLUID -> key.fluid().getHoverName();
            case CHEMICAL -> ModList.get().isLoaded("mekanism") ? ChemicalClient.name(key) : Component.literal(String.valueOf(key.id()));
            case ENERGY -> Component.translatable("gui.grimstorage.kind.energy");
            case SOURCE -> Component.translatable("gui.grimstorage.kind.source");
            case EXPERIENCE -> Component.translatable("gui.grimstorage.kind.experience");
        };
    }

    private static TextureAtlasSprite fluidSprite(FluidStack stack) {
        ResourceLocation tex = IClientFluidTypeExtensions.of(stack.getFluid()).getStillTexture(stack);
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(tex);
    }

    static void drawSprite(GuiGraphics g, TextureAtlasSprite sprite, int tint, int x, int y) {
        float a = ((tint >>> 24) & 0xFF) / 255f;
        g.setColor(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f, a == 0 ? 1f : a);
        g.blit(x, y, 0, 16, 16, sprite);
        g.setColor(1f, 1f, 1f, 1f);
    }
}
