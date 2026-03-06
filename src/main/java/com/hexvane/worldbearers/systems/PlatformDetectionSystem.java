package com.hexvane.worldbearers.systems;

import com.hexvane.worldbearers.giant.GiantInstance;
import com.hexvane.worldbearers.giant.GiantManager;
import com.hexvane.worldbearers.platform.CollisionMath;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Option B: Custom AABB "on platform" detection. Runs after body part positions
 * are updated; computes which players (riders) are standing on which body parts
 * via feet AABB vs body part world AABB overlap. Writes rider -> body part map
 * to GiantManager for platform carry systems.
 */
public class PlatformDetectionSystem extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int LOG_THROTTLE_TICKS = 20;

    private static final int HEIGHT_LOG_THROTTLE_TICKS = 10;

    private final GiantManager giantManager;
    private int tickCounter;
    private final Map<Integer, String> lastReportedRiderIdToName = new HashMap<>();
    /** Last tick's rider -> part (for "why left" diagnostic). */
    private Map<Ref<EntityStore>, Ref<EntityStore>> lastTickRiderToPart = new HashMap<>();

    public PlatformDetectionSystem(GiantManager giantManager) {
        this.giantManager = giantManager;
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, GiantBodyPartUpdateSystem.class));
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        Map<Ref<EntityStore>, Ref<EntityStore>> riderToPart = new HashMap<>();

        World world = store.getExternalData().getWorld();
        if (world == null) return;

        Collection<PlayerRef> playerRefs = world.getPlayerRefs();
        if (playerRefs.isEmpty()) {
            giantManager.setRiderToPartMap(riderToPart);
            return;
        }

        Map<Ref<EntityStore>, String> riderToName = new HashMap<>();
        for (PlayerRef playerRef : playerRefs) {
            Ref<EntityStore> riderRef = playerRef.getReference();
            if (riderRef == null || !riderRef.isValid()) continue;
            riderToName.put(riderRef, playerRef.getUsername());
        }

        for (PlayerRef playerRef : playerRefs) {
            Ref<EntityStore> riderRef = playerRef.getReference();
            if (riderRef == null || !riderRef.isValid()) continue;

            TransformComponent riderTransform = store.getComponent(riderRef, TransformComponent.getComponentType());
            if (riderTransform == null) continue;

            Vector3d riderPos = riderTransform.getPosition();
            Box feetBox = CollisionMath.feetAABB(riderPos,
                    giantManager.getFeetHalfWidth(), giantManager.getFeetHalfHeight(), giantManager.getFeetHalfDepth(),
                    giantManager.getFeetYOffsetBelowCenter());

            for (GiantInstance instance : giantManager.getActiveGiants()) {
                if (!instance.isActive()) continue;

                for (Map.Entry<String, Ref<EntityStore>> entry : instance.getBodyPartEntities().entrySet()) {
                    Ref<EntityStore> partRef = entry.getValue();
                    if (partRef == null || !partRef.isValid()) continue;

                    TransformComponent partTransform = store.getComponent(partRef, TransformComponent.getComponentType());
                    BoundingBox partBbox = store.getComponent(partRef, BoundingBox.getComponentType());
                    if (partTransform == null || partBbox == null) continue;

                    Box partBoxLocal = partBbox.getBoundingBox();
                    Box partBoxWorld = new Box(partBoxLocal);
                    partBoxWorld.offset(partTransform.getPosition());

                    if (CollisionMath.boxesOverlap(feetBox, partBoxWorld)) {
                        riderToPart.put(riderRef, partRef);
                        break; // one part per rider
                    }
                }
                if (riderToPart.containsKey(riderRef)) break;
            }
            // Standalone: test feet vs fixed platform entity; when overlapping, mount to the mount entity (not the platform)
            if (!riderToPart.containsKey(riderRef)) {
                Ref<EntityStore> platformRef = giantManager.getStandalonePlatformRef();
                Ref<EntityStore> mountRef = giantManager.getStandaloneMountRef();
                if (platformRef != null && platformRef.isValid() && mountRef != null && mountRef.isValid()) {
                    TransformComponent partTransform = store.getComponent(platformRef, TransformComponent.getComponentType());
                    BoundingBox partBbox = store.getComponent(platformRef, BoundingBox.getComponentType());
                    if (partTransform != null && partBbox != null) {
                        Box partBoxLocal = partBbox.getBoundingBox();
                        Box partBoxWorld = new Box(partBoxLocal);
                        partBoxWorld.offset(partTransform.getPosition());
                        if (CollisionMath.boxesOverlap(feetBox, partBoxWorld)) {
                            riderToPart.put(riderRef, mountRef);
                        }
                    }
                }
            }
        }

        // Diagnostic: when overlap is lost, log rider Y vs part top so we can see why
        if (giantManager.isDebugLog()) {
            for (Integer id : new ArrayList<>(lastReportedRiderIdToName.keySet())) {
                boolean stillOn = riderToPart.entrySet().stream().anyMatch(entry -> entry.getKey().getIndex() == id);
                if (!stillOn) {
                    String name = lastReportedRiderIdToName.remove(id);
                    Ref<EntityStore> riderRef = lastTickRiderToPart.keySet().stream()
                            .filter(r -> r.getIndex() == id)
                            .findFirst()
                            .orElse(null);
                    Ref<EntityStore> partRef = riderRef != null ? lastTickRiderToPart.get(riderRef) : null;
                    logWhyLeftPart(store, id, name, riderRef, partRef, giantManager);
                }
            }
            if (++tickCounter % HEIGHT_LOG_THROTTLE_TICKS == 0) {
                for (Map.Entry<Ref<EntityStore>, Ref<EntityStore>> e : riderToPart.entrySet()) {
                    logOnPartHeights(store, e.getKey(), e.getValue(), riderToName.get(e.getKey()), giantManager);
                }
            }
            if (tickCounter % LOG_THROTTLE_TICKS == 0) {
                for (Map.Entry<Ref<EntityStore>, Ref<EntityStore>> e : riderToPart.entrySet()) {
                    int id = e.getKey().getIndex();
                    if (!lastReportedRiderIdToName.containsKey(id)) {
                        String name = riderToName.get(e.getKey());
                        String partName = giantManager.getBodyPartName(e.getValue());
                        lastReportedRiderIdToName.put(id, name != null ? name : "?");
                        LOGGER.atInfo().log("Platform: Player %s on part %s", name, partName);
                    }
                }
            }
        }

        lastTickRiderToPart = new HashMap<>(riderToPart);
        giantManager.setRiderToPartMap(riderToPart);
    }

    private void logOnPartHeights(Store<EntityStore> store, Ref<EntityStore> riderRef, Ref<EntityStore> partRef,
                                   String name, GiantManager giantManager) {
        TransformComponent riderT = store.getComponent(riderRef, TransformComponent.getComponentType());
        TransformComponent partT = store.getComponent(partRef, TransformComponent.getComponentType());
        BoundingBox partBbox = store.getComponent(partRef, BoundingBox.getComponentType());
        if (riderT == null || partT == null || partBbox == null) return;
        double riderY = riderT.getPosition().y;
        Box feet = CollisionMath.feetAABB(riderT.getPosition(),
                giantManager.getFeetHalfWidth(), giantManager.getFeetHalfHeight(), giantManager.getFeetHalfDepth(),
                giantManager.getFeetYOffsetBelowCenter());
        Box partLocal = partBbox.getBoundingBox();
        Vector3d partPos = partT.getPosition();
        double partTop = partPos.y + partLocal.max.y;
        String partName = giantManager.getBodyPartName(partRef);
        LOGGER.atInfo().log("Platform heights: %s riderY=%.3f feetY=[%.3f,%.3f] part %s partTop=%.3f",
                name != null ? name : "ref" + riderRef.getIndex(), riderY, feet.min.y, feet.max.y, partName, partTop);
        if (giantManager.isDebugAutoAdjust() && riderY > partTop + GiantManager.getBounceThresholdAbovePartTop()) {
            giantManager.adjustNudgeFromBounce();
        }
    }

    private void logWhyLeftPart(Store<EntityStore> store, int riderId, String name, Ref<EntityStore> riderRef,
                                Ref<EntityStore> partRef, GiantManager giantManager) {
        double riderY = Double.NaN;
        double partTop = Double.NaN;
        if (riderRef != null && riderRef.isValid()) {
            TransformComponent riderT = store.getComponent(riderRef, TransformComponent.getComponentType());
            if (riderT != null) riderY = riderT.getPosition().y;
        }
        if (partRef != null && partRef.isValid()) {
            TransformComponent partT = store.getComponent(partRef, TransformComponent.getComponentType());
            BoundingBox partBbox = store.getComponent(partRef, BoundingBox.getComponentType());
            if (partT != null && partBbox != null)
                partTop = partT.getPosition().y + partBbox.getBoundingBox().max.y;
        }
        LOGGER.atInfo().log("Platform: Player %s left part riderY=%.3f partTop=%.3f (rider below part? gravity/overwrite?)",
                name, riderY, partTop);
        if (giantManager.isDebugAutoAdjust() && !Double.isNaN(riderY) && !Double.isNaN(partTop) && riderY < partTop) {
            giantManager.adjustNudgeFromFallThrough();
        }
    }
}
