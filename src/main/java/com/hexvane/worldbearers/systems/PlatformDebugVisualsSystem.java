package com.hexvane.worldbearers.systems;

import com.hexvane.worldbearers.giant.GiantManager;
import com.hexvane.worldbearers.platform.CollisionMath;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Debug visuals: feet AABB (cyan) and rider-part link lines (yellow) when
 * corresponding flags are set. Runs after PlatformDetectionSystem so riderToPart is current.
 */
public class PlatformDebugVisualsSystem extends TickingSystem<EntityStore> {

    private static final float LINE_THICKNESS = 0.04f;
    private static final float LINE_TIME = 0.15f;
    private static final Vector3f FEET_COLOR = DebugUtils.COLOR_CYAN;
    /** When on platform: marker at actual TransformComponent position (to see if it drifts from synced). */
    private static final Vector3f ACTUAL_POS_COLOR = DebugUtils.COLOR_RED;
    private static final double ACTUAL_POS_MARKER_SCALE = 0.2;
    private static final Vector3f RIDER_LINE_COLOR = DebugUtils.COLOR_YELLOW;
    private static final double RIDER_MARKER_SCALE = 0.15;

    private final GiantManager giantManager;

    public PlatformDebugVisualsSystem(GiantManager giantManager) {
        this.giantManager = giantManager;
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, PlatformDetectionSystem.class));
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        if (world == null) return;

        if (giantManager.isDebugFeet()) {
            drawFeetBoxes(store, world);
        }
        if (giantManager.isDebugRiders()) {
            drawRiderPartLines(store, world);
        }
    }

    private void drawFeetBoxes(Store<EntityStore> store, World world) {
        Collection<PlayerRef> playerRefs = world.getPlayerRefs();
        Map<Ref<EntityStore>, Ref<EntityStore>> riderToPart = giantManager.getRiderToPartMap();
        double hw = giantManager.getFeetHalfWidth();
        double hh = giantManager.getFeetHalfHeight();
        double hd = giantManager.getFeetHalfDepth();
        double yOff = giantManager.getFeetYOffsetBelowCenter();

        for (PlayerRef playerRef : playerRefs) {
            Ref<EntityStore> riderRef = playerRef.getReference();
            if (riderRef == null || !riderRef.isValid()) continue;
            TransformComponent t = store.getComponent(riderRef, TransformComponent.getComponentType());
            if (t == null) continue;

            Vector3d positionForFeet;
            Ref<EntityStore> partRef = riderToPart.get(riderRef);
            if (partRef != null && partRef.isValid()) {
                // On our platform: draw feet at the same position we sync to (part + offset, Y clamped)
                positionForFeet = computeSyncedRiderPosition(store, riderRef, partRef);
                if (positionForFeet == null) positionForFeet = t.getPosition();
                // Red marker = actual TransformComponent position (shows drift from synced if any)
                Vector3d actual = t.getPosition();
                DebugUtils.addSphere(world, actual.x, actual.y, actual.z, ACTUAL_POS_COLOR, ACTUAL_POS_MARKER_SCALE, LINE_TIME);
            } else {
                positionForFeet = t.getPosition();
            }
            Box feet = CollisionMath.feetAABB(positionForFeet, hw, hh, hd, yOff);
            drawBoxEdges(world, feet.min.x, feet.min.y, feet.min.z, feet.max.x, feet.max.y, feet.max.z, FEET_COLOR);
        }
    }

    /** Same math as PlatformCarryMountSystem/Resync: part + offset with Y clamp. Returns null if missing components. */
    private Vector3d computeSyncedRiderPosition(Store<EntityStore> store, Ref<EntityStore> riderRef, Ref<EntityStore> partRef) {
        TransformComponent partT = store.getComponent(partRef, TransformComponent.getComponentType());
        MountedComponent mounted = store.getComponent(riderRef, MountedComponent.getComponentType());
        if (partT == null || mounted == null || !partRef.equals(mounted.getMountedToEntity())) return null;
        Vector3d partPos = partT.getPosition();
        Vector3f offset = mounted.getAttachmentOffset();
        double nudgeY = giantManager.isDebugAutoAdjust() ? giantManager.getSyncYNudge() : 0.0;
        double riderY = partPos.y + offset.y + nudgeY;
        BoundingBox partBbox = store.getComponent(partRef, BoundingBox.getComponentType());
        if (partBbox != null) {
            double partTop = partPos.y + partBbox.getBoundingBox().max.y;
            double minRiderY = giantManager.computeMinRiderYForPlatform(partTop);
            riderY = Math.max(riderY, minRiderY);
        }
        return new Vector3d(partPos.x + offset.x, riderY, partPos.z + offset.z);
    }

    private void drawRiderPartLines(Store<EntityStore> store, World world) {
        Map<Ref<EntityStore>, Ref<EntityStore>> riderToPart = giantManager.getRiderToPartMap();
        for (Map.Entry<Ref<EntityStore>, Ref<EntityStore>> e : riderToPart.entrySet()) {
            Ref<EntityStore> riderRef = e.getKey();
            Ref<EntityStore> partRef = e.getValue();
            if (!riderRef.isValid() || !partRef.isValid()) continue;
            TransformComponent riderT = store.getComponent(riderRef, TransformComponent.getComponentType());
            TransformComponent partT = store.getComponent(partRef, TransformComponent.getComponentType());
            if (riderT == null || partT == null) continue;
            Vector3d riderPos = riderT.getPosition();
            Vector3d partPos = partT.getPosition();
            DebugUtils.addLine(world, riderPos.x, riderPos.y, riderPos.z,
                    partPos.x, partPos.y, partPos.z,
                    RIDER_LINE_COLOR, LINE_THICKNESS, LINE_TIME, true);
            DebugUtils.addSphere(world, partPos.x, partPos.y, partPos.z, RIDER_LINE_COLOR, RIDER_MARKER_SCALE, LINE_TIME);
        }
    }

    private static void drawBoxEdges(World world, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Vector3f color) {
        line(world, minX, minY, minZ, maxX, minY, minZ, color);
        line(world, maxX, minY, minZ, maxX, minY, maxZ, color);
        line(world, maxX, minY, maxZ, minX, minY, maxZ, color);
        line(world, minX, minY, maxZ, minX, minY, minZ, color);
        line(world, minX, maxY, minZ, maxX, maxY, minZ, color);
        line(world, maxX, maxY, minZ, maxX, maxY, maxZ, color);
        line(world, maxX, maxY, maxZ, minX, maxY, maxZ, color);
        line(world, minX, maxY, maxZ, minX, maxY, minZ, color);
        line(world, minX, minY, minZ, minX, maxY, minZ, color);
        line(world, maxX, minY, minZ, maxX, maxY, minZ, color);
        line(world, maxX, minY, maxZ, maxX, maxY, maxZ, color);
        line(world, minX, minY, maxZ, minX, maxY, maxZ, color);
    }

    private static void line(World world, double x1, double y1, double z1, double x2, double y2, double z2, Vector3f color) {
        DebugUtils.addLine(world, x1, y1, z1, x2, y2, z2, color, LINE_THICKNESS, LINE_TIME, true);
    }
}
