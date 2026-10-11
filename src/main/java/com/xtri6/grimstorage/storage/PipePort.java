package com.xtri6.grimstorage.storage;

import javax.annotation.Nullable;

/**
 * A block that other mods' pipes can plug into: the Network Interface and the Import / Export Buses.
 * Says which resources pipes may push into the network through it, and which they may pull back out.
 */
public interface PipePort {
    @Nullable
    StorageNetwork network();

    /** May a pipe push this resource into the network here? */
    boolean pipeInsert(BulkKey key);

    /** May a pipe pull this resource out of the network here? */
    boolean pipeExtract(BulkKey key);

    /** A pipe just moved this much through the port (for status displays). */
    default void pipeMoved(long amount) {}

    /** Should pipes see this stored resource in the port's tanks? By default, only what they may pull. */
    default boolean pipeLists(BulkKey key) {
        return pipeExtract(key);
    }
}
