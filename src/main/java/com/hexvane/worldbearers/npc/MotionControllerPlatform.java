package com.hexvane.worldbearers.npc;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.movement.MotionKind;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerBase;
import com.hypixel.hytale.server.npc.movement.controllers.ProbeMoveData;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.movement.Steering;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import javax.annotation.Nonnull;

/**
 * Motion controller for NPCs that are moved exclusively by external systems (e.g. giant body parts).
 * Applies no gravity and no movement; position/rotation are driven by the engine read in steer()
 * and our systems overwrite the transform each tick, so the entity stays where we put it.
 */
public class MotionControllerPlatform extends MotionControllerBase {

    public static final String TYPE = "Platform";

    public MotionControllerPlatform(@Nonnull BuilderSupport builderSupport, @Nonnull BuilderMotionControllerPlatform builder) {
        super(builderSupport, builder);
        this.setGravity(0.0);
    }

    @Nonnull
    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected double computeMove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Role role,
            Steering steering,
            double interval,
            @Nonnull Vector3d translation,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        this.saveMotionKind();
        this.setMotionKind(MotionKind.STANDING);
        translation.assign(0.0, 0.0, 0.0);
        return interval;
    }

    @Override
    protected double executeMove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Role role,
            double dt,
            @Nonnull Vector3d translation,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        return dt;
    }

    @Override
    public boolean isFastMotionKind(double speed) {
        return false;
    }

    @Override
    public double probeMove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ProbeMoveData probeMoveData,
            @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        return 0.0;
    }

    @Override
    public boolean onGround() {
        return true;
    }

    @Override
    public boolean inAir() {
        return false;
    }

    @Override
    public boolean inWater() {
        return false;
    }

    @Override
    public double getCurrentMaxBodyRotationSpeed() {
        return 0.0;
    }

    @Override
    public double getMaximumSpeed() {
        return 0.0;
    }

    @Override
    public double getCurrentSpeed() {
        return 0.0;
    }

    @Override
    public void constrainRotations(Role role, TransformComponent transform) {
    }

    @Override
    public void spawned() {
    }

    @Override
    public double getHeightOverGround() {
        return 0.0;
    }

    @Override
    public boolean canRestAtPlace() {
        return true;
    }

    @Override
    public MotionController.VerticalRange getDesiredVerticalRange(
            @Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        MotionController.VerticalRange range = new MotionController.VerticalRange();
        range.assign(this.position.y, this.position.y, this.position.y);
        return range;
    }

    @Override
    public float getMaxSinkAngle() {
        return 0.0f;
    }

    @Override
    public float getMaxClimbAngle() {
        return 0.0f;
    }

    @Override
    public boolean estimateVelocity(Steering steering, Vector3d velocityOut) {
        velocityOut.assign(0.0, 0.0, 0.0);
        return true;
    }

    @Override
    public double getCurrentTurnRadius() {
        return 0.0;
    }

    @Override
    public boolean is2D() {
        return false;
    }

    @Override
    public double getDesiredAltitudeWeight() {
        return 0.0;
    }

    @Override
    public double getWanderVerticalMovementRatio() {
        return 0.0;
    }
}
