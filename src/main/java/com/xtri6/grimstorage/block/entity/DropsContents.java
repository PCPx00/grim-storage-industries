package com.xtri6.grimstorage.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Block entities whose inventory spills on the ground when the block is broken. */
public interface DropsContents {
    void dropContents(Level level, BlockPos pos);
}
