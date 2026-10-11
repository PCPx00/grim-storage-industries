package com.xtri6.grimstorage.storage;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One kind of stored non-item resource: a fluid (with its components), a chemical (by registry id), or the
 * single energy / Source pool.
 */
public final class BulkKey {
    private static final BulkKey ENERGY = new BulkKey(BulkKind.ENERGY, null, FluidStack.EMPTY);
    private static final BulkKey SOURCE = new BulkKey(BulkKind.SOURCE, null, FluidStack.EMPTY);
    private static final BulkKey EXPERIENCE = new BulkKey(BulkKind.EXPERIENCE, null, FluidStack.EMPTY);

    private final BulkKind kind;
    @Nullable
    private final ResourceLocation id;
    private final FluidStack fluid;
    private final int hash;

    private BulkKey(BulkKind kind, @Nullable ResourceLocation id, FluidStack fluid) {
        this.kind = kind;
        this.id = id;
        this.fluid = fluid;
        this.hash = kind == BulkKind.FLUID ? FluidStack.hashFluidAndComponents(fluid) : Objects.hash(kind, id);
    }

    @Nullable
    public static BulkKey fluid(FluidStack stack) {
        return stack == null || stack.isEmpty() ? null : new BulkKey(BulkKind.FLUID, null, stack.copyWithAmount(1));
    }

    public static BulkKey chemical(ResourceLocation id) {
        return new BulkKey(BulkKind.CHEMICAL, id, FluidStack.EMPTY);
    }

    public static BulkKey flat(BulkKind kind) {
        return switch (kind) {
            case ENERGY -> ENERGY;
            case EXPERIENCE -> EXPERIENCE;
            default -> SOURCE;
        };
    }

    public BulkKind kind() {
        return kind;
    }

    /** The chemical id (chemicals only). */
    @Nullable
    public ResourceLocation id() {
        return id;
    }

    /** A 1 mB template of the fluid (fluids only). Never modify it. */
    public FluidStack fluid() {
        return fluid;
    }

    public FluidStack fluid(int amount) {
        return fluid.copyWithAmount(amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BulkKey k) || k.kind != kind || k.hash != hash) {
            return false;
        }
        return kind == BulkKind.FLUID ? FluidStack.isSameFluidSameComponents(fluid, k.fluid) : Objects.equals(id, k.id);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    // ------------------------------------------------------------------ saving

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (kind == BulkKind.FLUID) {
            tag.put("fluid", fluid.save(registries));
        } else if (id != null) {
            tag.putString("id", id.toString());
        }
        return tag;
    }

    @Nullable
    static BulkKey load(BulkKind kind, CompoundTag tag, HolderLookup.Provider registries) {
        return switch (kind) {
            case FLUID -> fluid(FluidStack.parse(registries, tag.getCompound("fluid")).orElse(FluidStack.EMPTY));
            case CHEMICAL -> {
                ResourceLocation rl = ResourceLocation.tryParse(tag.getString("id"));
                yield rl == null ? null : chemical(rl);
            }
            default -> flat(kind);
        };
    }

    // ------------------------------------------------------------------ network

    public static final StreamCodec<RegistryFriendlyByteBuf, BulkKey> STREAM_CODEC = StreamCodec.of(
            (buf, k) -> {
                buf.writeEnum(k.kind);
                if (k.kind == BulkKind.FLUID) {
                    FluidStack.STREAM_CODEC.encode(buf, k.fluid);
                } else if (k.kind == BulkKind.CHEMICAL) {
                    buf.writeResourceLocation(k.id);
                }
            },
            buf -> {
                BulkKind kind = buf.readEnum(BulkKind.class);
                return switch (kind) {
                    case FLUID -> Optional.ofNullable(fluid(FluidStack.STREAM_CODEC.decode(buf))).orElse(flat(BulkKind.ENERGY));
                    case CHEMICAL -> chemical(buf.readResourceLocation());
                    default -> flat(kind);
                };
            });
}
