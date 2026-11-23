package github.com.gengyoubo.thaumgiesis.mixin;

import com.pockyl.diegesis.api.client.DiegesisClient;
import github.com.gengyoubo.mfcl.bridge.LegacyServices;
import github.com.gengyoubo.thaumgiesis.crucible.CrucibleStation;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "github.com.gengyoubo.mfcl.compat.thaumcraft.ThaumcraftGoggles", remap = false)
abstract class ThaumcraftGogglesMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void thaumgiesis$skipCrucibleOverlay(LegacyServices services, RenderLevelStageEvent event,
                                                       CallbackInfo callback) {
        if (DiegesisClient.isStationScreen(Minecraft.getInstance().screen)
                && DiegesisClient.focusedStation()
                        .filter(view -> view.type().id().equals(CrucibleStation.ID)).isPresent()) {
            callback.cancel();
        }
    }
}
