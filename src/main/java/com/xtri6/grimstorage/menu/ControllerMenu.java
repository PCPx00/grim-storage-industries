package com.xtri6.grimstorage.menu;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.entity.ControllerBlockEntity;
import com.xtri6.grimstorage.net.Payloads;
import com.xtri6.grimstorage.storage.CraftingJob;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** The network overview: storage use, block counts and running crafting jobs (with cancel). */
public class ControllerMenu extends BlockMenus.AtBlock {
    @Nullable
    private final ControllerBlockEntity controller;
    private final Player player;
    private int ticks;

    public Payloads.ControllerStatus clientStatus;

    public ControllerMenu(int id, Inventory inv, @Nullable ControllerBlockEntity controller, ContainerLevelAccess access) {
        super(Registration.CONTROLLER_MENU.get(), id, access, controller == null ? null : controller.getBlockState().getBlock());
        this.controller = controller;
        this.player = inv.player;
    }

    public static ControllerMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new ControllerMenu(id, inv, null, ContainerLevelAccess.NULL);
    }

    @Nullable
    public StorageNetwork network() {
        return controller == null ? null : controller.ownNetwork();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (controller == null || !(player instanceof ServerPlayer sp) || ticks++ % 10 != 0) {
            return;
        }
        StorageNetwork net = controller.ownNetwork();
        List<Payloads.JobInfo> jobs = new ArrayList<>();
        long[] stats = {0, 0, 0, 0};
        int types = 0, members = 0, drives = 0;
        boolean active = false;
        Component problem = Component.empty();
        if (net != null) {
            active = net.isActive();
            problem = net.problem();
            members = net.memberCount();
            drives = net.driveCount();
            if (active) {
                stats = net.stats();
                types = net.contents().size();
                for (CraftingJob job : net.jobs()) {
                    jobs.add(new Payloads.JobInfo(job.id(), job.target().toStack(1), job.amount(), job.status(), job.progress(), job.source()));
                }
            }
        }
        Payloads.ControllerStatus status = new Payloads.ControllerStatus(containerId, active, problem, stats[0], stats[1], stats[3],
                types, (int) stats[2], drives, members, jobs);
        PacketDistributor.sendToPlayer(sp, status);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return ItemStack.EMPTY;
    }
}
