package github.com.gengyoubo.thaumgiesis.client;

import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.client.StationView;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspects;
import github.com.gengyoubo.tcmodern.lib.client.AspectIcons;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * What is drawn over the crucible's scene: the essentia in it, seen through goggles as in Thaumcraft 4, in a row under the caption;
 * the closed recipe book standing by the crucible; and the tooltip of whatever the cursor is on in the open recipe book, which is drawn
 * in the scene by {@link CrucibleRecipeBook}.
 */
final class CrucibleHud {
    private static final String LANG = "thaumgiesis.crucible.";
    /** Milliseconds a change in an aspect takes to rise from the row, and its icon to pulse. */
    private static final long CHANGE_TIME = 900;
    private static final long PULSE_TIME = 300;
    private static final int PANEL = 0xE0221B2A;
    private static final int PANEL_EDGE = 0xFF735B42;
    private static final int PANEL_LIGHT = 0x40C9A97A;
    private static final int TEXT = 0xFFF8E9C7;
    private static final int TEXT_DIM = 0xFFBFA98A;
    private static Map<String, TcAspect> aspectIcons;
    private StationView station;
    @Nullable
    private CrucibleRecipeBook book;
    private CrucibleGoggles goggles;
    private CompoundTag lastEssentia = new CompoundTag();
    private boolean essentiaSeen;
    private List<String> aspectOrder = List.of();
    private final Map<String, Change> changes = new HashMap<>();
    /** How far the closed book has risen under the cursor, 0 to 1. */
    private float closedLift;

    void register(IEventBus bus) {
        bus.addListener(this::render);
    }

    void open(StationView view) {
        station = view;
        book = null;
        goggles = new CrucibleGoggles();
        lastEssentia = new CompoundTag();
        essentiaSeen = false;
        aspectOrder = List.of();
        changes.clear();
        closedLift = 0;
        updateBookNode(view);
    }

    void close(StationView view) {
        if (station == view) {
            book = null;
            updateBookNode(view);
            station = null;
        }
    }

    /** Gives the open book's node its painter, which draws whichever book is out at the time. */
    void sceneReady(StationView view) {
        view.node("recipe_book").ifPresent(node -> node.setPainter((pose, buffers, light) ->
                CrucibleRecipeBook.paintClosed(pose, buffers, light, closedLift * 1.2F)));
        view.node(CrucibleRecipeBook.NODE).ifPresent(node -> {
            node.setVisible(book != null && book.isShown());
            node.setPainter(new NodePainter() {
                @Override
                public void paint(PoseStack pose, MultiBufferSource buffers, int light) {
                    if (book != null) {
                        book.paint(pose, buffers, light);
                    }
                }

                @Override
                public boolean smooth() {
                    return true;
                }
            });
        });
        updateBookNode(view);
    }

    /** Opens the book (reading the recipes afresh, as research may have been done since) or shuts it. */
    void toggleBook(StationView view) {
        if (station != view) {
            return;
        }
        if (book == null) {
            book = CrucibleRecipeBook.load();
        }
        book.setWanted(!book.isWanted());
        updateBookNode(view);
    }

    void frame(StationView view, float seconds) {
        if (station != view) {
            return;
        }
        boolean hovered = view.node("recipe_book").map(node -> node.isHovered()).orElse(false);
        closedLift = Mth.lerp(Math.min(1, seconds * 12), closedLift, hovered ? 1 : 0);
        if (book == null) {
            return;
        }
        CompoundTag essentia = view.state().data().getCompound("essentia");
        book.frame(view, seconds, seeing() ? essentia : null);
        if (!book.isShown()) {
            book = null;
            updateBookNode(view);
        }
    }

    void clickBook() {
        if (book != null && book.isWanted()) {
            book.click();
        }
    }

    void scrollBook(double delta) {
        if (book != null && book.isWanted() && delta != 0) {
            book.turn(delta > 0 ? -1 : 1);
        }
    }

    /** The closed book stands by the crucible while the open one is put away. */
    void updateBookNode(StationView view) {
        view.node("recipe_book").ifPresent(node -> node.setVisible(station != view || book == null || !book.isWanted()));
    }

    private boolean active(Screen screen) {
        return station != null && DiegesisClient.isStationScreen(screen)
                && DiegesisClient.focusedStation().orElse(null) == station;
    }

    private boolean seeing() {
        var player = Minecraft.getInstance().player;
        return player != null && goggles.reveals(player);
    }

    private void render(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!active(screen)) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        CompoundTag data = station.state().data();
        CompoundTag essentia = data.getCompound("essentia");
        track(essentia);
        boolean seeing = seeing();
        if (seeing) {
            renderEssentia(screen, graphics, essentia, event.getMouseX(), event.getMouseY());
        }
        if (book != null) {
            book.renderTooltip(graphics, Minecraft.getInstance().font, event.getMouseX(), event.getMouseY());
        }
    }

    /** Notes what changed in the crucible since the last frame, so that it can rise from the row and the icon pulse. */
    private void track(CompoundTag essentia) {
        if (lastEssentia.equals(essentia)) {
            return;
        }
        long now = Util.getMillis();
        if (essentiaSeen) {
            for (String tag : essentia.getAllKeys()) {
                int delta = essentia.getInt(tag) - lastEssentia.getInt(tag);
                if (delta != 0) {
                    changes.put(tag, new Change(now, delta));
                }
            }
        }
        essentiaSeen = true;
        lastEssentia = essentia.copy();
        aspectOrder = essentia.getAllKeys().stream().filter(key -> essentia.getInt(key) > 0).sorted().toList();
    }

    private void renderEssentia(Screen screen, GuiGraphics graphics, CompoundTag essentia, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        int top = 27;
        if (aspectOrder.isEmpty()) {
            Component empty = Component.translatable(LANG + "no_aspects");
            int width = font.width(empty) + 12;
            int left = (screen.width - width) / 2;
            panel(graphics, left, top, width, 16);
            graphics.drawCenteredString(font, empty, screen.width / 2, top + 4, TEXT_DIM);
            return;
        }
        if (aspectIcons == null) {
            aspectIcons = loadAspects();
        }
        long now = Util.getMillis();
        int size = aspectOrder.size();
        int cell = 30;
        int columns = Math.max(1, Math.min(10, (screen.width * 2 / 3 - 12) / cell));
        int total = 0;
        List<Component> tooltip = null;
        for (int index = 0; index < size; index++) {
            int rowSize = Math.min(columns, size - index + index % columns);
            int rowTop = top + index / columns * 23;
            int rowLeft = (screen.width - rowSize * cell) / 2;
            if (index % columns == 0) {
                panel(graphics, rowLeft - 3, rowTop, rowSize * cell + 6, 21);
            }
            String tag = aspectOrder.get(index);
            var icon = aspectIcons.get(tag);
            int count = essentia.getInt(tag);
            total += count;
            int left = rowLeft + index % columns * cell;
            int colour = icon == null ? 0xEADAB9 : icon.color() & 0xFFFFFF;
            graphics.fill(left - 1, rowTop + 1, left + 17, rowTop + 19, 0x30 << 24 | colour);
            Change change = changes.get(tag);
            float pulse = change == null ? 0 : 1 - Mth.clamp((now - change.at()) / (float) PULSE_TIME, 0, 1);
            graphics.pose().pushPose();
            graphics.pose().translate(left + 8, rowTop + 10, 0);
            float scale = 1 + 0.3F * pulse * pulse;
            graphics.pose().scale(scale, scale, 1);
            if (icon != null) {
                AspectIcons.render(graphics, icon, -8, -8);
            } else {
                graphics.drawCenteredString(font, "?", 0, -4, TEXT);
            }
            graphics.pose().popPose();
            graphics.drawString(font, Integer.toString(count), left + 16, rowTop + 7,
                    pulse > 0 ? lerpColour(pulse, TEXT, 0xFFFFFFFF) : TEXT);
            if (mouseX >= left && mouseX < left + cell && mouseY >= rowTop && mouseY < rowTop + 21) {
                tooltip = aspectTip(tag, colour, count, -1);
            }
        }
        // What came and went: "+3" rises from under its icon and fades, in green, or red once used up by a recipe.
        for (int index = 0; index < size; index++) {
            Change change = changes.get(aspectOrder.get(index));
            if (change == null) {
                continue;
            }
            float age = (now - change.at()) / (float) CHANGE_TIME;
            if (age >= 1) {
                continue;
            }
            int rowTop = top + index / columns * 23;
            int rowSize = Math.min(columns, size - index + index % columns);
            int left = (screen.width - rowSize * cell) / 2 + index % columns * cell;
            int alpha = Math.max(8, Math.round(255 * (1 - age * age)));
            String text = (change.delta() > 0 ? "+" : "") + change.delta();
            int rgb = change.delta() > 0 ? 0x9CF08A : 0xF08A7A;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(font, text, left + 16, Math.round(rowTop + 20 + 8 * age), alpha << 24 | rgb);
            graphics.pose().popPose();
        }
        int rows = (size + columns - 1) / columns;
        Component sum = Component.translatable(LANG + "total", total);
        graphics.pose().pushPose();
        graphics.pose().translate(screen.width / 2F, top + rows * 23 + 1, 0);
        graphics.pose().scale(0.75F, 0.75F, 1);
        graphics.drawCenteredString(font, sum, 0, 0, TEXT_DIM);
        graphics.pose().popPose();
        if (tooltip != null) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private static void panel(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left, top, left + width, top + height, PANEL);
        graphics.renderOutline(left, top, width, height, PANEL_EDGE);
        graphics.hLine(left + 1, left + width - 2, top + 1, PANEL_LIGHT);
    }

    private static int lerpColour(float t, int from, int to) {
        int a = Math.round(Mth.lerp(t, from >>> 24, to >>> 24));
        int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return a << 24 | r << 16 | g << 8 | b;
    }

    /**
     * An aspect's tooltip as Thaumcraft 4 words it: the name in the aspect's colour, what it is made of (or that it is primal), and how
     * much the crucible holds and a recipe needs, where known (else -1).
     */
    static List<Component> aspectTip(String tag, int colour, int have, int need) {
        if (aspectIcons == null) {
            aspectIcons = loadAspects();
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(AspectIcons.name(tag)).withStyle(style -> style.withColor(colour & 0xFFFFFF)));
        TcAspect aspect = aspectIcons.get(tag);
        if (aspect != null) {
            if (aspect.primal() || aspect.components().size() < 2) {
                lines.add(Component.translatable(LANG + "aspect.primal").withStyle(style -> style.withColor(0xA08B7B)));
            } else {
                lines.add(Component.translatable(LANG + "aspect.components", AspectIcons.name(aspect.components().get(0)),
                        AspectIcons.name(aspect.components().get(1))).withStyle(style -> style.withColor(0xA08B7B)));
            }
        }
        if (have >= 0 && need >= 0) {
            lines.add(Component.translatable(LANG + "aspect.have_need", have, need)
                    .withStyle(style -> style.withColor(have >= need ? 0x8FD07A : 0xE08070)));
        } else if (have >= 0) {
            lines.add(Component.translatable(LANG + "aspect.have", have));
        } else if (need >= 0) {
            lines.add(Component.translatable(LANG + "aspect.need", need));
        }
        return lines;
    }

    /** Every aspect Thaumcraft knows, by tag: icon, colour and what it is made of. */
    static Map<String, TcAspect> aspects() {
        if (aspectIcons == null || aspectIcons.isEmpty()) {
            aspectIcons = loadAspects();
        }
        return aspectIcons;
    }

    private static Map<String, TcAspect> loadAspects() {
        Map<String, TcAspect> icons = new HashMap<>();
        try {
            TcModernApi.runtime().ifPresent(runtime -> TcAspects.snapshot(runtime)
                    .forEach(aspect -> icons.put(aspect.tag(), aspect)));
        } catch (RuntimeException failure) {
            LogUtils.getLogger().warn("Could not load Thaumcraft aspect icons", failure);
        }
        return icons;
    }

    private record Change(long at, int delta) {
    }
}
