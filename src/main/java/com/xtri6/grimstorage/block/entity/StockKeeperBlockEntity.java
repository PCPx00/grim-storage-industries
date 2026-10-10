package com.xtri6.grimstorage.block.entity;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.storage.CraftingPlanner;
import com.xtri6.grimstorage.storage.ItemKey;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** "Always keep N of this." Up to 9 rules; it starts crafting jobs whenever stock drops below a target. */
public class StockKeeperBlockEntity extends NetworkBlockEntity {
    public static final int RULES = 9;
    public static final int MAX_TARGET = 1_000_000_000;
    private static final int BATCH = 4096;
    private static final long RETRY_TICKS = 200;

    private final GhostContainer filters = new GhostContainer(RULES, false, this::setChanged);
    private final int[] targets = new int[RULES];
    private final long[] retryAt = new long[RULES];
    /** Per rule: 0 = idle, 1 = crafting, 2 = cannot craft (missing items or no recipe). */
    private final int[] state = new int[RULES];

    public StockKeeperBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.STOCK_KEEPER_ENTITY.get(), pos, state);
    }

    public GhostContainer filters() {
        return filters;
    }

    public int target(int rule) {
        return targets[rule];
    }

    public int ruleState(int rule) {
        return state[rule];
    }

    public void setTarget(int rule, int target) {
        if (rule >= 0 && rule < RULES) {
            targets[rule] = Math.max(0, Math.min(MAX_TARGET, target));
            retryAt[rule] = 0;
            setChanged();
        }
    }

    public void check(StorageNetwork network, long now) {
        for (int i = 0; i < RULES; i++) {
            ItemKey key = ItemKey.of(filters.getItem(i));
            if (key == null || targets[i] <= 0) {
                state[i] = 0;
                continue;
            }
            long crafting = network.craftingAmount(key);
            long have = network.count(key);
            if (crafting > 0) {
                state[i] = 1;
                continue;
            }
            if (have >= targets[i]) {
                state[i] = 0;
                continue;
            }
            if (now < retryAt[i] || !network.canStartJob()) {
                continue;
            }
            CraftingPlanner.Plan plan = network.request(key, Math.min(BATCH, targets[i] - have), "Stock Keeper");
            if (plan.ok()) {
                state[i] = 1;
            } else {
                state[i] = 2;
                retryAt[i] = now + RETRY_TICKS;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveGhosts(tag, "filters", filters.items(), registries);
        tag.putIntArray("targets", targets);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadGhosts(tag, "filters", filters.items(), registries);
        int[] t = tag.getIntArray("targets");
        System.arraycopy(t, 0, targets, 0, Math.min(t.length, RULES));
    }
}
