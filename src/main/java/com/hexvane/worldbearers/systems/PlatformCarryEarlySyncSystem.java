package com.hexvane.worldbearers.systems;

import com.hexvane.worldbearers.giant.GiantManager;
import com.hexvane.worldbearers.giant.GiantManager.PlatformBounds;
import com.hypixel.hytale.builtin.mounts.MountedByComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Set;

/**
 * Runs at the very start of each tick (RootDependency.firstSet()) to re-apply rider position
 * for anyone already mounted to our platform. Fixes the case where something (e.g. client
 * input or physics) overwrote our synced position at the end of the previous tick — without
 * this, the feet would show correct for one frame then snap back and the rider would fall through.
 * By syncing first, the rider is on the platform before detection runs, so they stay in riderToPart.
 */
public class PlatformCarryEarlySyncSystem extends TickingSystem<EntityStore> {

    private final GiantManager giantManager;
    private volatile ComponentType<EntityStore, MountedComponent> mountedComponentType;

    public PlatformCarryEarlySyncSystem(GiantManager giantManager) {
        this.giantManager = giantManager;
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return RootDependency.firstSet();
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        if (mountedComponentType == null) {
            mountedComponentType = MountedComponent.getComponentType();
        }
        World world = store.getExternalData().getWorld();
        if (world == null) return;

        // At tick start: pin standalone mount to platform plane so it never "falls" into the tick (gravity/physics may have moved it)
        Ref<EntityStore> standaloneMountRef = giantManager.getStandaloneMountRef();
        if (standaloneMountRef != null && standaloneMountRef.isValid()) {
            MountedByComponent mountedBy = store.getComponent(standaloneMountRef, MountedByComponent.getComponentType());
            if (mountedBy != null && !mountedBy.getPassengers().isEmpty()) {
                PlatformBounds bounds = giantManager.getStandalonePlatformBounds();
                BoundingBox partBbox = store.getComponent(standaloneMountRef, BoundingBox.getComponentType());
                TransformComponent mountTransform = store.getComponent(standaloneMountRef, TransformComponent.getComponentType());
                if (bounds != null && partBbox != null && mountTransform != null) {
                    Vector3d pos = mountTransform.getPosition();
                    double clampX = Math.max(bounds.minX(), Math.min(bounds.maxX(), pos.x));
                    double clampZ = Math.max(bounds.minZ(), Math.min(bounds.maxZ(), pos.z));
                    double partY = bounds.topY() - partBbox.getBoundingBox().max.y;
                    mountTransform.getPosition().assign(clampX, partY, clampZ);
                }
            }
        }

        Collection<PlayerRef> playerRefs = world.getPlayerRefs();
        for (PlayerRef playerRef : playerRefs) {
            Ref<EntityStore> riderRef = playerRef.getReference();
            if (riderRef == null || !riderRef.isValid()) continue;

            MountedComponent mounted = store.getComponent(riderRef, mountedComponentType);
            if (mounted == null) continue;

            Ref<EntityStore> partRef = mounted.getMountedToEntity();
            if (partRef == null || !partRef.isValid() || !giantManager.isBodyPartRef(partRef)) continue;

            TransformComponent partTransform = store.getComponent(partRef, TransformComponent.getComponentType());
            TransformComponent riderTransform = store.getComponent(riderRef, TransformComponent.getComponentType());
            if (partTransform == null || riderTransform == null) continue;

            Vector3d partPos = partTransform.getPosition();
            Vector3f offset = mounted.getAttachmentOffset();
            double nudgeY = giantManager.isDebugAutoAdjust() ? giantManager.getSyncYNudge() : 0.0;
            double riderY = partPos.y + offset.y + nudgeY;
            BoundingBox partBbox = store.getComponent(partRef, BoundingBox.getComponentType());
            if (partBbox != null) {
                double partTop = partPos.y + partBbox.getBoundingBox().max.y;
                double minRiderY = giantManager.computeMinRiderYForPlatform(partTop);
                riderY = Math.max(riderY, minRiderY);
            }
            riderTransform.getPosition().assign(
                    partPos.x + offset.x,
                    riderY,
                    partPos.z + offset.z
            );
        }
    }
}
