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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = Thaumgiesis.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CrucibleClient implements StationBehavior {
    private static final String LANG = "thaumgiesis.crucible.";
    private static final String MOUTH = "mouth";
    private final List<Toss> tosses = new ArrayList<>();
    private boolean hidingCursor;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> DiegesisClient.registerBehavior(CrucibleStation.ID, view -> new CrucibleClient()));
    }

    @Override
    public void onOpen(StationView view) {
        update(view);
    }

    @Override
    public void onClose(StationView view) {
        view.setCarriedHidden(false);
        hidingCursor = false;
    }

    @Override
    public void onSceneReady(StationView view) {
        view.node("broth").ifPresent(node -> node.setPainter((pose, buffers, light) -> paint(view, pose, buffers, light)));
        update(view);
    }

    @Override
    public boolean onItemClick(StationView view, String node, ItemStack stack, int button, boolean shift) {
        if (!MOUTH.equals(node) || button != 0 && button != 1) {
            return false;
        }
        click(view, button, false);
        return true;
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
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
            tosses.add(new Toss(item, Mth.clamp(x, 1, 15), Mth.clamp(z, 1, 15), Mth.clamp(surface, 4, 16),
                    data.getBoolean("pour")));
            if (view.isFocused()) {
                view.setCarriedHidden(true);
                hidingCursor = true;
            }
        }
    }

    @Override
    public void frame(StationView view, float deltaSeconds) {
        for (var iterator = tosses.iterator(); iterator.hasNext();) {
            Toss toss = iterator.next();
            toss.time += deltaSeconds;
            if (!toss.splashed && toss.time >= toss.duration() * (toss.pour ? 0.5F : 1)) {
                toss.splashed = true;
                view.spawnParticles(toss.pour ? DiegesisParticles.SPLASH : DiegesisParticles.BUBBLE,
                        new Vector3f(8, toss.surface, 8), new Vector3f(0, 3, 0), 5, 8,
                        toss.pour ? 0x739EF5 : 0xCFA6E6);
            }
            if (toss.time >= toss.duration()) {
                iterator.remove();
            }
        }
        if (hidingCursor && tosses.isEmpty()) {
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
        var essence = data.getCompound("essentia");
        var status = Component.translatable(LANG + "contents", data.getInt("water"));
        for (String aspect : essence.getAllKeys().stream().sorted().toList()) {
            int count = essence.getInt(aspect);
            status.append("\n").append(Component.translatableWithFallback("tc.aspect." + aspect, aspect))
                    .append(" × " + count);
        }
        status.append("\n").append(Component.translatable(LANG + "mouth"));
        view.node(MOUTH).ifPresent(node -> node.setTooltip(status));
        String hint = data.getString("hint");
        view.showCaption(Component.translatable(LANG + (hint.isEmpty() ? "guide" : hint)), 0);
    }

    private void paint(StationView view, PoseStack pose, MultiBufferSource buffers, int light) {
        var renderer = Minecraft.getInstance().getItemRenderer();
        for (Toss toss : tosses) {
            float fraction = Mth.clamp(toss.time / toss.duration(), 0, 1);
            float progress = toss.pour ? Mth.sin(fraction * Mth.PI) : fraction;
            float y = toss.pour ? toss.surface + 5 + progress * 2
                    : Mth.lerp(fraction * fraction, 19, toss.surface);
            pose.pushPose();
            pose.translate(Mth.lerp(progress, toss.x - 8, 0), y, Mth.lerp(progress, toss.z - 10, 0));
            pose.mulPose(Axis.ZP.rotationDegrees(toss.pour ? -105 * progress : 160 * fraction));
            pose.mulPose(Axis.YP.rotationDegrees(toss.pour ? 0 : 180 * fraction));
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
        final boolean pour;
        float time;
        boolean splashed;

        Toss(ItemStack item, float x, float z, float surface, boolean pour) {
            this.item = item;
            this.x = x;
            this.z = z;
            this.surface = surface;
            this.pour = pour;
        }

        float duration() {
            return pour ? 0.48F : 0.36F;
        }
    }
}
