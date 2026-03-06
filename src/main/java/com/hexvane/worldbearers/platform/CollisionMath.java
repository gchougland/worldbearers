package com.hexvane.worldbearers.platform;

import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;

import javax.annotation.Nonnull;

/**
 * AABB overlap and platform detection helpers.
 * Box in Hytale does not provide intersects(Box); we implement overlap here.
 */
public final class CollisionMath {

    private CollisionMath() {}

    /**
     * Returns true if two axis-aligned boxes overlap (intersect).
     */
    public static boolean boxesOverlap(@Nonnull Box a, @Nonnull Box b) {
        return a.min.x <= b.max.x && a.max.x >= b.min.x
                && a.min.y <= b.max.y && a.max.y >= b.min.y
                && a.min.z <= b.max.z && a.max.z >= b.min.z;
    }

    /**
     * Build a "feet" AABB for standing-on-platform detection: a thin box
     * at the character's feet (slightly below position) with given half-extents.
     */
    @Nonnull
    public static Box feetAABB(@Nonnull Vector3d position, double halfWidth, double halfHeight, double halfDepth) {
        return feetAABB(position, halfWidth, halfHeight, halfDepth, 0.25);
    }

    /**
     * Build a "feet" AABB with explicit Y offset below position (for tunable debug).
     */
    @Nonnull
    public static Box feetAABB(@Nonnull Vector3d position, double halfWidth, double halfHeight, double halfDepth,
                               double feetYOffsetBelowCenter) {
        double feetY = position.y - feetYOffsetBelowCenter;
        return new Box(
                position.x - halfWidth,
                feetY - halfHeight,
                position.z - halfDepth,
                position.x + halfWidth,
                feetY + halfHeight,
                position.z + halfDepth
        );
    }
}
