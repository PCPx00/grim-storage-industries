package com.xtri6.grimstorage.recipe;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xtri6.grimstorage.Registration;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Soul Infuser recipe: up to 3 ingredients in any slot order, one result, a time in ticks. */
public record InfusingRecipe(List<Ingredient> ingredients, ItemStack result, int time) implements Recipe<InfusingRecipe.Input> {
    public static final int SLOTS = 3;

    public record Input(List<ItemStack> items) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return items.get(index);
        }

        @Override
        public int size() {
            return items.size();
        }
    }

    /** Which input slot each ingredient uses, or null if the inputs do not match exactly. */
    @Nullable
    public int[] assign(Input input) {
        int filled = 0;
        for (ItemStack s : input.items()) {
            if (!s.isEmpty()) {
                filled++;
            }
        }
        if (filled != ingredients.size()) {
            return null;
        }
        int[] slots = new int[ingredients.size()];
        return assign(input, 0, slots, new boolean[input.size()]) ? slots : null;
    }

    private boolean assign(Input input, int idx, int[] slots, boolean[] used) {
        if (idx == ingredients.size()) {
            return true;
        }
        for (int s = 0; s < input.size(); s++) {
            if (!used[s] && ingredients.get(idx).test(input.getItem(s))) {
                used[s] = true;
                slots[idx] = s;
                if (assign(input, idx + 1, slots, used)) {
                    return true;
                }
                used[s] = false;
            }
        }
        return false;
    }

    @Override
    public boolean matches(Input input, Level level) {
        return assign(input) != null;
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(ingredients);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Registration.INFUSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return Registration.INFUSING_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static final class Serializer implements RecipeSerializer<InfusingRecipe> {
        private static final MapCodec<InfusingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC_NONEMPTY.listOf(1, SLOTS).fieldOf("ingredients").forGetter(InfusingRecipe::ingredients),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(InfusingRecipe::result),
                Codec.intRange(1, 72000).optionalFieldOf("time", 100).forGetter(InfusingRecipe::time)
        ).apply(i, InfusingRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, InfusingRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), InfusingRecipe::ingredients,
                ItemStack.STREAM_CODEC, InfusingRecipe::result,
                ByteBufCodecs.VAR_INT, InfusingRecipe::time,
                InfusingRecipe::new);

        @Override
        public MapCodec<InfusingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, InfusingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
