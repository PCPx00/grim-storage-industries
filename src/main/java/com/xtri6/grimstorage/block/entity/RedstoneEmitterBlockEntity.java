package com.xtri6.grimstorage.block.entity;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.storage.CraftingPlanner;
import com.xtri6.grimstorage.storage.ItemKey;
import com.xtri6.grimstorage.storage.NetworkManager;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Redstone Emitter: gives a redstone signal when the network has more (or less) than an amount of an item, for
 * example "switch the mob farm off at 10,000 bones". In craft mode it does the opposite job: while it gets a
 * redstone signal, it keeps that item crafted up to the amount.
 */
public class RedstoneEmitterBlockEntity extends NetworkBlockEntity implements Ticking {
    public static final int AT_LEAST = 0;
    public static final int BELOW = 1;
    public static final int CRAFT = 2;
    public static final int MAX = 2_000_000_000;

    private final GhostContainer filter = new GhostContainer(1, false, this::setChanged);
    private int mode = AT_LEAST;
    private int amount = 64;
    private boolean emitting;
    private int ticks;
    private long retryAt;

    public RedstoneEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.REDSTONE_EMITTER_ENTITY.get(), pos, state);
    }

    public GhostContainer filter() {
        return filter;
    }

    public int mode() {
        return mode;
    }

    public int amount() {
        return amount;
    }

    public boolean emitting() {
        return emitting;
    }

    public void cycleMode() {
        mode = (mode + 1) % 3;
        setChanged();
        ticks = 0;
    }

    public void setAmount(int v) {
        amount = Math.max(0, Math.min(MAX, v));
        setChanged();
        ticks = 0;
    }

    /** How many the network has right now (-1 with no network). */
    public long stored() {
        ItemKey key = ItemKey.of(filter.getItem(0));
        StorageNetwork net = level == null ? null : NetworkManager.get(level, worldPosition);
        return key == null || net == null ? -1 : net.count(key);
    }

    @Override
    public void serverTick() {
        if (level == null || ticks++ % 10 != 0) {
            return;
        }
        ItemKey key = ItemKey.of(filter.getItem(0));
        StorageNetwork net = NetworkManager.get(level, worldPosition);
        boolean on = false;
        if (key != null && net != null) {
            long have = net.count(key);
            if (mode == AT_LEAST) {
                on = have >= amount;
            } else if (mode == BELOW) {
                on = have < amount;
            } else if (level.hasNeighborSignal(worldPosition) && have < amount && net.craftingAmount(key) <= 0
                    && level.getGameTime() >= retryAt && net.canStartJob()) {
                CraftingPlanner.Plan plan = net.request(key, Math.min(4096, amount - have), "Redstone Emitter");
                if (!plan.ok()) {
                    retryAt = level.getGameTime() + 200;
                }
            }
        }
        if (on != emitting) {
            emitting = on;
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveGhosts(tag, "filter", filter.items(), registries);
        tag.putInt("mode", mode);
        tag.putInt("amount", amount);
        tag.putBoolean("emitting", emitting);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadGhosts(tag, "filter", filter.items(), registries);
        mode = tag.getInt("mode");
        amount = tag.contains("amount") ? tag.getInt("amount") : 64;
        emitting = tag.getBoolean("emitting");
    }
}
