package github.com.gengyoubo.thaumgiesis;

import github.com.gengyoubo.thaumgiesis.crucible.CrucibleStation;
import net.minecraftforge.fml.common.Mod;

@Mod(Thaumgiesis.MOD_ID)
public final class Thaumgiesis {
    public static final String MOD_ID = "thaumgiesis";

    public Thaumgiesis() {
        CrucibleStation.register();
    }
}
