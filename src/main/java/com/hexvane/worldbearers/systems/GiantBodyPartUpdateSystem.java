package com.hexvane.worldbearers.systems;

import com.hexvane.worldbearers.giant.BodyPartDefinition;
import com.hexvane.worldbearers.giant.GiantInstance;
import com.hexvane.worldbearers.giant.GiantManager;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Store-level ticking system that runs once per server tick to update
 * giant body part collision entity positions.
 *
 * Extends TickingSystem directly (not EntityTickingSystem) to avoid the
 * query registration problem: all per-entity component types (NPCEntity,
 * TransformComponent, etc.) are null during plugin setup because their
 * modules haven't initialized yet. TickingSystem has no query requirement,
 * so it registers cleanly and then iterates our tracked giant list directly.
 */
public class GiantBodyPartUpdateSystem extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final GiantManager giantManager;

    public GiantBodyPartUpdateSystem(GiantManager giantManager) {
        this.giantManager = giantManager;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        for (GiantInstance instance : giantManager.getActiveGiants()) {
            if (!instance.isActive()) continue;

            // Read the giant's current transform
            TransformComponent mainTransform = store.getComponent(
                    instance.getMainEntityRef(), TransformComponent.getComponentType());
            if (mainTransform == null) continue;

            Vector3d giantPos = mainTransform.getPosition();
            Vector3f giantRot = mainTransform.getRotation();
            float headingRad = (float) Math.toRadians(giantRot.y);

            // Reposition each body part collision entity
            for (BodyPartDefinition part : instance.getDefinition().getCollidableBodyParts()) {
                var partRef = instance.getBodyPartEntity(part.getName());
                if (partRef == null || !partRef.isValid()) continue;

                TransformComponent partTransform = store.getComponent(
                        partRef, TransformComponent.getComponentType());
                if (partTransform == null) continue;

                Vector3d newPos = part.computeWorldPosition(giantPos, headingRad);
                partTransform.getPosition().assign(newPos);
            }
        }
    }
}
