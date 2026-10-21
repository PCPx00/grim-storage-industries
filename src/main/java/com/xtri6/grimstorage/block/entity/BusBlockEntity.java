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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** A free-standing Import / Export Bus block. All the work is done by its {@link BusLogic}. */
public class BusBlockEntity extends NetworkBlockEntity implements Ticking, PipePort, BusLogic.Host, DropsContents {
    public static final int ITEMS_BIT = BusLogic.ITEMS_BIT;
    public static final int RESOURCES = BusLogic.RESOURCES;

    private final BusLogic logic;

    public BusBlockEntity(BlockPos pos, BlockState state) {
        this(state.is(Registration.EXPORT_BUS.get()) ? Registration.EXPORT_BUS_ENTITY.get() : Registration.IMPORT_BUS_ENTITY.get(),
                pos, state, state.is(Registration.EXPORT_BUS.get()));
    }

    /** For the wireless variants, which need their own block entity type. */
    protected BusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean export) {
        super(type, pos, state);
        this.logic = new BusLogic(this, export);
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
        java.util.UUID bound = this instanceof WirelessDevice w ? w.boundNetwork() : null;
        boolean wireless = this instanceof WirelessDevice;
        BlockPos pos = worldPosition;
        Level lvl = level;
        net.minecraft.world.level.block.Block nextBlock = wireless
                ? (logic.isExport() ? Registration.WIRELESS_EXPORT_BUS.get() : Registration.WIRELESS_IMPORT_BUS.get())
                : (logic.isExport() ? Registration.EXPORT_BUS.get() : Registration.IMPORT_BUS.get());
        BlockState next = nextBlock.defaultBlockState().setValue(BusBlock.FACING, facing());
        lvl.setBlock(pos, next, net.minecraft.world.level.block.Block.UPDATE_ALL);
        if (lvl.getBlockEntity(pos) instanceof BusBlockEntity fresh) {
            fresh.logic.load(tag, lvl.registryAccess());
            if (fresh instanceof WirelessDevice fw) {
                fw.bindNetwork(bound);
            }
            fresh.setChanged();
        }
        com.xtri6.grimstorage.menu.Menus.openBus(player, pos);
    }

    @Override
    public void dropContents(Level lvl, BlockPos pos) {
        for (net.minecraft.world.item.ItemStack st : logic.takeUpgrades()) {
            net.minecraft.world.Containers.dropItemStack(lvl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, st);
        }
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
    public void pipeMoved(long amount) {
        logic.pipeMoved(amount);
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
