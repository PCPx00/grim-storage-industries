package com.xtri6.grimstorage.block.entity;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds the buses mounted on a Soul Cable, one per side at most. Only cables that have a bus get one of these, so
 * plain cable stays free. Each bus works exactly like a bus block facing the block on its side.
 */
public class CableBusBlockEntity extends NetworkBlockEntity implements Ticking {
    private final BusLogic[] buses = new BusLogic[6];

    public CableBusBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.CABLE_BUS_ENTITY.get(), pos, state);
    }

    @Nullable
    public BusLogic part(Direction side) {
        return side == null ? null : buses[side.ordinal()];
    }

    public void addBus(Direction side, boolean export) {
        buses[side.ordinal()] = new BusLogic(new SideHost(side), export);
        setChanged();
    }

    public void removeBus(Direction side) {
        buses[side.ordinal()] = null;
        setChanged();
    }

    @Override
    public void serverTick() {
        for (BusLogic b : buses) {
            if (b != null) {
                b.serverTick();
            }
        }
    }

    public void wake() {
        for (BusLogic b : buses) {
            if (b != null) {
                b.wake();
            }
        }
    }

    /** A bus's view of this cable: same place and network, facing its own side. */
    private final class SideHost implements BusLogic.Host {
        private final Direction side;

        SideHost(Direction side) {
            this.side = side;
        }

        @Override
        public Level level() {
            return level;
        }

        @Override
        public BlockPos pos() {
            return worldPosition;
        }

        @Override
        public Direction facing() {
            return side;
        }

        @Nullable
        @Override
        public StorageNetwork network() {
            return CableBusBlockEntity.this.network();
        }

        @Override
        public void markChanged() {
            setChanged();
        }

        @Override
        public boolean removed() {
            return isRemoved() || buses[side.ordinal()] == null;
        }

        @Override
        public void switchDirection(net.minecraft.server.level.ServerPlayer player) {
            BusLogic bus = buses[side.ordinal()];
            if (bus == null || level == null || level.isClientSide) {
                return;
            }
            bus.flipDirection();
            BlockState state = level.getBlockState(worldPosition);
            level.setBlock(worldPosition, state.setValue(com.xtri6.grimstorage.block.CableBlock.SIDES.get(side),
                    bus.isExport() ? com.xtri6.grimstorage.block.CableBlock.Side.EXPORT : com.xtri6.grimstorage.block.CableBlock.Side.IMPORT),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
            com.xtri6.grimstorage.menu.Menus.openCableBus(player, worldPosition, side);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag all = new CompoundTag();
        for (Direction d : Direction.values()) {
            BusLogic b = buses[d.ordinal()];
            if (b != null) {
                CompoundTag one = new CompoundTag();
                one.putBoolean("export", b.isExport());
                b.save(one, registries);
                all.put(d.getSerializedName(), one);
            }
        }
        tag.put("buses", all);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CompoundTag all = tag.getCompound("buses");
        for (Direction d : Direction.values()) {
            if (all.contains(d.getSerializedName())) {
                CompoundTag one = all.getCompound(d.getSerializedName());
                BusLogic b = new BusLogic(new SideHost(d), one.getBoolean("export"));
                b.load(one, registries);
                buses[d.ordinal()] = b;
            } else {
                buses[d.ordinal()] = null;
            }
        }
    }
}
