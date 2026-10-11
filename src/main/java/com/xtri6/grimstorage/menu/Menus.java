package com.xtri6.grimstorage.menu;

import com.xtri6.grimstorage.block.entity.AssemblerBlockEntity;
import com.xtri6.grimstorage.block.entity.BusBlockEntity;
import com.xtri6.grimstorage.block.entity.ControllerBlockEntity;
import com.xtri6.grimstorage.block.entity.DriveBlockEntity;
import com.xtri6.grimstorage.block.entity.FilterBlockEntity;
import com.xtri6.grimstorage.block.entity.ProcessingInterfaceBlockEntity;
import com.xtri6.grimstorage.block.entity.SoulInfuserBlockEntity;
import com.xtri6.grimstorage.block.entity.StockKeeperBlockEntity;
import com.xtri6.grimstorage.item.WirelessTerminalItem;
import com.xtri6.grimstorage.storage.NetworkManager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Opens every screen in the mod, server side. */
public final class Menus {
    private Menus() {}

    private static Component title(ServerPlayer player, BlockPos pos) {
        return player.level().getBlockState(pos).getBlock().getName();
    }

    public static void openDrive(ServerPlayer p, BlockPos pos) {
        if (p.level().getBlockEntity(pos) instanceof DriveBlockEntity be) {
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> BlockMenus.Drive.server(id, inv, be), title(p, pos)), pos);
        }
    }

    public static void openAssembler(ServerPlayer p, BlockPos pos) {
        if (p.level().getBlockEntity(pos) instanceof AssemblerBlockEntity be) {
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> BlockMenus.Assembler.server(id, inv, be), title(p, pos)), pos);
        }
    }

    public static void openProcessing(ServerPlayer p, BlockPos pos) {
        if (p.level().getBlockEntity(pos) instanceof ProcessingInterfaceBlockEntity be) {
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> BlockMenus.Processing.server(id, inv, be), title(p, pos)), pos);
        }
    }

    public static void openFilter(ServerPlayer p, BlockPos pos) {
        if (p.level().getBlockEntity(pos) instanceof FilterBlockEntity be) {
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> BlockMenus.Filter.server(id, inv, be), title(p, pos)), pos);
        }
    }

    public static void openBus(ServerPlayer p, BlockPos pos) {
        if (p.level().getBlockEntity(pos) instanceof BusBlockEntity be) {
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> BlockMenus.Bus.server(id, inv, be.logic(),
                    ContainerLevelAccess.create(p.level(), pos), be.getBlockState().getBlock()), title(p, pos)), pos);
        }
    }

    /** A bus mounted on one side of a Soul Cable. */
    public static void openCableBus(ServerPlayer p, BlockPos pos, net.minecraft.core.Direction side) {
        if (p.level().getBlockEntity(pos) instanceof com.xtri6.grimstorage.block.entity.CableBusBlockEntity be && be.part(side) != null) {
            com.xtri6.grimstorage.block.entity.BusLogic logic = be.part(side);
            Component name = Component.translatable(logic.isExport() ? "block.grimstorage.export_bus" : "block.grimstorage.import_bus");
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> BlockMenus.Bus.server(id, inv, logic,
                    ContainerLevelAccess.create(p.level(), pos), be.getBlockState().getBlock()), name), pos);
        }
    }

    public static void openStockKeeper(ServerPlayer p, BlockPos pos) {
        if (p.level().getBlockEntity(pos) instanceof StockKeeperBlockEntity be) {
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> BlockMenus.StockKeeper.server(id, inv, be), title(p, pos)), pos);
        }
    }

    public static void openInfuser(ServerPlayer p, BlockPos pos) {
        if (p.level().getBlockEntity(pos) instanceof SoulInfuserBlockEntity be) {
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> BlockMenus.Infuser.server(id, inv, be), title(p, pos)), pos);
        }
    }

    public static void openController(ServerPlayer p, BlockPos pos) {
        if (p.level().getBlockEntity(pos) instanceof ControllerBlockEntity be) {
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ControllerMenu(id, inv, be,
                    ContainerLevelAccess.create(p.level(), pos)), title(p, pos)), pos);
        }
    }

    public static void openTerminal(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        Block block = level.getBlockState(pos).getBlock();
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new TerminalMenu(id, inv,
                () -> NetworkManager.get(level, pos),
                player -> level.getBlockState(pos).is(block) && player.distanceToSqr(pos.getCenter()) <= 64.0), title(p, pos)), pos);
    }

    public static void openWirelessTerminal(ServerPlayer p, InteractionHand hand) {
        ItemStack held = p.getItemInHand(hand);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new TerminalMenu(id, inv,
                () -> {
                    ItemStack now = p.getItemInHand(hand);
                    return now.getItem() instanceof WirelessTerminalItem ? WirelessTerminalItem.resolve(p, now) : null;
                },
                player -> player.getItemInHand(hand).getItem() instanceof WirelessTerminalItem
                        && WirelessTerminalItem.resolve(p, player.getItemInHand(hand)) != null),
                held.getHoverName()), buf -> {});
    }

    public static void openRecipeCard(ServerPlayer p, InteractionHand hand) {
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new CardMenus.Recipe(id, inv, hand), p.getItemInHand(hand).getHoverName()),
                buf -> buf.writeEnum(hand));
    }

    public static void openProcessingCard(ServerPlayer p, InteractionHand hand) {
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new CardMenus.Processing(id, inv, hand), p.getItemInHand(hand).getHoverName()),
                buf -> buf.writeEnum(hand));
    }
}
