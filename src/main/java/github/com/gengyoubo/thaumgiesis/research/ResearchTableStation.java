package github.com.gengyoubo.thaumgiesis.research;

import com.pockyl.diegesis.api.Diegesis;
import com.pockyl.diegesis.api.station.StationType;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import github.com.gengyoubo.thaumgiesis.Thaumgiesis;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Thaumcraft 4's research table as a station: the table (the half of {@code blockTable} with metadata 2 to 5 that holds the tile)
 * opens a scene over its top where the player takes up the quill, dips it in aspects and writes them on the research note.
 * Thaumcraft keeps its turn in the metadata, not in a block state Diegesis can read, so each of the four turns is a station of its
 * own with a fixed scene turned to match ({@code research_table_<meta>}).
 * <p>
 * The table is two blocks. When the scribing tools make it, the block clicked becomes the main half with the tile, its metadata the
 * side the other half lies on (2 north, 3 south, 4 west, 5 east, as Forge numbers sides); the other half gets the opposite side plus
 * 4 (so 7, 6, 9, 8). Each station opens from both halves and lives at the main one.
 */
public final class ResearchTableStation {
    public static final String INSERT = "insert";
    public static final String TAKE = "take";
    public static final String PLACE = "place";
    public static final String ERASE = "erase";
    public static final String COMBINE = "combine";
    /** An effect: the player gained an aspect (data: {@code aspect}, {@code amount}, and {@code new} when first discovered). */
    public static final ResourceLocation GAINED = ResourceLocation.fromNamespaceAndPath(Thaumgiesis.MOD_ID, "research_gained");
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
            Direction side = Direction.from3DDataValue(meta);
            int partner = side.getOpposite().get3DDataValue() + 4;
            Diegesis.station(id(meta))
                    .block(ResearchTableStation::table)
                    .when(state -> metadata(state) == meta || metadata(state) == partner)
                    .anchor((level, pos, state) -> metadata(state) == partner ? pos.relative(side.getOpposite()) : pos)
                    .orientation(StationType.Orientation.FIXED)
                    // The other half opens the station only while its main half is there to work.
                    .openWhen((player, pos, state) -> !player.isShiftKeyDown() && (metadata(state) == meta
                            || metadata(player.level().getBlockState(pos)) == meta
                            || metadata(player.level().getBlockState(pos.relative(side.getOpposite()))) == meta))
                    .exclusive()
                    .logic(station -> new ResearchTableLogic())
                    .register();
        }
    }

    private static Block table() {
        return ForgeRegistries.BLOCKS.getValue(ModernContentBridge.id("Thaumcraft:blockTable"));
    }

    /** The block's Thaumcraft metadata, or -1 when it is no research table. */
    private static int metadata(BlockState state) {
        return state.is(table()) && state.hasProperty(ModernContentBridge.METADATA) ? state.getValue(ModernContentBridge.METADATA) : -1;
    }
}
