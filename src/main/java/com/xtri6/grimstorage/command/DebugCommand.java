package com.xtri6.grimstorage.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.xtri6.grimstorage.GrimStorage;
import com.xtri6.grimstorage.item.WirelessTerminalItem;
import com.xtri6.grimstorage.storage.DebugLog;
import com.xtri6.grimstorage.storage.NetworkManager;
import com.xtri6.grimstorage.storage.StorageNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /grimstorage debug}: writes everything about a network (its blocks, cells, machines, recipes, running jobs,
 * recent problems and the mod list) to a text file to attach to a bug report. Look at any block of the network, or
 * carry a linked wireless terminal. Operators can also name a position.
 */
public final class DebugCommand {
    private DebugCommand() {}

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("grimstorage")
                .then(Commands.literal("debug")
                        .executes(DebugCommand::fromPlayer)
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .requires(s -> s.hasPermission(2))
                                .executes(c -> report(c.getSource(), c.getSource().getLevel(), BlockPosArgument.getLoadedBlockPos(c, "pos"))))));
    }

    private static int fromPlayer(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        if (!(src.getEntity() instanceof ServerPlayer p)) {
            src.sendFailure(Component.translatable("command.grimstorage.debug.where"));
            return 0;
        }
        BlockPos look = lookedAt(p);
        StorageNetwork net = look == null ? null : NetworkManager.getAny(p.serverLevel(), look);
        if (net == null) {
            net = WirelessTerminalItem.findCarried(p);
        }
        if (net == null) {
            src.sendFailure(Component.translatable("command.grimstorage.debug.none"));
            return 0;
        }
        if (!net.allows(p)) {
            src.sendFailure(Component.translatable("message.grimstorage.access.denied", net.ownerName()));
            return 0;
        }
        return write(src, net);
    }

    private static int report(CommandSourceStack src, ServerLevel level, BlockPos pos) {
        StorageNetwork net = NetworkManager.getAny(level, pos);
        if (net == null) {
            src.sendFailure(Component.translatable("command.grimstorage.debug.none"));
            return 0;
        }
        return write(src, net);
    }

    @Nullable
    private static BlockPos lookedAt(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getViewVector(1f).scale(8));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    private static int write(CommandSourceStack src, StorageNetwork network) {
        List<String> lines = new ArrayList<>();
        String version = ModList.get().getModContainerById(GrimStorage.MODID).map(m -> m.getModInfo().getVersion().toString()).orElse("?");
        lines.add("Grim Storage Industries " + version + " debug report");
        lines.add("Made " + LocalDateTime.now() + " by " + src.getTextName());
        lines.add("Minecraft " + net.minecraft.SharedConstants.getCurrentVersion().getName() + ", NeoForge "
                + ModList.get().getModContainerById("neoforge").map(m -> m.getModInfo().getVersion().toString()).orElse("?"));
        lines.add("");
        lines.add("== Network");
        try {
            lines.addAll(network.debugReport());
        } catch (RuntimeException e) {
            lines.add("(could not read all of it: " + e + ")");
        }
        lines.add("");
        lines.add("== Recent notes (newest last)");
        List<String> notes = DebugLog.lines();
        lines.addAll(notes.isEmpty() ? List.of("(none)") : notes);
        lines.add("");
        lines.add("== Mods (" + ModList.get().size() + ")");
        ModList.get().getSortedMods().forEach(m -> lines.add("  " + m.getModId() + " " + m.getModInfo().getVersion()));
        Path dir = FMLPaths.GAMEDIR.get().resolve("grimstorage-debug");
        Path file = dir.resolve("network-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
        try {
            Files.createDirectories(dir);
            Files.write(file, lines);
        } catch (Exception e) {
            GrimStorage.LOGGER.error("Could not write the debug report", e);
            src.sendFailure(Component.translatable("command.grimstorage.debug.failed", e.getMessage()));
            return 0;
        }
        Path shown = FMLPaths.GAMEDIR.get().relativize(file);
        Component link = Component.literal(shown.toString()).withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.toAbsolutePath().toString()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("command.grimstorage.debug.open"))));
        src.sendSuccess(() -> Component.translatable("command.grimstorage.debug.done", link), false);
        return 1;
    }
}
