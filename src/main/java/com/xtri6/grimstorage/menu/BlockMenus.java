package com.xtri6.grimstorage.menu;

import com.xtri6.grimstorage.Registration;
import com.xtri6.grimstorage.block.entity.AssemblerBlockEntity;
import com.xtri6.grimstorage.block.entity.BusLogic;
import com.xtri6.grimstorage.block.entity.DriveBlockEntity;
import com.xtri6.grimstorage.block.entity.FilterBlockEntity;
import com.xtri6.grimstorage.block.entity.GhostContainer;
import com.xtri6.grimstorage.block.entity.InterfaceBlockEntity;
import com.xtri6.grimstorage.block.entity.ProcessingInterfaceBlockEntity;
import com.xtri6.grimstorage.block.entity.SoulInfuserBlockEntity;
import com.xtri6.grimstorage.block.entity.StockKeeperBlockEntity;
import com.xtri6.grimstorage.item.ProcessingCardItem;
import com.xtri6.grimstorage.item.RecipeCardItem;
import com.xtri6.grimstorage.storage.BulkKind;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Menus for the block GUIs that are mostly slots. Each has a server constructor and a client one. */
public final class BlockMenus {
    private BlockMenus() {}

    private static ItemStackHandler dummy(int size, java.util.function.Predicate<ItemStack> valid) {
        return new ItemStackHandler(size) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return valid.test(stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
    }

    /** Base for menus tied to a block: closes when you walk away or the block is broken. */
    public abstract static class AtBlock extends GrimMenu {
        protected final ContainerLevelAccess access;
        private final Block block;

        protected AtBlock(net.minecraft.world.inventory.MenuType<?> type, int id, ContainerLevelAccess access, Block block) {
            super(type, id);
            this.access = access;
            this.block = block;
        }

        @Override
        public boolean stillValid(Player player) {
            return block == null || stillValid(access, player, block);
        }
    }

    // ------------------------------------------------------------------ drive
    public static class Drive extends AtBlock {
        public Drive(int id, Inventory inv, IItemHandler cells, ContainerLevelAccess access, Block block) {
            super(Registration.DRIVE_MENU.get(), id, access, block);
            for (int i = 0; i < DriveBlockEntity.SLOTS; i++) {
                addSlot(new SlotItemHandler(cells, i, 53 + (i % 4) * 18, 26 + (i / 4) * 18));
            }
            addPlayerInventory(inv, 8, 84);
        }

        public static Drive server(int id, Inventory inv, DriveBlockEntity be) {
            return new Drive(id, inv, be.cells(), ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()), be.getBlockState().getBlock());
        }

        public static Drive client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
            return new Drive(id, inv, dummy(DriveBlockEntity.SLOTS, DriveBlockEntity::isCell), ContainerLevelAccess.NULL, null);
        }
    }

    // ------------------------------------------------------------------ assembler
    public static class Assembler extends AtBlock {
        public Assembler(int id, Inventory inv, IItemHandler cards, IItemHandler upgrades, ContainerLevelAccess access, Block block) {
            super(Registration.ASSEMBLER_MENU.get(), id, access, block);
            for (int i = 0; i < AssemblerBlockEntity.CARD_SLOTS; i++) {
                addSlot(new SlotItemHandler(cards, i, 44 + (i % 3) * 18, 17 + (i / 3) * 18));
            }
            for (int i = 0; i < AssemblerBlockEntity.UPGRADE_SLOTS; i++) {
                addSlot(new SlotItemHandler(upgrades, i, 134 + (i % 2) * 18, 26 + (i / 2) * 18));
            }
            addPlayerInventory(inv, 8, 84);
        }

        public static Assembler server(int id, Inventory inv, AssemblerBlockEntity be) {
            return new Assembler(id, inv, be.cardHandler(), be.upgradeHandler(),
                    ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()), be.getBlockState().getBlock());
        }

        public static Assembler client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
            return new Assembler(id, inv, dummy(9, s -> s.getItem() instanceof RecipeCardItem),
                    dummy(4, s -> s.is(Registration.SPEED_UPGRADE.get())), ContainerLevelAccess.NULL, null);
        }
    }

    // ------------------------------------------------------------------ processing interface
    public static class Processing extends AtBlock {
        public Processing(int id, Inventory inv, IItemHandler cards, ContainerLevelAccess access, Block block) {
            super(Registration.PROCESSING_MENU.get(), id, access, block);
            for (int i = 0; i < ProcessingInterfaceBlockEntity.SLOTS; i++) {
                addSlot(new SlotItemHandler(cards, i, 108 + (i % 3) * 18, 17 + (i / 3) * 18));
            }
            addPlayerInventory(inv, 8, 84);
        }

        public static Processing server(int id, Inventory inv, ProcessingInterfaceBlockEntity be) {
            return new Processing(id, inv, be.cardHandler(), ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()),
                    be.getBlockState().getBlock());
        }

        public static Processing client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
            return new Processing(id, inv, dummy(9, s -> s.getItem() instanceof ProcessingCardItem), ContainerLevelAccess.NULL, null);
        }
    }

    // ------------------------------------------------------------------ machine bus
    public static class MachineBus extends AtBlock {
        public static final int LEARN = 0;
        public static final int FUEL = 1;
        public final BlockPos pos;
        private final ContainerData data;
        @javax.annotation.Nullable
        private final com.xtri6.grimstorage.block.entity.MachineBusBlockEntity be;
        private final boolean wireless;

        public boolean wireless() {
            return wireless;
        }

        public MachineBus(int id, Inventory inv, IItemHandler cards, @javax.annotation.Nullable IItemHandler range, ContainerData data, BlockPos pos,
                          ContainerLevelAccess access, Block block,
                          @javax.annotation.Nullable com.xtri6.grimstorage.block.entity.MachineBusBlockEntity be) {
            super(Registration.MACHINE_BUS_MENU.get(), id, access, block);
            this.pos = pos;
            this.data = data;
            this.be = be;
            for (int i = 0; i < com.xtri6.grimstorage.block.entity.MachineBusBlockEntity.SLOTS; i++) {
                addSlot(new SlotItemHandler(cards, i, 8 + (i % 3) * 18, 34 + (i / 3) * 18));
            }
            this.wireless = range != null;
            if (range != null) {
                addSlot(new SlotItemHandler(range, 0, 8, 106));
            }
            addDataSlots(data);
            addPlayerInventory(inv, 8, range != null ? 136 : 124);
        }

        public static MachineBus server(int id, Inventory inv, com.xtri6.grimstorage.block.entity.MachineBusBlockEntity be) {
            ContainerData data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case 0 -> (be.learns() ? 1 : 0) | (be.fuels() ? 2 : 0);
                        case 1 -> be.recipeCount();
                        default -> be instanceof com.xtri6.grimstorage.block.entity.WirelessMachineBusBlockEntity w ? w.wirelessStatus() : 0;
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 3;
                }
            };
            IItemHandler range = be instanceof com.xtri6.grimstorage.block.entity.WirelessMachineBusBlockEntity w ? w.rangeHandler() : null;
            return new MachineBus(id, inv, be.cardHandler(), range, data, be.getBlockPos(),
                    ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()), be.getBlockState().getBlock(), be);
        }

        public static MachineBus client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            boolean wireless = buf.readBoolean();
            return new MachineBus(id, inv, dummy(9, s -> s.getItem() instanceof ProcessingCardItem),
                    wireless ? dummy(1, s -> s.getItem() instanceof com.xtri6.grimstorage.item.RangeCardItem) : null,
                    new SimpleContainerData(3), pos, ContainerLevelAccess.NULL, null, null);
        }

        public boolean learns() {
            return (data.get(0) & 1) != 0;
        }

        public boolean fuels() {
            return (data.get(0) & 2) != 0;
        }

        public int recipes() {
            return data.get(1);
        }

        /** 0 connected, 1 no network, 2 needs a range card. Only wireless machine buses report anything else than 0. */
        public int wirelessStatus() {
            return data.get(2);
        }

        @Override
        public boolean clickMenuButton(Player player, int id) {
            if (be == null) {
                return false;
            }
            if (id == LEARN) {
                be.toggleLearn();
            } else if (id == FUEL) {
                be.toggleFuel();
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ chest link
    public static class ChestLink extends AtBlock {
        public static final int MODE = 0;
        public static final int FIRST = 1;
        public final BlockPos pos;
        private final ContainerData data;
        @javax.annotation.Nullable
        private final com.xtri6.grimstorage.block.entity.ChestLinkBlockEntity be;

        public ChestLink(int id, Inventory inv, ContainerData data, BlockPos pos, ContainerLevelAccess access, Block block,
                         @javax.annotation.Nullable com.xtri6.grimstorage.block.entity.ChestLinkBlockEntity be) {
            super(Registration.CHEST_LINK_MENU.get(), id, access, block);
            this.pos = pos;
            this.data = data;
            this.be = be;
            addDataSlots(data);
        }

        public static ChestLink server(int id, Inventory inv, com.xtri6.grimstorage.block.entity.ChestLinkBlockEntity be) {
            ContainerData data = new ContainerData() {
                @Override
                public int get(int index) {
                    long[] u = be.usage();
                    return switch (index) {
                        case 0 -> be.mode() | (be.fillFirst() ? 4 : 0) | (be.handler() == null ? 8 : 0);
                        case 1 -> lo((int) Math.min(Integer.MAX_VALUE, u[0]));
                        case 2 -> hi((int) Math.min(Integer.MAX_VALUE, u[0]));
                        case 3 -> lo((int) Math.min(Integer.MAX_VALUE, u[1]));
                        default -> hi((int) Math.min(Integer.MAX_VALUE, u[1]));
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 5;
                }
            };
            return new ChestLink(id, inv, data, be.getBlockPos(), ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()),
                    be.getBlockState().getBlock(), be);
        }

        public static ChestLink client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
            return new ChestLink(id, inv, new SimpleContainerData(5), buf.readBlockPos(), ContainerLevelAccess.NULL, null, null);
        }

        public int mode() {
            return data.get(0) & 3;
        }

        public boolean fillFirst() {
            return (data.get(0) & 4) != 0;
        }

        public boolean linked() {
            return (data.get(0) & 8) == 0;
        }

        public int used() {
            return join(data.get(1), data.get(2));
        }

        public int capacity() {
            return join(data.get(3), data.get(4));
        }

        @Override
        public boolean clickMenuButton(Player player, int id) {
            if (be == null) {
                return false;
            }
            if (id == MODE) {
                be.cycleMode();
            } else if (id == FIRST) {
                be.toggleFillFirst();
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ redstone emitter
    public static class Emitter extends AtBlock {
        public static final int MODE = 0;
        private final ContainerData data;
        @javax.annotation.Nullable
        private final com.xtri6.grimstorage.block.entity.RedstoneEmitterBlockEntity be;

        public Emitter(int id, Inventory inv, GhostContainer filter, ContainerData data, ContainerLevelAccess access, Block block,
                       @javax.annotation.Nullable com.xtri6.grimstorage.block.entity.RedstoneEmitterBlockEntity be) {
            super(Registration.EMITTER_MENU.get(), id, access, block);
            this.data = data;
            this.be = be;
            addSlot(new GhostSlot(filter, 0, 26, 24, false));
            addDataSlots(data);
            addPlayerInventory(inv, 8, 84);
        }

        public static Emitter server(int id, Inventory inv, com.xtri6.grimstorage.block.entity.RedstoneEmitterBlockEntity be) {
            ContainerData data = new ContainerData() {
                @Override
                public int get(int index) {
                    long stored = be.stored();
                    int st = (int) Math.max(-1, Math.min(Integer.MAX_VALUE, stored));
                    return switch (index) {
                        case 0 -> be.mode() | (be.emitting() ? 4 : 0);
                        case 1 -> lo(be.amount());
                        case 2 -> hi(be.amount());
                        case 3 -> lo(st);
                        default -> hi(st);
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 5;
                }
            };
            return new Emitter(id, inv, be.filter(), data, ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()), be.getBlockState().getBlock(), be);
        }

        public static Emitter client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
            return new Emitter(id, inv, new GhostContainer(1, false, () -> {}), new SimpleContainerData(5), ContainerLevelAccess.NULL, null, null);
        }

        public int mode() {
            return data.get(0) & 3;
        }

        public boolean emitting() {
            return (data.get(0) & 4) != 0;
        }

        public int amount() {
            return join(data.get(1), data.get(2));
        }

        public int stored() {
            return join(data.get(3), data.get(4));
        }

        public void setAmount(int v) {
            if (be != null) {
                be.setAmount(v);
            }
        }

        @Override
        public boolean clickMenuButton(Player player, int id) {
            if (be != null && id == MODE) {
                be.cycleMode();
                return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ filters (void filter, network interface)
    public static class Filter extends AtBlock {
        /** Which block this is, so the screen can show the right hint (void filter or interface). */
        public final BlockPos pos;
        private final ContainerData exports;
        @javax.annotation.Nullable
        private final InterfaceBlockEntity iface;

        public Filter(int id, Inventory inv, GhostContainer filters, ContainerLevelAccess access, Block block, BlockPos pos,
                      ContainerData exports, @javax.annotation.Nullable InterfaceBlockEntity iface) {
            super(Registration.FILTER_MENU.get(), id, access, block);
            this.pos = pos;
            this.exports = exports;
            this.iface = iface;
            for (int i = 0; i < FilterBlockEntity.SLOTS; i++) {
                addSlot(new GhostSlot(filters, i, 8 + i * 18, 35, false));
            }
            addDataSlots(exports);
            addPlayerInventory(inv, 8, 84);
        }

        public static Filter server(int id, Inventory inv, FilterBlockEntity be) {
            InterfaceBlockEntity iface = be instanceof InterfaceBlockEntity i ? i : null;
            ContainerData data = new ContainerData() {
                @Override
                public int get(int index) {
                    return iface != null && iface.exports(BulkKind.values()[index]) ? 1 : 0;
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return BulkKind.values().length;
                }
            };
            return new Filter(id, inv, be.filters(), ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()), be.getBlockState().getBlock(),
                    be.getBlockPos(), data, iface);
        }

        public static Filter client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
            return new Filter(id, inv, new GhostContainer(FilterBlockEntity.SLOTS, false, () -> {}), ContainerLevelAccess.NULL, null,
                    buf.readBlockPos(), new SimpleContainerData(BulkKind.values().length), null);
        }

        public boolean exports(BulkKind kind) {
            return exports.get(kind.ordinal()) != 0;
        }

        /** Server: the player flipped an export switch. */
        public void toggleExport(BulkKind kind) {
            if (iface != null) {
                iface.toggleExport(kind);
            }
        }
    }

    // ------------------------------------------------------------------ import / export bus
    public static class Bus extends AtBlock {
        public static final int BLACKLIST = 0;
        public static final int MATCH_COMPONENTS = 1;
        public static final int DIRECTION = 2;
        /** Buttons 10+ toggle one resource (see {@link BusLogic#ITEMS_BIT}). */
        public static final int RESOURCE = 10;

        private final ContainerData data;
        @javax.annotation.Nullable
        private final BusLogic be;

        /** Filters run along one row; the upgrade column is on the right, speed above range. */
        public static final int FILTER_Y = 32;
        public static final int SPEED_X = 182;
        public static final int SPEED_Y = 32;
        public static final int RANGE_X = 182;
        public static final int RANGE_Y = 80;
        /** Slot numbers of the speed slot and the range slot (the range slot only exists on wireless buses). */
        public static final int SPEED_SLOT_INDEX = FilterBlockEntity.SLOTS;
        public static final int RANGE_SLOT_INDEX = FilterBlockEntity.SLOTS + 1;
        private final boolean wireless;

        public Bus(int id, Inventory inv, GhostContainer filters, IItemHandler upgrades, boolean wireless, ContainerData data,
                   ContainerLevelAccess access, Block block, @javax.annotation.Nullable BusLogic be) {
            super(Registration.BUS_MENU.get(), id, access, block);
            this.data = data;
            this.be = be;
            this.wireless = wireless;
            for (int i = 0; i < FilterBlockEntity.SLOTS; i++) {
                addSlot(new GhostSlot(filters, i, 8 + i * 18, FILTER_Y, false));
            }
            addSlot(new SlotItemHandler(upgrades, BusLogic.SPEED_SLOT, SPEED_X, SPEED_Y));
            if (wireless) {
                addSlot(new SlotItemHandler(upgrades, BusLogic.RANGE_SLOT, RANGE_X, RANGE_Y));
            }
            addDataSlots(data);
            addPlayerInventory(inv, 8, 136);
        }

        public boolean wireless() {
            return wireless;
        }

        /** A bus block, or a bus on one side of a cable ({@code block} is whatever block hosts it). */
        public static Bus server(int id, Inventory inv, BusLogic be, ContainerLevelAccess access, Block block) {
            ContainerData data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case 0 -> (be.blacklist() ? 1 : 0) | (be.matchComponents() ? 2 : 0) | (be.isExport() ? 4 : 0);
                        case 1 -> be.resources();
                        default -> be.status().ordinal();
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 3;
                }
            };
            return new Bus(id, inv, be.filters(), be.upgrades(), be.wireless(), data, access, block, be);
        }

        public static Bus client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
            buf.readBlockPos();
            boolean wireless = buf.readBoolean();
            return new Bus(id, inv, new GhostContainer(FilterBlockEntity.SLOTS, false, () -> {}),
                    new ItemStackHandler(BusLogic.UPGRADE_SLOTS) {
                        @Override
                        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
                            return slot == BusLogic.SPEED_SLOT ? stack.is(Registration.SPEED_UPGRADE.get())
                                    : stack.getItem() instanceof com.xtri6.grimstorage.item.RangeCardItem;
                        }

                        @Override
                        public int getSlotLimit(int slot) {
                            return slot == BusLogic.SPEED_SLOT ? BusLogic.SPEED_STACK : 1;
                        }
                    }, wireless, new SimpleContainerData(3), ContainerLevelAccess.NULL, null, null);
        }

        public boolean blacklist() {
            return (data.get(0) & 1) != 0;
        }

        public boolean matchComponents() {
            return (data.get(0) & 2) != 0;
        }

        public boolean isExport() {
            return (data.get(0) & 4) != 0;
        }

        public boolean moves(int bit) {
            return (data.get(1) & (1 << bit)) != 0;
        }

        public BusLogic.Status status() {
            BusLogic.Status[] all = BusLogic.Status.values();
            int i = data.get(2);
            return i >= 0 && i < all.length ? all[i] : BusLogic.Status.IDLE;
        }

        /** Server: a button on the screen was pressed. */
        public void press(int button, net.minecraft.server.level.ServerPlayer player) {
            if (be == null) {
                return;
            }
            if (button == DIRECTION) {
                be.switchDirection(player);
            } else if (button == BLACKLIST) {
                be.toggleBlacklist();
            } else if (button == MATCH_COMPONENTS) {
                be.toggleMatchComponents();
            } else if (button >= RESOURCE) {
                be.toggleResource(button - RESOURCE);
            }
        }
    }

    // ------------------------------------------------------------------ stock keeper
    public static class StockKeeper extends AtBlock {
        private final ContainerData data;
        private final StockKeeperBlockEntity be;

        public StockKeeper(int id, Inventory inv, GhostContainer filters, ContainerData data, ContainerLevelAccess access, Block block,
                           StockKeeperBlockEntity be) {
            super(Registration.STOCK_KEEPER_MENU.get(), id, access, block);
            this.data = data;
            this.be = be;
            for (int i = 0; i < StockKeeperBlockEntity.RULES; i++) {
                addSlot(new GhostSlot(filters, i, 8 + i * 18, 20, false));
            }
            addDataSlots(data);
            addPlayerInventory(inv, 8, 84);
        }

        public static StockKeeper server(int id, Inventory inv, StockKeeperBlockEntity be) {
            ContainerData data = new ContainerData() {
                @Override
                public int get(int index) {
                    int rule = index / 3;
                    return switch (index % 3) {
                        case 0 -> lo(be.target(rule));
                        case 1 -> hi(be.target(rule));
                        default -> be.ruleState(rule);
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return StockKeeperBlockEntity.RULES * 3;
                }
            };
            return new StockKeeper(id, inv, be.filters(), data, ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()),
                    be.getBlockState().getBlock(), be);
        }

        public static StockKeeper client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
            return new StockKeeper(id, inv, new GhostContainer(StockKeeperBlockEntity.RULES, false, () -> {}),
                    new SimpleContainerData(StockKeeperBlockEntity.RULES * 3), ContainerLevelAccess.NULL, null, null);
        }

        public int target(int rule) {
            return join(data.get(rule * 3), data.get(rule * 3 + 1));
        }

        public int ruleState(int rule) {
            return data.get(rule * 3 + 2);
        }

        /** Server side: a player typed a new target. */
        public void setTarget(int rule, int target) {
            if (be != null) {
                be.setTarget(rule, target);
            }
        }
    }

    // ------------------------------------------------------------------ soul infuser
    public static class Infuser extends AtBlock {
        private final ContainerData data;

        public Infuser(int id, Inventory inv, IItemHandler items, ContainerData data, ContainerLevelAccess access, Block block) {
            super(Registration.INFUSER_MENU.get(), id, access, block);
            this.data = data;
            addSlot(new SlotItemHandler(items, 0, 38, 17));
            addSlot(new SlotItemHandler(items, 1, 38, 35));
            addSlot(new SlotItemHandler(items, 2, 38, 53));
            addSlot(new SlotItemHandler(items, SoulInfuserBlockEntity.OUTPUT, 116, 35) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
            addDataSlots(data);
            addPlayerInventory(inv, 8, 84);
        }

        public static Infuser server(int id, Inventory inv, SoulInfuserBlockEntity be) {
            return new Infuser(id, inv, be.items(), be.data(), ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()),
                    be.getBlockState().getBlock());
        }

        public static Infuser client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
            return new Infuser(id, inv, new ItemStackHandler(4), new SimpleContainerData(2), ContainerLevelAccess.NULL, null);
        }

        /** 0..1 */
        public float progress() {
            int total = data.get(1);
            return total <= 0 ? 0 : Math.min(1f, (float) data.get(0) / total);
        }
    }
}
