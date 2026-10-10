package com.xtri6.grimstorage.compat;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.xtri6.grimstorage.block.entity.InterfaceBlockEntity;
import com.xtri6.grimstorage.storage.BulkKey;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Ars Nouveau Source. Only loaded with Ars Nouveau installed. */
final class ArsCompat implements Compat.Source {
    /** Same name and type as Ars Nouveau's own capability. */
    static final BlockCapability<ISourceCap, Direction> SOURCE = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source"), ISourceCap.class);
    private static final BulkKey KEY = BulkKey.flat(BulkKind.SOURCE);

    @Override
    public void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<InterfaceBlockEntity> type) {
        event.registerBlockEntity(SOURCE, type, (be, side) -> new Cap(be));
    }

    /** The interface as a Source container: anything can add Source, taking it is allowed when switched on. */
    private static final class Cap implements ISourceCap {
        private final InterfaceBlockEntity be;

        Cap(InterfaceBlockEntity be) {
            this.be = be;
        }

        private long stored() {
            StorageNetwork net = be.network();
            return net == null ? 0 : net.countBulk(KEY);
        }

        private long capacity() {
            StorageNetwork net = be.network();
            return net == null ? 0 : net.bulkStats(BulkKind.SOURCE)[1];
        }

        @Override
        public boolean canAcceptSource(int amount) {
            return capacity() - stored() >= amount;
        }

        @Override
        public boolean canProvideSource(int amount) {
            return be.exports(BulkKind.SOURCE) && stored() >= amount;
        }

        @Override
        public int getMaxExtract() {
            return be.exports(BulkKind.SOURCE) ? Integer.MAX_VALUE : 0;
        }

        @Override
        public int getMaxReceive() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getSource() {
            return (int) Math.min(Integer.MAX_VALUE, stored());
        }

        @Override
        public int getSourceCapacity() {
            return (int) Math.min(Integer.MAX_VALUE, capacity());
        }

        @Override
        public void setSource(int source) {}

        @Override
        public void setMaxSource(int max) {}

        @Override
        public int receiveSource(int amount, boolean simulate) {
            StorageNetwork net = be.network();
            return net == null ? 0 : (int) net.insertBulk(KEY, amount, simulate);
        }

        @Override
        public int extractSource(int amount, boolean simulate) {
            StorageNetwork net = be.network();
            return net == null || !be.exports(BulkKind.SOURCE) ? 0 : (int) net.extractBulk(KEY, amount, simulate);
        }
    }
}
