package com.xtri6.grimstorage.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xtri6.grimstorage.GrimStorage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which recipe types each machine runs, so a Machine Bus knows what a machine can make. Read from datapacks:
 * {@code data/<namespace>/grimstorage_machines/<any>.json}, each with {@code "machines"} (block ids or {@code #tags})
 * and {@code "recipe_types"}. The mod ships files for the big tech and magic mods; pack makers can add more.
 * <p>
 * A machine with no entry still works when its name matches one of its mod's recipe types (a "crusher" runs
 * "crushing", a "sag_mill" runs "sag_milling", an "osmium_compressor" runs "compressing", and so on).
 */
public final class MachineRecipes extends SimpleJsonResourceReloadListener {
    public static final MachineRecipes INSTANCE = new MachineRecipes();

    private static final String[] PREFIXES = {"machine_", "basic_", "advanced_", "elite_", "ultimate_", "electric_", "powered_",
            "steam_", "bronze_", "steel_", "industrial_", "creative_"};
    private static final String[] SUFFIXES = {"_factory", "_machine", "_block", "_controller", "_chamber", "_station"};
    private static final String[] ENDINGS = {"ing", "ator", "er", "or", "ery", "y"};

    private final Map<ResourceLocation, Set<ResourceLocation>> byBlock = new HashMap<>();
    private final Map<TagKey<Block>, Set<ResourceLocation>> byTag = new HashMap<>();

    private MachineRecipes() {
        super(new com.google.gson.Gson(), "grimstorage_machines");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        byBlock.clear();
        byTag.clear();
        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
            try {
                JsonElement root = file.getValue();
                List<JsonObject> entries = new ArrayList<>();
                if (root.isJsonArray()) {
                    root.getAsJsonArray().forEach(e -> entries.add(e.getAsJsonObject()));
                } else if (root.getAsJsonObject().has("entries")) {
                    GsonHelper.getAsJsonArray(root.getAsJsonObject(), "entries").forEach(e -> entries.add(e.getAsJsonObject()));
                } else {
                    entries.add(root.getAsJsonObject());
                }
                for (JsonObject entry : entries) {
                    Set<ResourceLocation> types = new LinkedHashSet<>();
                    GsonHelper.getAsJsonArray(entry, "recipe_types").forEach(t -> types.add(ResourceLocation.parse(t.getAsString())));
                    for (JsonElement m : GsonHelper.getAsJsonArray(entry, "machines")) {
                        String id = m.getAsString();
                        if (id.startsWith("#")) {
                            byTag.computeIfAbsent(TagKey.create(Registries.BLOCK, ResourceLocation.parse(id.substring(1))), k -> new LinkedHashSet<>())
                                    .addAll(types);
                        } else {
                            byBlock.computeIfAbsent(ResourceLocation.parse(id), k -> new LinkedHashSet<>()).addAll(types);
                        }
                    }
                }
            } catch (RuntimeException e) {
                GrimStorage.LOGGER.warn("Bad Machine Bus recipe file {}: {}", file.getKey(), e.getMessage());
            }
        }
        GrimStorage.LOGGER.debug("Machine Bus knows {} machines and {} machine tags", byBlock.size(), byTag.size());
        NetworkManager.markDirty();
    }

    /** Recipe types the block runs: from the datapack files, or failing that, by matching names. */
    public List<RecipeType<?>> typesFor(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        Set<ResourceLocation> ids = new LinkedHashSet<>(byBlock.getOrDefault(id, Set.of()));
        for (Map.Entry<TagKey<Block>, Set<ResourceLocation>> e : byTag.entrySet()) {
            if (state.is(e.getKey())) {
                ids.addAll(e.getValue());
            }
        }
        List<RecipeType<?>> out = new ArrayList<>();
        for (ResourceLocation t : ids) {
            BuiltInRegistries.RECIPE_TYPE.getOptional(t).ifPresent(out::add);
        }
        if (out.isEmpty() && !id.getNamespace().equals("minecraft") && id.getPath().endsWith("furnace")) {
            // any mod's furnace ("iron_furnace", "gold_furnace", ...) smelts, and a blast furnace blasts
            ResourceLocation t = id.getPath().contains("blast") ? ResourceLocation.withDefaultNamespace("blasting")
                    : ResourceLocation.withDefaultNamespace("smelting");
            BuiltInRegistries.RECIPE_TYPE.getOptional(t).ifPresent(out::add);
        }
        if (out.isEmpty() && !id.getNamespace().equals("minecraft")) {
            String stem = stem(id.getPath());
            if (stem.length() >= 4) {
                for (ResourceLocation t : BuiltInRegistries.RECIPE_TYPE.keySet()) {
                    String ts = stem(t.getPath());
                    // "crusher" runs "crushing"; "osmium_compressor" runs "compressing"
                    if (t.getNamespace().equals(id.getNamespace()) && (ts.equals(stem) || ts.length() >= 5 && stem.endsWith("_" + ts))) {
                        out.add(BuiltInRegistries.RECIPE_TYPE.get(t));
                    }
                }
            }
        }
        return out;
    }

    /** "basic_crushing_factory" and "crusher" both become "crush". */
    public static String stem(String path) {
        String s = path;
        for (String p : PREFIXES) {
            if (s.startsWith(p)) {
                s = s.substring(p.length());
            }
        }
        for (String p : SUFFIXES) {
            if (s.endsWith(p)) {
                s = s.substring(0, s.length() - p.length());
            }
        }
        for (String e : ENDINGS) {
            if (s.length() > e.length() + 3 && s.endsWith(e)) {
                s = s.substring(0, s.length() - e.length());
                break;
            }
        }
        return s;
    }
}
