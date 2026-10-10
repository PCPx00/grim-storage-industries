package com.xtri6.grimstorage.block.entity;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Joins two networks across any distance or dimension. Pair two links with a Soul Tether. */
public class QuantumLinkBlockEntity extends NetworkBlockEntity {
    @Nullable
    private GlobalPos partner;

    public QuantumLinkBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.QUANTUM_LINK_ENTITY.get(), pos, state);
    }

    @Nullable
    public GlobalPos partner() {
        return partner;
    }

    public void setPartner(@Nullable GlobalPos partner) {
        this.partner = partner;
        setChanged();
        com.xtri6.grimstorage.storage.NetworkManager.markDirty();
    }

    /** The partner, if it is loaded and paired back to this link. */
    @Nullable
    public GlobalPos linkedPartner(MinecraftServer server) {
        if (partner == null || level == null) {
            return null;
        }
        ServerLevel other = server.getLevel(partner.dimension());
        if (other == null || !other.isLoaded(partner.pos())) {
            return null;
        }
        if (other.getBlockEntity(partner.pos()) instanceof QuantumLinkBlockEntity q
                && GlobalPos.of(level.dimension(), worldPosition).equals(q.partner)) {
            return partner;
        }
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (partner != null) {
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, partner).result().ifPresent(t -> tag.put("partner", t));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        partner = tag.contains("partner")
                ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("partner")).result().orElse(null)
                : null;
    }
}
