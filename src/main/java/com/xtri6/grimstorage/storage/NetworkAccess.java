package com.xtri6.grimstorage.storage;

import com.xtri6.grimstorage.block.NetworkMember;
import com.xtri6.grimstorage.block.entity.NetworkBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Who may use a network. The player who places a controller owns it; the owner can trust other players, or open
 * the network to everyone, from the controller screen. Server operators can always get in. A network without an
 * owner (built before 1.13, or placed by a machine) is open until someone claims it.
 */
public final class NetworkAccess {
    private NetworkAccess() {}

    /** True if {@code player} may use the network at {@code pos}; tells them whose it is if not. */
    public static boolean check(Player player, Level level, BlockPos pos) {
        if (level.isClientSide) {
            return true;
        }
        StorageNetwork net = NetworkManager.get(level, pos);
        if (net == null || net.allows(player)) {
            return true;
        }
        player.displayClientMessage(Component.translatable("message.grimstorage.access.denied", net.ownerName())
                .withStyle(ChatFormatting.RED), true);
        return false;
    }

    /** Nor can they join their own blocks onto it (or join two networks to switch one off). */
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof Player player) || !(e.getLevel() instanceof Level level) || level.isClientSide
                || !(e.getPlacedBlock().getBlock() instanceof NetworkMember)) {
            return;
        }
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockPos n = e.getPos().relative(d);
            if (level.getBlockState(n).getBlock() instanceof NetworkMember && !check(player, level, n)) {
                e.setCanceled(true);
                return;
            }
        }
    }

    /** Players who may not use a network can't break its blocks either. */
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        boolean ours = e.getState().getBlock() instanceof NetworkMember || level.getBlockEntity(e.getPos()) instanceof NetworkBlockEntity;
        if (ours && !check(e.getPlayer(), level, e.getPos())) {
            e.setCanceled(true);
        }
    }
}
