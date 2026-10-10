package com.xtri6.grimstorage.storage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out the full crafting tree for a request against a snapshot of the network: uses what is stored
 * first, crafts what is missing (recursively), and reports anything that can neither be found nor crafted.
 * Steps come out bottom-up, so running them in order always makes ingredients before they are needed.
 */
public final class CraftingPlanner {
    private static final int MAX_DEPTH = 32;

    public record Step(Pattern pattern, long times) {}

    public static final class Plan {
        private final List<Step> steps = new ArrayList<>();
        private final Map<ItemKey, Long> missing = new LinkedHashMap<>();
        private boolean loop;

        public List<Step> steps() {
            return steps;
        }

        public Map<ItemKey, Long> missing() {
            return missing;
        }

        public boolean loop() {
            return loop;
        }

        public boolean ok() {
            return missing.isEmpty() && !loop && !steps.isEmpty();
        }
    }

    private final StorageNetwork network;
    private final Map<ItemKey, Long> available;
    private final Deque<ItemKey> path = new ArrayDeque<>();
    private final Plan plan = new Plan();

    private CraftingPlanner(StorageNetwork network) {
        this.network = network;
        this.available = new HashMap<>(network.contents());
    }

    /** Plans crafting exactly {@code amount} of {@code key} (stock of that item itself is not used). */
    public static Plan plan(StorageNetwork network, ItemKey key, long amount) {
        CraftingPlanner planner = new CraftingPlanner(network);
        planner.craft(key, amount);
        return planner.plan;
    }

    private boolean need(ItemKey key, long amount) {
        long have = available.getOrDefault(key, 0L);
        long take = Math.min(have, amount);
        if (take > 0) {
            available.put(key, have - take);
        }
        long rest = amount - take;
        return rest <= 0 || craft(key, rest);
    }

    private boolean craft(ItemKey key, long amount) {
        List<Pattern> options = network.patternsFor(key);
        if (options.isEmpty()) {
            plan.missing.merge(key, amount, Long::sum);
            return false;
        }
        if (path.contains(key) || path.size() >= MAX_DEPTH) {
            plan.loop = true;
            plan.missing.merge(key, amount, Long::sum);
            return false;
        }
        Pattern pattern = options.get(0);
        long per = pattern.outputCount(key);
        long times = (amount + per - 1) / per;

        path.push(key);
        boolean ok = true;
        for (Map.Entry<ItemKey, Long> in : pattern.inputs().entrySet()) {
            ok &= need(in.getKey(), in.getValue() * times);
        }
        path.pop();

        plan.steps.add(new Step(pattern, times));
        // Anything made beyond what was asked (and any by-products) is available to later steps.
        for (Map.Entry<ItemKey, Long> out : pattern.outputs().entrySet()) {
            long made = out.getValue() * times - (out.getKey().equals(key) ? amount : 0);
            if (made > 0) {
                available.merge(out.getKey(), made, Long::sum);
            }
        }
        return ok;
    }
}
