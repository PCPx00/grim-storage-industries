package com.xtri6.grimstorage.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.util.Fmt;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Why an item a craft needs counts as missing, in plain words: the usual reasons are an item with different data, a
 * look-alike from another mod the recipe won't take, a chest the network may only put things into, or the player's
 * own pockets. Shown under each missing item in the craft preview and in the chat message.
 */
public final class MissingHints {
    private MissingHints() {}

    public static List<Component> explain(StorageNetwork net, ItemKey key, @Nullable Player player) {
        List<Component> out = new ArrayList<>();
        Map<ItemKey, Long> contents = net.contents();
        Item item = key.item();

        // the same item, but damaged, enchanted, renamed or otherwise different
        long other = 0;
        for (Map.Entry<ItemKey, Long> e : contents.entrySet()) {
            if (e.getKey().item() == item && !e.getKey().equals(key)) {
                other += e.getValue();
            }
        }
        if (other > 0) {
            out.add(Component.translatable("gui.grimstorage.missing.data", Fmt.big(other)));
        }

        // something else with the same common tag (another mod's copper) that this recipe doesn't take
        TagKey<Item> sharedTag = null;
        ItemKey lookAlike = null;
        long lookAlikes = 0;
        // only specific common tags (c:ingots/copper), not whole groups like c:ingots
        List<TagKey<Item>> tags = key.stack().getTags()
                .filter(t -> t.location().getNamespace().equals("c") && t.location().getPath().contains("/")).toList();
        if (!tags.isEmpty()) {
            for (Map.Entry<ItemKey, Long> e : contents.entrySet()) {
                if (e.getKey().item() == item) {
                    continue;
                }
                for (TagKey<Item> t : tags) {
                    if (e.getKey().stack().is(t)) {
                        lookAlikes += e.getValue();
                        if (lookAlike == null || e.getValue() > contents.getOrDefault(lookAlike, 0L)) {
                            lookAlike = e.getKey();
                            sharedTag = t;
                        }
                        break;
                    }
                }
            }
        }
        if (lookAlike != null) {
            out.add(Component.translatable("gui.grimstorage.missing.tag", Fmt.big(lookAlikes), lookAlike.stack().getHoverName(),
                    modName(lookAlike), "#" + sharedTag.location()));
        }

        long locked = net.lockedInLinks(key);
        if (locked > 0) {
            out.add(Component.translatable("gui.grimstorage.missing.link", Fmt.big(locked)));
        }

        if (player != null) {
            int carried = 0;
            for (ItemStack s : player.getInventory().items) {
                if (key.matches(s)) {
                    carried += s.getCount();
                }
            }
            if (carried > 0) {
                out.add(Component.translatable("gui.grimstorage.missing.carried", Fmt.big(carried)));
            }
        }

        if (out.isEmpty()) {
            out.add(Component.translatable(net.patternsFor(key).isEmpty() ? "gui.grimstorage.missing.none" : "gui.grimstorage.missing.loop"));
        }
        return out;
    }

    /** "Create" for create:copper_ingot, from the mod list if the mod is there. */
    private static String modName(ItemKey key) {
        String ns = BuiltInRegistries.ITEM.getKey(key.item()).getNamespace();
        return net.neoforged.fml.ModList.get().getModContainerById(ns).map(c -> c.getModInfo().getDisplayName()).orElse(ns);
    }
}
