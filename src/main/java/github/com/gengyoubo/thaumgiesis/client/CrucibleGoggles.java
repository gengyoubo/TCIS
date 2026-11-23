package github.com.gengyoubo.thaumgiesis.client;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.LegacyObjects;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

final class CrucibleGoggles {
    private ItemStack checked = ItemStack.EMPTY;
    private boolean revealed;

    boolean reveals(LocalPlayer player) {
        ItemStack worn = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!ItemStack.matches(worn, checked)) {
            checked = worn.copy();
            revealed = hasGoggles(worn);
        }
        return revealed;
    }

    private static boolean hasGoggles(ItemStack worn) {
        if (worn.isEmpty()) {
            return false;
        }
        var key = ForgeRegistries.ITEMS.getKey(worn.getItem());
        if (ModernContentBridge.id("Thaumcraft:ItemGoggles").equals(key)) {
            return true;
        }
        // Respect Thaumcraft's goggles interface, including goggles installed on other helmets.
        try {
            var bridge = ModernContentBridge.active();
            var services = bridge.services();
            return services.items().inLegacy(() -> {
                var stack = bridge.containerStack(worn);
                if (stack == null) {
                    return false;
                }
                Object original = services.items().materialize(stack);
                Object item = LegacyObjects.item(original);
                var loader = services.loader();
                var goggles = loader.loadClass("thaumcraft.api.IGoggles");
                if (!goggles.isInstance(item)) {
                    return false;
                }
                services.containers().preparePlayer();
                var minecraft = loader.loadClass("net.minecraft.client.Minecraft");
                Object client = minecraft.getMethod("func_71410_x").invoke(null);
                Object wearer = minecraft.getField("field_71439_g").get(client);
                return wearer != null && (boolean) goggles.getMethod("showIngamePopups",
                        loader.loadClass("net.minecraft.item.ItemStack"),
                        loader.loadClass("net.minecraft.entity.EntityLivingBase")).invoke(item, original, wearer);
            });
        } catch (Exception | LinkageError failure) {
            LogUtils.getLogger().debug("Could not check the equipped Thaumcraft goggles", failure);
            return false;
        }
    }
}
