package com.xtri6.grimstorage.block.entity;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.storage.NetworkManager;
import com.xtri6.grimstorage.storage.StorageNetwork;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** The heart of a network. It owns the network object and runs it every tick. */
public class ControllerBlockEntity extends NetworkBlockEntity implements Ticking {
    private StorageNetwork network;

    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.CONTROLLER_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ who may use it

    @Nullable
    private UUID owner;
    private String ownerName = "";
    private final Map<UUID, String> trusted = new LinkedHashMap<>();
    /** Open to everyone, not just the owner and trusted players. */
    private boolean open;

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean isOpen() {
        return open || owner == null;
    }

    public Map<UUID, String> trusted() {
        return trusted;
    }

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        trusted.remove(owner);
        setChanged();
    }

    public boolean allows(Player player) {
        return isOpen() || player.getUUID().equals(owner) || trusted.containsKey(player.getUUID()) || player.hasPermissions(2);
    }

    /** The owner (or an operator) changes who may use it; anyone may claim a network nobody owns. */
    public boolean canManage(Player player) {
        return owner == null || player.getUUID().equals(owner) || player.hasPermissions(2);
    }

    public void trust(UUID id, String name) {
        if (!id.equals(owner)) {
            trusted.put(id, name);
            setChanged();
        }
    }

    public void untrust(String name) {
        trusted.values().removeIf(n -> n.equalsIgnoreCase(name));
        setChanged();
    }

    public void toggleOpen() {
        open = !open;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("owner", owner);
            tag.putString("ownerName", ownerName);
        }
        tag.putBoolean("open", open);
        ListTag list = new ListTag();
        trusted.forEach((id, name) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            list.add(t);
        });
        tag.put("trusted", list);
        // crafting jobs and items waiting for room, so a restart doesn't lose them
        if (network != null && network.builtEpoch() >= 0 && savedState == null) {
            tag.put("network", network.saveState(registries));
        } else if (savedState != null) {
            tag.put("network", savedState);
        }
    }

    /** What the network saved last time, until the network picks it up after the world loads. */
    @Nullable
    private CompoundTag savedState;

    /** Hands the saved jobs to the network, once. */
    @Nullable
    public CompoundTag takeSavedState() {
        CompoundTag s = savedState;
        savedState = null;
        return s;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        open = tag.getBoolean("open");
        trusted.clear();
        for (Tag t : tag.getList("trusted", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            trusted.put(c.getUUID("id"), c.getString("name"));
        }
        if (tag.contains("network") && (network == null || network.builtEpoch() < 0)) {
            savedState = tag.getCompound("network");
        }
    }

    /** This controller's network, working or not. Null until its first tick. */
    public StorageNetwork ownNetwork() {
        return network;
    }

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        if (network == null) {
            network = new StorageNetwork(this);
        }
        if (network.builtEpoch() != NetworkManager.epoch()) {
            network.rebuild(sl);
        }
        network.tick(sl);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (network != null) {
            network.dispose();
        }
    }
}
