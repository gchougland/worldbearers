package com.hexvane.worldbearers.systems;

import com.hexvane.worldbearers.giant.BodyPartDefinition;
import com.hexvane.worldbearers.giant.GiantInstance;
import com.hexvane.worldbearers.giant.GiantManager;
import com.hexvane.worldbearers.giant.GiantManager.PlatformBounds;
import com.hypixel.hytale.builtin.mounts.MountedByComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Runs after our carry mount system. Intended to run late in the tick so that after
 * HandleMountInput applies the client's movement (including gravity) to the rider and
 * body part, we (1) reset body part positions to the correct place from the giant, and
 * (2) re-sync rider position = part + offset. We cannot depend on ProcessPlayerInput
 * (not registered when our plugin loads), so we depend on BodyPartMountInputSystem and
 * rely on registration order to run after engine input systems when possible.
 */
public class PlatformCarryResyncSystem extends TickingSystem<EntityStore> {

    private final GiantManager giantManager;

    public PlatformCarryResyncSystem(GiantManager giantManager) {
        this.giantManager = giantManager;
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, BodyPartMountInputSystem.class));
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        List<GiantInstance> toRemove = null;
        for (GiantInstance instance : giantManager.getActiveGiants()) {
            if (!instance.isActive()) continue;
            if (!instance.getMainEntityRef().isValid()) {
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(instance);
                continue;
            }
            TransformComponent mainTransform = store.getComponent(
                    instance.getMainEntityRef(), TransformComponent.getComponentType());
            if (mainTransform == null) continue;

            Vector3d giantPos = mainTransform.getPosition();
            Vector3f giantRot = mainTransform.getRotation();
            float headingRad = (float) Math.toRadians(giantRot.y);

            // 1) Reset body part positions (undo HandleMountInput having moved them)
            for (BodyPartDefinition part : instance.getDefinition().getCollidableBodyParts()) {
                Ref<EntityStore> partRef = instance.getBodyPartEntity(part.getName());
                if (partRef == null || !partRef.isValid()) continue;
                TransformComponent partTransform = store.getComponent(partRef, TransformComponent.getComponentType());
                if (partTransform == null) continue;
                Vector3d correctPos = part.computeWorldPosition(giantPos, headingRad);
                partTransform.getPosition().assign(correctPos);
            }

            // 2) Re-sync riders on our body parts: position = part + offset; clamp Y so feet on platform top
            for (Ref<EntityStore> partRef : instance.getBodyPartEntities().values()) {
                if (partRef == null || !partRef.isValid()) continue;
                MountedByComponent mountedBy = store.getComponent(partRef, MountedByComponent.getComponentType());
                if (mountedBy == null) continue;
                TransformComponent partTransform = store.getComponent(partRef, TransformComponent.getComponentType());
                BoundingBox partBbox = store.getComponent(partRef, BoundingBox.getComponentType());
                if (partTransform == null) continue;
                Vector3d partPos = partTransform.getPosition();
                double partTop = partBbox != null ? partPos.y + partBbox.getBoundingBox().max.y : partPos.y;
                double minRiderY = giantManager.computeMinRiderYForPlatform(partTop);
                for (Ref<EntityStore> riderRef : new ArrayList<>(mountedBy.getPassengers())) {
                    if (!riderRef.isValid()) continue;
                    MountedComponent mounted = store.getComponent(riderRef, MountedComponent.getComponentType());
                    if (mounted == null || !partRef.equals(mounted.getMountedToEntity())) continue;
                    TransformComponent riderTransform = store.getComponent(riderRef, TransformComponent.getComponentType());
                    if (riderTransform == null) continue;
                    Vector3f offset = mounted.getAttachmentOffset();
                    double nudgeY = giantManager.isDebugAutoAdjust() ? giantManager.getSyncYNudge() : 0.0;
                    double riderY = Math.max(partPos.y + offset.y + nudgeY, minRiderY);
                    riderTransform.getPosition().assign(
                            partPos.x + offset.x,
                            riderY,
                            partPos.z + offset.z
                    );
                }
            }
        }
        if (toRemove != null) {
            for (GiantInstance instance : toRemove) {
                giantManager.removeGiant(instance, store);
            }
        }
        // Standalone mount: if has riders, correct mount position (clamp XZ, set Y); else restore from stored. Platform entity is never moved.
        Ref<EntityStore> standaloneMountRef = giantManager.getStandaloneMountRef();
        if (standaloneMountRef != null && standaloneMountRef.isValid()) {
            TransformComponent partTransform = store.getComponent(standaloneMountRef, TransformComponent.getComponentType());
            if (partTransform != null) {
                MountedByComponent mountedBy = store.getComponent(standaloneMountRef, MountedByComponent.getComponentType());
                boolean hasRiders = mountedBy != null && !mountedBy.getPassengers().isEmpty();
                if (hasRiders) {
                    // Correct mount position after HandleMountInput (clamp XZ, set Y to platform plane)
                    PlatformBounds bounds = giantManager.getStandalonePlatformBounds();
                    BoundingBox partBbox = store.getComponent(standaloneMountRef, BoundingBox.getComponentType());
                    if (bounds != null && partBbox != null) {
                        Vector3d partPos = partTransform.getPosition();
                        double clampX = Math.max(bounds.minX(), Math.min(bounds.maxX(), partPos.x));
                        double clampZ = Math.max(bounds.minZ(), Math.min(bounds.maxZ(), partPos.z));
                        double partY = bounds.topY() - partBbox.getBoundingBox().max.y;
                        partTransform.getPosition().assign(clampX, partY, clampZ);
                    }
                } else {
                    // No riders: restore mount position from stored
                    Vector3d storedPos = giantManager.getStandaloneMountLastPosition();
                    if (storedPos != null) {
                        partTransform.getPosition().assign(storedPos.x, storedPos.y, storedPos.z);
                    }
                }
                // Re-sync riders: position = mount + offset; clamp Y so feet on platform top
                Vector3d partPos = partTransform.getPosition();
                BoundingBox partBbox = store.getComponent(standaloneMountRef, BoundingBox.getComponentType());
                double partTop = partBbox != null ? partPos.y + partBbox.getBoundingBox().max.y : partPos.y;
                double minRiderY = giantManager.computeMinRiderYForPlatform(partTop);
                if (mountedBy != null) {
                    for (Ref<EntityStore> riderRef : new ArrayList<>(mountedBy.getPassengers())) {
                        if (!riderRef.isValid()) continue;
                        MountedComponent mounted = store.getComponent(riderRef, MountedComponent.getComponentType());
                        if (mounted == null || !standaloneMountRef.equals(mounted.getMountedToEntity())) continue;
                        TransformComponent riderTransform = store.getComponent(riderRef, TransformComponent.getComponentType());
                        if (riderTransform == null) continue;
                        Vector3f offset = mounted.getAttachmentOffset();
                        double nudgeY = giantManager.isDebugAutoAdjust() ? giantManager.getSyncYNudge() : 0.0;
                        double riderY = Math.max(partPos.y + offset.y + nudgeY, minRiderY);
                        riderTransform.getPosition().assign(
                                partPos.x + offset.x,
                                riderY,
                                partPos.z + offset.z
                        );
                    }
                }
            }
        }
    }
}
