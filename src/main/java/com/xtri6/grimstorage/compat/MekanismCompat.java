package com.xtri6.grimstorage.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.storage.BulkKey;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.PipePort;
import com.xtri6.grimstorage.storage.StorageNetwork;

import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Mekanism chemicals (gases, infuse types, pigments, slurries). Only loaded with Mekanism installed. */
final class MekanismCompat implements Compat.Chemicals {
    /** Same names and types as Mekanism's own capabilities, so NeoForge treats them as the same capability. */
    static final BlockCapability<IChemicalHandler, Direction> CHEMICAL_BLOCK = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);
    static final ItemCapability<IChemicalHandler, Void> CHEMICAL_ITEM = ItemCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    static ChemicalStack stack(BulkKey key, long amount) {
        if (amount <= 0 || key.id() == null) {
            return ChemicalStack.EMPTY;
        }
        Chemical chemical = MekanismAPI.CHEMICAL_REGISTRY.get(key.id());
        return chemical == null || chemical.isEmptyType() ? ChemicalStack.EMPTY : new ChemicalStack(chemical, amount);
    }

    @Nullable
    static BulkKey key(ChemicalStack stack) {
        return stack.isEmpty() ? null : BulkKey.chemical(stack.getTypeRegistryName());
    }

    @Override
    public <T extends BlockEntity> void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<T> type,
            java.util.function.BiFunction<T, Direction, PipePort> port) {
        event.registerBlockEntity(CHEMICAL_BLOCK, type, (be, side) -> {
            PipePort p = port.apply(be, side);
            return p == null ? null : new Handler(p);
        });
    }

    @Nullable
    @Override
    public BulkKey chemicalIn(ItemStack stack) {
        IChemicalHandler tank = stack.isEmpty() ? null : stack.getCapability(CHEMICAL_ITEM);
        if (tank == null) {
            return null;
        }
        for (int i = 0; i < tank.getChemicalTanks(); i++) {
            BulkKey k = key(tank.getChemicalInTank(i));
            if (k != null) {
                return k;
            }
        }
        return null;
    }

    @Override
    public Compat.BulkLink link(ServerLevel level, BlockPos pos, Direction side, BooleanSupplier valid, Runnable invalidated) {
        BlockCapabilityCache<IChemicalHandler, Direction> cache = BlockCapabilityCache.create(CHEMICAL_BLOCK, level, pos, side, valid, invalidated);
        return new Compat.BulkLink() {
            @Override
            public long pull(StorageNetwork net, long max, Predicate<BulkKey> ok) {
                IChemicalHandler h = cache.getCapability();
                if (h == null) {
                    return -1;
                }
                long moved = 0;
                for (int t = 0; t < h.getChemicalTanks() && moved < max; t++) {
                    ChemicalStack inside = h.getChemicalInTank(t);
                    BulkKey k = key(inside);
                    if (k == null || !ok.test(k)) {
                        continue;
                    }
                    ChemicalStack can = h.extractChemical(t, Math.min(max - moved, inside.getAmount()), Action.SIMULATE);
                    long fits = can.isEmpty() ? 0 : net.insertBulk(k, can.getAmount(), true);
                    if (fits <= 0) {
                        continue;
                    }
                    ChemicalStack got = h.extractChemical(t, fits, Action.EXECUTE);
                    if (!got.isEmpty()) {
                        net.insertBulk(k, got.getAmount(), false);
                        moved += got.getAmount();
                    }
                }
                return moved;
            }

            @Override
            public long push(StorageNetwork net, long max, Iterable<BulkKey> candidates) {
                IChemicalHandler h = cache.getCapability();
                if (h == null) {
                    return -1;
                }
                long moved = 0;
                for (BulkKey k : candidates) {
                    if (moved >= max) {
                        break;
                    }
                    long have = net.extractBulk(k, max - moved, true);
                    ChemicalStack offer = stack(k, have);
                    if (offer.isEmpty()) {
                        continue;
                    }
                    long accepted = have - h.insertChemical(offer, Action.SIMULATE).getAmount();
                    if (accepted <= 0) {
                        continue;
                    }
                    long got = net.extractBulk(k, accepted, false);
                    ChemicalStack left = h.insertChemical(stack(k, got), Action.EXECUTE);
                    if (!left.isEmpty()) {
                        net.insertBulk(k, left.getAmount(), false);
                    }
                    moved += got - left.getAmount();
                }
                return moved;
            }
        };
    }

    @Override
    public ItemStack click(ServerPlayer player, StorageNetwork net, ItemStack carried, @Nullable BulkKey key) {
        IChemicalHandler tank = carried.getCapability(CHEMICAL_ITEM);
        if (tank == null) {
            return carried;
        }
        // Mekanism tank items move a limited amount per transfer, so keep going until nothing more moves.
        if (!tank.extractChemical(Long.MAX_VALUE, Action.SIMULATE).isEmpty()) {
            for (int i = 0; i < 1000; i++) {
                ChemicalStack inside = tank.extractChemical(Long.MAX_VALUE, Action.SIMULATE);
                if (inside.isEmpty()) {
                    break;
                }
                BulkKey k = key(inside);
                long fits = net.insertBulk(k, inside.getAmount(), true);
                if (fits <= 0) {
                    break;
                }
                ChemicalStack taken = tank.extractChemical(inside.copyWithAmount(fits), Action.EXECUTE);
                if (taken.isEmpty()) {
                    break;
                }
                net.insertBulk(k, taken.getAmount(), false);
            }
            return carried;
        }
        if (key != null) {
            for (int i = 0; i < 1000; i++) {
                long have = net.countBulk(key);
                ChemicalStack offer = stack(key, have);
                if (offer.isEmpty()) {
                    break;
                }
                long accepted = have - tank.insertChemical(offer, Action.SIMULATE).getAmount();
                if (accepted <= 0) {
                    break;
                }
                long got = net.extractBulk(key, accepted, false);
                ChemicalStack left = tank.insertChemical(stack(key, got), Action.EXECUTE);
                if (!left.isEmpty()) {
                    net.insertBulk(key, left.getAmount(), false);
                }
                if (got - left.getAmount() <= 0) {
                    break;
                }
            }
        }
        return carried;
    }

    /** Chemical side of a pipe port (interface or bus): what pipes may push in and pull out is up to the port. */
    private static final class Handler implements IChemicalHandler {
        private final PipePort be;

        Handler(PipePort be) {
            this.be = be;
        }

        /** The chemicals pipes may pull here, one tank each, plus an empty tank to push into. */
        private List<BulkKey> keys(StorageNetwork net) {
            if (net == null) {
                return List.of();
            }
            List<BulkKey> out = new ArrayList<>();
            for (BulkKey k : net.bulkContents(BulkKind.CHEMICAL).keySet()) {
                if (be.pipeLists(k)) {
                    out.add(k);
                }
            }
            return out;
        }

        @Override
        public int getChemicalTanks() {
            return keys(be.network()).size() + 1;
        }

        @Override
        public ChemicalStack getChemicalInTank(int tank) {
            StorageNetwork net = be.network();
            List<BulkKey> keys = keys(net);
            return tank < 0 || tank >= keys.size() ? ChemicalStack.EMPTY : stack(keys.get(tank), net.countBulk(keys.get(tank)));
        }

        @Override
        public void setChemicalInTank(int tank, ChemicalStack stack) {}

        @Override
        public long getChemicalTankCapacity(int tank) {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean isValid(int tank, ChemicalStack stack) {
            return true;
        }

        @Override
        public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
            StorageNetwork net = be.network();
            BulkKey k = key(stack);
            if (net == null || k == null || !be.pipeInsert(k)) {
                return stack;
            }
            long in = net.insertBulk(k, stack.getAmount(), action.simulate());
            if (action.execute()) {
                be.pipeMoved(in);
            }
            return in >= stack.getAmount() ? ChemicalStack.EMPTY : stack.copyWithAmount(stack.getAmount() - in);
        }

        @Override
        public ChemicalStack extractChemical(int tank, long amount, Action action) {
            StorageNetwork net = be.network();
            if (net == null) {
                return ChemicalStack.EMPTY;
            }
            List<BulkKey> keys = keys(net);
            if (tank < 0 || tank >= keys.size() || !be.pipeExtract(keys.get(tank))) {
                return ChemicalStack.EMPTY;
            }
            long got = net.extractBulk(keys.get(tank), amount, action.simulate());
            if (action.execute()) {
                be.pipeMoved(got);
            }
            return stack(keys.get(tank), got);
        }
    }
}
