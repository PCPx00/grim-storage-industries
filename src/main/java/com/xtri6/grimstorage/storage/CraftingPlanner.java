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
        private final Map<ItemKey, Long> used = new LinkedHashMap<>();
        private final Map<BulkKey, Long> missingBulk = new LinkedHashMap<>();

        /** Fluids and chemicals a machine step needs that storage doesn't have enough of. */
        public Map<BulkKey, Long> missingBulk() {
            return missingBulk;
        }
        private boolean loop;

        /** What the plan takes out of storage. */
        public Map<ItemKey, Long> used() {
            return used;
        }

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
            return missing.isEmpty() && missingBulk.isEmpty() && !loop && !steps.isEmpty();
        }
    }

    private final StorageNetwork network;
    private final Map<ItemKey, Long> available;
    /** Fluids and chemicals in storage, used up as the plan takes them. */
    private final Map<BulkKey, Long> availableBulk = new HashMap<>();
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
        network.contents().forEach((k, had) -> {
            long left = planner.available.getOrDefault(k, 0L);
            if (left < had) {
                planner.plan.used.put(k, had - left);
            }
        });
        return planner.plan;
    }

    /** Fluids and chemicals come from storage only (nothing crafts them). */
    private boolean needBulk(BulkKey key, long amount) {
        long have = availableBulk.computeIfAbsent(key, network::countBulk);
        long take = Math.min(have, amount);
        availableBulk.put(key, have - take);
        if (take < amount) {
            plan.missingBulk.merge(key, amount - take, Long::sum);
            return false;
        }
        return true;
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
        // Several ways to make it (a furnace and a crafting recipe, say): use the first one that works with what is
        // in storage, and only report missing items if none of them do.
        Pattern pattern = options.get(0);
        if (options.size() > 1) {
            // try first the ways whose ingredients are already in storage ("any planks": the planks you have), so
            // the first try usually works and big trees stay quick to plan
            List<Pattern> ordered = new java.util.ArrayList<>(options);
            ordered.sort(java.util.Comparator.comparingDouble(o -> -inStock(o, key, amount)));
            for (Pattern option : ordered) {
                Map<ItemKey, Long> savedAvailable = new HashMap<>(available);
                Map<ItemKey, Long> savedMissing = new LinkedHashMap<>(plan.missing);
                Map<BulkKey, Long> savedBulk = new HashMap<>(availableBulk);
                Map<BulkKey, Long> savedMissingBulk = new LinkedHashMap<>(plan.missingBulk);
                int savedSteps = plan.steps.size();
                boolean savedLoop = plan.loop;
                if (tryOption(option, key, amount) && plan.missing.equals(savedMissing) && plan.missingBulk.equals(savedMissingBulk) && !plan.loop) {
                    return true;
                }
                availableBulk.clear();
                availableBulk.putAll(savedBulk);
                plan.missingBulk.clear();
                plan.missingBulk.putAll(savedMissingBulk);
                available.clear();
                available.putAll(savedAvailable);
                plan.missing.clear();
                plan.missing.putAll(savedMissing);
                while (plan.steps.size() > savedSteps) {
                    plan.steps.remove(plan.steps.size() - 1);
                }
                plan.loop = savedLoop;
            }
        }
        return tryOption(pattern, key, amount);
    }

    /** 0..1: how much of this way's ingredients for {@code amount} are in storage right now. */
    private double inStock(Pattern p, ItemKey key, long amount) {
        long per = Math.max(1, p.outputCount(key));
        long times = (amount + per - 1) / per;
        double have = 0, need = 0;
        for (Map.Entry<ItemKey, Long> in : p.inputs().entrySet()) {
            long n = in.getValue() * times;
            need += n;
            have += Math.min(n, available.getOrDefault(in.getKey(), 0L));
        }
        return need <= 0 ? 0 : have / need;
    }

    private boolean tryOption(Pattern pattern, ItemKey key, long amount) {
        long per = pattern.outputCount(key);
        long times = (amount + per - 1) / per;

        path.push(key);
        boolean ok = true;
        for (Map.Entry<ItemKey, Long> in : pattern.inputs().entrySet()) {
            ok &= need(in.getKey(), in.getValue() * times);
        }
        for (Map.Entry<BulkKey, Long> in : pattern.bulkInputs().entrySet()) {
            ok &= needBulk(in.getKey(), in.getValue() * times);
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
