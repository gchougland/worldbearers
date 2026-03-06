package com.hexvane.worldbearers.systems;

import com.hexvane.worldbearers.giant.GiantManager;
import com.hexvane.worldbearers.giant.GiantManager.PlatformBounds;
import com.hexvane.worldbearers.giant.GiantInstance;
import com.hypixel.hytale.builtin.mounts.MountedByComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Option 2: Platform carry using mount. Runs after platform detection.
 * Attaches/detaches MountedComponent based on detection; syncs rider position
 * to body part position + attachmentOffset each tick.
 */
public class PlatformCarryMountSystem extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int SYNC_LOG_THROTTLE_TICKS = 40;
    /** Ticks a rider must be off detection before we dismount (reduces jerk-then-fall from one-tick flicker). */
    private static final int DISMOUNT_HYSTERESIS_TICKS = 2;

    private final GiantManager giantManager;
    private int tickCounter;
    /** Rider ref index -> position we last set (to detect overwrite by gravity/input). */
    private final Map<Integer, Vector3d> lastSyncedPosition = new HashMap<>();
    /** Rider ref index -> ticks they have not been in riderToPart (hysteresis before dismount). */
    private final Map<Integer, Integer> ticksNotOnPart = new HashMap<>();

    public PlatformCarryMountSystem(GiantManager giantManager) {
        this.giantManager = giantManager;
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, PlatformDetectionSystem.class));
    }

    /** Rider anchor Y in mount local space (top of platform + a bit so rider center is on top). Client uses this for rider visual. */
    private static final float NPC_MOUNT_ANCHOR_Y = 0.5f;

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        tickCounter++;
        Map<Ref<EntityStore>, Ref<EntityStore>> riderToPart = giantManager.getRiderToPartMap();
        boolean syncLoggedThisTick = false;
        World world = store.getExternalData().getWorld();

        // Attach: ensure MountedComponent for each (rider, part); rider-sync position; add NPCMountComponent so client draws rider at anchor
        for (Map.Entry<Ref<EntityStore>, Ref<EntityStore>> entry : riderToPart.entrySet()) {
            Ref<EntityStore> riderRef = entry.getKey();
            Ref<EntityStore> partRef = entry.getValue();
            if (!riderRef.isValid() || !partRef.isValid()) continue;

            TransformComponent partTransform = store.getComponent(partRef, TransformComponent.getComponentType());
            TransformComponent riderTransform = store.getComponent(riderRef, TransformComponent.getComponentType());
            if (partTransform == null || riderTransform == null) continue;

            MountedComponent mounted = store.getComponent(riderRef, MountedComponent.getComponentType());
            if (mounted == null) {
                // First time on this part: move body part under rider (standalone only), then add MountedComponent
                Vector3d partPos = partTransform.getPosition();
                Vector3d riderPos = riderTransform.getPosition();
                BoundingBox partBbox = store.getComponent(partRef, BoundingBox.getComponentType());
                Ref<EntityStore> standaloneMountRef = giantManager.getStandaloneMountRef();
                if (partRef.equals(standaloneMountRef) && standaloneMountRef != null && partBbox != null) {
                    PlatformBounds bounds = giantManager.getStandalonePlatformBounds();
                    if (bounds != null) {
                        double partHalfHeight = partBbox.getBoundingBox().max.y;
                        double desiredX = Math.max(bounds.minX(), Math.min(bounds.maxX(), riderPos.x));
                        double desiredZ = Math.max(bounds.minZ(), Math.min(bounds.maxZ(), riderPos.z));
                        double desiredPartY = bounds.topY() - partHalfHeight;
                        partTransform.getPosition().assign(desiredX, desiredPartY, desiredZ);
                        partPos = partTransform.getPosition();
                    }
                }
                double partTop = partBbox != null ? partPos.y + partBbox.getBoundingBox().max.y : partPos.y;
                double minRiderY = giantManager.computeMinRiderYForPlatform(partTop);
                Vector3f offset = new Vector3f(
                        (float) (riderPos.x - partPos.x),
                        (float) (minRiderY - partPos.y),
                        (float) (riderPos.z - partPos.z)
                );
                store.addComponent(riderRef, MountedComponent.getComponentType(),
                        new MountedComponent(partRef, offset, MountController.Minecart));
                mounted = store.getComponent(riderRef, MountedComponent.getComponentType());
                ensureNPCMountAnchorOnPart(store, world, partRef, riderRef);
                if (giantManager.isDebugLog()) {
                    String partName = giantManager.getBodyPartName(partRef);
                    LOGGER.atInfo().log("Platform: ref %d mounted to part %s offset (%.2f, %.2f, %.2f)",
                            riderRef.getIndex(), partName, offset.x, offset.y, offset.z);
                }
            } else if (!partRef.equals(mounted.getMountedToEntity())) {
                // Switched to another part: replace MountedComponent
                store.removeComponent(riderRef, MountedComponent.getComponentType());
                Vector3d partPos = partTransform.getPosition();
                Vector3d riderPos = riderTransform.getPosition();
                Vector3f offset = new Vector3f(
                        (float) (riderPos.x - partPos.x),
                        (float) (riderPos.y - partPos.y),
                        (float) (riderPos.z - partPos.z)
                );
                store.addComponent(riderRef, MountedComponent.getComponentType(),
                        new MountedComponent(partRef, offset, MountController.Minecart));
                mounted = store.getComponent(riderRef, MountedComponent.getComponentType());
                ensureNPCMountAnchorOnPart(store, world, partRef, riderRef);
            }

            if (mounted != null) {
                // Overwrite check: something may have moved the rider after our last sync (e.g. gravity, client input)
                Vector3d before = riderTransform.getPosition();
                Vector3d lastSynced = lastSyncedPosition.get(riderRef.getIndex());
                if (giantManager.isDebugLog() && lastSynced != null && (Math.abs(before.y - lastSynced.y) > 0.01)) {
                    LOGGER.atInfo().log("Platform overwrite: ref %d was synced to Y=%.3f but is now Y=%.3f (gravity/input overwrote?)",
                            riderRef.getIndex(), lastSynced.y, before.y);
                }
                // Rider-sync: position = part position + attachmentOffset; clamp Y so feet sit on platform top (avoid sinking)
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
                double bx = before.x, by = before.y, bz = before.z;
                riderTransform.getPosition().assign(
                        partPos.x + offset.x,
                        riderY,
                        partPos.z + offset.z
                );
                Vector3d after = riderTransform.getPosition();
                lastSyncedPosition.put(riderRef.getIndex(), new Vector3d(after.x, after.y, after.z));
                if (giantManager.isDebugLog() && !syncLoggedThisTick && (tickCounter % SYNC_LOG_THROTTLE_TICKS == 0)) {
                    String partName = giantManager.getBodyPartName(partRef);
                    LOGGER.atInfo().log("Platform sync: ref %d before (%.2f,%.2f,%.2f) part %s (%.2f,%.2f,%.2f) offset (%.2f,%.2f,%.2f) after (%.2f,%.2f,%.2f)",
                            riderRef.getIndex(), bx, by, bz, partName, partPos.x, partPos.y, partPos.z,
                            offset.x, offset.y, offset.z, after.x, after.y, after.z);
                    syncLoggedThisTick = true;
                }
            }
        }
        // Clear lastSynced for riders no longer on any part so we don't leak
        lastSyncedPosition.keySet().removeIf(idx -> !riderToPart.keySet().stream().anyMatch(r -> r.getIndex() == idx));

        // Dismount: remove MountedComponent only after hysteresis (reduces jerk-then-fall from one-tick detection flicker)
        for (Ref<EntityStore> r : riderToPart.keySet()) ticksNotOnPart.put(r.getIndex(), 0);
        for (GiantInstance instance : giantManager.getActiveGiants()) {
            if (!instance.isActive()) continue;
            for (Ref<EntityStore> partRef : instance.getBodyPartEntities().values()) {
                if (partRef == null || !partRef.isValid()) continue;
                MountedByComponent mountedBy = store.getComponent(partRef, MountedByComponent.getComponentType());
                if (mountedBy == null) continue;
                List<Ref<EntityStore>> passengers = new ArrayList<>(mountedBy.getPassengers());
                for (Ref<EntityStore> riderRef : passengers) {
                    if (!riderRef.isValid()) continue;
                    Ref<EntityStore> expectedPart = riderToPart.get(riderRef);
                    if (!partRef.equals(expectedPart)) {
                        int ticks = ticksNotOnPart.getOrDefault(riderRef.getIndex(), 0) + 1;
                        ticksNotOnPart.put(riderRef.getIndex(), ticks);
                        if (ticks >= DISMOUNT_HYSTERESIS_TICKS) {
                            if (giantManager.isDebugLog()) {
                                String partName = giantManager.getBodyPartName(partRef);
                                LOGGER.atInfo().log("Platform: ref %d dismounted from part %s (after %d ticks off)", riderRef.getIndex(), partName, ticks);
                            }
                            store.tryRemoveComponent(riderRef, MountedComponent.getComponentType());
                            removeNPCMountFromPartIfOwner(store, partRef, riderRef, world);
                            ticksNotOnPart.remove(riderRef.getIndex());
                        }
                    }
                }
            }
        }
        // Standalone mount dismount: when the MOUNT entity's position is outside the platform XZ bounds (not rider/feet)
        Ref<EntityStore> standaloneMountRef = giantManager.getStandaloneMountRef();
        if (standaloneMountRef != null && standaloneMountRef.isValid()) {
            PlatformBounds bounds = giantManager.getStandalonePlatformBounds();
            MountedByComponent mountedBy = store.getComponent(standaloneMountRef, MountedByComponent.getComponentType());
            TransformComponent mountTransform = store.getComponent(standaloneMountRef, TransformComponent.getComponentType());
            if (mountedBy != null && bounds != null && mountTransform != null) {
                Vector3d mountPos = mountTransform.getPosition();
                boolean mountOffEdge = mountPos.x < bounds.minX() || mountPos.x > bounds.maxX()
                        || mountPos.z < bounds.minZ() || mountPos.z > bounds.maxZ();
                if (mountOffEdge) {
                    for (Ref<EntityStore> riderRef : new ArrayList<>(mountedBy.getPassengers())) {
                        if (!riderRef.isValid()) continue;
                        if (giantManager.isDebugLog()) {
                            LOGGER.atInfo().log("Platform: ref %d dismounted from platform_mount (mount off edge at %.2f,%.2f,%.2f)", riderRef.getIndex(), mountPos.x, mountPos.y, mountPos.z);
                        }
                        store.tryRemoveComponent(riderRef, MountedComponent.getComponentType());
                        removeNPCMountFromPartIfOwner(store, standaloneMountRef, riderRef, world);
                    }
                }
            }
        }
        // Store standalone mount position only when it has no riders (resync restores when empty)
        Ref<EntityStore> standaloneMountRefForStore = giantManager.getStandaloneMountRef();
        if (standaloneMountRefForStore != null && standaloneMountRefForStore.isValid()) {
            MountedByComponent mountedByMount = store.getComponent(standaloneMountRefForStore, MountedByComponent.getComponentType());
            if (mountedByMount == null || mountedByMount.getPassengers().isEmpty()) {
                TransformComponent partT = store.getComponent(standaloneMountRefForStore, TransformComponent.getComponentType());
                if (partT != null) {
                    giantManager.setStandaloneMountLastPosition(partT.getPosition());
                }
            }
        }
    }

    /** Add or update NPCMountComponent on the part so the client draws the rider at the anchor (fixes rider falling through visual). */
    private void ensureNPCMountAnchorOnPart(Store<EntityStore> store, World world, Ref<EntityStore> partRef, Ref<EntityStore> riderRef) {
        if (world == null) return;
        PlayerRef playerRef = findPlayerRefByEntity(world, riderRef);
        if (playerRef == null) return;
        NPCMountComponent mount = store.getComponent(partRef, NPCMountComponent.getComponentType());
        if (mount == null) {
            mount = new NPCMountComponent();
            mount.setOriginalRoleIndex(0);
            mount.setOwnerPlayerRef(playerRef);
            mount.setAnchor(0f, NPC_MOUNT_ANCHOR_Y, 0f);
            store.addComponent(partRef, NPCMountComponent.getComponentType(), mount);
        } else {
            mount.setOwnerPlayerRef(playerRef);
            mount.setAnchor(0f, NPC_MOUNT_ANCHOR_Y, 0f);
        }
    }

    private void removeNPCMountFromPartIfOwner(Store<EntityStore> store, Ref<EntityStore> partRef, Ref<EntityStore> riderRef, World world) {
        if (world == null) return;
        PlayerRef playerRef = findPlayerRefByEntity(world, riderRef);
        if (playerRef == null) return;
        NPCMountComponent mount = store.getComponent(partRef, NPCMountComponent.getComponentType());
        if (mount != null && playerRef.equals(mount.getOwnerPlayerRef())) {
            store.tryRemoveComponent(partRef, NPCMountComponent.getComponentType());
        }
    }

    private static PlayerRef findPlayerRefByEntity(World world, Ref<EntityStore> entityRef) {
        for (PlayerRef p : world.getPlayerRefs()) {
            Ref<EntityStore> ref = p.getReference();
            if (ref != null && ref.equals(entityRef)) return p;
        }
        return null;
    }
}
