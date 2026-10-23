package com.xtri6.grimstorage.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.xtri6.grimstorage.GrimStorage;
import com.xtri6.grimstorage.compat.jei.GrimJeiPlugin;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * {@code /grimstorage_export_machines}: reads which machines JEI shows each recipe type on (its "catalysts") and writes
 * them as a Machine Bus file. Pack makers put that file in a datapack, so every server's Machine Buses learn what each
 * machine can make, for every mod, with no per-mod rules. Run it on a client with every mod and JEI installed.
 */
public final class MachineExport {
    private static final ResourceLocation CRAFTING = ResourceLocation.withDefaultNamespace("crafting");

    private MachineExport() {}

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("grimstorage_export_machines").executes(MachineExport::export));
    }

    private static int export(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        if (!ModList.get().isLoaded("jei") || GrimJeiPlugin.runtime == null) {
            src.sendFailure(Component.literal("Export needs JEI running: install JEI and wait for it to load, then run this again."));
            return 0;
        }
        // machine block id -> recipe type ids it is a catalyst for
        Map<ResourceLocation, TreeSet<String>> byBlock = new TreeMap<>();
        var jei = GrimJeiPlugin.runtime.getRecipeManager();
        jei.createRecipeCategoryLookup().includeHidden().get().forEach(category -> {
            ResourceLocation type = category.getRecipeType().getUid();
            // only vanilla recipe types can be read by the Machine Bus; a crafting grid is not a machine
            if (!BuiltInRegistries.RECIPE_TYPE.containsKey(type) || type.equals(CRAFTING)) {
                return;
            }
            jei.createRecipeCatalystLookup(category.getRecipeType()).includeHidden().getItemStack().forEach(stack -> {
                if (stack.getItem() instanceof BlockItem blockItem) {
                    ResourceLocation block = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
                    byBlock.computeIfAbsent(block, k -> new TreeSet<>()).add(type.toString());
                }
            });
        });
        JsonArray entries = new JsonArray();
        int links = 0;
        for (Map.Entry<ResourceLocation, TreeSet<String>> e : byBlock.entrySet()) {
            JsonObject entry = new JsonObject();
            JsonArray machines = new JsonArray();
            machines.add(e.getKey().toString());
            entry.add("machines", machines);
            JsonArray types = new JsonArray();
            e.getValue().forEach(types::add);
            entry.add("recipe_types", types);
            entries.add(entry);
            links += e.getValue().size();
        }
        JsonObject root = new JsonObject();
        root.add("entries", entries);
        Path out = Minecraft.getInstance().gameDirectory.toPath().resolve("grimstorage_export").resolve("grimstorage_machines.json");
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            GrimStorage.LOGGER.warn("Could not write the machine export", ex);
            src.sendFailure(Component.literal("Could not write the machine export: " + ex.getMessage()));
            return 0;
        }
        final int machines = byBlock.size();
        final int recipeLinks = links;
        final Path written = out;
        src.sendSuccess(() -> Component.literal("Wrote " + machines + " machines (" + recipeLinks + " recipe links) to " + written
                + ". Put it in a datapack at data/grimstorage/grimstorage_machines/ to use it."), false);
        return byBlock.size();
    }
}
