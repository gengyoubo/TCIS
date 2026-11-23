package github.com.gengyoubo.thaumgiesis.crucible;

import github.com.gengyoubo.mfcl.content.LegacyBlockEntity;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Reads and updates the original tile so heating, decay, and world saves use the crucible's actual contents. */
final class LegacyCrucible {
    private final LegacyBlockEntity host;
    private final ModernContentBridge bridge;
    private final ClassLoader loader;
    private final Object tile;
    private final Field heat;
    private final Field aspects;
    private final Field counter;
    private final Object tank;
    private final Method amount;
    private final Method surfaceHeight;
    private final Method addAspects;
    private final Method copyAspects;
    private final Method listAspects;
    private final Method aspectAmount;
    private final Method aspectTag;
    private final Method copyStack;
    private final Method objectTags;
    private final Method bonusTags;
    private final Method findRecipe;
    private final Method recipeOutput;
    private final Method removeMatching;

    LegacyCrucible(LegacyBlockEntity host) throws ReflectiveOperationException {
        this.host = host;
        bridge = ModernContentBridge.active();
        loader = bridge.services().loader();
        tile = host.legacy();
        Class<?> type = tile.getClass();
        heat = type.getField("heat");
        aspects = type.getField("aspects");
        counter = type.getDeclaredField("counter");
        counter.setAccessible(true);
        tank = type.getField("tank").get(tile);
        amount = tank.getClass().getMethod("getFluidAmount");
        surfaceHeight = type.getMethod("getFluidHeight");
        Class<?> aspectListType = aspects.getType();
        Class<?> aspectType = loader.loadClass("thaumcraft.api.aspects.Aspect");
        Class<?> itemType = loader.loadClass("net.minecraft.item.ItemStack");
        Class<?> manager = loader.loadClass("thaumcraft.common.lib.crafting.ThaumcraftCraftingManager");
        Class<?> recipeType = loader.loadClass("thaumcraft.api.crafting.CrucibleRecipe");
        addAspects = aspectListType.getMethod("add", aspectListType);
        copyAspects = aspectListType.getMethod("copy");
        listAspects = aspectListType.getMethod("getAspects");
        aspectAmount = aspectListType.getMethod("getAmount", aspectType);
        aspectTag = aspectType.getMethod("getTag");
        copyStack = itemType.getMethod("func_77946_l");
        objectTags = manager.getMethod("getObjectTags", itemType);
        bonusTags = manager.getMethod("getBonusTags", itemType, aspectListType);
        findRecipe = manager.getMethod("findMatchingCrucibleRecipe", String.class, aspectListType, itemType);
        recipeOutput = recipeType.getMethod("getRecipeOutput");
        removeMatching = recipeType.getMethod("removeMatching", aspectListType);
    }

    boolean isAt(LegacyBlockEntity other) {
        return host == other && !host.isRemoved() && host.legacy() == tile;
    }

    boolean isBoiling() throws ReflectiveOperationException {
        return heat.getShort(tile) > 150 && (int) amount.invoke(tank) > 0;
    }

    float surfaceHeight() throws ReflectiveOperationException {
        return ((Number) surfaceHeight.invoke(tile)).floatValue() * 16;
    }

    CompoundTag readContents() throws ReflectiveOperationException {
        CompoundTag contents = new CompoundTag();
        contents.putInt("heat", heat.getShort(tile));
        contents.putInt("water", (int) amount.invoke(tank));
        CompoundTag dissolved = new CompoundTag();
        Object list = aspects.get(tile);
        Object entries = listAspects.invoke(list);
        for (int i = 0; i < Array.getLength(entries); i++) {
            Object entry = Array.get(entries, i);
            if (entry != null) {
                dissolved.putInt((String) aspectTag.invoke(entry), (int) aspectAmount.invoke(list, entry));
            }
        }
        contents.put("essentia", dissolved);
        return contents;
    }

    boolean fill() throws Exception {
        return change(() -> {
            Class<?> fluid = loader.loadClass("net.minecraftforge.fluids.Fluid");
            Class<?> stack = loader.loadClass("net.minecraftforge.fluids.FluidStack");
            Object water = loader.loadClass("net.minecraftforge.fluids.FluidRegistry").getField("WATER").get(null);
            Object bucket = stack.getConstructor(fluid, int.class).newInstance(water, 1000);
            return (int) tank.getClass().getMethod("fill", stack, boolean.class).invoke(tank, bucket, true) > 0;
        });
    }

    boolean dissolve(ItemStack ingredient) throws Exception {
        return change(() -> {
            if (!isBoiling()) {
                return false;
            }
            Object input = originalItem(ingredient);
            if (input == null) {
                return false;
            }
            Object tags = bonusTags.invoke(null, input, objectTags.invoke(null, input));
            if (tags == null || (int) tags.getClass().getMethod("size").invoke(tags) == 0) {
                return false;
            }
            Object updated = copyAspects.invoke(aspects.get(tile));
            addAspects.invoke(updated, tags);
            aspects.set(tile, updated);
            counter.setLong(tile, -150);
            return true;
        });
    }

    ItemStack craft(ServerPlayer player, ItemStack catalyst) throws Exception {
        return bridge.logicTransaction(host.getLevel(), host.getBlockPos(), () ->
                bridge.withApiPlayer(player, actor -> {
                    try {
                        Object recipe = matchingRecipe(player, catalyst);
                        if (recipe == null) {
                            return ItemStack.EMPTY;
                        }
                        Object output = recipeOutput.invoke(recipe);
                        ItemStack result = hostItem(output);
                        if (result.isEmpty()) {
                            return ItemStack.EMPTY;
                        }
                        Object remaining = removeMatching.invoke(recipe, aspects.get(tile));
                        fireCrafted(actor, copyStack.invoke(output), catalyst);
                        tank.getClass().getMethod("drain", int.class, boolean.class).invoke(tank, 50, true);
                        aspects.set(tile, remaining);
                        counter.setLong(tile, -250);
                        host.markLegacyDirty();
                        return result;
                    } catch (Exception failure) {
                        throw new IllegalStateException("Cannot complete the crucible recipe", failure);
                    }
                }), true);
    }

    private Object matchingRecipe(ServerPlayer player, ItemStack catalyst) throws Exception {
        if (catalyst.isEmpty() || !isBoiling()) {
            return null;
        }
        Object input = originalItem(catalyst);
        return input == null ? null : findRecipe.invoke(null, player.getGameProfile().getName(), aspects.get(tile), input);
    }

    private Object originalItem(ItemStack stack) throws Exception {
        var legacy = bridge.containerStack(stack.copyWithCount(1));
        return legacy == null ? null : bridge.services().items().materialize(legacy);
    }

    private ItemStack hostItem(Object stack) throws Exception {
        return bridge.gameplayStack(bridge.services().items().snapshot(stack));
    }

    private void fireCrafted(Object actor, Object output, ItemStack catalyst) throws Exception {
        Class<?> item = loader.loadClass("net.minecraft.item.ItemStack");
        Object items = Array.newInstance(item, 1);
        Array.set(items, 0, originalItem(catalyst));
        Object input = loader.loadClass("thaumcraft.common.container.InventoryFake")
                .getConstructor(items.getClass()).newInstance(items);
        Class<?> handler = loader.loadClass("cpw.mods.fml.common.FMLCommonHandler");
        handler.getMethod("firePlayerCraftingEvent", loader.loadClass("net.minecraft.entity.player.EntityPlayer"),
                        item, loader.loadClass("net.minecraft.inventory.IInventory"))
                .invoke(handler.getMethod("instance").invoke(null), actor, output, input);
    }

    private boolean change(Callable<Boolean> action) throws Exception {
        return bridge.logicTransaction(host.getLevel(), host.getBlockPos(), () -> bridge.services().items().inLegacy(() -> {
            boolean changed = action.call();
            if (changed) {
                host.markLegacyDirty();
            }
            return changed;
        }), true);
    }
}
