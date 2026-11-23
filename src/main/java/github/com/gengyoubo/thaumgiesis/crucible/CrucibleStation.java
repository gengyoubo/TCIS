package github.com.gengyoubo.thaumgiesis.crucible;

import com.pockyl.diegesis.api.Diegesis;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import github.com.gengyoubo.thaumgiesis.Thaumgiesis;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

public final class CrucibleStation {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Thaumgiesis.MOD_ID, "crucible");
    public static final ResourceLocation TOSS = ID.withSuffix("_toss");
    public static final String DISSOLVE = "dissolve";
    public static final String USE_ITEM = "use_item";

    private CrucibleStation() {
    }

    public static void register() {
        Diegesis.station(ID)
                .block(() -> ForgeRegistries.BLOCKS.getValue(ModernContentBridge.id("Thaumcraft:blockMetalDevice")))
                .when(state -> state.hasProperty(ModernContentBridge.METADATA)
                        && state.getValue(ModernContentBridge.METADATA) == 0)
                .openWhen((player, pos, state) -> !player.isShiftKeyDown())
                .exclusive()
                .logic(station -> new CrucibleLogic())
                .register();
    }
}
