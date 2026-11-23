package github.com.gengyoubo.thaumgiesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.pockyl.diegesis.api.state.StationState;
import github.com.gengyoubo.thaumgiesis.Thaumgiesis;
import github.com.gengyoubo.thaumgiesis.crucible.CrucibleStation;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = Thaumgiesis.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CrucibleClient implements StationBehavior {
    private static final String LANG = "thaumgiesis.crucible.";
    private static final String MOUTH = "mouth";
    private static final String BOOK = "recipe_book";
    private static final String HAND = "hand";
    /** The crucible's rim, scene pixels, and how high the bucket is drawn as it pours. */
    private static final float RIM = 16;
    private static final float POUR_SIZE = 5;
    private final CrucibleHud hud;
    private final List<Toss> tosses = new ArrayList<>();
    private boolean hidingCursor;
    @Nullable
    private PourMotion pour;

    private CrucibleClient(CrucibleHud hud) {
        this.hud = hud;
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            CrucibleHud hud = new CrucibleHud();
            hud.register(MinecraftForge.EVENT_BUS);
            DiegesisClient.registerBehavior(CrucibleStation.ID, view -> new CrucibleClient(hud));
        });
    }

    @Override
    public void onOpen(StationView view) {
        hud.open(view);
        update(view);
    }

    @Override
    public void onClose(StationView view) {
        hud.close(view);
        if (pour != null) {
            pour.stop();
            pour = null;
        }
        view.setCarriedHidden(false);
        hidingCursor = false;
    }

    @Override
    public void onSceneReady(StationView view) {
        view.node("broth").ifPresent(node -> node.setPainter((pose, buffers, light) -> paint(view, pose, buffers, light)));
        hud.sceneReady(view);
        update(view);
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (BOOK.equals(node) && button == 0) {
            hud.toggleBook(view);
            return true;
        }
        if (CrucibleRecipeBook.NODE.equals(node)) {
            if (button == 0) {
                hud.clickBook();
            }
            return true;
        }
        if (!MOUTH.equals(node) || button != 0 && button != 1) {
            return false;
        }
        click(view, button, false);
        return true;
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && BOOK.equals(click.node()) && click.button() == 0) {
            hud.toggleBook(view);
            return;
        }
        if (event instanceof SceneEvent.Click click && CrucibleRecipeBook.NODE.equals(click.node())) {
            if (click.button() == 0) {
                hud.clickBook();
            }
            return;
        }
        if (event instanceof SceneEvent.Click click && MOUTH.equals(click.node())
                && (click.button() == 0 || click.button() == 1)) {
            var player = Minecraft.getInstance().player;
            if (player != null && !player.getMainHandItem().isEmpty()) {
                click(view, click.button(), true);
            }
        }
    }

    private static void click(StationView view, int button, boolean hand) {
        CompoundTag data = new CompoundTag();
        data.putBoolean("hand", hand);
        view.cursorOn(18).ifPresent(at -> {
            data.putFloat("x", at.x());
            data.putFloat("z", at.z());
        });
        view.sendAction(button == 0 ? CrucibleStation.DISSOLVE : CrucibleStation.USE_ITEM, data);
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (!CrucibleStation.TOSS.equals(effect) || !"broth".equals(node) || tosses.size() >= 12) {
            return;
        }
        ItemStack item = ItemStack.of(data.getCompound("item"));
        float x = data.getFloat("x");
        float z = data.getFloat("z");
        float surface = data.getFloat("surface");
        if (!item.isEmpty() && Float.isFinite(x) && Float.isFinite(z) && Float.isFinite(surface)) {
            x = Mth.clamp(x, 1, 15);
            z = Mth.clamp(z, 1, 15);
            surface = Mth.clamp(surface, 4, 16);
            if (data.getBoolean("pour")) {
                if (!startPour(view, item, x, z, surface)) {
                    return;
                }
            } else {
                tosses.add(new Toss(item, x, z, surface));
            }
            if (view.isFocused()) {
                view.setCarriedHidden(true);
                hidingCursor = true;
            }
        }
    }

    /**
     * A bucket of water poured in as Hands-On Delight's cooks pour: from where the player clicked, over the crucible's rim, into the
     * middle of the water.
     */
    private boolean startPour(StationView view, ItemStack bucket, float x, float z, float surface) {
        var hand = view.node(HAND).orElse(null);
        if (hand == null) {
            return false;
        }
        if (pour != null) {
            pour.stop();
        }
        pour = new PourMotion(hand, "crucible_pour", bucket, new Vector3f(x, RIM + 6, z - 6),
                new Vector3f(8, surface, 8), null, POUR_SIZE, -1, 0).lipAtLeast(RIM + 1.5F);
        return true;
    }

    @Override
    public boolean onScroll(StationView view, String node, double delta) {
        if (CrucibleRecipeBook.NODE.equals(node)) {
            hud.scrollBook(delta);
            return true;
        }
        return false;
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        hud.frame(view, deltaSeconds);
        for (var iterator = tosses.iterator(); iterator.hasNext();) {
            Toss toss = iterator.next();
            toss.time += deltaSeconds;
            if (!toss.splashed && toss.time >= toss.duration()) {
                toss.splashed = true;
                view.spawnParticles(DiegesisParticles.BUBBLE, new Vector3f(8, toss.surface, 8), new Vector3f(0, 3, 0),
                        5, 8, 0xCFA6E6);
            }
            if (toss.time >= toss.duration()) {
                iterator.remove();
            }
        }
        if (pour != null && !pour.frame(view, deltaSeconds)) {
            pour = null;
        }
        if (hidingCursor && tosses.isEmpty() && pour == null) {
            view.setCarriedHidden(false);
            hidingCursor = false;
        }
    }

    @Override
    public void onStateChanged(StationView view, StationState.Changes changes) {
        if (view.isFocused()) {
            update(view);
        }
    }

    private void update(StationView view) {
        var data = view.state().data();
        String hint = data.getString("hint");
        view.showCaption(Component.translatable(LANG + (hint.isEmpty() ? "guide" : hint)), 0);
    }

    private void paint(StationView view, PoseStack pose, MultiBufferSource buffers, int light) {
        var renderer = Minecraft.getInstance().getItemRenderer();
        for (Toss toss : tosses) {
            float fraction = Mth.clamp(toss.time / toss.duration(), 0, 1);
            float y = Mth.lerp(fraction * fraction, 19, toss.surface);
            pose.pushPose();
            pose.translate(Mth.lerp(fraction, toss.x - 8, 0), y, Mth.lerp(fraction, toss.z - 10, 0));
            pose.mulPose(Axis.ZP.rotationDegrees(160 * fraction));
            pose.mulPose(Axis.YP.rotationDegrees(180 * fraction));
            pose.scale(3.2F, 3.2F, 3.2F);
            renderer.renderStatic(toss.item, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, view.level(), 0);
            pose.popPose();
        }
    }

    private static final class Toss {
        final ItemStack item;
        final float x;
        final float z;
        final float surface;
        float time;
        boolean splashed;

        Toss(ItemStack item, float x, float z, float surface) {
            this.item = item;
            this.x = x;
            this.z = z;
            this.surface = surface;
        }

        float duration() {
            return 0.36F;
        }
    }
}
