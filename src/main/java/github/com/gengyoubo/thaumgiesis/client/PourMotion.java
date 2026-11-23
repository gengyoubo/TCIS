/*
 * Adapted from Hands-On Delight's KitchenMotions.Pour (https://github.com/Pocky-l/handson-delight).
 *
 * MIT License
 *
 * Copyright (c) 2026 Pocky
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package github.com.gengyoubo.thaumgiesis.client;

import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.scene.Visual;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

/**
 * A bucket of water poured into the crucible as Hands-On Delight's cooks pour: brought from the hand over the crucible, cocked back a
 * little, tipped over, poured in a stream that thins as it runs out, set upright again empty and taken away. It is played on a node
 * that stands at the scene's origin with no turn of its own, so that its moves are scene pixels.
 */
final class PourMotion {
    private static final Vector3f ITEM_MIDDLE = new Vector3f(8.0F, 8.0F, 8.0F);
    /** Upright things lean back by this much, degrees, so that the camera above the station sees their faces and not their edges. */
    private static final float LEAN = 16.0F;
    /** An item visual at scale 1 is drawn this many pixels high: the item's own "fixed" display halves it. */
    private static final float ITEM_DRAWN = 8.0F;
    /** Where a container has its rim, as a part of its size from its middle, on the side it tips to. */
    private static final float LIP_X = -0.3F;
    private static final float LIP_Y = 0.36F;
    private static final float ARRIVE = 0.3F;
    private static final float TIP = 0.2F;
    private static final float FLOW = 0.8F;
    private static final float BACK = 0.2F;
    private static final float AWAY = 0.3F;
    private static final float ARC = 2.0F;
    /** The hand cocks the container back before tipping it, and tips it further as it empties, degrees. */
    private static final float COCK = -12.0F;
    private static final float EMPTYING = 14.0F;
    /** How high over the landing the rim is while it pours, and how far out from the rim a full stream lands, pixels. */
    private static final float OVER = 3.2F;
    private static final float THROW = 1.3F;
    /** It comes from the hand this much smaller: it grows as it comes nearer, it does not jump out at the camera. */
    private static final float FROM_SCALE = 0.5F;
    private static final int WATER = 0xFF3F76E4;

    private final NodeView node;
    private final String key;
    private final ItemStack full;
    private final Vector3f from;
    private final Vector3f middle;
    private final float landY;
    @Nullable
    private final String liquidNode;
    private final float size;
    private final float tilt;
    private final float side;
    private final float lipX;
    private final float yaw;
    private float age;
    private boolean flowing;
    private boolean emptied;

    /**
     * @param node   the node that shows the bucket
     * @param key    tells this stream from others at the station
     * @param full   the full bucket
     * @param from   where the bucket comes from, scene pixels
     * @param onto   where the stream lands, scene pixels (its y is the surface it lands on)
     * @param liquid the node whose liquid it pours into, or null: it only splashes
     * @param size   how high it is drawn, pixels
     * @param side   1 tips it to the right of the screen, -1 to the left
     * @param yaw    how the station's camera is turned: 0 when it looks along +z, 180 along -z
     */
    PourMotion(NodeView node, String key, ItemStack full, Vector3f from, Vector3f onto, @Nullable String liquid, float size,
               float side, float yaw) {
        this.node = node;
        this.key = key;
        this.full = full.copy();
        this.from = new Vector3f(from);
        this.landY = onto.y;
        this.liquidNode = liquid;
        this.size = size;
        this.side = side;
        this.yaw = yaw;
        this.lipX = side * LIP_X;
        // A bucket goes over slowly and runs thick.
        this.tilt = side * 105.0F;
        Vector3f lip = tipped(lipX, LIP_Y, size, tilt, yaw);
        Vector3f out = outward(THROW);
        this.middle = new Vector3f(onto.x - out.x - lip.x, landY + OVER - lip.y, onto.z - out.z - lip.z);
        showItem(full.getItem());
        node.scaleTo(size / ITEM_DRAWN * FROM_SCALE, 0.0F);
        place(this.from, 0.0F);
    }

    /**
     * Holds the bucket so high that its lip is at least at {@code y} as it pours, scene pixels: over the crucible's rim rather than
     * down by the water in it. The stream falls the rest of the way.
     */
    PourMotion lipAtLeast(float y) {
        Vector3f lip = tipped(lipX, LIP_Y, size, tilt, yaw);
        middle.y = Math.max(middle.y, y - lip.y);
        return this;
    }

    /** Advances the pour; false once it is over and its node is hidden. */
    boolean frame(StationView view, float dt) {
        age += dt;
        float t = age;
        if (t < ARRIVE) {
            float k = smooth(t / ARRIVE);
            Vector3f at = new Vector3f(from).lerp(middle, k).add(0.0F, ARC * Mth.sin(k * Mth.PI), 0.0F);
            place(at, side * COCK * k);
            node.scaleTo(size / ITEM_DRAWN * Mth.lerp(k, FROM_SCALE, 1.0F), 0.0F);
            return true;
        }
        t -= ARRIVE;
        if (t < TIP) {
            place(middle, Mth.lerp(smooth(t / TIP), side * COCK, tilt));
            return true;
        }
        t -= TIP;
        if (t < FLOW) {
            if (!flowing) {
                flowing = true;
                view.playSound(SoundEvents.BUCKET_EMPTY, null, 0.45F, 1.0F);
            }
            float k = t / FLOW;
            // A hand is never quite still, and the bucket goes over further as it empties.
            float angle = tilt + side * EMPTYING * k + 2.0F * Mth.sin(age * 11.0F);
            place(middle, angle);
            float strength = smooth(t / 0.08F) * (1.0F - 0.75F * smooth((k - 0.55F) / 0.45F));
            Vector3f lip = tipped(lipX, LIP_Y, size, angle, yaw).add(middle);
            // It leaves the rim with some speed and lands out from it, nearer as it runs thin.
            Vector3f out = outward(THROW * (0.4F + 0.6F * strength));
            view.pourStream(key, lip, new Vector3f(lip.x + out.x, landY, lip.z + out.z), WATER, strength, liquidNode);
            return true;
        }
        t -= FLOW;
        if (!emptied) {
            emptied = true;
            Vector3f lip = tipped(lipX, LIP_Y, size, tilt + side * EMPTYING, yaw).add(middle);
            view.spawnParticles(DiegesisParticles.DROPLET, lip, new Vector3f(0.0F, -6.0F, 0.0F), 0.05F, 2, WATER);
            // What goes away is the empty bucket.
            Item container = full.getItem().getCraftingRemainingItem();
            showItem(container == null || container == Items.AIR ? Items.BUCKET : container);
        }
        if (t < BACK) {
            place(new Vector3f(middle).add(0.0F, smooth(t / BACK), 0.0F), Mth.lerp(smooth(t / BACK), tilt + side * EMPTYING, 0.0F));
            return true;
        }
        t -= BACK;
        if (t < AWAY) {
            float k = smooth(t / AWAY);
            Vector3f lifted = new Vector3f(middle).add(0.0F, 1.0F, 0.0F);
            Vector3f gone = new Vector3f(from).add(0.0F, 3.0F, 0.0F);
            place(lifted.lerp(gone, k), 0.0F);
            node.scaleTo(size / ITEM_DRAWN * (1.0F - k), 0.0F);
            return true;
        }
        stop();
        return false;
    }

    /** Stops it where it is and hides it: the station shows something else now. */
    void stop() {
        node.setVisible(false);
    }

    /**
     * Where point {@code (x, y)} of an upright item (parts of its size from the middle) is, tipped by {@code tilt}, leant back and
     * turned to face the camera by {@code yaw}.
     */
    private static Vector3f tipped(float x, float y, float size, float tilt, float yaw) {
        Vector3f point = new Vector3f(x * size, y * size, 0.0F);
        point.rotateZ(tilt * Mth.DEG_TO_RAD);
        point.rotateX(LEAN * Mth.DEG_TO_RAD);
        point.rotateY(yaw * Mth.DEG_TO_RAD);
        return point;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private void showItem(Item item) {
        node.setShape(null, null);
        node.setVisual(Visual.ofItem(item).withAnchor(ITEM_MIDDLE));
        node.setTint(0xFFFFFFFF);
        node.scaleTo(size / ITEM_DRAWN, 0.0F);
        node.setVisible(true);
    }

    /** {@code amount} pixels the way the rim points, level. */
    private Vector3f outward(float amount) {
        return new Vector3f(-side * amount, 0.0F, 0.0F).rotateY(yaw * Mth.DEG_TO_RAD);
    }

    private void place(Vector3f at, float angle) {
        node.moveTo(at.x, at.y, at.z, 0.0F);
        node.rotateTo(LEAN, yaw, angle, 0.0F);
    }
}
