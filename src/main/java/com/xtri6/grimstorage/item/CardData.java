package com.xtri6.grimstorage.item;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Item component records. ItemStack has no equals(), so each record compares its stacks by content. */
public final class CardData {
    private CardData() {}

    private static final Codec<List<ItemStack>> STACKS = ItemStack.OPTIONAL_CODEC.listOf();
    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> STACKS_STREAM =
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list());

    static List<ItemStack> sized(List<ItemStack> in, int size) {
        List<ItemStack> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(i < in.size() ? in.get(i).copy() : ItemStack.EMPTY);
        }
        return List.copyOf(out);
    }

    /** A saved 3x3 crafting recipe: the exact items in each grid slot, and what they make. */
    public record Recipe(List<ItemStack> grid, ItemStack result) {
        public static final Codec<Recipe> CODEC = RecordCodecBuilder.create(i -> i.group(
                STACKS.fieldOf("grid").forGetter(Recipe::grid),
                ItemStack.OPTIONAL_CODEC.fieldOf("result").forGetter(Recipe::result)
        ).apply(i, Recipe::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Recipe> STREAM_CODEC = StreamCodec.composite(
                STACKS_STREAM, Recipe::grid, ItemStack.OPTIONAL_STREAM_CODEC, Recipe::result, Recipe::new);

        public Recipe {
            grid = sized(grid, 9);
            result = result.copy();
        }

        public boolean valid() {
            return !result.isEmpty();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Recipe r && ItemStack.listMatches(grid, r.grid) && ItemStack.matches(result, r.result);
        }

        @Override
        public int hashCode() {
            return 31 * ItemStack.hashStackList(grid) + ItemStack.hashItemAndComponents(result) + result.getCount();
        }
    }

    /** A machine recipe: what to send in and what comes back out (counts included). */
    public record Processing(List<ItemStack> inputs, List<ItemStack> outputs) {
        public static final Codec<Processing> CODEC = RecordCodecBuilder.create(i -> i.group(
                STACKS.fieldOf("inputs").forGetter(Processing::inputs),
                STACKS.fieldOf("outputs").forGetter(Processing::outputs)
        ).apply(i, Processing::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Processing> STREAM_CODEC = StreamCodec.composite(
                STACKS_STREAM, Processing::inputs, STACKS_STREAM, Processing::outputs, Processing::new);

        public Processing {
            inputs = sized(inputs, 9);
            outputs = sized(outputs, 3);
        }

        public boolean valid() {
            return inputs.stream().anyMatch(s -> !s.isEmpty()) && outputs.stream().anyMatch(s -> !s.isEmpty());
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Processing p && ItemStack.listMatches(inputs, p.inputs) && ItemStack.listMatches(outputs, p.outputs);
        }

        @Override
        public int hashCode() {
            return 31 * ItemStack.hashStackList(inputs) + ItemStack.hashStackList(outputs);
        }
    }

    /** The item a Singularity Cell is locked to. */
    public record Locked(ItemStack stack) {
        public static final Codec<Locked> CODEC = ItemStack.SINGLE_ITEM_CODEC.xmap(Locked::new, Locked::stack);
        public static final StreamCodec<RegistryFriendlyByteBuf, Locked> STREAM_CODEC =
                ItemStack.STREAM_CODEC.map(Locked::new, Locked::stack);

        public Locked {
            stack = stack.copyWithCount(1);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Locked l && ItemStack.isSameItemSameComponents(stack, l.stack);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(stack);
        }
    }

    /** Fill level shown in a cell's tooltip. Refreshed by the drive holding the cell. */
    public record CellStats(long used, int types) {
        public static final Codec<CellStats> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("used").forGetter(CellStats::used),
                Codec.INT.fieldOf("types").forGetter(CellStats::types)
        ).apply(i, CellStats::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, CellStats> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, CellStats::used, ByteBufCodecs.VAR_INT, CellStats::types, CellStats::new);
    }
}
