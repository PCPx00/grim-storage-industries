package com.xtri6.grimstorage.compat;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.xtri6.grimstorage.storage.BulkKey;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.PipePort;
import com.xtri6.grimstorage.storage.StorageNetwork;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
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
    public <T extends BlockEntity> void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<T> type,
            java.util.function.BiFunction<T, Direction, PipePort> port) {
        event.registerBlockEntity(SOURCE, type, (be, side) -> {
            PipePort p = port.apply(be, side);
            return p == null ? null : new Cap(p);
        });
    }

    @Override
    public Compat.BulkLink link(ServerLevel level, BlockPos pos, Direction side, BooleanSupplier valid, Runnable invalidated) {
        BlockCapabilityCache<ISourceCap, Direction> cache = BlockCapabilityCache.create(SOURCE, level, pos, side, valid, invalidated);
        return new Compat.BulkLink() {
            @Override
            public long pull(StorageNetwork net, long max, Predicate<BulkKey> ok) {
                ISourceCap cap = cache.getCapability();
                if (cap == null) {
                    return -1;
                }
                int want = (int) Math.min(Math.min(max, Integer.MAX_VALUE), cap.getSource());
                int can = want <= 0 ? 0 : cap.extractSource(want, true);
                long fits = can <= 0 ? 0 : net.insertBulk(KEY, can, true);
                if (fits <= 0) {
                    return 0;
                }
                int got = cap.extractSource((int) fits, false);
                net.insertBulk(KEY, got, false);
                return got;
            }

            @Override
            public long push(StorageNetwork net, long max, Iterable<BulkKey> candidates) {
                ISourceCap cap = cache.getCapability();
                if (cap == null) {
                    return -1;
                }
                long have = net.extractBulk(KEY, Math.min(max, Integer.MAX_VALUE), true);
                int room = Math.max(0, cap.getSourceCapacity() - cap.getSource());
                int accepted = have <= 0 || room <= 0 ? 0 : cap.receiveSource((int) Math.min(have, room), true);
                if (accepted <= 0) {
                    return 0;
                }
                long got = net.extractBulk(KEY, accepted, false);
                int in = cap.receiveSource((int) got, false);
                if (in < got) {
                    net.insertBulk(KEY, got - in, false);
                }
                return in;
            }
        };
    }

    /** A pipe port (interface or bus) as a Source container; the port decides whether Source may go in or out. */
    private static final class Cap implements ISourceCap {
        private final PipePort be;

        Cap(PipePort be) {
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
            return be.pipeInsert(KEY) && capacity() - stored() >= amount;
        }

        @Override
        public boolean canProvideSource(int amount) {
            return be.pipeExtract(KEY) && stored() >= amount;
        }

        @Override
        public int getMaxExtract() {
            return be.pipeExtract(KEY) ? Integer.MAX_VALUE : 0;
        }

        @Override
        public int getMaxReceive() {
            return be.pipeInsert(KEY) ? Integer.MAX_VALUE : 0;
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
            return net == null || !be.pipeInsert(KEY) ? 0 : (int) net.insertBulk(KEY, amount, simulate);
        }

        @Override
        public int extractSource(int amount, boolean simulate) {
            StorageNetwork net = be.network();
            return net == null || !be.pipeExtract(KEY) ? 0 : (int) net.extractBulk(KEY, amount, simulate);
        }
    }
}
