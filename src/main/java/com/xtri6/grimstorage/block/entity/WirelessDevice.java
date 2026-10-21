package com.xtri6.grimstorage.block.entity;

import java.util.UUID;

import javax.annotation.Nullable;

/**
 * A device that joins a network by binding to its ID instead of touching it. It stays a member of that network as
 * long as it is loaded and within reach of the network's controller.
 */
public interface WirelessDevice {
    /** The network this device is bound to, or null if it is not bound. */
    @Nullable
    UUID boundNetwork();

    /** Binds to a network (or unbinds with null). The network is rebuilt to pick the change up. */
    void bindNetwork(@Nullable UUID network);

    /** How far this device reaches from a controller, in blocks. 0 means no range card, so it can't join a network. */
    int wirelessRange();

    /** Whether this device reaches any distance (and, if the server allows it, any dimension). */
    boolean infiniteReach();
}
