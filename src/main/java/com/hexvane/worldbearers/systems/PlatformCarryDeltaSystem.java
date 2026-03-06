package com.hexvane.worldbearers.systems;

import com.hexvane.worldbearers.giant.GiantManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Option 1 fallback: Platform carry by delta application (no mount).
 * Stores previous position per body part; each tick applies (current - previous)
 * to all riders on that part. Use instead of Option 2 (mount) if the mount path
 * is too invasive.
 */
public class PlatformCarryDeltaSystem extends TickingSystem<EntityStore> {

    private final GiantManager giantManager;

    /** Last tick's position per body part ref (for delta computation). */
    private final Map<Ref<EntityStore>, Vector3d> lastPartPosition = new HashMap<>();

    public PlatformCarryDeltaSystem(GiantManager giantManager) {
        this.giantManager = giantManager;
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, PlatformDetectionSystem.class));
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        Map<Ref<EntityStore>, Ref<EntityStore>> riderToPart = giantManager.getRiderToPartMap();

        // Build current position per part that has riders, and apply deltas
        Map<Ref<EntityStore>, Vector3d> currentPartPosition = new HashMap<>();
        for (Map.Entry<Ref<EntityStore>, Ref<EntityStore>> entry : riderToPart.entrySet()) {
            Ref<EntityStore> riderRef = entry.getKey();
            Ref<EntityStore> partRef = entry.getValue();
            if (!riderRef.isValid() || !partRef.isValid()) continue;

            TransformComponent partTransform = store.getComponent(partRef, TransformComponent.getComponentType());
            TransformComponent riderTransform = store.getComponent(riderRef, TransformComponent.getComponentType());
            if (partTransform == null || riderTransform == null) continue;

            Vector3d partPos = partTransform.getPosition();
            currentPartPosition.put(partRef, new Vector3d(partPos.x, partPos.y, partPos.z));

            Vector3d last = lastPartPosition.get(partRef);
            if (last != null) {
                double dx = partPos.x - last.x;
                double dy = partPos.y - last.y;
                double dz = partPos.z - last.z;
                riderTransform.getPosition().add(dx, dy, dz);
            }
        }

        // Update last positions for next tick
        lastPartPosition.clear();
        for (Map.Entry<Ref<EntityStore>, Vector3d> e : currentPartPosition.entrySet()) {
            if (e.getKey().isValid()) {
                lastPartPosition.put(e.getKey(), e.getValue());
            }
        }
    }
}
