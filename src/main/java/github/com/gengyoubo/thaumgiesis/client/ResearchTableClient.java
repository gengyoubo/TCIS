package github.com.gengyoubo.thaumgiesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.StationState;
import github.com.gengyoubo.mfcl.content.LegacyBlockEntity;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspect;
import github.com.gengyoubo.thaumgiesis.Thaumgiesis;
import github.com.gengyoubo.thaumgiesis.research.ResearchTableStation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

/**
 * The research table worked by hand. The scene's {@code desk} node lies on the table top, its pixels those of the table as
 * Thaumcraft's renderer draws it unturned (metadata 5: the table runs along +x from the block, the inkwell in the block's far
 * corner); each turn's scene turns the node instead. The scene draws the table itself from Thaumcraft's model while it is open, with
 * the inkwell and Thaumcraft's quill; beside the inkwell the player's aspects and two mixing dishes, and on the table's other half
 * the research note: its paper, running over the table's front edge, and its hexes.
 * <p>
 * Click the quill to take it up; with it, click an aspect to dip it, then an empty hex of the note to write it there (right-click
 * a written one to rub it out). Two aspects dropped in the dishes are combined. Click the inkwell again to put the quill back;
 * right-click the note or the inkwell, quill down, to take them off the table.
 */
@Mod.EventBusSubscriber(modid = Thaumgiesis.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ResearchTableClient implements StationBehavior {
    private static final String LANG = "thaumgiesis.research.";
    private static final String DESK = "desk";
    private static final int TOOLS = 0;
    private static final int NOTE = 1;
    /** The inkwell, where Thaumcraft's model has it, and its height. */
    private static final float INK_X = 3.5F;
    private static final float INK_Z = 3.5F;
    private static final float INK_HALF = 1.5F;
    private static final float INK_HIGH = 2.0F;
    /** The note, on the table's other half: the room its hexes have on its paper. */
    private static final float NOTE_X = 17.5F;
    private static final float NOTE_Z = 2.0F;
    private static final float NOTE_SIZE = 13.0F;
    private static final float BOARD = 11.0F;
    private static final float BOARD_X = NOTE_X + NOTE_SIZE / 2;
    private static final float BOARD_Z = NOTE_Z + NOTE_SIZE / 2 + 0.4F;
    /** The aspects, beside the inkwell: a grid, and its arrows under it. */
    private static final float PALETTE_X = 6.3F;
    private static final float PALETTE_Z = 1.2F;
    private static final int COLUMNS = 4;
    private static final int ROWS = 5;
    private static final float CELL = 2.25F;
    private static final int PER_PAGE = COLUMNS * ROWS;
    private static final float ARROWS_Z = PALETTE_Z + ROWS * CELL + 0.6F;
    /** The two mixing dishes, under the inkwell. */
    private static final float DISH_X = 3.5F;
    private static final float[] DISH_Z = {8.5F, 12.0F};
    private static final float DISH_RADIUS = 1.3F;
    private static final float SQRT3 = Mth.sqrt(3);
    /** Ink on the note's paper. */
    private static final int INK = 0xFF3E281C;
    /**
     * The note's paper under its hexes, a little bigger than the half of the table it lies on: its foot runs over the table's front
     * edge and hangs down.
     */
    private static final ResourceLocation PARCHMENT = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/parchment.png");
    private static final float PAPER_WIDE = 15.6F;
    private static final float PAPER_DEEP = 17.4F;
    private static final float TABLE_FRONT = 16.0F;
    private static final float PAPER_DROP = 72.0F;
    @Nullable
    private static ResearchTableClient focused;

    private boolean penHeld;
    @Nullable
    private String dipped;
    private final String[] dishes = new String[2];
    private int palettePage;
    @Nullable
    private Vector3f pointer;
    @Nullable
    private Spot spot;
    private CompoundTag seen = new CompoundTag();
    private Map<String, Cell> cells = Map.of();
    private float hexSize = 1;
    private List<String> aspects = List.of();
    private String caption = "";
    @Nullable
    private LegacyBlockEntity hidden;
    private float time;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (ResourceLocation id : ResearchTableStation.IDS) {
                DiegesisClient.registerBehavior(id, view -> new ResearchTableClient());
            }
            MinecraftForge.EVENT_BUS.addListener(ResearchTableClient::renderTooltip);
        });
    }

    @Override
    public void onOpen(StationView view) {
        focused = this;
        penHeld = false;
        dipped = null;
        dishes[0] = dishes[1] = null;
        read(view);
    }

    @Override
    public void onClose(StationView view) {
        if (focused == this) {
            focused = null;
        }
        penHeld = false;
        showLegacyDrawing();
    }

    @Override
    public void onSceneReady(StationView view) {
        view.node(DESK).ifPresent(node -> node.setPainter((pose, buffers, light) -> paint(view, pose, buffers, light)));
        read(view);
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        read(view);
    }

    @Override
    public void frame(StationView view, float seconds) {
        time += seconds;
        if (!view.isFocused()) {
            return;
        }
        hideLegacyDrawing(view);
        pointer = view.pointerOn(DESK, 0).orElse(null);
        spot = pointer == null ? null : find(pointer.x, pointer.z);
        updateCaption(view);
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (!DESK.equals(node)) {
            return false;
        }
        if (spot != null && button == 0) {
            if (spot.kind() == Kind.INKWELL && !seen.getBoolean("tools")) {
                insert(view, TOOLS, false);
            } else if (spot.kind().onNote() && !seen.getBoolean("note")) {
                insert(view, NOTE, false);
            }
        }
        return true;
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && DESK.equals(click.node())) {
            click(view, click.button());
        }
    }

    @Override
    public boolean onScroll(StationView view, String node, double delta) {
        if (DESK.equals(node) && spot != null && spot.kind().onPalette()) {
            turnPalette(delta > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    // ----------------------------------------------------------------------------------------------------------------- the hand

    private void click(StationView view, int button) {
        if (spot == null) {
            return;
        }
        boolean tools = seen.getBoolean("tools");
        boolean note = seen.getBoolean("note");
        boolean handFull = Minecraft.getInstance().player != null && !Minecraft.getInstance().player.getMainHandItem().isEmpty();
        switch (spot.kind()) {
            case INKWELL -> {
                if (!tools) {
                    if (handFull) {
                        insert(view, TOOLS, true);
                    }
                } else if (button == 0) {
                    penHeld = !penHeld;
                    if (!penHeld) {
                        dipped = null;
                    }
                    sound(penHeld ? 1.3F : 0.9F);
                } else if (button == 1 && !penHeld) {
                    take(view, TOOLS);
                }
            }
            case NOTE, HEX -> {
                if (!note) {
                    if (handFull && button == 0) {
                        insert(view, NOTE, true);
                    }
                } else if (!penHeld) {
                    if (button == 1) {
                        take(view, NOTE);
                    }
                } else if (spot.kind() == Kind.HEX) {
                    write(view, spot.cell(), button);
                }
            }
            case ASPECT -> {
                if (penHeld && button == 0) {
                    if (count(spot.tag()) <= 0) {
                        view.showCaption(Component.translatable(LANG + "empty_aspect"), 2);
                    } else {
                        dipped = spot.tag();
                        sound(1.6F);
                    }
                }
            }
            case PREVIOUS -> turnPalette(-1);
            case NEXT -> turnPalette(1);
            case DISH -> {
                int index = spot.index();
                if (button == 1) {
                    dishes[index] = null;
                } else if (penHeld && dipped != null) {
                    dishes[index] = dipped;
                    sound(1.1F);
                    if (dishes[0] != null && dishes[1] != null) {
                        CompoundTag data = new CompoundTag();
                        data.putString("first", dishes[0]);
                        data.putString("second", dishes[1]);
                        view.sendAction(ResearchTableStation.COMBINE, data);
                        dishes[0] = dishes[1] = null;
                    }
                }
            }
            default -> {
            }
        }
    }

    /** With the quill: an aspect written on an empty hex, or one written before rubbed out. */
    private void write(StationView view, Cell cell, int button) {
        if (seen.getBoolean("complete")) {
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putInt("q", cell.q());
        data.putInt("r", cell.r());
        if (button == 0 && cell.type() == 0 && dipped != null) {
            if (count(dipped) <= 0) {
                view.showCaption(Component.translatable(LANG + "empty_aspect"), 2);
                return;
            }
            if (seen.getFloat("ink") <= 0) {
                view.showCaption(Component.translatable(LANG + "no_ink"), 2);
                return;
            }
            data.putString("aspect", dipped);
            view.sendAction(ResearchTableStation.PLACE, data);
        } else if (button == 1 && cell.type() == 2) {
            view.sendAction(ResearchTableStation.ERASE, data);
            sound(0.7F);
        }
    }

    private static void insert(StationView view, int slot, boolean hand) {
        CompoundTag data = new CompoundTag();
        data.putInt("slot", slot);
        data.putBoolean("hand", hand);
        view.sendAction(ResearchTableStation.INSERT, data);
    }

    private static void take(StationView view, int slot) {
        CompoundTag data = new CompoundTag();
        data.putInt("slot", slot);
        view.sendAction(ResearchTableStation.TAKE, data);
    }

    private void turnPalette(int direction) {
        int pages = Math.max(1, (aspects.size() + PER_PAGE - 1) / PER_PAGE);
        if (pages > 1) {
            palettePage = Math.floorMod(palettePage + direction, pages);
            sound(1.0F);
        }
    }

    private static void sound(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, pitch, 0.35F));
    }

    private void updateCaption(StationView view) {
        String hint = seen.getString("hint");
        String key;
        if (!hint.isEmpty()) {
            key = hint;
        } else if (!penHeld) {
            key = "pick_pen";
        } else {
            key = dipped == null ? "dip" : "write";
        }
        if (!key.equals(caption)) {
            caption = key;
            view.showCaption(Component.translatable(LANG + key), 0);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------- the table

    private void read(StationView view) {
        CompoundTag data = view.state().data();
        if (data.equals(seen)) {
            return;
        }
        seen = data.copy();
        Map<String, Cell> parsed = new HashMap<>();
        int radius = 0;
        ListTag hexes = data.getList("hexes", Tag.TAG_COMPOUND);
        for (int i = 0; i < hexes.size(); i++) {
            CompoundTag hex = hexes.getCompound(i);
            Cell cell = new Cell(hex.getInt("q"), hex.getInt("r"), hex.getInt("t"), hex.contains("a") ? hex.getString("a") : null);
            parsed.put(cell.q() + ":" + cell.r(), cell);
            radius = Math.max(radius, (Math.abs(cell.q()) + Math.abs(cell.r()) + Math.abs(cell.q() + cell.r())) / 2);
        }
        cells = parsed;
        hexSize = BOARD / Math.max(3 * radius + 2, SQRT3 * (2 * radius + 1));
        Map<String, TcAspect> known = CrucibleHud.aspects();
        List<String> tags = new ArrayList<>(data.getCompound("pool").getAllKeys());
        for (String tag : data.getCompound("bonus").getAllKeys()) {
            if (!tags.contains(tag)) {
                tags.add(tag);
            }
        }
        tags.sort(Comparator.<String, Boolean>comparing(tag -> {
            TcAspect aspect = known.get(tag);
            return aspect != null && !aspect.primal();
        }).thenComparing(AspectIconsName::of));
        aspects = tags;
        palettePage = Math.min(palettePage, Math.max(0, (aspects.size() - 1) / PER_PAGE));
        if (!data.getBoolean("tools")) {
            penHeld = false;
            dipped = null;
        }
    }

    private int count(String tag) {
        return seen.getCompound("pool").getInt(tag) + seen.getCompound("bonus").getInt(tag);
    }

    /** What of the table is at {@code x, z}. */
    @Nullable
    private Spot find(float x, float z) {
        if (Math.abs(x - INK_X) <= INK_HALF + 0.6F && Math.abs(z - INK_Z) <= INK_HALF + 0.6F) {
            return new Spot(Kind.INKWELL, null, null, 0);
        }
        for (int index = 0; index < 2; index++) {
            float dx = x - DISH_X;
            float dz = z - DISH_Z[index];
            if (dx * dx + dz * dz <= (DISH_RADIUS + 0.3F) * (DISH_RADIUS + 0.3F)) {
                return new Spot(Kind.DISH, null, null, index);
            }
        }
        if (x >= NOTE_X && x < NOTE_X + NOTE_SIZE && z >= NOTE_Z && z < NOTE_Z + NOTE_SIZE) {
            Cell cell = hexAt(x, z);
            return cell != null ? new Spot(Kind.HEX, cell, null, 0) : new Spot(Kind.NOTE, null, null, 0);
        }
        if (x >= PALETTE_X && x < PALETTE_X + COLUMNS * CELL) {
            if (z >= PALETTE_Z && z < PALETTE_Z + ROWS * CELL) {
                int index = palettePage * PER_PAGE + (int) ((z - PALETTE_Z) / CELL) * COLUMNS + (int) ((x - PALETTE_X) / CELL);
                return index < aspects.size() ? new Spot(Kind.ASPECT, null, aspects.get(index), index) : null;
            }
            if (z >= ARROWS_Z && z < ARROWS_Z + 1.6F) {
                return x < PALETTE_X + COLUMNS * CELL / 2 ? new Spot(Kind.PREVIOUS, null, null, 0)
                        : new Spot(Kind.NEXT, null, null, 0);
            }
        }
        return null;
    }

    /** The hex of the note under {@code x, z}, rounded as Thaumcraft rounds it (flat-topped, axial q and r). */
    @Nullable
    private Cell hexAt(float x, float z) {
        float fx = (x - BOARD_X) / hexSize;
        float fz = (z - BOARD_Z) / hexSize;
        float q = 2F / 3 * fx;
        float r = -1F / 3 * fx + SQRT3 / 3 * fz;
        float s = -q - r;
        int rq = Math.round(q);
        int rr = Math.round(r);
        int rs = Math.round(s);
        float dq = Math.abs(rq - q);
        float dr = Math.abs(rr - r);
        float ds = Math.abs(rs - s);
        if (dq > dr && dq > ds) {
            rq = -rr - rs;
        } else if (dr > ds) {
            rr = -rq - rs;
        }
        return cells.get(rq + ":" + rr);
    }

    private float hexX(Cell cell) {
        return BOARD_X + hexSize * 1.5F * cell.q();
    }

    private float hexZ(Cell cell) {
        return BOARD_Z + hexSize * SQRT3 * (cell.r() + cell.q() / 2F);
    }

    /**
     * Thaumcraft's renderer draws the table with the tools, the note and a blank parchment on it; while the station is open the
     * scene draws the table itself instead. MFCL's renderer leaves alone a tile it is to retry later, so the tile is put off to the
     * end of time meanwhile, and taken off that list when the player goes.
     */
    private void hideLegacyDrawing(StationView view) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getBlockEntity(view.pos()) instanceof LegacyBlockEntity host)) {
            return;
        }
        Map<Object, Long> retry = retryMap(host);
        if (retry != null) {
            retry.put(host, Long.MAX_VALUE);
            hidden = host;
        }
    }

    private void showLegacyDrawing() {
        if (hidden != null) {
            Map<Object, Long> retry = retryMap(hidden);
            if (retry != null && Long.valueOf(Long.MAX_VALUE).equals(retry.get(hidden))) {
                retry.remove(hidden);
            }
            hidden = null;
        }
    }

    /** True while the scene draws the table in place of Thaumcraft's renderer. */
    private boolean drawsTable() {
        return hidden != null && !hidden.isRemoved();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<Object, Long> retryMap(LegacyBlockEntity host) {
        try {
            Object renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(host);
            if (renderer == null) {
                return null;
            }
            Field field = renderer.getClass().getDeclaredField("retryAfter");
            field.setAccessible(true);
            return (Map<Object, Long>) field.get(renderer);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    // ----------------------------------------------------------------------------------------------------------------- drawing

    private void paint(StationView view, PoseStack pose, MultiBufferSource buffers, int light) {
        TableCanvas canvas = new TableCanvas(pose, buffers, light);
        boolean tools = seen.getBoolean("tools");
        boolean note = seen.getBoolean("note");
        if (drawsTable()) {
            ResearchTableModel.render(pose, buffers, light, tools);
        }
        if (tools) {
            if (!penHeld) {
                ResearchTableModel.quill(pose, buffers, light, INK_X - 0.3F, INK_HIGH - 0.8F, INK_Z + 0.2F, 6, -15, 15);
            }
        } else {
            canvas.disc(INK_X, INK_Z, INK_HALF + 0.3F, 0.03F, 0x40E8D5A8);
        }
        drawNote(canvas, note);
        drawPalette(canvas);
        drawDishes(canvas);
        if (penHeld && pointer != null) {
            boolean overHex = spot != null && spot.kind() == Kind.HEX;
            float lift = overHex ? 0.15F : 0.8F + 0.15F * Mth.sin(time * 4);
            ResearchTableModel.quill(pose, buffers, light, pointer.x, lift, pointer.z, 6, -28, 0);
            if (dipped != null) {
                TcAspect aspect = CrucibleHud.aspects().get(dipped);
                int colour = aspect == null ? 0x6060A0 : aspect.color();
                canvas.disc(pointer.x, pointer.z, 0.25F, 0.06F, 0xC0000000 | colour);
            }
        }
    }

    private void drawNote(TableCanvas canvas, boolean note) {
        if (!note) {
            int edge = 0x60E8D5A8;
            canvas.rect(NOTE_X, NOTE_Z, NOTE_SIZE, 0.15F, 0.03F, edge);
            canvas.rect(NOTE_X, NOTE_Z + NOTE_SIZE - 0.15F, NOTE_SIZE, 0.15F, 0.03F, edge);
            canvas.rect(NOTE_X, NOTE_Z, 0.15F, NOTE_SIZE, 0.03F, edge);
            canvas.rect(NOTE_X + NOTE_SIZE - 0.15F, NOTE_Z, 0.15F, NOTE_SIZE, 0.03F, edge);
            String text = I18n.get(LANG + "place_note");
            float scale = Math.min(0.07F, (NOTE_SIZE - 2) / Math.max(1, canvas.width(text, 1)));
            canvas.text(text, BOARD_X - canvas.width(text, scale) / 2, BOARD_Z - 0.3F, scale, 0.05F, 0xC0E8D5A8);
            return;
        }
        float paperX = BOARD_X - PAPER_WIDE / 2;
        float paperZ = BOARD_Z - PAPER_DEEP / 2 + 0.6F;
        if (!canvas.drapedTexture(PARCHMENT, paperX, paperZ, PAPER_WIDE, PAPER_DEEP, 0.02F, TABLE_FRONT, PAPER_DROP, 0xFFFFFFFF)) {
            canvas.rect(paperX, paperZ, PAPER_WIDE, Math.min(PAPER_DEEP, TABLE_FRONT - paperZ), 0.02F, 0xFFE8D5A8);
        }
        String key = seen.getString("key");
        String name = I18n.exists("tc.research_name." + key) ? I18n.get("tc.research_name." + key) : key;
        if (!name.isEmpty()) {
            float scale = Math.min(0.06F, (NOTE_SIZE - 3) / Math.max(1, canvas.width(name, 1)));
            canvas.text(name, BOARD_X - canvas.width(name, scale) / 2, NOTE_Z - 0.2F, scale, 0.05F, INK);
        }
        Map<String, TcAspect> known = CrucibleHud.aspects();
        for (Cell cell : cells.values()) {
            float x = hexX(cell);
            float z = hexZ(cell);
            boolean hovered = spot != null && spot.kind() == Kind.HEX && spot.cell() == cell;
            int fill = cell.type() == 1 ? 0x30604020 : hovered && penHeld ? 0x40FFFFFF : 0x143E281C;
            canvas.hexagon(x, z, hexSize * 0.92F, 0.04F, fill);
            canvas.hexagonEdge(x, z, hexSize * 0.92F, 0.06F, 0.05F, cell.type() == 1 ? 0xC05A3A1A : 0x703E281C);
        }
        // The lines between linked aspects next to each other: one is made from the other.
        for (Cell cell : cells.values()) {
            if (cell.aspect() == null) {
                continue;
            }
            for (int[] step : new int[][] {{1, 0}, {0, 1}, {1, -1}}) {
                Cell next = cells.get((cell.q() + step[0]) + ":" + (cell.r() + step[1]));
                if (next != null && next.aspect() != null && linked(known, cell.aspect(), next.aspect())) {
                    canvas.line(hexX(cell), hexZ(cell), hexX(next), hexZ(next), 0.12F, 0.07F, 0xC0603A80);
                }
            }
        }
        for (Cell cell : cells.values()) {
            if (cell.aspect() != null) {
                icon(canvas, known, cell.aspect(), hexX(cell), hexZ(cell), hexSize * 1.15F, 0.09F, 0xFF);
            } else if (penHeld && dipped != null && spot != null && spot.kind() == Kind.HEX && spot.cell() == cell) {
                icon(canvas, known, dipped, hexX(cell), hexZ(cell), hexSize * 1.15F, 0.09F, 0x70);
            }
        }
        if (seen.getBoolean("complete")) {
            canvas.rect(NOTE_X, NOTE_Z, NOTE_SIZE, NOTE_SIZE, 0.11F, 0x30A060FF);
            String done = I18n.get(LANG + "done");
            float scale = 0.09F;
            canvas.text(done, BOARD_X - canvas.width(done, scale) / 2, NOTE_Z + NOTE_SIZE - 1.0F, scale, 0.12F, 0xFF5A2A8A);
        }
    }

    private static boolean linked(Map<String, TcAspect> known, String a, String b) {
        TcAspect first = known.get(a);
        TcAspect second = known.get(b);
        return first != null && first.components().contains(b) || second != null && second.components().contains(a);
    }

    private void drawPalette(TableCanvas canvas) {
        Map<String, TcAspect> known = CrucibleHud.aspects();
        canvas.rect(PALETTE_X - 0.4F, PALETTE_Z - 0.4F, COLUMNS * CELL + 0.8F, ROWS * CELL + 2.6F, 0.02F, 0x50201828);
        if (aspects.isEmpty()) {
            String none = I18n.get(LANG + "no_aspects");
            canvas.text(none, PALETTE_X, PALETTE_Z + 1, 0.05F, 0.05F, 0xFFE8D5A8);
            return;
        }
        for (int slot = 0; slot < PER_PAGE; slot++) {
            int index = palettePage * PER_PAGE + slot;
            if (index >= aspects.size()) {
                break;
            }
            String tag = aspects.get(index);
            float x = PALETTE_X + slot % COLUMNS * CELL;
            float z = PALETTE_Z + slot / COLUMNS * CELL;
            boolean hovered = spot != null && spot.kind() == Kind.ASPECT && tag.equals(spot.tag());
            if (tag.equals(dipped)) {
                canvas.rect(x + 0.05F, z + 0.05F, CELL - 0.1F, CELL - 0.1F, 0.03F, 0x80E0B050);
            } else if (hovered) {
                canvas.rect(x + 0.05F, z + 0.05F, CELL - 0.1F, CELL - 0.1F, 0.03F, 0x40FFFFFF);
            }
            int amount = count(tag);
            icon(canvas, known, tag, x + CELL / 2, z + CELL / 2 - 0.15F, 1.6F, 0.05F, amount > 0 ? 0xFF : 0x60);
            String number = Integer.toString(amount);
            float scale = 0.045F;
            canvas.text(number, x + CELL - 0.15F - canvas.width(number, scale), z + CELL - 0.55F, scale, 0.08F,
                    amount > 0 ? 0xFFF8E9C7 : 0xFF9A8A7A);
        }
        int pages = Math.max(1, (aspects.size() + PER_PAGE - 1) / PER_PAGE);
        if (pages > 1) {
            String text = "‹  " + (palettePage + 1) + "/" + pages + "  ›";
            float scale = 0.06F;
            canvas.text(text, PALETTE_X + COLUMNS * CELL / 2 - canvas.width(text, scale) / 2, ARROWS_Z + 0.2F, scale, 0.05F,
                    0xFFF8E9C7);
        }
    }

    private void drawDishes(TableCanvas canvas) {
        Map<String, TcAspect> known = CrucibleHud.aspects();
        for (int index = 0; index < 2; index++) {
            boolean hovered = spot != null && spot.kind() == Kind.DISH && spot.index() == index;
            canvas.disc(DISH_X, DISH_Z[index], DISH_RADIUS, 0.03F, hovered ? 0xFF8A8478 : 0xFF6E685E);
            canvas.disc(DISH_X, DISH_Z[index], DISH_RADIUS - 0.25F, 0.04F, 0xFF3A362F);
            if (dishes[index] != null) {
                icon(canvas, known, dishes[index], DISH_X, DISH_Z[index], 1.5F, 0.06F, 0xFF);
            }
        }
        canvas.text("+", DISH_X - 0.25F, (DISH_Z[0] + DISH_Z[1]) / 2 - 0.4F, 0.09F, 0.05F, 0xC0E8D5A8);
    }

    /** An aspect's icon, its texture tinted with its colour as Thaumcraft draws it, or a swatch when it has none. */
    private static void icon(TableCanvas canvas, Map<String, TcAspect> known, String tag, float x, float z, float size, float lift,
                             int alpha) {
        TcAspect aspect = known.get(tag);
        int colour = alpha << 24 | (aspect == null ? 0x808080 : aspect.color() & 0xFFFFFF);
        ResourceLocation texture = aspect == null ? null : ResourceLocation.tryParse(aspect.texture());
        if (texture == null || aspect.texture().isEmpty()
                || !canvas.texture(texture, x - size / 2, z - size / 2, size, size, lift, colour)) {
            canvas.disc(x, z, size * 0.4F, lift, colour);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------- tooltips

    private static void renderTooltip(ScreenEvent.Render.Post event) {
        ResearchTableClient table = focused;
        if (table == null || table.spot == null || !DiegesisClient.isStationScreen(event.getScreen())) {
            return;
        }
        List<Component> lines = table.tooltip();
        if (!lines.isEmpty()) {
            event.getGuiGraphics().renderComponentTooltip(Minecraft.getInstance().font, lines, event.getMouseX(), event.getMouseY());
        }
    }

    private List<Component> tooltip() {
        Map<String, TcAspect> known = CrucibleHud.aspects();
        boolean tools = seen.getBoolean("tools");
        return switch (spot.kind()) {
            case INKWELL -> !tools ? List.of(Component.translatable(LANG + "tip.tools_empty"))
                    : List.of(Component.translatable(LANG + (penHeld ? "tip.pen_return" : "tip.pen_take")),
                    Component.translatable(LANG + "tip.ink", Math.round(seen.getFloat("ink") * 100)));
            case NOTE -> List.of(Component.translatable(LANG + (seen.getBoolean("note") ? "tip.note_take" : "tip.note_empty")));
            case HEX -> spot.cell().aspect() == null ? List.of()
                    : CrucibleHud.aspectTip(spot.cell().aspect(), colour(known, spot.cell().aspect()), -1, -1);
            case ASPECT -> CrucibleHud.aspectTip(spot.tag(), colour(known, spot.tag()), count(spot.tag()), -1);
            case DISH -> dishes[spot.index()] == null ? List.of(Component.translatable(LANG + "tip.dish"))
                    : CrucibleHud.aspectTip(dishes[spot.index()], colour(known, dishes[spot.index()]), -1, -1);
            case PREVIOUS, NEXT -> List.of(Component.translatable(LANG + "tip.page"));
        };
    }

    private static int colour(Map<String, TcAspect> known, String tag) {
        TcAspect aspect = known.get(tag);
        return aspect == null ? 0xFFFFFF : aspect.color();
    }

    private enum Kind {
        INKWELL, NOTE, HEX, ASPECT, PREVIOUS, NEXT, DISH;

        boolean onNote() {
            return this == NOTE || this == HEX;
        }

        boolean onPalette() {
            return this == ASPECT || this == PREVIOUS || this == NEXT;
        }
    }

    private record Spot(Kind kind, @Nullable Cell cell, @Nullable String tag, int index) {
    }

    /** A hex of the note: where it is, what is on it (0 nothing, 1 given by the research, 2 written by the player), and its aspect. */
    private record Cell(int q, int r, int type, @Nullable String aspect) {
    }

    /** Sorting by an aspect's shown name. */
    private static final class AspectIconsName {
        static String of(String tag) {
            return github.com.gengyoubo.tcmodern.lib.client.AspectIcons.name(tag);
        }
    }
}
