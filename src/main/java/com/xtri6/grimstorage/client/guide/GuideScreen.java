package com.xtri6.grimstorage.client.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.xtri6.grimstorage.client.ui.GrimButton;
import com.xtri6.grimstorage.client.ui.Theme;
import com.xtri6.grimstorage.client.ui.ThemeToggle;
import com.xtri6.grimstorage.client.ui.Ui;
import com.xtri6.grimstorage.guide.GuideData;
import com.xtri6.grimstorage.recipe.InfusingRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

import org.lwjgl.glfw.GLFW;

/**
 * The Grim Storage Guide. Chapters down the left, pages on the right. Long pages flow onto extra pages
 * automatically, and recipes are read live from the game, so a pack that changes a recipe changes the book too.
 */
public class GuideScreen extends Screen {
    private static final int W = 336;
    private static final int H = 212;
    private static final int SIDE = 108;
    private static final int ROW = 14;
    private static final int PX = SIDE + 10;
    private static final int PW = W - PX - 10;
    private static final int TEXT_TOP = 26;
    private static final int BOTTOM = H - 24;
    private static final int LINE = 10;
    private static final int GAP = 4;
    private static final int ITEMS_H = 24;
    private static final int RECIPE_H = 64;

    private static int lastChapter;
    private static int lastPage;

    /** One drawn line: text, how far it is indented, whether it starts a bullet; null text = paragraph gap. */
    private record Line(@Nullable FormattedCharSequence text, int indent, boolean bullet) {
        int height() {
            return text == null ? GAP : LINE;
        }
    }

    /** A screen page: one JSON page may become several of these when its text is long. */
    private record View(String title, List<Line> lines, List<ItemStack> items, @Nullable RecipeHolder<?> recipe) {}

    private List<GuideData.Chapter> chapters = List.of();
    private final List<List<View>> views = new ArrayList<>();
    private int chapter = lastChapter;
    private int page = lastPage;
    private int left;
    private int top;
    private GrimButton prev;
    private GrimButton next;
    private ItemStack hoveredStack = ItemStack.EMPTY;

    public GuideScreen() {
        super(Component.translatable("item.grimstorage.guide_book"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new GuideScreen());
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        chapters = GuideBook.load();
        views.clear();
        for (GuideData.Chapter c : chapters) {
            List<View> list = new ArrayList<>();
            for (GuideData.Page p : c.pages()) {
                list.addAll(layout(p));
            }
            if (list.isEmpty()) {
                list.add(new View(c.title(), List.of(), List.of(), null));
            }
            views.add(list);
        }
        if (chapter >= views.size()) {
            chapter = 0;
            page = 0;
        }
        if (!views.isEmpty() && page >= views.get(chapter).size()) {
            page = 0;
        }
        prev = addRenderableWidget(new GrimButton(left + PX, top + H - 19, 22, 14, Component.literal("<"), b -> flip(-1)));
        next = addRenderableWidget(new GrimButton(left + W - 32, top + H - 19, 22, 14, Component.literal(">"), b -> flip(1)));
        addRenderableWidget(new ThemeToggle(left + W - 16, top + 6));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ layout

    /** Parses **bold** and `highlight` markers into a styled line of text. */
    private static Component styled(String text, Theme t) {
        MutableComponent out = Component.empty();
        StringBuilder buf = new StringBuilder();
        boolean bold = false, code = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean toggleBold = ch == '*' && i + 1 < text.length() && text.charAt(i + 1) == '*';
            if (toggleBold || ch == '`') {
                if (!buf.isEmpty()) {
                    Style st = Style.EMPTY.withBold(bold);
                    if (code) {
                        st = st.withColor(t.accent() & 0xFFFFFF);
                    }
                    out.append(Component.literal(buf.toString()).withStyle(st));
                    buf.setLength(0);
                }
                if (toggleBold) {
                    bold = !bold;
                    i++;
                } else {
                    code = !code;
                }
                continue;
            }
            buf.append(ch);
        }
        if (!buf.isEmpty()) {
            Style st = Style.EMPTY.withBold(bold);
            if (code) {
                st = st.withColor(t.accent() & 0xFFFFFF);
            }
            out.append(Component.literal(buf.toString()).withStyle(st));
        }
        return out;
    }

    private List<View> layout(GuideData.Page p) {
        Theme t = Theme.get();
        List<Line> lines = new ArrayList<>();
        String[] paragraphs = p.text().split("\n");
        for (int i = 0; i < paragraphs.length; i++) {
            String para = paragraphs[i];
            boolean bullet = para.startsWith("- ");
            int indent = bullet ? 8 : 0;
            List<FormattedCharSequence> split = font.split(styled(bullet ? para.substring(2) : para, t), PW - indent);
            for (int j = 0; j < split.size(); j++) {
                lines.add(new Line(split.get(j), indent, bullet && j == 0));
            }
            if (i < paragraphs.length - 1 && !(bullet && paragraphs[i + 1].startsWith("- "))) {
                lines.add(new Line(null, 0, false));
            }
        }
        List<ItemStack> items = new ArrayList<>();
        for (ResourceLocation id : p.items()) {
            var item = BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR) {
                items.add(new ItemStack(item));
            }
        }
        RecipeHolder<?> recipe = null;
        if (p.recipe() != null && minecraft.level != null) {
            Optional<RecipeHolder<?>> found = minecraft.level.getRecipeManager().byKey(p.recipe());
            recipe = found.orElse(null);
        }
        int extras = (items.isEmpty() ? 0 : ITEMS_H) + (recipe == null ? 0 : RECIPE_H);
        int full = BOTTOM - TEXT_TOP;
        int last = full - extras;

        List<View> out = new ArrayList<>();
        List<Line> current = new ArrayList<>();
        int used = 0;
        int remaining = lines.stream().mapToInt(Line::height).sum();
        for (Line line : lines) {
            int capacity = remaining <= last ? last : full;
            if (used + line.height() > capacity && !current.isEmpty()) {
                out.add(new View(p.title(), current, List.of(), null));
                current = new ArrayList<>();
                used = 0;
                if (line.text() == null) {
                    remaining -= line.height();
                    continue;
                }
            }
            current.add(line);
            used += line.height();
            remaining -= line.height();
        }
        out.add(new View(p.title(), current, items, recipe));
        return out;
    }

    // ------------------------------------------------------------------ navigation

    private void flip(int dir) {
        if (views.isEmpty()) {
            return;
        }
        int p = page + dir;
        int c = chapter;
        if (p < 0) {
            if (c == 0) {
                return;
            }
            c--;
            p = views.get(c).size() - 1;
        } else if (p >= views.get(c).size()) {
            if (c == views.size() - 1) {
                return;
            }
            c++;
            p = 0;
        }
        go(c, p);
    }

    private void go(int c, int p) {
        if (c != chapter || p != page) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        }
        chapter = c;
        page = p;
        lastChapter = c;
        lastPage = p;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < chapters.size(); i++) {
            int y = top + 22 + i * ROW;
            if (mx >= left + 6 && mx < left + SIDE && my >= y && my < y + ROW) {
                go(i, 0);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        flip(sy > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_D) {
            flip(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_A) {
            flip(-1);
            return true;
        }
        if (minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ drawing

    /** Widgets and tooltips go on top of the book, which is drawn as part of the background pass. */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (!hoveredStack.isEmpty()) {
            g.renderTooltip(font, hoveredStack, mouseX, mouseY);
        }
    }

    private void drawBook(GuiGraphics g, int mouseX, int mouseY) {
        Theme t = Theme.get();
        hoveredStack = ItemStack.EMPTY;
        Ui.panel(g, t, left, top, W, H);
        // sidebar
        g.fill(left + SIDE + 2, top + 6, left + SIDE + 3, top + H - 6, Theme.withAlpha(t.border() & 0xFFFFFF, 0x90));
        g.drawString(font, Component.translatable("gui.grimstorage.guide.contents"), left + 8, top + 8, t.textDim(), false);
        for (int i = 0; i < chapters.size(); i++) {
            GuideData.Chapter c = chapters.get(i);
            int y = top + 22 + i * ROW;
            boolean sel = i == chapter;
            boolean hover = mouseX >= left + 6 && mouseX < left + SIDE && mouseY >= y && mouseY < y + ROW;
            if (sel || hover) {
                g.fill(left + 6, y, left + SIDE - 1, y + ROW - 1, sel ? Theme.withAlpha(t.accentDim(), 0xA0) : t.hover());
            }
            if (sel) {
                g.fill(left + 6, y, left + 7, y + ROW - 1, t.accent());
            }
            g.pose().pushPose();
            g.pose().translate(left + 9, y + 1, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(c.icon())), 0, 0);
            g.pose().popPose();
            String name = Ui.fit(font, c.title(), SIDE - 30);
            g.drawString(font, name, left + 23, y + 3, sel ? t.text() : t.textDim(), false);
        }

        if (views.isEmpty()) {
            g.drawString(font, Component.translatable("gui.grimstorage.guide.missing"), left + PX, top + 30, t.danger(), false);
            return;
        }

        List<View> pages = views.get(chapter);
        View v = pages.get(page);
        g.drawString(font, Ui.fit(font, v.title(), PW - 20), left + PX, top + 9, t.text(), false);
        Ui.rule(g, t, left + PX, top + 20, PW);
        int y = top + TEXT_TOP;
        for (Line line : v.lines()) {
            if (line.text() != null) {
                if (line.bullet()) {
                    g.fill(left + PX + 2, y + 3, left + PX + 5, y + 6, t.accent());
                }
                g.drawString(font, line.text(), left + PX + line.indent(), y, t.text(), false);
            }
            y += line.height();
        }

        int by = top + BOTTOM - (v.recipe() == null ? 0 : RECIPE_H) - (v.items().isEmpty() ? 0 : ITEMS_H);
        if (!v.items().isEmpty()) {
            int x = left + PX + 1;
            for (ItemStack s : v.items()) {
                if (x + 17 > left + PX + PW) {
                    break;
                }
                Ui.slot(g, t, x, by + 3);
                drawStack(g, s, x, by + 3, mouseX, mouseY);
                x += 19;
            }
            by += ITEMS_H;
        }
        if (v.recipe() != null) {
            drawRecipe(g, t, v.recipe(), left + PX, by, mouseX, mouseY);
        }

        // footer
        int total = 0, index = 0;
        for (int c = 0; c < views.size(); c++) {
            if (c == chapter) {
                index = total + page;
            }
            total += views.get(c).size();
        }
        prev.active = chapter > 0 || page > 0;
        next.active = chapter < views.size() - 1 || page < pages.size() - 1;
        String counter = Component.translatable("gui.grimstorage.guide.page", page + 1, pages.size()).getString();
        g.drawString(font, counter, left + PX + (PW - font.width(counter)) / 2, top + H - 16, t.textDim(), false);
        Ui.bar(g, t, left + PX + 28, top + H - 5, PW - 56, 2, total <= 1 ? 1 : (float) index / (total - 1), t.accentDim());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            renderPanorama(g, partialTick);
        }
        renderTransparentBackground(g);
        drawBook(g, mouseX, mouseY);
    }

    private void drawStack(GuiGraphics g, ItemStack s, int x, int y, int mouseX, int mouseY) {
        if (s.isEmpty()) {
            return;
        }
        g.renderItem(s, x, y);
        g.renderItemDecorations(font, s, x, y);
        if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) {
            hoveredStack = s;
        }
    }

    /** Ingredients that accept several items cycle through them once a second. */
    private static ItemStack cycle(Ingredient ing) {
        ItemStack[] options = ing.getItems();
        if (options.length == 0) {
            return ItemStack.EMPTY;
        }
        return options[(int) ((System.currentTimeMillis() / 1000) % options.length)];
    }

    private void drawRecipe(GuiGraphics g, Theme t, RecipeHolder<?> holder, int x, int y, int mouseX, int mouseY) {
        Recipe<?> recipe = holder.value();
        ItemStack result = recipe.getResultItem(minecraft.level.registryAccess());
        g.fill(x, y + 2, x + PW, y + RECIPE_H - 2, Theme.withAlpha(t.slot(), 0x50));
        int gx = x + 6, gy = y + 8;
        String kind;
        if (recipe instanceof InfusingRecipe inf) {
            kind = "gui.grimstorage.guide.recipe.infusing";
            for (int i = 0; i < 3; i++) {
                Ui.slot(g, t, gx, gy + i * 18 - 1);
                if (i < inf.ingredients().size()) {
                    drawStack(g, cycle(inf.ingredients().get(i)), gx, gy + i * 18 - 1, mouseX, mouseY);
                }
            }
        } else if (recipe instanceof AbstractCookingRecipe cook) {
            kind = "gui.grimstorage.guide.recipe.smelting";
            Ui.slot(g, t, gx + 18, gy + 17);
            drawStack(g, cycle(cook.getIngredients().get(0)), gx + 18, gy + 17, mouseX, mouseY);
        } else {
            kind = "gui.grimstorage.guide.recipe.crafting";
            int w = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
            List<Ingredient> ings = recipe.getIngredients();
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    Ui.slot(g, t, gx + c * 18, gy + r * 18 - 1);
                }
            }
            for (int i = 0; i < ings.size(); i++) {
                int c = i % w, r = i / w;
                if (r < 3 && c < 3) {
                    drawStack(g, cycle(ings.get(i)), gx + c * 18, gy + r * 18 - 1, mouseX, mouseY);
                }
            }
        }
        Ui.arrow(g, t, gx + 64, gy + 21, 1, false);
        Ui.bigSlot(g, t, gx + 98, gy + 17);
        drawStack(g, result, gx + 98, gy + 17, mouseX, mouseY);
        Component label = Component.translatable(kind).withStyle(ChatFormatting.ITALIC);
        g.drawString(font, label, x + PW - 6 - font.width(label), gy + 40, t.textFaint(), false);
    }
}
