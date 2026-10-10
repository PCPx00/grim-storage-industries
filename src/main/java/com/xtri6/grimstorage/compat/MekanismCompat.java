package com.xtri6.grimstorage.compat;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.block.entity.InterfaceBlockEntity;
import com.xtri6.grimstorage.storage.BulkKey;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.StorageNetwork;

import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
    public void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<InterfaceBlockEntity> type) {
        event.registerBlockEntity(CHEMICAL_BLOCK, type, (be, side) -> new Handler(be));
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

    /** The interface's chemical side: anything can be pushed in, pulling is allowed only when switched on. */
    private static final class Handler implements IChemicalHandler {
        private final InterfaceBlockEntity be;

        Handler(InterfaceBlockEntity be) {
            this.be = be;
        }

        private List<BulkKey> keys(StorageNetwork net) {
            return net == null ? List.of() : new ArrayList<>(net.bulkContents(BulkKind.CHEMICAL).keySet());
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
            if (net == null || stack.isEmpty()) {
                return stack;
            }
            long in = net.insertBulk(key(stack), stack.getAmount(), action.simulate());
            return in >= stack.getAmount() ? ChemicalStack.EMPTY : stack.copyWithAmount(stack.getAmount() - in);
        }

        @Override
        public ChemicalStack extractChemical(int tank, long amount, Action action) {
            StorageNetwork net = be.network();
            if (net == null || !be.exports(BulkKind.CHEMICAL)) {
                return ChemicalStack.EMPTY;
            }
            List<BulkKey> keys = keys(net);
            if (tank < 0 || tank >= keys.size()) {
                return ChemicalStack.EMPTY;
            }
            return stack(keys.get(tank), net.extractBulk(keys.get(tank), amount, action.simulate()));
        }
    }
}
