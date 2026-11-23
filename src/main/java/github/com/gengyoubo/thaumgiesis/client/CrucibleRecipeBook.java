package github.com.gengyoubo.thaumgiesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.client.StationView;
import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.bridge.LegacyClientData;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipe;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipes;
import github.com.gengyoubo.tcmodern.api.research.TcThaumonomicon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The crucible's recipes in a leather-bound book hanging in the air beside it, opened out, after the Thaumonomicon: the player reads
 * and works the crucible at once. The left page lists what the crucible can make, what it could make right now (seen through goggles)
 * first and lit; the right page shows the recipe under the cursor, else the one picked, with what each aspect needs against what the
 * crucible holds. The book lies in its node's x-z plane with its spine along z, the left page at +x and the tops of the pages at +z;
 * pages are measured in units of about a GUI pixel. The wheel and the arrows turn the list, a tab over the right page shows only what
 * can be made now, and the ribbon shuts the book.
 */
final class CrucibleRecipeBook implements NodePainter {
    static final String NODE = "book_open";
    private static final String LANG = "thaumgiesis.crucible.book.";
    private static final int UNITS_WIDE = 72;
    private static final int UNITS_HIGH = 112;
    /** A unit of the page in the node's pixels, and the page's size there. */
    private static final float UNIT = 0.14F;
    private static final float PAGE_WIDTH = UNITS_WIDE * UNIT;
    private static final float PAGE_HEIGHT = UNITS_HIGH * UNIT;
    /** The block of pages under each sheet, the board under that, and how far the boards stand out round the pages, units. */
    private static final float BLOCK = 6.0F;
    private static final float BOARD = 1.6F;
    private static final float OVERHANG = 3.0F;
    /** Where the fold lies over the node's origin: the boards' undersides on it, open. */
    private static final float FOLD_Y = (BLOCK + BOARD) * UNIT;
    /** How far each page rises from flat towards its outer edge, open; shut, the halves stand up against each other. */
    private static final float OPEN_TILT = 0.12F;
    private static final float SHUT_TILT = Mth.HALF_PI;
    /** Seconds the book takes to open or shut, and a sheet to go over. */
    private static final float OPEN_TIME = 0.35F;
    private static final float FLIP_TIME = 0.32F;
    /** Over the paper, in units: wells and shade, then what lies on them, then the ribbon over everything. */
    private static final float WELL_LIFT = 0.3F;
    private static final float ITEM_LIFT = 1.2F;
    private static final float COVER_LIFT = 1.9F;
    /** How thick items are on the page: flat, with their faces apart, as Hands-On Delight's book draws them. */
    private static final float ITEM_DEPTH = 8.0F;
    private static final float BLOCK_DEPTH = 0.5F;
    /** The list on the left page: a title, three wells across and four down, the arrows under it. */
    private static final int COLUMNS = 3;
    private static final int ROWS = 4;
    private static final int PITCH = 19;
    private static final int WELL_SIZE = 18;
    private static final int GRID_X = 6;
    private static final int GRID_Y = 16;
    private static final int PER_PAGE = COLUMNS * ROWS;
    private static final int ARROW_Y = 97;
    private static final int ARROW_WIDTH = 12;
    private static final int ARROW_HEIGHT = 11;
    /** The right page: the recipe about the middle of its open part, clear of the fold on its left. */
    private static final int MIDDLE = 38;
    private static final int ASPECT_TOP = 54;
    private static final int ASPECT_COLUMN = 34;
    private static final int ASPECT_ROOM = 45;
    private static final int ICON = 14;
    /** The switch: a paper tab standing over the right page's top; the ribbon: hanging from its foot, it shuts the book. */
    private static final float TAB_X = 48.0F;
    private static final float TAB_WIDE = 16.0F;
    private static final float TAB_HIGH = 11.0F;
    private static final float RIBBON_X = 59.0F;
    private static final float RIBBON_WIDE = 5.0F;
    private static final float RIBBON_LONG = 14.0F;
    private static final int LIT = 12;
    private static final int INK = 0xFF3E281C;
    private static final int FADED = 0xFF7B5B42;
    private static final int ENOUGH = 0xFF2F6B2A;
    private static final int SHORT = 0xFFA0322A;
    private static final int PAPER = 0xFFF3E3BC;
    private static final int PAPER_SHEET = 0xFFEFDDB2;
    private static final int PAPER_EDGE = 0xFFD8C49A;
    private static final int PAPER_LINE = 0xFFC2AA7E;
    private static final int LEATHER = 0xFF5A3426;
    private static final int LEATHER_EDGE = 0xFF3A2019;
    private static final int GOLD = 0xFFD9B45A;
    private static final int RULE = 0x805A3C18;
    private static final int WELL = 0x285A3C18;
    private static final int WELL_READY = 0x70E0A83C;
    private static final int WELL_PICKED = 0xFF8A5A2B;
    private static final int HOVER = 0x40FFFFFF;
    private static final int RIBBON = 0xFF9A2A2A;
    /** Where the paper has aged: faint stains, the same in every book, in units: left, top, size. */
    private static final int[][] STAINS = {{9, 21, 7}, {50, 84, 9}, {27, 100, 6}, {57, 11, 5}, {12, 66, 4}};
    private static final CompoundTag NOTHING = new CompoundTag();
    private static final Map<ResourceLocation, Boolean> TEXTURES = new HashMap<>();

    private final List<Page> pages;
    private final boolean unavailable;
    private List<Page> listed = List.of();
    @Nullable
    private CompoundTag listedFor;
    private boolean listedFiltered;
    private boolean onlyReady;
    private int listPage;
    @Nullable
    private Page picked;
    /** The crucible's essentia as the player sees it: null without goggles. */
    @Nullable
    private CompoundTag essentia;
    private boolean wanted;
    private float open;
    private float flip = 1;
    private int flipDirection;
    @Nullable
    private Spot spot;
    private int shade = LightTexture.FULL_BRIGHT;

    private CrucibleRecipeBook(List<Page> pages, boolean unavailable) {
        this.pages = pages;
        this.unavailable = unavailable;
    }

    static CrucibleRecipeBook load() {
        var player = Minecraft.getInstance().player;
        var runtime = TcModernApi.runtime();
        if (player == null || runtime.isEmpty()) {
            return new CrucibleRecipeBook(List.of(), true);
        }
        try {
            var legacy = runtime.orElseThrow();
            var learned = TcThaumonomicon.completed(legacy, player.getGameProfile().getName());
            var bridge = ModernContentBridge.active();
            List<Page> pages = new ArrayList<>();
            for (TcRecipe recipe : TcRecipes.snapshot(legacy).recipes()) {
                if (recipe.kind() != TcRecipe.Kind.CRUCIBLE
                        || !recipe.research().isBlank() && !learned.contains(recipe.research())) {
                    continue;
                }
                ItemStack result = toItem(bridge, recipe.output());
                List<ItemStack> catalysts = recipe.inputs().get(0).stream().map(stack -> toItem(bridge, stack))
                        .filter(stack -> !stack.isEmpty()).toList();
                if (!result.isEmpty() && !catalysts.isEmpty()) {
                    var costs = recipe.aspects().stream().map(cost -> new Cost(
                            new TcAspect(cost.tag(), cost.color(), cost.texture(), List.of()), cost.amount())).toList();
                    pages.add(new Page(result, catalysts, costs));
                }
            }
            pages.sort(Comparator.comparing(entry -> entry.result().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
            return new CrucibleRecipeBook(pages, false);
        } catch (RuntimeException failure) {
            LogUtils.getLogger().warn("Could not read Thaumcraft crucible recipes", failure);
            return new CrucibleRecipeBook(List.of(), true);
        }
    }

    private static ItemStack toItem(ModernContentBridge bridge, LegacyStack stack) {
        int meta = stack.metadata() == TcThaumonomicon.WILDCARD ? 0 : stack.metadata();
        return bridge.gameplayStack(new LegacyClientData.Stack(stack.key(), meta, stack.count(), "", stack.snbt()));
    }

    // ---------------------------------------------------------------------------------------------------------------- the player

    boolean isWanted() {
        return wanted;
    }

    /** Opens the book or shuts it; one caught halfway turns back from where it is. */
    void setWanted(boolean wanted) {
        if (this.wanted != wanted) {
            this.wanted = wanted;
            sound(wanted ? 0.9F : 0.75F, 0.5F);
        }
    }

    /** Whether it is out at all: shut and gone, nothing of it is drawn and the closed book stands in its place again. */
    boolean isShown() {
        return wanted || open > 0;
    }

    /** Each frame: opening or shutting, the sheet going over, and what of the open pages the cursor is on. */
    void frame(StationView view, float seconds, @Nullable CompoundTag seen) {
        essentia = seen;
        relist();
        open = Mth.clamp(open + (wanted ? seconds : -seconds) / OPEN_TIME, 0, 1);
        flip = Math.min(1, flip + seconds / FLIP_TIME);
        view.node(NODE).ifPresent(node -> node.setVisible(isShown()));
        spot = open >= 1 ? find(view) : null;
    }

    /** A click on the book: the arrows, a well of the list, the switch, the ribbon. */
    void click() {
        if (spot == null) {
            return;
        }
        switch (spot.kind()) {
            case PREVIOUS -> turn(-1);
            case NEXT -> turn(1);
            case FILTER -> {
                onlyReady = !onlyReady;
                listedFor = null;
                listPage = 0;
                sound(1.4F, 0.3F);
            }
            case RIBBON -> setWanted(false);
            case LIST -> {
                Page entry = spot.page();
                picked = picked == entry ? null : entry;
                sound(1.6F, 0.15F);
            }
            default -> {
            }
        }
    }

    void turn(int direction) {
        int count = pageCount();
        if (count > 1 && open >= 1) {
            listPage = Math.floorMod(listPage + direction, count);
            flip = 0;
            flipDirection = direction;
            sound(1.0F, 0.6F);
        }
    }

    private static void sound(float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, pitch, volume));
    }

    /** The tooltip of what the cursor is on, drawn over the screen by the HUD. */
    void renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (spot == null) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        switch (spot.kind()) {
            case LIST, RESULT -> graphics.renderTooltip(font, spot.page().result(), mouseX, mouseY);
            case CATALYST -> graphics.renderTooltip(font, catalyst(spot.page()), mouseX, mouseY);
            case ASPECT -> {
                Cost cost = spot.page().costs().get(spot.index());
                graphics.renderComponentTooltip(font, CrucibleHud.aspectTip(cost.aspect().tag(), cost.aspect().color(),
                        essentia == null ? -1 : essentia.getInt(cost.aspect().tag()), cost.amount()), mouseX, mouseY);
            }
            case FILTER -> graphics.renderComponentTooltip(font, List.of(
                    Component.translatable(LANG + (onlyReady ? "only_ready" : "showing_all")),
                    Component.translatable(LANG + "filter_hint").withStyle(style -> style.withColor(0xA08B7B))),
                    mouseX, mouseY);
            case RIBBON -> graphics.renderTooltip(font, Component.translatable(LANG + "close"), mouseX, mouseY);
            default -> {
            }
        }
        graphics.pose().popPose();
    }

    /**
     * What of the open pages the cursor is on. The pages rise from the fold to their outer edges, so the cursor's ray meets a page
     * above the fold's plane, the higher the further out: a few steps find the height where it comes down on the page.
     */
    @Nullable
    private Spot find(StationView view) {
        float height = FOLD_Y;
        Vector3f point = null;
        for (int step = 0; step < 4; step++) {
            Optional<Vector3f> cursor = view.pointerOn(NODE, height);
            if (cursor.isEmpty()) {
                return null;
            }
            point = cursor.get();
            height = FOLD_Y + Math.abs(point.x) * Mth.sin(OPEN_TILT) / Mth.cos(OPEN_TILT);
        }
        float y = (PAGE_HEIGHT / 2 - point.z) / UNIT;
        float along = Math.abs(point.x) / Mth.cos(OPEN_TILT) / UNIT;
        return point.x > 0 ? leftSpot(UNITS_WIDE - along, y) : rightSpot(along, y);
    }

    @Nullable
    private Spot leftSpot(float x, float y) {
        if (pageCount() > 1 && y >= ARROW_Y && y < ARROW_Y + ARROW_HEIGHT) {
            if (x >= GRID_X && x < GRID_X + ARROW_WIDTH) {
                return new Spot(Kind.PREVIOUS, null, 0);
            }
            float right = GRID_X + COLUMNS * PITCH - 1 - ARROW_WIDTH;
            if (x >= right && x < right + ARROW_WIDTH) {
                return new Spot(Kind.NEXT, null, 0);
            }
        }
        if (x < GRID_X || y < GRID_Y) {
            return null;
        }
        int column = (int) ((x - GRID_X) / PITCH);
        int row = (int) ((y - GRID_Y) / PITCH);
        int index = listPage * PER_PAGE + row * COLUMNS + column;
        if (column < COLUMNS && row < ROWS && index < listed.size()
                && x - GRID_X - column * PITCH < WELL_SIZE && y - GRID_Y - row * PITCH < WELL_SIZE) {
            return new Spot(Kind.LIST, listed.get(index), index);
        }
        return null;
    }

    @Nullable
    private Spot rightSpot(float x, float y) {
        if (essentia != null && x >= TAB_X && x < TAB_X + TAB_WIDE && y >= -TAB_HIGH && y < 0) {
            return new Spot(Kind.FILTER, null, 0);
        }
        if (x >= RIBBON_X && x < RIBBON_X + RIBBON_WIDE && y >= UNITS_HIGH - 2 && y < UNITS_HIGH + RIBBON_LONG) {
            return new Spot(Kind.RIBBON, null, 0);
        }
        Page shown = shown(null);
        if (shown == null || x < 0 || x >= UNITS_WIDE || y < 0 || y >= UNITS_HIGH) {
            return null;
        }
        if (inside(x, y, MIDDLE - 29, 16, WELL_SIZE, WELL_SIZE)) {
            return new Spot(Kind.CATALYST, shown, 0);
        }
        if (inside(x, y, MIDDLE + 11, 16, WELL_SIZE, WELL_SIZE) || inside(x, y, 4, 3, UNITS_WIDE - 6, 10)) {
            return new Spot(Kind.RESULT, shown, 0);
        }
        int pitch = aspectPitch(shown);
        for (int index = 0; index < shown.costs().size(); index++) {
            if (inside(x, y, 6 + index % 2 * ASPECT_COLUMN, ASPECT_TOP + index / 2 * pitch, ASPECT_COLUMN - 2, pitch)) {
                return new Spot(Kind.ASPECT, shown, index);
            }
        }
        return null;
    }

    /** The recipe on the right page: the one the cursor is on in the list, else the one picked; with neither, the page is blank. */
    @Nullable
    private Page shown(@Nullable Spot over) {
        if (over != null && over.kind() == Kind.LIST) {
            return over.page();
        }
        if (picked != null && listed.contains(picked)) {
            return picked;
        }
        return null;
    }

    /** What the list shows: everything, what can be made now first, or only that once the switch is on. */
    private void relist() {
        boolean filtered = onlyReady && essentia != null;
        CompoundTag key = essentia == null ? NOTHING : essentia;
        if (listedFor != null && listedFiltered == filtered && listedFor.equals(key)) {
            return;
        }
        listedFor = key.copy();
        listedFiltered = filtered;
        if (essentia == null) {
            listed = pages;
        } else {
            List<Page> ready = new ArrayList<>();
            List<Page> rest = new ArrayList<>();
            for (Page page : pages) {
                (page.ready(essentia) ? ready : rest).add(page);
            }
            if (!filtered) {
                ready.addAll(rest);
            }
            listed = ready;
        }
        listPage = Mth.clamp(listPage, 0, pageCount() - 1);
    }

    private int pageCount() {
        return Math.max(1, (listed.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private static ItemStack catalyst(Page page) {
        return page.catalysts().get((int) (Util.getMillis() / 1200 % page.catalysts().size()));
    }

    private static int aspectPitch(Page page) {
        int rows = Math.max(1, (page.costs().size() + 1) / 2);
        return Math.min(15, ASPECT_ROOM / rows);
    }

    private static boolean inside(float x, float y, float left, float top, float width, float height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    // ---------------------------------------------------------------------------------------------------------------- drawing

    /** Hangs before the camera: drawn over the scene and smooth, as Hands-On Delight's books are. */
    @Override
    public boolean smooth() {
        return true;
    }

    @Override
    public void paint(PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (open <= 0) {
            return;
        }
        shade = LightTexture.pack(Math.max(LightTexture.block(light), LIT), Math.max(LightTexture.sky(light), LIT));
        float eased = 1 - (1 - open) * (1 - open) * (1 - open);
        float tilt = Mth.lerp(eased, SHUT_TILT, OPEN_TILT);
        poseStack.pushPose();
        // It grows a little as it comes, from the size of the closed book it stands in for.
        float grow = Mth.lerp(eased, 0.55F, 1);
        poseStack.scale(grow, grow, grow);
        side(poseStack, buffers, true, tilt);
        side(poseStack, buffers, false, -tilt);
        if (flip < 1 && open >= 1) {
            sheet(poseStack, buffers);
        }
        poseStack.popPose();
    }

    /**
     * The book shut, lying by the crucible for the player to open: a box of pages between two leather boards, the spine on the +x side
     * with gilt bands, gold at the front cover's corners and the Thaumonomicon's eye tooled on it. It is centred on the node's origin,
     * its foot on the node's plane, and rises by {@code lift} pixels while the cursor is on it.
     */
    static void paintClosed(PoseStack poseStack, MultiBufferSource buffers, int light, float lift) {
        int shade = LightTexture.pack(Math.max(LightTexture.block(light), 6), Math.max(LightTexture.sky(light), 6));
        float wide = UNITS_WIDE + OVERHANG;
        float high = UNITS_HIGH + 2 * OVERHANG;
        float thick = 2 * (BLOCK + BOARD);
        poseStack.pushPose();
        poseStack.translate(0, lift, 0);
        poseStack.last().pose().mul(new Matrix4f(-UNIT, 0, 0, 0, 0, 0, -UNIT, 0, 0, UNIT, 0, 0,
                wide * UNIT / 2, 0, high * UNIT / 2, 1));
        Sheet canvas = new Sheet(poseStack, buffers, shade);
        // The boards: each a slab, top, underside and edges; the spine joins them along the far side.
        for (float base : new float[] {0, thick - BOARD}) {
            canvas.box(0, 0, wide, high, base, LEATHER_EDGE);
            canvas.box(0, 0, wide, high, base + BOARD, LEATHER);
            canvas.wall(0, 0, 0, high, base, base + BOARD, LEATHER_EDGE);
            canvas.wall(0, 0, wide, 0, base, base + BOARD, LEATHER_EDGE);
            canvas.wall(0, high, wide, high, base, base + BOARD, LEATHER_EDGE);
        }
        canvas.wall(wide, 0, wide, high, 0, thick, LEATHER_EDGE);
        for (float band : new float[] {0.22F, 0.5F, 0.78F}) {
            float at = high * band;
            canvas.wall(wide + 0.05F, at - 1.5F, wide + 0.05F, at + 1.5F, 0.6F, thick - 0.6F, GOLD);
        }
        // The block of pages between them, set in from the boards' edges, lined.
        float from = BOARD;
        float to = thick - BOARD;
        canvas.wall(0.8F, OVERHANG, 0.8F, high - OVERHANG, from, to, PAPER_EDGE);
        canvas.wall(0.8F, OVERHANG, wide, OVERHANG, from, to, PAPER_EDGE);
        canvas.wall(0.8F, high - OVERHANG, wide, high - OVERHANG, from, to, PAPER_EDGE);
        for (float line = from + 1.2F; line < to - 0.4F; line += 1.2F) {
            canvas.wall(0.75F, OVERHANG, 0.75F, high - OVERHANG, line, line + 0.25F, PAPER_LINE);
            canvas.wall(0.8F, OVERHANG - 0.05F, wide, OVERHANG - 0.05F, line, line + 0.25F, PAPER_LINE);
            canvas.wall(0.8F, high - OVERHANG + 0.05F, wide, high - OVERHANG + 0.05F, line, line + 0.25F, PAPER_LINE);
        }
        // The front cover's tooling: a gilt rule round it, brass at the corners away from the spine, the eye in its middle.
        float top = thick + 0.05F;
        canvas.outlineAt(5, 5, wide - 12, high - 10, top, 0xFFB08A48);
        canvas.box(0, 0, 6, 6, top + 0.02F, GOLD);
        canvas.box(0, high - 6, 6, 6, top + 0.02F, GOLD);
        float cx = (wide - 7) / 2;
        float cy = high / 2;
        for (int row = -9; row <= 9; row++) {
            float half = 15 * (1 - Math.abs(row) / 10F);
            canvas.rect(cx - half, cy + row, 2 * half, 1, top + 0.03F, row == -9 || row == 9 ? GOLD : 0xFF6A3E2C);
            canvas.rect(cx - half, cy + row, 1.2F, 1, top + 0.04F, GOLD);
            canvas.rect(cx + half - 1.2F, cy + row, 1.2F, 1, top + 0.04F, GOLD);
        }
        for (int row = -4; row <= 4; row++) {
            float half = (float) Math.sqrt(25 - row * row);
            canvas.rect(cx - half, cy + row, 2 * half, 1, top + 0.05F, GOLD);
        }
        canvas.rect(cx - 1.5F, cy - 2, 3, 4, top + 0.06F, 0xFF2A1810);
        poseStack.popPose();
    }

    /** One half of the book, turned {@code turn} about the spine: its board, its block of pages and the page on top. */
    private void side(PoseStack poseStack, MultiBufferSource buffers, boolean left, float turn) {
        poseStack.pushPose();
        onPage(poseStack, left, turn);
        Sheet canvas = new Sheet(poseStack, buffers, shade);
        canvas.body(left);
        if (open > 0.5F) {
            if (left) {
                drawList(canvas);
            } else {
                drawRecipe(canvas);
            }
        }
        poseStack.popPose();
    }

    /** The node's pixels to a page's units: x along the page, y down it, z up off it. */
    private static void onPage(PoseStack poseStack, boolean left, float turn) {
        poseStack.translate(0, FOLD_Y, 0);
        poseStack.mulPose(new Quaternionf().rotationZ(turn));
        Matrix4f page = new Matrix4f(-UNIT, 0, 0, 0, 0, 0, -UNIT, 0, 0, UNIT, 0, 0,
                left ? PAGE_WIDTH : 0, 0, PAGE_HEIGHT / 2, 1);
        poseStack.last().pose().mul(page);
    }

    /** A sheet going over from one side to the other, blank on both faces, shaded as it stands up. */
    private void sheet(PoseStack poseStack, MultiBufferSource buffers) {
        float eased = flip * flip * (3 - 2 * flip);
        float from = -OPEN_TILT;
        float to = -(Mth.PI - OPEN_TILT);
        float turn = flipDirection > 0 ? Mth.lerp(eased, from, to) : Mth.lerp(eased, to, from);
        poseStack.pushPose();
        poseStack.translate(0, FOLD_Y + 0.02F, 0);
        poseStack.mulPose(new Quaternionf().rotationZ(turn));
        poseStack.last().pose().mul(new Matrix4f(-UNIT, 0, 0, 0, 0, 0, -UNIT, 0, 0, UNIT, 0, 0, 0, 0, PAGE_HEIGHT / 2, 1));
        Sheet canvas = new Sheet(poseStack, buffers, shade);
        float standing = Mth.sin(eased * Mth.PI);
        int tone = Math.round(255 - 40 * standing);
        int colour = 0xFF000000 | mix(PAPER_SHEET, tone);
        canvas.box(0, 0, UNITS_WIDE, UNITS_HIGH, 0.4F, colour);
        canvas.box(0, 0, 4, UNITS_HIGH, 0.45F, 0x183E281C);
        poseStack.popPose();
    }

    private void drawList(Sheet canvas) {
        Font font = Minecraft.getInstance().font;
        Component title = Component.translatable(LANG + "title");
        canvas.text(title, (UNITS_WIDE - 4 - font.width(title)) / 2F, 4, INK);
        canvas.rect(8, 13, UNITS_WIDE - 18, 0.6F, WELL_LIFT, RULE);
        if (listed.isEmpty()) {
            Component empty = Component.translatable(LANG + (unavailable ? "unavailable"
                    : !pages.isEmpty() ? "none_ready" : "locked"));
            float line = GRID_Y + 2;
            for (var part : font.split(empty, UNITS_WIDE - 14)) {
                canvas.text(part, 6, line, FADED);
                line += 10;
            }
            return;
        }
        for (int slot = 0; slot < PER_PAGE; slot++) {
            int index = listPage * PER_PAGE + slot;
            if (index >= listed.size()) {
                break;
            }
            Page entry = listed.get(index);
            float left = GRID_X + slot % COLUMNS * PITCH;
            float top = GRID_Y + slot / COLUMNS * PITCH;
            canvas.well(left, top, essentia != null && entry.ready(essentia) ? WELL_READY : WELL);
            if (entry == picked) {
                canvas.outline(left - 1, top - 1, WELL_SIZE, WELL_SIZE, WELL_PICKED);
            }
            if (spot != null && spot.kind() == Kind.LIST && spot.page() == entry) {
                canvas.rect(left - 1, top - 1, WELL_SIZE, WELL_SIZE, WELL_LIFT + 0.05F, HOVER);
            }
            canvas.item(entry.result(), left, top, 16);
        }
        if (pageCount() > 1) {
            float right = GRID_X + COLUMNS * PITCH - 1 - ARROW_WIDTH;
            canvas.arrow(GRID_X, ARROW_Y, false, spot != null && spot.kind() == Kind.PREVIOUS);
            canvas.arrow(right, ARROW_Y, true, spot != null && spot.kind() == Kind.NEXT);
        }
        String number = (listPage + 1) + "/" + pageCount();
        float middle = GRID_X + (COLUMNS * PITCH - 1) / 2F;
        canvas.text(number, middle - font.width(number) * 0.75F / 2, ARROW_Y + 2.5F, 0.75F, FADED);
    }

    private void drawRecipe(Sheet canvas) {
        Font font = Minecraft.getInstance().font;
        if (essentia != null) {
            canvas.tab(TAB_X, TAB_WIDE, TAB_HIGH, onlyReady, spot != null && spot.kind() == Kind.FILTER);
        }
        canvas.ribbon(spot != null && spot.kind() == Kind.RIBBON);
        Page recipe = shown(spot);
        if (recipe == null) {
            return;
        }
        String name = recipe.result().getHoverName().getString();
        float room = UNITS_WIDE - 10;
        float scale = font.width(name) > room ? Math.max(0.6F, room / font.width(name)) : 1;
        if (font.width(name) * scale > room) {
            name = font.plainSubstrByWidth(name, (int) (room / scale) - font.width("…")) + "…";
        }
        canvas.text(name, MIDDLE - font.width(name) * scale / 2, 4 + (1 - scale) * 4, scale, INK);

        ItemStack catalyst = catalyst(recipe);
        boolean ready = essentia != null && recipe.ready(essentia);
        canvas.well(MIDDLE - 29, 16, WELL);
        canvas.well(MIDDLE + 11, 16, ready ? WELL_READY : WELL);
        canvas.item(catalyst, MIDDLE - 29, 16, 16);
        canvas.item(recipe.result(), MIDDLE + 11, 16, 16);
        canvas.arrow(MIDDLE - 7, 19.5F, true, false);

        canvas.rect(8, 40, MIDDLE - 12, 0.6F, WELL_LIFT, RULE);
        canvas.rect(MIDDLE + 4, 40, UNITS_WIDE - MIDDLE - 10, 0.6F, WELL_LIFT, RULE);
        canvas.ornament(MIDDLE, 40.3F);
        canvas.text(Component.translatable(LANG + "aspects"), 6, 43, INK);

        List<Cost> costs = recipe.costs();
        if (costs.isEmpty()) {
            canvas.text(Component.translatable(LANG + "no_aspects"), 8, ASPECT_TOP + 3, FADED);
        }
        int pitch = aspectPitch(recipe);
        float icon = Math.min(ICON, pitch - 1);
        for (int index = 0; index < costs.size(); index++) {
            Cost cost = costs.get(index);
            float left = 6 + index % 2 * ASPECT_COLUMN;
            float top = ASPECT_TOP + index / 2 * pitch;
            if (spot != null && spot.kind() == Kind.ASPECT && spot.index() == index) {
                canvas.rect(left - 1, top - 1, ASPECT_COLUMN - 2, pitch, WELL_LIFT, 0x205A3C18);
            }
            canvas.aspect(cost.aspect(), left, top, icon);
            float textX = left + icon + 1.5F;
            if (essentia == null) {
                canvas.text(Integer.toString(cost.amount()), textX, top + (icon - 7) / 2, 1, INK);
            } else {
                int have = essentia.getInt(cost.aspect().tag());
                boolean enough = have >= cost.amount();
                canvas.text(have + "/" + cost.amount(), textX, top + 1, 0.7F, enough ? ENOUGH : SHORT);
                float bar = 16 * Math.min(1F, have / (float) cost.amount());
                canvas.rect(textX, top + icon - 4, 16, 1.6F, WELL_LIFT, 0x305A3C18);
                canvas.rect(textX, top + icon - 4, bar, 1.6F, WELL_LIFT + 0.05F, enough ? 0xFF5E9E3A : 0xFFC9783A);
            }
        }

        Component verdict;
        int colour;
        if (essentia == null) {
            verdict = Component.translatable(LANG + "need_goggles");
            colour = FADED;
        } else if (ready) {
            verdict = Component.translatable(LANG + "ready");
            colour = ENOUGH;
        } else {
            verdict = Component.translatable(LANG + "missing");
            colour = SHORT;
        }
        float verdictScale = Math.min(1, (UNITS_WIDE - 8F) / font.width(verdict));
        canvas.text(verdict.getString(), MIDDLE - font.width(verdict) * verdictScale / 2, 101 + (1 - verdictScale) * 4,
                verdictScale, colour);
    }

    private static int mix(int rgb, int tone) {
        int r = (rgb >> 16 & 0xFF) * tone / 255;
        int g = (rgb >> 8 & 0xFF) * tone / 255;
        int b = (rgb & 0xFF) * tone / 255;
        return r << 16 | g << 8 | b;
    }

    /** Drawing on a page, in its units: x along it, y down it, z up off the paper. */
    private static final class Sheet {
        private final PoseStack poseStack;
        private final MultiBufferSource buffers;
        private final int shade;

        Sheet(PoseStack poseStack, MultiBufferSource buffers, int shade) {
            this.poseStack = poseStack;
            this.buffers = buffers;
            this.shade = shade;
        }

        /**
         * The page, its block of pages and its board: the paper with its aged stains and the shade of the fold, the block's edges in
         * lines of pages, and the leather standing out round it with gold at its corners.
         */
        void body(boolean left) {
            float outer = left ? 0 : UNITS_WIDE;
            float foldX = left ? UNITS_WIDE : 0;
            float boardFrom = left ? -OVERHANG : 0;
            float boardTo = left ? UNITS_WIDE : UNITS_WIDE + OVERHANG;
            float under = -BLOCK;
            float bottom = -BLOCK - BOARD;
            // The board: its top round the pages, its underside (the cover, seen while the book opens), and its edges.
            box(boardFrom, -OVERHANG, boardTo - boardFrom, UNITS_HIGH + 2 * OVERHANG, under, LEATHER);
            face(boardFrom, -OVERHANG, bottom, boardTo, UNITS_HIGH + OVERHANG, bottom, LEATHER_EDGE, true);
            float edgeX = left ? boardFrom : boardTo;
            wall(edgeX, -OVERHANG, edgeX, UNITS_HIGH + OVERHANG, bottom, under, LEATHER_EDGE);
            wall(boardFrom, -OVERHANG, boardTo, -OVERHANG, bottom, under, LEATHER_EDGE);
            wall(boardFrom, UNITS_HIGH + OVERHANG, boardTo, UNITS_HIGH + OVERHANG, bottom, under, LEATHER_EDGE);
            float cornerX = left ? boardFrom : boardTo - 4;
            box(cornerX, -OVERHANG, 4, 4, under + 0.05F, GOLD);
            box(cornerX, UNITS_HIGH + OVERHANG - 4, 4, 4, under + 0.05F, GOLD);
            float studX = left ? boardFrom + 8 : boardTo - 16;
            face(studX, UNITS_HIGH / 2F - 8, bottom - 0.05F, studX + 8, UNITS_HIGH / 2F + 8, bottom - 0.05F, GOLD, true);
            // The block of pages: its outer edge, top and foot, lined.
            wall(outer, 0, outer, UNITS_HIGH, under, 0, PAPER_EDGE);
            wall(0, 0, UNITS_WIDE, 0, under, 0, PAPER_EDGE);
            wall(0, UNITS_HIGH, UNITS_WIDE, UNITS_HIGH, under, 0, PAPER_EDGE);
            for (float line = under + 1.2F; line < -0.5F; line += 1.2F) {
                float out = left ? -0.02F : 0.02F;
                wall(outer + out, 0, outer + out, UNITS_HIGH, line, line + 0.25F, PAPER_LINE);
                wall(0, -0.02F, UNITS_WIDE, -0.02F, line, line + 0.25F, PAPER_LINE);
                wall(0, UNITS_HIGH + 0.02F, UNITS_WIDE, UNITS_HIGH + 0.02F, line, line + 0.25F, PAPER_LINE);
            }
            // The page itself.
            box(0, 0, UNITS_WIDE, UNITS_HIGH, 0, PAPER);
            for (int[] stain : STAINS) {
                float sx = left ? stain[0] : UNITS_WIDE - stain[0] - stain[2];
                rect(sx, stain[1], stain[2], stain[2], 0.05F, 0x0C6B4A20);
                rect(sx + 1, stain[1] - 1, stain[2] - 2, stain[2] + 2, 0.06F, 0x0A6B4A20);
            }
            for (int column = 0; column < 8; column++) {
                int alpha = (8 - column) * 8;
                float fx = left ? foldX - 1 - column : foldX + column;
                rect(fx, 0, 1, UNITS_HIGH, 0.1F, alpha << 24 | 0x3E281C);
            }
            rect(0, 0, UNITS_WIDE, 3, 0.08F, 0x103E281C);
            rect(0, UNITS_HIGH - 3, UNITS_WIDE, 3, 0.08F, 0x103E281C);
        }

        void well(float x, float y, int colour) {
            rect(x - 1, y - 1, WELL_SIZE, WELL_SIZE, WELL_LIFT, colour);
            rect(x - 1, y - 1, WELL_SIZE, 0.7F, WELL_LIFT + 0.02F, 0x305A3C18);
            rect(x - 1, y - 1, 0.7F, WELL_SIZE, WELL_LIFT + 0.02F, 0x305A3C18);
        }

        void outline(float x, float y, float width, float height, int colour) {
            float lift = WELL_LIFT + 0.04F;
            rect(x, y, width, 0.8F, lift, colour);
            rect(x, y + height - 0.8F, width, 0.8F, lift, colour);
            rect(x, y, 0.8F, height, lift, colour);
            rect(x + width - 0.8F, y, 0.8F, height, lift, colour);
        }

        /** A page arrow in the ink of the page, warmer under the cursor, drawn row by row so that it sits on the paper in any font. */
        void arrow(float x, float y, boolean next, boolean over) {
            int colour = over ? WELL_PICKED : INK;
            int middle = ARROW_HEIGHT / 2;
            for (int row = 0; row < ARROW_HEIGHT; row++) {
                int off = Math.abs(row - middle);
                float from = off <= 1 ? 1 : 5;
                float to = ARROW_WIDTH - 1 - off;
                if (to > from) {
                    float left = next ? x + from : x + ARROW_WIDTH - to;
                    rect(left, y + row, to - from, 1, WELL_LIFT, colour);
                }
            }
        }

        /** A little diamond, the Thaumonomicon's mark between the parts of a page. */
        void ornament(float middle, float y) {
            for (int step = 0; step < 3; step++) {
                rect(middle - step, y - 2 + step, 2 * step + 1, 1, WELL_LIFT + 0.05F, FADED);
                rect(middle - step, y + 2 - step, 2 * step + 1, 1, WELL_LIFT + 0.05F, FADED);
            }
        }

        /** The switch: a paper tab standing over the page's top, a box on it filled while only what can be made is shown. */
        void tab(float x, float wide, float high, boolean on, boolean over) {
            int paper = over ? 0xFFF8EBCB : on ? PAPER : 0xFFDCC9A2;
            box(x, -high, wide, high + 1, 0.02F, paper);
            rect(x, -high, wide, 0.8F, 0.06F, 0x405A3C18);
            float bx = x + (wide - 7) / 2;
            float by = -high + 2;
            outlineAt(bx, by, 7, 7, 0.1F, INK);
            if (on) {
                rect(bx + 2, by + 2, 3, 3, 0.12F, WELL_PICKED);
            }
        }

        void outlineAt(float x, float y, float width, float height, float lift, int colour) {
            rect(x, y, width, 0.8F, lift, colour);
            rect(x, y + height - 0.8F, width, 0.8F, lift, colour);
            rect(x, y, 0.8F, height, lift, colour);
            rect(x + width - 0.8F, y, 0.8F, height, lift, colour);
        }

        /** The ribbon marking the place, hanging out over the page's foot: it shuts the book. */
        void ribbon(boolean over) {
            int colour = over ? 0xFFC04040 : RIBBON;
            box(RIBBON_X, UNITS_HIGH - 30, RIBBON_WIDE, 30 + RIBBON_LONG - 2, COVER_LIFT, colour);
            for (int step = 0; step < 3; step++) {
                float wide = (RIBBON_WIDE - 1) / 2 - step;
                rect(RIBBON_X, UNITS_HIGH + RIBBON_LONG - 2 + step, wide, 1, COVER_LIFT, colour);
                rect(RIBBON_X + RIBBON_WIDE - wide, UNITS_HIGH + RIBBON_LONG - 2 + step, wide, 1, COVER_LIFT, colour);
            }
            rect(RIBBON_X + RIBBON_WIDE - 1.2F, UNITS_HIGH - 30, 1.2F, 30 + RIBBON_LONG - 2, COVER_LIFT + 0.02F, 0x30000000);
        }

        /** An aspect's icon: its texture tinted with its colour, as Thaumcraft draws it; a swatch with its initial if it has none. */
        void aspect(TcAspect aspect, float x, float y, float size) {
            ResourceLocation texture = ResourceLocation.tryParse(aspect.texture());
            int colour = 0xFF000000 | aspect.color();
            if (texture != null && !aspect.texture().isEmpty() && exists(texture)) {
                quad(buffers.getBuffer(RenderType.text(texture)), x, y, x + size, y + size, ITEM_LIFT, colour, true);
            } else {
                rect(x, y, size, size, WELL_LIFT + 0.05F, colour);
                text(aspect.tag().substring(0, 1).toUpperCase(), x + size / 2 - 2.5F, y + (size - 7) / 2, 1, 0xFFFFFFFF);
            }
        }

        /** An item as an icon flat on the page, lying as in a GUI. */
        void item(ItemStack stack, float x, float y, float size) {
            var renderer = Minecraft.getInstance().getItemRenderer();
            BakedModel model = renderer.getModel(stack, Minecraft.getInstance().level, null, 0);
            poseStack.pushPose();
            poseStack.translate(x + size / 2, y + size / 2, ITEM_LIFT);
            Matrix3f facing = new Matrix3f(poseStack.last().normal());
            poseStack.scale(size, -size, model.isGui3d() ? BLOCK_DEPTH : ITEM_DEPTH);
            // A flip of one axis breaks the normals on 1.20.1; they keep the page's own.
            poseStack.last().normal().set(facing);
            renderer.render(stack, ItemDisplayContext.GUI, false, poseStack, buffers, shade, OverlayTexture.NO_OVERLAY, model);
            poseStack.popPose();
        }

        void text(Component text, float x, float y, int colour) {
            Matrix4f matrix = new Matrix4f(poseStack.last().pose()).translate(x, y, ITEM_LIFT);
            Minecraft.getInstance().font.drawInBatch(text, 0, 0, colour, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, shade);
        }

        void text(FormattedCharSequence text, float x, float y, int colour) {
            Matrix4f matrix = new Matrix4f(poseStack.last().pose()).translate(x, y, ITEM_LIFT);
            Minecraft.getInstance().font.drawInBatch(text, 0, 0, colour, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, shade);
        }

        /** Text drawn smaller ({@code scale} under 1) from its top left corner: scaled as a whole, never wrapped. */
        void text(String text, float x, float y, float scale, int colour) {
            Matrix4f matrix = new Matrix4f(poseStack.last().pose()).translate(x, y, ITEM_LIFT).scale(scale, scale, 1);
            Minecraft.getInstance().font.drawInBatch(text, 0, 0, colour, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, shade);
        }

        /** A flat rectangle on the page, seen from over it. */
        void rect(float x, float y, float width, float height, float lift, int argb) {
            quad(buffers.getBuffer(RenderType.textBackground()), x, y, x + width, y + height, lift, argb, false);
        }

        /** A flat rectangle seen from either side: a board, a sheet standing up. */
        void box(float x, float y, float width, float height, float lift, int argb) {
            face(x, y, lift, x + width, y + height, lift, argb, true);
        }

        /** A wall standing up from the page between two points of it, from {@code low} to {@code high} off the paper. */
        void wall(float x0, float y0, float x1, float y1, float low, float high, int argb) {
            VertexConsumer consumer = buffers.getBuffer(RenderType.textBackground());
            Matrix4f matrix = poseStack.last().pose();
            int a = argb >>> 24;
            int r = argb >> 16 & 0xFF;
            int g = argb >> 8 & 0xFF;
            int b = argb & 0xFF;
            float[][] corners = {{x0, y0, low}, {x0, y0, high}, {x1, y1, high}, {x1, y1, low}};
            for (int side = 0; side < 2; side++) {
                for (int corner = 0; corner < 4; corner++) {
                    float[] at = corners[side == 0 ? corner : 3 - corner];
                    consumer.vertex(matrix, at[0], at[1], at[2]).color(r, g, b, a).uv2(shade).endVertex();
                }
            }
        }

        private void face(float x0, float y0, float z0, float x1, float y1, float z1, int argb, boolean both) {
            VertexConsumer consumer = buffers.getBuffer(RenderType.textBackground());
            Matrix4f matrix = poseStack.last().pose();
            int a = argb >>> 24;
            int r = argb >> 16 & 0xFF;
            int g = argb >> 8 & 0xFF;
            int b = argb & 0xFF;
            float[][] corners = {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z1}, {x1, y0, z1}};
            for (int side = 0; side < (both ? 2 : 1); side++) {
                for (int corner = 0; corner < 4; corner++) {
                    float[] at = corners[side == 0 ? corner : 3 - corner];
                    consumer.vertex(matrix, at[0], at[1], at[2]).color(r, g, b, a).uv2(shade).endVertex();
                }
            }
        }

        private void quad(VertexConsumer consumer, float x0, float y0, float x1, float y1, float lift, int argb,
                          boolean textured) {
            Matrix4f matrix = poseStack.last().pose();
            int a = argb >>> 24;
            int r = argb >> 16 & 0xFF;
            int g = argb >> 8 & 0xFF;
            int b = argb & 0xFF;
            float[][] corners = {{x0, y0, 0, 0}, {x0, y1, 0, 1}, {x1, y1, 1, 1}, {x1, y0, 1, 0}};
            for (int side = 0; side < 2; side++) {
                for (int corner = 0; corner < 4; corner++) {
                    float[] at = corners[side == 0 ? corner : 3 - corner];
                    consumer.vertex(matrix, at[0], at[1], lift).color(r, g, b, a);
                    if (textured) {
                        consumer.uv(at[2], at[3]);
                    }
                    consumer.uv2(shade).endVertex();
                }
            }
        }
    }

    private static boolean exists(ResourceLocation texture) {
        return TEXTURES.computeIfAbsent(texture,
                location -> Minecraft.getInstance().getResourceManager().getResource(location).isPresent());
    }

    private enum Kind {
        LIST, PREVIOUS, NEXT, FILTER, RIBBON, CATALYST, RESULT, ASPECT
    }

    /** What the cursor is on: what it is, and the recipe and which of its aspects or the list's entries, where that applies. */
    private record Spot(Kind kind, @Nullable Page page, int index) {
    }

    private record Page(ItemStack result, List<ItemStack> catalysts, List<Cost> costs) {
        /** Whether the crucible holds enough of every aspect this recipe asks for. */
        boolean ready(CompoundTag essentia) {
            for (Cost cost : costs) {
                if (essentia.getInt(cost.aspect().tag()) < cost.amount()) {
                    return false;
                }
            }
            return true;
        }
    }

    private record Cost(TcAspect aspect, int amount) {
    }
}
