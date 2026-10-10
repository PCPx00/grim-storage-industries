package com.xtri6.grimstorage.block.entity;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.storage.NetworkManager;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** The heart of a network. It owns the network object and runs it every tick. */
public class ControllerBlockEntity extends NetworkBlockEntity implements Ticking {
    private StorageNetwork network;

    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.CONTROLLER_ENTITY.get(), pos, state);
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
