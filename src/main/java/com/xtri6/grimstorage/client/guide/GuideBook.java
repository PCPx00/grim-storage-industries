package com.xtri6.grimstorage.client.guide;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonParser;
import com.xtri6.grimstorage.GrimStorage;
import com.xtri6.grimstorage.guide.GuideData;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Loads the guide's text from assets/grimstorage/guide/&lt;language&gt;.json (falling back to en_us), so it can be
 * edited, translated or replaced by a resource pack without touching code.
 */
public final class GuideBook {
    private GuideBook() {}

    public static List<GuideData.Chapter> load() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        List<GuideData.Chapter> chapters = read(lang);
        return chapters.isEmpty() && !"en_us".equals(lang) ? read("en_us") : chapters;
    }

    private static List<GuideData.Chapter> read(String lang) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(GrimStorage.MODID, "guide/" + lang + ".json");
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(file);
        if (res.isEmpty()) {
            return List.of();
        }
        try (Reader reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
            return GuideData.parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            GrimStorage.LOGGER.error("Could not read the guide book from {}", file, e);
            return List.of();
        }
    }
}
