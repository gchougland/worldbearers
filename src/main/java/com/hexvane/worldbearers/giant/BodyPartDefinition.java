package com.hexvane.worldbearers.giant;

import com.hypixel.hytale.math.vector.Vector3d;

/**
 * Defines a body part region on a giant entity.
 * Each body part has a local-space offset from the giant's origin,
 * a collision box size, and properties that determine how players interact with it.
 */
public class BodyPartDefinition {

    public enum PartType {
        WALKABLE,
        CLIMBABLE,
        WEAK_POINT,
        BODY
    }

    private final String name;
    private final PartType type;
    private final double localOffsetX, localOffsetY, localOffsetZ;
    private final double halfExtentX, halfExtentY, halfExtentZ;
    private final float damageMultiplier;

    public BodyPartDefinition(String name, PartType type,
                               double localOffsetX, double localOffsetY, double localOffsetZ,
                               double halfExtentX, double halfExtentY, double halfExtentZ,
                               float damageMultiplier) {
        this.name = name;
        this.type = type;
        this.localOffsetX = localOffsetX;
        this.localOffsetY = localOffsetY;
        this.localOffsetZ = localOffsetZ;
        this.halfExtentX = halfExtentX;
        this.halfExtentY = halfExtentY;
        this.halfExtentZ = halfExtentZ;
        this.damageMultiplier = damageMultiplier;
    }

    public String getName() { return name; }
    public PartType getType() { return type; }
    public double getHalfExtentX() { return halfExtentX; }
    public double getHalfExtentY() { return halfExtentY; }
    public double getHalfExtentZ() { return halfExtentZ; }
    public float getDamageMultiplier() { return damageMultiplier; }

    /**
     * Computes the world-space position of this body part given the giant's position and heading.
     * Only handles yaw rotation since giants walk upright.
     */
    public Vector3d computeWorldPosition(Vector3d giantPosition, float headingRadians) {
        double cos = Math.cos(headingRadians);
        double sin = Math.sin(headingRadians);

        double worldX = giantPosition.x + (localOffsetX * cos - localOffsetZ * sin);
        double worldY = giantPosition.y + localOffsetY;
        double worldZ = giantPosition.z + (localOffsetX * sin + localOffsetZ * cos);

        return new Vector3d(worldX, worldY, worldZ);
    }
}
