package com.hexvane.worldbearers.systems;

import com.hexvane.worldbearers.giant.GiantManager;
import com.hexvane.worldbearers.giant.GiantManager.PlatformBounds;
import com.hypixel.hytale.builtin.mounts.MountedByComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Runs after Resync. Pins the standalone mount to the platform plane every tick so
 * the mount never falls. We only depend on our own systems (plugin load order may
 * register core/mount systems after us). EarlySync also pins at tick start; if
 * HandleMountInput runs after us and applies gravity, the next tick's EarlySync
 * will correct it.
 */
public class PlatformMountGravityOverrideSystem extends TickingSystem<EntityStore> {

    private final GiantManager giantManager;

    public PlatformMountGravityOverrideSystem(GiantManager giantManager) {
        this.giantManager = giantManager;
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, PlatformCarryResyncSystem.class));
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> standaloneMountRef = giantManager.getStandaloneMountRef();
        if (standaloneMountRef == null || !standaloneMountRef.isValid()) return;

        MountedByComponent mountedBy = store.getComponent(standaloneMountRef, MountedByComponent.getComponentType());
        if (mountedBy == null || mountedBy.getPassengers().isEmpty()) return;

        PlatformBounds bounds = giantManager.getStandalonePlatformBounds();
        BoundingBox partBbox = store.getComponent(standaloneMountRef, BoundingBox.getComponentType());
        TransformComponent mountTransform = store.getComponent(standaloneMountRef, TransformComponent.getComponentType());
        if (bounds == null || partBbox == null || mountTransform == null) return;

        Vector3d pos = mountTransform.getPosition();
        double clampX = Math.max(bounds.minX(), Math.min(bounds.maxX(), pos.x));
        double clampZ = Math.max(bounds.minZ(), Math.min(bounds.maxZ(), pos.z));
        double partY = bounds.topY() - partBbox.getBoundingBox().max.y;
        mountTransform.getPosition().assign(clampX, partY, clampZ);
    }
}
