package com.xtri6.grimstorage.compat;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.GrimStorage;
import com.xtri6.grimstorage.storage.BulkKey;
import com.xtri6.grimstorage.storage.PipePort;
import com.xtri6.grimstorage.storage.StorageNetwork;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Optional integrations. Mekanism and Ars Nouveau classes are only touched inside their own classes, which are
 * only loaded when that mod is installed, so the mod runs fine without either.
 */
public final class Compat {
    private Compat() {}

    /**
     * A cached connection from an Import/Export Bus to the block it faces, for one optional-mod resource.
     * Both methods return how much was moved.
     */
    public interface BulkLink {
        long pull(StorageNetwork net, long max, Predicate<BulkKey> ok);

        long push(StorageNetwork net, long max, Iterable<BulkKey> candidates);
    }

    public interface Chemicals {
        <T extends BlockEntity & PipePort> void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<T> type);

        BulkLink link(ServerLevel level, BlockPos pos, Direction side, BooleanSupplier valid, Runnable invalidated);

        /** The chemical inside a chemical tank item, for bus filters. */
        @Nullable
        BulkKey chemicalIn(ItemStack stack);

        /** Terminal click with a chemical tank on the cursor: empties it into the network, or fills it with {@code key}. */
        ItemStack click(ServerPlayer player, StorageNetwork network, ItemStack carried, @Nullable BulkKey key);
    }

    public interface Source {
        <T extends BlockEntity & PipePort> void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<T> type);

        BulkLink link(ServerLevel level, BlockPos pos, Direction side, BooleanSupplier valid, Runnable invalidated);
    }

    private static Chemicals chemicals;
    private static Source source;
    private static boolean resolved;

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (ModList.get().isLoaded("mekanism")) {
            try {
                chemicals = new MekanismCompat();
                GrimStorage.LOGGER.info("Mekanism found: chemical storage enabled");
            } catch (Throwable t) {
                GrimStorage.LOGGER.error("Mekanism found but its chemical API could not be loaded; chemical storage disabled", t);
            }
        }
        if (ModList.get().isLoaded("ars_nouveau")) {
            try {
                source = new ArsCompat();
                GrimStorage.LOGGER.info("Ars Nouveau found: Source storage enabled");
            } catch (Throwable t) {
                GrimStorage.LOGGER.error("Ars Nouveau found but its Source API could not be loaded; Source storage disabled", t);
            }
        }
    }

    @Nullable
    public static Chemicals chemicals() {
        resolve();
        return chemicals;
    }

    @Nullable
    public static Source source() {
        resolve();
        return source;
    }
}
