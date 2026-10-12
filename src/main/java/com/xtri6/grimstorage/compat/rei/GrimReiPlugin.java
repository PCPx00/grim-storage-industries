package com.xtri6.grimstorage.compat.rei;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.xtri6.grimstorage.client.GrimScreen;
import com.xtri6.grimstorage.client.RecipeViewerSearch;
import com.xtri6.grimstorage.compat.ViewerActions;
import com.xtri6.grimstorage.menu.CardMenus;
import com.xtri6.grimstorage.menu.TerminalMenu;

import me.shedaniel.rei.api.client.REIRuntime;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

/** REI: the + button writes cards and fills the terminal grid, items drag onto filter slots, and search syncs. */
@REIPluginClient
public final class GrimReiPlugin implements REIClientPlugin {
    private static final String CRAFTING = "minecraft:plugins/crafting";

    @Override
    public void registerTransferHandlers(TransferHandlerRegistry reg) {
        reg.register(context -> {
            AbstractContainerScreen<?> screen = context.getContainerScreen();
            Display d = context.getDisplay();
            boolean crafting = CRAFTING.equals(d.getCategoryIdentifier().toString());
            boolean recipeCard = screen.getMenu() instanceof CardMenus.Recipe && crafting;
            boolean terminal = screen.getMenu() instanceof TerminalMenu && crafting;
            boolean processing = screen.getMenu() instanceof CardMenus.Processing;
            if (!recipeCard && !terminal && !processing) {
                return TransferHandler.Result.createNotApplicable();
            }
            if (!context.isActuallyCrafting()) {
                return TransferHandler.Result.createSuccessful();
            }
            boolean done;
            if (processing) {
                List<List<ItemStack>> in = new ArrayList<>();
                for (EntryIngredient ing : d.getInputEntries()) {
                    List<ItemStack> options = items(ing);
                    if (!options.isEmpty()) {
                        in.add(options);
                    }
                }
                List<ItemStack> out = new ArrayList<>();
                for (EntryIngredient ing : d.getOutputEntries()) {
                    List<ItemStack> options = items(ing);
                    if (!options.isEmpty()) {
                        out.add(options.get(0));
                    }
                }
                done = ViewerActions.processingCard(screen, in, out);
            } else {
                var id = d.getDisplayLocation().orElse(null);
                done = recipeCard ? ViewerActions.recipeCard(screen, id) : ViewerActions.terminalGrid(screen, id);
            }
            return done ? TransferHandler.Result.createSuccessful().blocksFurtherHandling() : TransferHandler.Result.createNotApplicable();
        });
    }

    private static List<ItemStack> items(EntryIngredient ing) {
        List<ItemStack> out = new ArrayList<>();
        for (EntryStack<?> e : ing) {
            if (e.getValue() instanceof ItemStack s && !s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    @Override
    public void registerScreens(ScreenRegistry reg) {
        reg.registerDraggableStackVisitor(new DraggableStackVisitor<Screen>() {
            @Override
            public <R extends Screen> boolean isHandingScreen(R screen) {
                return screen instanceof GrimScreen<?>;
            }

            @Override
            public DraggedAcceptorResult acceptDraggedStack(DraggingContext<Screen> context, DraggableStack stack) {
                if (context.getScreen() instanceof AbstractContainerScreen<?> screen && stack.getStack().getValue() instanceof ItemStack item
                        && ViewerActions.dropOnGhost(screen, item, context.getCurrentPosition().x, context.getCurrentPosition().y)) {
                    return DraggedAcceptorResult.CONSUMED;
                }
                return DraggedAcceptorResult.PASS;
            }

            @Override
            public Stream<BoundsProvider> getDraggableAcceptingBounds(DraggingContext<Screen> context, DraggableStack stack) {
                return Stream.empty();
            }
        });
        RecipeViewerSearch.connect(() -> {
            var field = REIRuntime.getInstance().getSearchTextField();
            return field == null ? null : field.getText();
        }, text -> {
            var field = REIRuntime.getInstance().getSearchTextField();
            if (field != null) {
                field.setText(text);
            }
        });
    }
}
