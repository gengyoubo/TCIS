package github.com.gengyoubo.thaumgiesis.research;

import com.mojang.logging.LogUtils;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import github.com.gengyoubo.mfcl.content.LegacyBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * The research table's rules, all Thaumcraft's: the scene asks to put the tools or a note on the table, to take them off, to write
 * or rub out an aspect on a hex and to combine two aspects; the logic does it on the original tile and publishes what the table
 * and the player at it hold.
 */
final class ResearchTableLogic implements StationLogic {
    private LegacyResearchTable table;
    private LegacyBlockEntity failedTile;
    private String hint = "";
    private String user = "";

    @Override
    public void onOpen(StationContext station, Player player) {
        hint = "";
        user = player.getGameProfile().getName();
        publish(station);
    }

    @Override
    public void tick(StationContext station) {
        if (station.level().getGameTime() % 5 == 0) {
            publish(station);
        }
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        user = player.getGameProfile().getName();
        try {
            LegacyResearchTable tile = tile(station);
            if (tile == null) {
                publish(station);
                return;
            }
            switch (action) {
                case ResearchTableStation.INSERT -> insert(serverPlayer, tile, data);
                case ResearchTableStation.TAKE -> {
                    int slot = data.getInt("slot") == LegacyResearchTable.NOTE ? LegacyResearchTable.NOTE : LegacyResearchTable.TOOLS;
                    ItemStack taken = tile.take(slot);
                    if (!taken.isEmpty()) {
                        ItemHandlerHelper.giveItemToPlayer(serverPlayer, taken);
                        hint = slot == LegacyResearchTable.NOTE ? "note_taken" : "tools_taken";
                    }
                }
                case ResearchTableStation.PLACE -> {
                    tile.place(serverPlayer, data.getInt("q"), data.getInt("r"), data.getString("aspect"));
                    hint = "";
                    station.level().playSound(null, station.pos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.3F, 1.6F);
                }
                case ResearchTableStation.ERASE -> {
                    tile.place(serverPlayer, data.getInt("q"), data.getInt("r"), null);
                    hint = "";
                }
                case ResearchTableStation.COMBINE -> {
                    CompoundTag made = tile.combine(serverPlayer, data.getString("first"), data.getString("second"));
                    hint = made != null ? "combined" : "no_combination";
                    if (made != null) {
                        station.level().playSound(null, station.pos(), SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.4F, 1.4F);
                        // The player at the table is told what they made, as Thaumcraft tells them in the screen's corner.
                        station.effect(ResearchTableStation.GAINED, "desk", made);
                    }
                }
                default -> {
                }
            }
            publish(station);
        } catch (Exception failure) {
            fail(station, failure);
        }
    }

    /** A note or the scribing tools from the cursor or the hand onto the table. */
    private void insert(ServerPlayer player, LegacyResearchTable tile, CompoundTag data) throws Exception {
        boolean hand = data.getBoolean("hand");
        ItemStack source = hand ? player.getMainHandItem() : player.inventoryMenu.getCarried();
        if (source.isEmpty()) {
            return;
        }
        int slot = data.getInt("slot") == LegacyResearchTable.NOTE ? LegacyResearchTable.NOTE : LegacyResearchTable.TOOLS;
        ItemStack left = tile.insert(slot, source.copy());
        if (left.getCount() == source.getCount()) {
            hint = slot == LegacyResearchTable.NOTE ? "not_a_note" : "not_tools";
            return;
        }
        if (hand) {
            player.setItemInHand(InteractionHand.MAIN_HAND, left);
        } else {
            player.inventoryMenu.setCarried(left);
        }
        hint = "";
        station(player);
    }

    private static void station(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 0.5F, 1);
    }

    private LegacyResearchTable tile(StationContext station) throws ReflectiveOperationException {
        if (!(station.blockEntity() instanceof LegacyBlockEntity host) || host == failedTile) {
            return null;
        }
        if (table == null || !table.isAt(host)) {
            table = new LegacyResearchTable(host);
            hint = "";
        }
        return table;
    }

    private void publish(StationContext station) {
        try {
            LegacyResearchTable tile = tile(station);
            CompoundTag data = tile == null ? new CompoundTag() : tile.read(user);
            String current = hint;
            if (tile == null) {
                current = "unavailable";
            } else if (current.isEmpty()) {
                current = !data.getBoolean("tools") ? "need_tools" : !data.getBoolean("note") ? "need_note"
                        : data.getBoolean("complete") ? "complete" : data.getFloat("ink") <= 0 ? "no_ink" : "";
            }
            data.putString("hint", current);
            if (!data.equals(station.state().data())) {
                station.state().setData(data);
            }
        } catch (Exception failure) {
            fail(station, failure);
        }
    }

    private void fail(StationContext station, Exception failure) {
        if (station.blockEntity() instanceof LegacyBlockEntity host && failedTile != host) {
            failedTile = host;
            LogUtils.getLogger().warn("Could not use the legacy research table at {}", station.pos(), failure);
        }
        CompoundTag data = new CompoundTag();
        data.putString("hint", "unavailable");
        station.state().setData(data);
    }
}
