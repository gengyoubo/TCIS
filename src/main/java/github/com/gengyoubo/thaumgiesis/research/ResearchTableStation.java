package github.com.gengyoubo.thaumgiesis.research;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.station.StationType;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import github.com.gengyoubo.thaumgiesis.Thaumgiesis;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Thaumcraft 4's research table as a station: the table (the half of {@code blockTable} with metadata 2 to 5 that holds the tile)
 * opens a scene over its top where the player takes up the quill, dips it in aspects and writes them on the research note.
 * Thaumcraft keeps its turn in the metadata, not in a block state Diegesis can read, so each of the four turns is a station of its
 * own with a fixed scene turned to match ({@code research_table_<meta>}).
 */
public final class ResearchTableStation {
    public static final String INSERT = "insert";
    public static final String TAKE = "take";
    public static final String PLACE = "place";
    public static final String ERASE = "erase";
    public static final String COMBINE = "combine";
    /** The four turns, by metadata: how far Thaumcraft's renderer turns the table, degrees about y. */
    private static final int[] METAS = {2, 3, 4, 5};
    public static final List<ResourceLocation> IDS = new ArrayList<>();

    static {
        for (int meta : METAS) {
            IDS.add(id(meta));
        }
    }

    private ResearchTableStation() {
    }

    public static ResourceLocation id(int meta) {
        return ResourceLocation.fromNamespaceAndPath(Thaumgiesis.MOD_ID, "research_table_" + meta);
    }

    @Nullable
    public static Integer metaOf(ResourceLocation id) {
        for (int meta : METAS) {
            if (id(meta).equals(id)) {
                return meta;
            }
        }
        return null;
    }

    public static void register() {
        for (int meta : METAS) {
            Diegesis.station(id(meta))
                    .block(() -> ForgeRegistries.BLOCKS.getValue(ModernContentBridge.id("Thaumcraft:blockTable")))
                    .when(state -> state.hasProperty(ModernContentBridge.METADATA)
                            && state.getValue(ModernContentBridge.METADATA) == meta)
                    .orientation(StationType.Orientation.FIXED)
                    .openWhen((player, pos, state) -> !player.isShiftKeyDown())
                    .exclusive()
                    .logic(station -> new ResearchTableLogic())
                    .register();
        }
    }
}
