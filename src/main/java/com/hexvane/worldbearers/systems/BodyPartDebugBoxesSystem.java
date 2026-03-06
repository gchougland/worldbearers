package com.hexvane.worldbearers.systems;

import com.hexvane.worldbearers.giant.GiantInstance;
import com.hexvane.worldbearers.giant.GiantManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * When debug body part boxes is enabled, draws each body part's collision AABB
 * as wireframe lines so players can see the platform detection volumes.
 */
public class BodyPartDebugBoxesSystem extends TickingSystem<EntityStore> {

    private static final float DEBUG_LINE_THICKNESS = 0.04f;
    private static final float DEBUG_LINE_TIME = 0.15f;
    private static final Vector3f DEBUG_COLOR = DebugUtils.COLOR_LIME;

    private final GiantManager giantManager;

    public BodyPartDebugBoxesSystem(GiantManager giantManager) {
        this.giantManager = giantManager;
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, GiantBodyPartUpdateSystem.class));
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        if (!giantManager.isDebugBodyPartBoxes()) return;

        World world = store.getExternalData().getWorld();
        if (world == null) return;

        for (GiantInstance instance : giantManager.getActiveGiants()) {
            if (!instance.isActive()) continue;
            for (Ref<EntityStore> partRef : instance.getBodyPartEntities().values()) {
                if (partRef == null || !partRef.isValid()) continue;
                TransformComponent partTransform = store.getComponent(partRef, TransformComponent.getComponentType());
                BoundingBox partBbox = store.getComponent(partRef, BoundingBox.getComponentType());
                if (partTransform == null || partBbox == null) continue;

                Box local = partBbox.getBoundingBox();
                Vector3d pos = partTransform.getPosition();
                double minX = pos.x + local.min.x, minY = pos.y + local.min.y, minZ = pos.z + local.min.z;
                double maxX = pos.x + local.max.x, maxY = pos.y + local.max.y, maxZ = pos.z + local.max.z;

                drawBoxEdges(world, minX, minY, minZ, maxX, maxY, maxZ);
            }
        }
        // Standalone platform
        Ref<EntityStore> standaloneRef = giantManager.getStandalonePlatformRef();
        if (standaloneRef != null && standaloneRef.isValid()) {
            TransformComponent partTransform = store.getComponent(standaloneRef, TransformComponent.getComponentType());
            BoundingBox partBbox = store.getComponent(standaloneRef, BoundingBox.getComponentType());
            if (partTransform != null && partBbox != null) {
                Box local = partBbox.getBoundingBox();
                Vector3d pos = partTransform.getPosition();
                double minX = pos.x + local.min.x, minY = pos.y + local.min.y, minZ = pos.z + local.min.z;
                double maxX = pos.x + local.max.x, maxY = pos.y + local.max.y, maxZ = pos.z + local.max.z;
                drawBoxEdges(world, minX, minY, minZ, maxX, maxY, maxZ);
            }
        }
    }

    private static void drawBoxEdges(World world, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        // Bottom face
        line(world, minX, minY, minZ, maxX, minY, minZ);
        line(world, maxX, minY, minZ, maxX, minY, maxZ);
        line(world, maxX, minY, maxZ, minX, minY, maxZ);
        line(world, minX, minY, maxZ, minX, minY, minZ);
        // Top face
        line(world, minX, maxY, minZ, maxX, maxY, minZ);
        line(world, maxX, maxY, minZ, maxX, maxY, maxZ);
        line(world, maxX, maxY, maxZ, minX, maxY, maxZ);
        line(world, minX, maxY, maxZ, minX, maxY, minZ);
        // Vertical edges
        line(world, minX, minY, minZ, minX, maxY, minZ);
        line(world, maxX, minY, minZ, maxX, maxY, minZ);
        line(world, maxX, minY, maxZ, maxX, maxY, maxZ);
        line(world, minX, minY, maxZ, minX, maxY, maxZ);
    }

    private static void line(World world, double x1, double y1, double z1, double x2, double y2, double z2) {
        DebugUtils.addLine(world, x1, y1, z1, x2, y2, z2, DEBUG_COLOR, DEBUG_LINE_THICKNESS, DEBUG_LINE_TIME, true);
    }
}
