package com.xtri6.grimstorage.guide;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

/** The guide book's content model and JSON format. Shared by the screen and the dev checks. */
public final class GuideData {
    public record Page(String title, String text, List<ResourceLocation> items, @Nullable ResourceLocation recipe) {}

    public record Chapter(String id, String title, ResourceLocation icon, List<Page> pages) {}

    private GuideData() {}

    /** Shared with the dev checks, which validate every item and recipe the guide mentions. */
    public static List<Chapter> parse(JsonObject root) {
        List<Chapter> out = new ArrayList<>();
        for (JsonElement ce : root.getAsJsonArray("chapters")) {
            JsonObject c = ce.getAsJsonObject();
            List<Page> pages = new ArrayList<>();
            for (JsonElement pe : c.getAsJsonArray("pages")) {
                JsonObject p = pe.getAsJsonObject();
                List<ResourceLocation> items = new ArrayList<>();
                if (p.has("items")) {
                    JsonArray arr = p.getAsJsonArray("items");
                    for (JsonElement ie : arr) {
                        ResourceLocation rl = ResourceLocation.tryParse(ie.getAsString());
                        if (rl != null) {
                            items.add(rl);
                        }
                    }
                }
                String text = p.has("text") ? joinText(p.get("text")) : "";
                ResourceLocation recipe = p.has("recipe") ? ResourceLocation.tryParse(p.get("recipe").getAsString()) : null;
                pages.add(new Page(p.has("title") ? p.get("title").getAsString() : "", text, items, recipe));
            }
            out.add(new Chapter(c.get("id").getAsString(), c.get("title").getAsString(),
                    ResourceLocation.parse(c.get("icon").getAsString()), pages));
        }
        return out;
    }

    /** Text may be one string or an array of paragraphs. */
    private static String joinText(JsonElement e) {
        if (e.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement p : e.getAsJsonArray()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(p.getAsString());
            }
            return sb.toString();
        }
        return e.getAsString();
    }
}
