package github.com.gengyoubo.thaumgiesis.crucible;

import com.mojang.logging.LogUtils;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;
import github.com.gengyoubo.mfcl.content.LegacyBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemHandlerHelper;

final class CrucibleLogic implements StationLogic {
    private LegacyCrucible crucible;
    private LegacyBlockEntity failedTile;
    private String hint = "";

    @Override
    public void onOpen(StationContext station, Player player) {
        hint = "";
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
        boolean dissolve = CrucibleStation.DISSOLVE.equals(action);
        if (!dissolve && !CrucibleStation.USE_ITEM.equals(action) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        boolean hand = data.getBoolean("hand");
        ItemStack cursor = serverPlayer.inventoryMenu.getCarried();
        if (hand && !cursor.isEmpty()) {
            return;
        }
        ItemStack source = (hand ? serverPlayer.getMainHandItem() : cursor).copy();
        if (source.isEmpty()) {
            return;
        }

        try {
            LegacyCrucible tile = tile(station);
            boolean pour = !dissolve && source.is(Items.WATER_BUCKET);
            if (tile == null || !pour && !tile.isBoiling()) {
                publish(station);
                return;
            }
            float surface = tile.surfaceHeight();
            if (pour) {
                if (tile.fill()) {
                    consume(serverPlayer, hand, source, new ItemStack(Items.BUCKET));
                    hint = "filled";
                    toss(station, source, data, true, 13, SoundEvents.BUCKET_EMPTY);
                } else {
                    hint = "full";
                }
            } else if (dissolve) {
                if (tile.dissolve(source)) {
                    consume(serverPlayer, hand, source, ItemStack.EMPTY);
                    hint = "dissolved";
                    toss(station, source, data, false, surface, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP);
                } else {
                    hint = "no_essentia";
                }
            } else {
                ItemStack crafted = tile.craft(serverPlayer, source);
                if (crafted.isEmpty()) {
                    hint = "no_recipe";
                } else {
                    consume(serverPlayer, hand, source, ItemStack.EMPTY);
                    ItemHandlerHelper.giveItemToPlayer(serverPlayer, crafted);
                    hint = "crafted";
                    toss(station, source, data, false, surface, SoundEvents.BREWING_STAND_BREW);
                }
            }
            publish(station);
        } catch (Exception failure) {
            fail(station, failure);
        }
    }

    private static void consume(ServerPlayer player, boolean hand, ItemStack original, ItemStack remainder) {
        ItemStack current = hand ? player.getMainHandItem() : player.inventoryMenu.getCarried();
        if (ItemStack.isSameItemSameTags(current, original) && !current.isEmpty()) {
            ItemStack next = current.copyWithCount(current.getCount() - 1);
            if (next.isEmpty()) {
                next = remainder;
                remainder = ItemStack.EMPTY;
            }
            if (hand) {
                player.setItemInHand(InteractionHand.MAIN_HAND, next);
            } else {
                player.inventoryMenu.setCarried(next);
            }
        }
        if (!remainder.isEmpty()) {
            ItemHandlerHelper.giveItemToPlayer(player, remainder);
        }
    }

    private static void toss(StationContext station, ItemStack source, CompoundTag input,
                             boolean pour, float surface, SoundEvent sound) {
        CompoundTag data = new CompoundTag();
        data.put("item", source.copyWithCount(1).save(new CompoundTag()));
        data.putBoolean("pour", pour);
        data.putFloat("x", boundedOrigin(input, "x", 8));
        data.putFloat("z", boundedOrigin(input, "z", 4));
        data.putFloat("surface", surface);
        station.effect(CrucibleStation.TOSS, "broth", data);
        station.level().playSound(null, station.pos(), sound, SoundSource.BLOCKS, 0.6F, 1);
    }

    private static float boundedOrigin(CompoundTag input, String key, float fallback) {
        float value = input.contains(key) ? input.getFloat(key) : fallback;
        return Float.isFinite(value) ? Mth.clamp(value, 1, 15) : fallback;
    }

    private LegacyCrucible tile(StationContext station) throws ReflectiveOperationException {
        if (!(station.blockEntity() instanceof LegacyBlockEntity host) || host == failedTile) {
            return null;
        }
        if (crucible == null || !crucible.isAt(host)) {
            crucible = new LegacyCrucible(host);
            hint = "";
        }
        return crucible;
    }

    private void publish(StationContext station) {
        try {
            LegacyCrucible tile = tile(station);
            CompoundTag data = tile == null ? new CompoundTag() : tile.readContents();
            String currentHint = hint;
            if (tile == null) {
                currentHint = "unavailable";
            } else if (data.getInt("water") == 0) {
                currentHint = "dry";
            } else if (data.getInt("heat") <= 150 && !"full".equals(hint)) {
                currentHint = "heating";
            }
            data.putString("hint", currentHint);
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
            LogUtils.getLogger().warn("Could not use the legacy crucible at {}", station.pos(), failure);
        }
        CompoundTag data = new CompoundTag();
        data.putString("hint", "unavailable");
        station.state().setData(data);
    }
}
