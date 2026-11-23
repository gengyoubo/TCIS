package github.com.gengyoubo.thaumgiesis.research;

import github.com.gengyoubo.mfcl.content.LegacyBlockEntity;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Callable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Reads and works the original research table tile, so that ink, the player's aspect pool, the note's hexes and its completion all
 * stay Thaumcraft's own: writing an aspect calls the tile's {@code placeAspect}, as the table's screen does; combining two aspects
 * does what the screen's combination packet does.
 */
final class LegacyResearchTable {
    static final int TOOLS = 0;
    static final int NOTE = 1;
    private final LegacyBlockEntity host;
    private final ModernContentBridge bridge;
    private final Object tile;
    private final Field contents;
    private final Field bonus;
    private final Method placeAspect;
    private final Method gatherResults;
    private final Method setSlot;
    private final Method validFor;
    private final Method markDirty;
    private final Method aspectByTag;
    private final Method aspectTag;
    private final Method listAspects;
    private final Method listAmount;
    private final Method listRemove;
    private final Method noteData;
    private final Method combination;
    private final Method damage;
    private final Method maxDamage;
    private final Method discovered;
    private final Method poolFor;
    private final Method addPool;
    private final Method syncKnowledge;
    private final Method scheduleSave;
    private final Method sendTo;
    private final Object network;
    private final Object knowledge;
    private final java.lang.reflect.Constructor<?> poolPacket;
    private final Method copyStack;

    LegacyResearchTable(LegacyBlockEntity host) throws ReflectiveOperationException {
        this.host = host;
        bridge = ModernContentBridge.active();
        ClassLoader loader = bridge.services().loader();
        tile = host.legacy();
        Class<?> type = tile.getClass();
        Class<?> aspect = loader.loadClass("thaumcraft.api.aspects.Aspect");
        Class<?> aspectList = loader.loadClass("thaumcraft.api.aspects.AspectList");
        Class<?> item = loader.loadClass("net.minecraft.item.ItemStack");
        Class<?> player = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        Class<?> manager = loader.loadClass("thaumcraft.common.lib.research.ResearchManager");
        contents = type.getField("contents");
        bonus = type.getField("bonusAspects");
        placeAspect = type.getMethod("placeAspect", int.class, int.class, aspect, player);
        gatherResults = type.getMethod("gatherResults");
        setSlot = type.getMethod("func_70299_a", int.class, item);
        validFor = type.getMethod("func_94041_b", int.class, item);
        markDirty = type.getMethod("func_70296_d");
        aspectByTag = aspect.getMethod("getAspect", String.class);
        aspectTag = aspect.getMethod("getTag");
        listAspects = aspectList.getMethod("getAspects");
        listAmount = aspectList.getMethod("getAmount", aspect);
        listRemove = aspectList.getMethod("remove", aspect, int.class);
        noteData = manager.getMethod("getData", item);
        combination = manager.getMethod("getCombinationResult", aspect, aspect);
        scheduleSave = manager.getMethod("scheduleSave", player);
        damage = item.getMethod("func_77960_j");
        maxDamage = item.getMethod("func_77958_k");
        copyStack = item.getMethod("func_77946_l");
        Object proxy = loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null);
        knowledge = proxy.getClass().getField("playerKnowledge").get(proxy);
        Class<?> knowledgeType = knowledge.getClass();
        discovered = knowledgeType.getMethod("getAspectsDiscovered", String.class);
        poolFor = knowledgeType.getMethod("getAspectPoolFor", String.class, aspect);
        addPool = knowledgeType.getMethod("addAspectPool", String.class, aspect, short.class);
        syncKnowledge = loader.loadClass("thaumcraft.common.lib.research.ScanManager")
                .getMethod("checkAndSyncAspectKnowledge", player, aspect, int.class);
        network = loader.loadClass("thaumcraft.common.lib.network.PacketHandler").getField("INSTANCE").get(null);
        Class<?> message = loader.loadClass("cpw.mods.fml.common.network.simpleimpl.IMessage");
        sendTo = network.getClass().getMethod("sendTo", message, loader.loadClass("net.minecraft.entity.player.EntityPlayerMP"));
        poolPacket = loader.loadClass("thaumcraft.common.lib.network.playerdata.PacketAspectPool")
                .getConstructor(String.class, Short.class, Short.class);
    }

    boolean isAt(LegacyBlockEntity other) {
        return host == other && !host.isRemoved() && host.legacy() == tile;
    }

    /** What the table and the player at it hold, for the scene: the tools and their ink, the note and its hexes, the aspects. */
    CompoundTag read(String playerName) throws Exception {
        return bridge.services().items().inLegacy(() -> {
            CompoundTag data = new CompoundTag();
            Object tools = slot(TOOLS);
            data.putBoolean("tools", tools != null);
            if (tools != null) {
                int most = Math.max(1, (int) maxDamage.invoke(tools));
                data.putFloat("ink", 1 - (int) damage.invoke(tools) / (float) most);
            }
            Object note = slot(NOTE);
            data.putBoolean("note", note != null);
            if (note != null) {
                data.putBoolean("complete", (int) damage.invoke(note) >= 64);
                Object research = noteData.invoke(null, note);
                if (research != null) {
                    readNote(research, data);
                }
            }
            data.put("pool", aspects(discovered.invoke(knowledge, playerName)));
            data.put("bonus", aspects(bonus.get(tile)));
            return data;
        });
    }

    private void readNote(Object research, CompoundTag data) throws Exception {
        Class<?> type = research.getClass();
        data.putString("key", String.valueOf(type.getField("key").get(research)));
        data.putInt("color", type.getField("color").getInt(research));
        data.putBoolean("complete", data.getBoolean("complete") || type.getField("complete").getBoolean(research));
        Map<?, ?> hexes = (Map<?, ?>) type.getField("hexes").get(research);
        Map<?, ?> entries = (Map<?, ?>) type.getField("hexEntries").get(research);
        ListTag list = new ListTag();
        for (Map.Entry<?, ?> hex : hexes.entrySet()) {
            Object value = hex.getValue();
            CompoundTag cell = new CompoundTag();
            cell.putInt("q", value.getClass().getField("q").getInt(value));
            cell.putInt("r", value.getClass().getField("r").getInt(value));
            Object entry = entries.get(hex.getKey());
            if (entry != null) {
                cell.putInt("t", entry.getClass().getField("type").getInt(entry));
                Object aspect = entry.getClass().getField("aspect").get(entry);
                if (aspect != null) {
                    cell.putString("a", (String) aspectTag.invoke(aspect));
                }
            }
            list.add(cell);
        }
        data.put("hexes", list);
    }

    private CompoundTag aspects(Object list) throws Exception {
        CompoundTag tags = new CompoundTag();
        if (list == null) {
            return tags;
        }
        Object entries = listAspects.invoke(list);
        for (int i = 0; i < Array.getLength(entries); i++) {
            Object aspect = Array.get(entries, i);
            if (aspect != null) {
                tags.putInt((String) aspectTag.invoke(aspect), (int) listAmount.invoke(list, aspect));
            }
        }
        return tags;
    }

    /** Writes {@code tag} on hex {@code q, r} of the note, or rubs it out with a null tag, as the table's screen does. */
    void place(ServerPlayer player, int q, int r, String tag) throws Exception {
        act(player, actor -> {
            Object aspect = tag == null ? null : aspectByTag.invoke(null, tag);
            if (tag != null && aspect == null) {
                return false;
            }
            placeAspect.invoke(tile, q, r, aspect, actor);
            return true;
        });
    }

    /**
     * Combines two aspects into their compound, as the table's screen does: each is taken from the player's pool, or from the
     * table's bonus aspects when the pool has none, and the compound is added to the pool.
     */
    boolean combine(ServerPlayer player, String first, String second) throws Exception {
        String name = player.getGameProfile().getName();
        return act(player, actor -> {
            Object a = aspectByTag.invoke(null, first);
            Object b = aspectByTag.invoke(null, second);
            if (a == null || b == null || a == b) {
                return false;
            }
            Object result = combination.invoke(null, a, b);
            if (result == null || !available(name, a) || !available(name, b)) {
                return false;
            }
            take(actor, name, a);
            take(actor, name, b);
            syncKnowledge.invoke(null, actor, result, 1);
            scheduleSave.invoke(null, actor);
            return true;
        });
    }

    private boolean available(String name, Object aspect) throws Exception {
        return (short) poolFor.invoke(knowledge, name, aspect) > 0 || (int) listAmount.invoke(bonus.get(tile), aspect) > 0;
    }

    private void take(Object actor, String name, Object aspect) throws Exception {
        if ((short) poolFor.invoke(knowledge, name, aspect) > 0) {
            addPool.invoke(knowledge, name, aspect, (short) -1);
            short left = (short) poolFor.invoke(knowledge, name, aspect);
            sendTo.invoke(network, poolPacket.newInstance(aspectTag.invoke(aspect), (short) 0, left), actor);
        } else {
            listRemove.invoke(bonus.get(tile), aspect, 1);
            markDirty.invoke(tile);
        }
    }

    /** Puts {@code stack} in the tools' or the note's place when it is empty and fits; returns what is left of the stack. */
    ItemStack insert(int slot, ItemStack stack) throws Exception {
        return change(() -> {
            if (slot(slot) != null) {
                return stack;
            }
            Object legacy = originalItem(stack.copyWithCount(1));
            if (legacy == null || !(boolean) validFor.invoke(tile, slot, legacy)) {
                return stack;
            }
            setSlot.invoke(tile, slot, legacy);
            gatherResults.invoke(tile);
            return stack.copyWithCount(stack.getCount() - 1);
        });
    }

    /** Takes the tools or the note off the table. */
    ItemStack take(int slot) throws Exception {
        return change(() -> {
            Object legacy = slot(slot);
            if (legacy == null) {
                return ItemStack.EMPTY;
            }
            ItemStack result = bridge.gameplayStack(bridge.services().items().snapshot(copyStack.invoke(legacy)));
            setSlot.invoke(tile, slot, null);
            gatherResults.invoke(tile);
            return result;
        });
    }

    private Object slot(int index) throws Exception {
        return Array.get(contents.get(tile), index);
    }

    private Object originalItem(ItemStack stack) throws Exception {
        var legacy = bridge.containerStack(stack);
        return legacy == null ? null : bridge.services().items().materialize(legacy);
    }

    private interface Act {
        boolean run(Object actor) throws Exception;
    }

    private boolean act(ServerPlayer player, Act action) throws Exception {
        return bridge.logicTransaction(host.getLevel(), host.getBlockPos(), () ->
                bridge.withApiPlayer(player, actor -> {
                    try {
                        boolean changed = action.run(actor);
                        if (changed) {
                            host.markLegacyDirty();
                        }
                        return changed;
                    } catch (Exception failure) {
                        throw new IllegalStateException("Cannot work the research table", failure);
                    }
                }), true);
    }

    private ItemStack change(Callable<ItemStack> action) throws Exception {
        return bridge.logicTransaction(host.getLevel(), host.getBlockPos(), () -> bridge.services().items().inLegacy(() -> {
            ItemStack result = action.call();
            markDirty.invoke(tile);
            host.markLegacyDirty();
            return result;
        }), true);
    }
}
