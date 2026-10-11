package com.xtri6.grimstorage.block.entity;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.BusBlock;
import com.xtri6.grimstorage.storage.BulkKey;
import com.xtri6.grimstorage.storage.BulkKind;
import com.xtri6.grimstorage.storage.PipePort;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** A free-standing Import / Export Bus block. All the work is done by its {@link BusLogic}. */
public class BusBlockEntity extends NetworkBlockEntity implements Ticking, PipePort, BusLogic.Host {
    public static final int ITEMS_BIT = BusLogic.ITEMS_BIT;
    public static final int RESOURCES = BusLogic.RESOURCES;

    private final BusLogic logic;

    public BusBlockEntity(BlockPos pos, BlockState state) {
        super(state.is(Registration.EXPORT_BUS.get()) ? Registration.EXPORT_BUS_ENTITY.get() : Registration.IMPORT_BUS_ENTITY.get(), pos, state);
        this.logic = new BusLogic(this, state.is(Registration.EXPORT_BUS.get()));
    }

    public BusLogic logic() {
        return logic;
    }

    // ---- host

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
        return getBlockState().getValue(BusBlock.FACING);
    }

    @Override
    public void markChanged() {
        super.setChanged();
    }

    @Override
    public boolean removed() {
        return isRemoved();
    }

    /** Swaps this block for the other kind of bus, facing the same way, with the same settings. */
    @Override
    public void switchDirection(net.minecraft.server.level.ServerPlayer player) {
        if (level == null || level.isClientSide) {
            return;
        }
        logic.flipDirection();
        CompoundTag tag = new CompoundTag();
        logic.save(tag, level.registryAccess());
        BlockPos pos = worldPosition;
        Level lvl = level;
        BlockState next = (logic.isExport() ? Registration.EXPORT_BUS.get() : Registration.IMPORT_BUS.get()).defaultBlockState()
                .setValue(BusBlock.FACING, facing());
        lvl.setBlock(pos, next, net.minecraft.world.level.block.Block.UPDATE_ALL);
        if (lvl.getBlockEntity(pos) instanceof BusBlockEntity fresh) {
            fresh.logic.load(tag, lvl.registryAccess());
            fresh.setChanged();
        }
        com.xtri6.grimstorage.menu.Menus.openBus(player, pos);
    }

    @Override
    public void serverTick() {
        logic.serverTick();
    }

    public void wake() {
        logic.wake();
    }

    // ---- the same methods the bus always had, passed through

    public GhostContainer filters() {
        return logic.filters();
    }

    public boolean isExport() {
        return logic.isExport();
    }

    public boolean blacklist() {
        return logic.blacklist();
    }

    public boolean matchComponents() {
        return logic.matchComponents();
    }

    public int resources() {
        return logic.resources();
    }

    public boolean moves(int bit) {
        return logic.moves(bit);
    }

    public boolean moves(BulkKind kind) {
        return logic.moves(kind);
    }

    public BusLogic.Status status() {
        return logic.status();
    }

    public void toggleBlacklist() {
        logic.toggleBlacklist();
    }

    public void toggleMatchComponents() {
        logic.toggleMatchComponents();
    }

    public void toggleResource(int bit) {
        logic.toggleResource(bit);
    }

    public IItemHandler pipeItems() {
        return logic.pipeItems();
    }

    public IFluidHandler pipeFluids() {
        return logic.pipeFluids();
    }

    public IEnergyStorage pipeEnergy() {
        return logic.pipeEnergy();
    }

    @Override
    public boolean pipeInsert(BulkKey key) {
        return logic.pipeInsert(key);
    }

    @Override
    public boolean pipeExtract(BulkKey key) {
        return logic.pipeExtract(key);
    }

    @Override
    @Nullable
    public com.xtri6.grimstorage.storage.StorageNetwork network() {
        return super.network();
    }

    @Override
    public void setChanged() {
        logic.changed();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        logic.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        logic.load(tag, registries);
    }
}
