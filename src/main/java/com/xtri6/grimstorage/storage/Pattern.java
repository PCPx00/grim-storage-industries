package com.xtri6.grimstorage.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.entity.ProcessingInterfaceBlockEntity;
import com.xtri6.grimstorage.item.CardData;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** One way the network knows to make something: a crafting recipe card or a machine processing card. */
public final class Pattern {
    public enum Kind { CRAFTING, PROCESSING }

    private final Kind kind;
    private final Map<ItemKey, Long> inputs;
    private final Map<ItemKey, Long> outputs;
    private final List<ItemStack> grid;
    @Nullable
    private final RecipeHolder<CraftingRecipe> recipe;
    @Nullable
    private final ProcessingInterfaceBlockEntity processor;

    private Pattern(Kind kind, Map<ItemKey, Long> inputs, Map<ItemKey, Long> outputs, List<ItemStack> grid,
                    @Nullable RecipeHolder<CraftingRecipe> recipe, @Nullable ProcessingInterfaceBlockEntity processor) {
        this.kind = kind;
        this.inputs = Collections.unmodifiableMap(inputs);
        this.outputs = Collections.unmodifiableMap(outputs);
        this.grid = grid;
        this.recipe = recipe;
        this.processor = processor;
    }

    @Nullable
    public static Pattern crafting(Level level, ItemStack card) {
        CardData.Recipe data = card.get(Registration.RECIPE_CARD_DATA.get());
        if (data == null || !data.valid()) {
            return null;
        }
        List<ItemStack> grid = new ArrayList<>(9);
        for (ItemStack s : data.grid()) {
            grid.add(s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1));
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        Optional<RecipeHolder<CraftingRecipe>> found = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (found.isEmpty()) {
            return null;
        }
        ItemStack result = found.get().value().assemble(input, level.registryAccess());
        ItemKey out = ItemKey.of(result);
        if (out == null) {
            return null;
        }
        Map<ItemKey, Long> in = new LinkedHashMap<>();
        for (ItemStack s : grid) {
            ItemKey k = ItemKey.of(s);
            if (k != null) {
                in.merge(k, 1L, Long::sum);
            }
        }
        Map<ItemKey, Long> outs = new LinkedHashMap<>();
        outs.put(out, (long) result.getCount());
        return new Pattern(Kind.CRAFTING, in, outs, List.copyOf(grid), found.get(), null);
    }

    @Nullable
    public static Pattern processing(ItemStack card, ProcessingInterfaceBlockEntity processor) {
        CardData.Processing data = card.get(Registration.PROCESSING_CARD_DATA.get());
        if (data == null || !data.valid()) {
            return null;
        }
        Map<ItemKey, Long> in = new LinkedHashMap<>();
        for (ItemStack s : data.inputs()) {
            ItemKey k = ItemKey.of(s);
            if (k != null) {
                in.merge(k, (long) s.getCount(), Long::sum);
            }
        }
        Map<ItemKey, Long> outs = new LinkedHashMap<>();
        for (ItemStack s : data.outputs()) {
            ItemKey k = ItemKey.of(s);
            if (k != null) {
                outs.merge(k, (long) s.getCount(), Long::sum);
            }
        }
        if (in.isEmpty() || outs.isEmpty()) {
            return null;
        }
        return new Pattern(Kind.PROCESSING, in, outs, List.of(), null, processor);
    }

    public Kind kind() {
        return kind;
    }

    public Map<ItemKey, Long> inputs() {
        return inputs;
    }

    public Map<ItemKey, Long> outputs() {
        return outputs;
    }

    public ItemKey primary() {
        return outputs.keySet().iterator().next();
    }

    public long outputCount(ItemKey key) {
        return outputs.getOrDefault(key, 0L);
    }

    @Nullable
    public RecipeHolder<CraftingRecipe> recipe() {
        return recipe;
    }

    @Nullable
    public ProcessingInterfaceBlockEntity processor() {
        return processor;
    }

    public CraftingInput craftingInput() {
        List<ItemStack> copy = new ArrayList<>(9);
        for (ItemStack s : grid) {
            copy.add(s.copy());
        }
        return CraftingInput.of(3, 3, copy);
    }
}
