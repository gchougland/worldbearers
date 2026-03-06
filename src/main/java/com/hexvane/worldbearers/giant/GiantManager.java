package com.hexvane.worldbearers.giant;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.builtin.mounts.MountedByComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central manager for all giant instances. Handles spawning giants with their
 * body part collision entities and updating positions each tick.
 *
 * Spike version - production will add animation-driven offsets, damage routing,
 * and proper cleanup.
 */
public class GiantManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<String, GiantDefinition> definitions = new HashMap<>();
    private final List<GiantInstance> activeGiants = new ArrayList<>();

    /** World-space bounds of the platform top face: XZ rectangle and top Y. Used to clamp body part movement. */
    public record PlatformBounds(double topY, double minX, double maxX, double minZ, double maxZ) {}

    /** Standalone: fixed entity used for detection and hitbox (never moved). */
    private volatile Ref<EntityStore> standalonePlatformRef;
    /** Standalone: mount entity (rider is mounted to this; we move this within platform bounds). */
    private volatile Ref<EntityStore> standaloneMountRef;
    /** Last position of the standalone *mount* entity (for resync when no riders). */
    private volatile Vector3d standaloneMountLastPosition;
    /** Bounds of standalone platform top face (from fixed platform entity; null when no standalone). */
    private volatile PlatformBounds standalonePlatformBounds;

    /** Current tick: rider ref -> body part ref (for platform carry). Set by PlatformDetectionSystem. */
    private volatile Map<Ref<EntityStore>, Ref<EntityStore>> riderToPartMap = Collections.emptyMap();

    /** When true, body part collision AABBs are drawn each tick (debug). */
    private volatile boolean debugBodyPartBoxes;
    private volatile boolean debugFeet;
    private volatile boolean debugRiders;
    private volatile boolean debugLog;
    /** When true, auto-adjust sync Y nudge from fall-through (nudge up) and bounce (nudge down). */
    private volatile boolean debugAutoAdjust;
    /** World-space Y added to synced rider position when debugAutoAdjust is on. Tuned by auto-adjust or /platformtune. */
    private volatile double syncYNudge = 0.0;
    private static final double SYNC_NUDGE_MAX = 0.5;
    private static final double SYNC_NUDGE_MIN = -0.2;
    private static final double SYNC_NUDGE_STEP = 0.03;
    private static final double BOUNCE_THRESHOLD_ABOVE_PART_TOP = 0.2;
    /** Extra Y above platform top when clamping rider. Use 0 so feet.min.y = partTop (feet touch surface)
     * and detection overlap is still true next tick; positive values would push feet above partTop and break overlap. */
    private static final double RIDER_MIN_ABOVE_PLATFORM_TOP = 0.0;

    /** Tunable feet AABB (in-memory). Y offset: how far below player position the feet box center is (0 = at position; was 0.9, often drew ~1 block too low). */
    private volatile double feetHalfWidth = 0.4;
    private volatile double feetHalfHeight = 0.05;
    private volatile double feetHalfDepth = 0.4;
    private volatile double feetYOffsetBelowCenter = 0.25;

    public static final double DEFAULT_FEET_HALF_WIDTH = 0.4;
    public static final double DEFAULT_FEET_HALF_HEIGHT = 0.05;
    public static final double DEFAULT_FEET_HALF_DEPTH = 0.4;
    public static final double DEFAULT_FEET_Y_OFFSET_BELOW_CENTER = 0.25;

    public boolean isDebugBodyPartBoxes() { return debugBodyPartBoxes; }
    public void setDebugBodyPartBoxes(boolean debugBodyPartBoxes) { this.debugBodyPartBoxes = debugBodyPartBoxes; }
    public boolean isDebugFeet() { return debugFeet; }
    public void setDebugFeet(boolean debugFeet) { this.debugFeet = debugFeet; }
    public boolean isDebugRiders() { return debugRiders; }
    public void setDebugRiders(boolean debugRiders) { this.debugRiders = debugRiders; }
    public boolean isDebugLog() { return debugLog; }
    public void setDebugLog(boolean debugLog) { this.debugLog = debugLog; }
    public boolean isDebugAutoAdjust() { return debugAutoAdjust; }
    public void setDebugAutoAdjust(boolean debugAutoAdjust) { this.debugAutoAdjust = debugAutoAdjust; }
    public double getSyncYNudge() { return syncYNudge; }
    public void setSyncYNudge(double v) { this.syncYNudge = Math.max(SYNC_NUDGE_MIN, Math.min(SYNC_NUDGE_MAX, v)); }
    /** Call when rider fell through (riderY < partTop): nudge up. */
    public void adjustNudgeFromFallThrough() {
        setSyncYNudge(syncYNudge + SYNC_NUDGE_STEP);
    }
    /** Call when rider is above part (riderY > partTop + threshold): nudge down. */
    public void adjustNudgeFromBounce() {
        setSyncYNudge(syncYNudge - SYNC_NUDGE_STEP);
    }
    public static double getBounceThresholdAbovePartTop() { return BOUNCE_THRESHOLD_ABOVE_PART_TOP; }

    public double getFeetHalfWidth() { return feetHalfWidth; }
    public void setFeetHalfWidth(double v) { this.feetHalfWidth = v; }
    public double getFeetHalfHeight() { return feetHalfHeight; }
    public void setFeetHalfHeight(double v) { this.feetHalfHeight = v; }
    public double getFeetHalfDepth() { return feetHalfDepth; }
    public void setFeetHalfDepth(double v) { this.feetHalfDepth = v; }
    public double getFeetYOffsetBelowCenter() { return feetYOffsetBelowCenter; }
    public void setFeetYOffsetBelowCenter(double v) { this.feetYOffsetBelowCenter = v; }

    /** Minimum rider center Y when syncing to a platform so feet sit on top (partTop = part position Y + part bbox max Y). */
    public double computeMinRiderYForPlatform(double partTop) {
        return partTop + feetYOffsetBelowCenter + feetHalfHeight + RIDER_MIN_ABOVE_PLATFORM_TOP;
    }

    /** Restore default tunable values. */
    public void resetTunables() {
        feetHalfWidth = DEFAULT_FEET_HALF_WIDTH;
        feetHalfHeight = DEFAULT_FEET_HALF_HEIGHT;
        feetHalfDepth = DEFAULT_FEET_HALF_DEPTH;
        feetYOffsetBelowCenter = DEFAULT_FEET_Y_OFFSET_BELOW_CENTER;
        syncYNudge = 0.0;
    }

    /** Get body part name for a body part ref, or null if not found. */
    public String getBodyPartName(Ref<EntityStore> partRef) {
        if (partRef == null || !partRef.isValid()) return null;
        if (partRef.equals(standalonePlatformRef)) return "platform";
        if (partRef.equals(standaloneMountRef)) return "platform_mount";
        for (GiantInstance instance : activeGiants) {
            if (!instance.isActive()) continue;
            for (Map.Entry<String, Ref<EntityStore>> e : instance.getBodyPartEntities().entrySet()) {
                if (e.getValue().equals(partRef)) return e.getKey();
            }
        }
        return null;
    }

    /** Spawn a single standalone platform at the given position (no giant). Replaces any existing one.
     * Spawns two entities: (1) platform = fixed hitbox for detection, (2) mount = entity we move, rider is mounted to this. */
    public Ref<EntityStore> spawnStandalonePlatform(Store<EntityStore> store, Vector3d position) {
        standalonePlatformRef = null;
        standaloneMountRef = null;
        standalonePlatformBounds = null;
        standaloneMountLastPosition = null;
        double hx = 1.0, hy = 0.25, hz = 1.0;
        Box partBox = new Box(
                new Vector3d(-hx, -hy, -hz),
                new Vector3d(hx, hy, hz)
        );
        // 1) Platform entity (fixed) – used for detection and hitbox; never moved
        var platformPair = NPCPlugin.get().spawnNPC(store, "Giant_BodyPart", null, position, new Vector3f());
        if (platformPair == null) {
            LOGGER.atWarning().log("Failed to spawn standalone platform (platform entity)");
            return null;
        }
        Ref<EntityStore> platformRef = platformPair.first();
        BoundingBox platformBbox = store.getComponent(platformRef, BoundingBox.getComponentType());
        if (platformBbox != null) {
            platformBbox.setBoundingBox(partBox);
            double topY = position.y + partBox.max.y;
            standalonePlatformBounds = new PlatformBounds(
                    topY,
                    position.x + partBox.min.x,
                    position.x + partBox.max.x,
                    position.z + partBox.min.z,
                    position.z + partBox.max.z
            );
        }
        standalonePlatformRef = platformRef;
        // 2) Mount entity (moved under rider, WASD) – rider is mounted to this
        var mountPair = NPCPlugin.get().spawnNPC(store, "Giant_BodyPart", null, position, new Vector3f());
        if (mountPair == null) {
            LOGGER.atWarning().log("Failed to spawn standalone platform (mount entity)");
            standalonePlatformRef = null;
            standalonePlatformBounds = null;
            return null;
        }
        Ref<EntityStore> mountRef = mountPair.first();
        BoundingBox mountBbox = store.getComponent(mountRef, BoundingBox.getComponentType());
        if (mountBbox != null) {
            mountBbox.setBoundingBox(partBox);
        }
        standaloneMountRef = mountRef;
        standaloneMountLastPosition = new Vector3d(position.x, position.y, position.z);
        LOGGER.atInfo().log("Spawned standalone platform at (%.1f, %.1f, %.1f) (platform + mount)", position.x, position.y, position.z);
        return platformRef;
    }

    public Ref<EntityStore> getStandalonePlatformRef() {
        return standalonePlatformRef;
    }

    /** Get the standalone mount entity (rider is mounted to this; we move this). */
    public Ref<EntityStore> getStandaloneMountRef() {
        return standaloneMountRef;
    }

    /** Store standalone mount position (used by resync when no riders). */
    public void setStandaloneMountLastPosition(Vector3d position) {
        this.standaloneMountLastPosition = position != null ? new Vector3d(position.x, position.y, position.z) : null;
    }

    /** Get stored position for standalone mount; used by resync when no riders. */
    public Vector3d getStandaloneMountLastPosition() {
        return standaloneMountLastPosition;
    }

    /** Get platform bounds for standalone (top face XZ rectangle and topY); null if no standalone. */
    public PlatformBounds getStandalonePlatformBounds() {
        return standalonePlatformBounds;
    }

    /** Clear standalone platform bounds (e.g. when clearing standalone). */
    public void setStandalonePlatformBounds(PlatformBounds bounds) {
        this.standalonePlatformBounds = bounds;
    }

    public void registerDefinition(GiantDefinition definition) {
        definitions.put(definition.getId(), definition);
        LOGGER.atInfo().log("Registered giant definition: %s with %d body parts",
                definition.getId(), definition.getBodyParts().size());
    }

    /**
     * Spawn a giant at the given position. Creates the main NPC entity and
     * invisible child entities for each collidable body part.
     */
    public GiantInstance spawnGiant(String definitionId, Store<EntityStore> store,
                                     Vector3d position, Vector3f rotation) {
        GiantDefinition definition = definitions.get(definitionId);
        if (definition == null) {
            LOGGER.atWarning().log("No giant definition found for id: %s", definitionId);
            return null;
        }

        // Spawn the main NPC entity using the role system
        var npcPair = NPCPlugin.get().spawnNPC(store, definition.getNpcRoleName(), null, position, rotation);
        if (npcPair == null) {
            LOGGER.atWarning().log("Failed to spawn main NPC entity for giant: %s", definitionId);
            return null;
        }

        Ref<EntityStore> mainRef = npcPair.first();
        GiantInstance instance = new GiantInstance(definition, mainRef);

        LOGGER.atInfo().log("Spawned giant '%s' main entity at (%.1f, %.1f, %.1f)",
                definitionId, position.x, position.y, position.z);

        // Spawn invisible child entities for walkable/climbable body parts
        for (BodyPartDefinition part : definition.getCollidableBodyParts()) {
            spawnBodyPartEntity(instance, part, store, position, rotation);
        }

        activeGiants.add(instance);
        return instance;
    }

    private void spawnBodyPartEntity(GiantInstance instance, BodyPartDefinition part,
                                      Store<EntityStore> store, Vector3d giantPos, Vector3f giantRotation) {
        float headingRad = (float) Math.toRadians(giantRotation.y);
        Vector3d worldPos = part.computeWorldPosition(giantPos, headingRad);

        // Spawn a minimal invisible NPC for collision
        var partPair = NPCPlugin.get().spawnNPC(store, "Giant_BodyPart", null, worldPos, new Vector3f());
        if (partPair == null) {
            LOGGER.atWarning().log("Failed to spawn body part entity: %s", part.getName());
            return;
        }

        Ref<EntityStore> partRef = partPair.first();

        // Resize bounding box to match the body part definition
        BoundingBox bbox = store.getComponent(partRef, BoundingBox.getComponentType());
        if (bbox != null) {
            Box partBox = new Box(
                    new Vector3d(-part.getHalfExtentX(), -part.getHalfExtentY(), -part.getHalfExtentZ()),
                    new Vector3d(part.getHalfExtentX(), part.getHalfExtentY(), part.getHalfExtentZ())
            );
            bbox.setBoundingBox(partBox);
        }

        instance.addBodyPartEntity(part.getName(), partRef);
        LOGGER.atInfo().log("Spawned body part '%s' at (%.1f, %.1f, %.1f)",
                part.getName(), worldPos.x, worldPos.y, worldPos.z);
    }

    /**
     * Called each server tick. Updates all body part entity positions to follow
     * their parent giant's current position and rotation.
     */
    public void tick(ComponentAccessor<EntityStore> componentAccessor) {
        for (GiantInstance instance : activeGiants) {
            if (!instance.isActive()) continue;

            TransformComponent mainTransform = componentAccessor.getComponent(
                    instance.getMainEntityRef(), TransformComponent.getComponentType());
            if (mainTransform == null) continue;

            Vector3d giantPos = mainTransform.getPosition();
            Vector3f giantRot = mainTransform.getRotation();
            float headingRad = (float) Math.toRadians(giantRot.y);

            for (BodyPartDefinition part : instance.getDefinition().getCollidableBodyParts()) {
                Ref<EntityStore> partRef = instance.getBodyPartEntity(part.getName());
                if (partRef == null) continue;

                TransformComponent partTransform = componentAccessor.getComponent(
                        partRef, TransformComponent.getComponentType());
                if (partTransform == null) continue;

                Vector3d newPos = part.computeWorldPosition(giantPos, headingRad);
                partTransform.getPosition().assign(newPos);
            }
        }
    }

    public void removeGiant(GiantInstance instance) {
        instance.setActive(false);
        activeGiants.remove(instance);
    }

    /**
     * Remove giant and dismount any riders on its body parts (Option 2 mount cleanup).
     * Call this when you have access to the store (e.g. when removing a giant from the world).
     */
    public void removeGiant(GiantInstance instance, Store<EntityStore> store) {
        for (Ref<EntityStore> partRef : instance.getBodyPartEntities().values()) {
            if (partRef == null || !partRef.isValid()) continue;
            MountedByComponent mountedBy = store.getComponent(partRef, MountedByComponent.getComponentType());
            if (mountedBy == null) continue;
            for (Ref<EntityStore> riderRef : new ArrayList<>(mountedBy.getPassengers())) {
                if (riderRef.isValid()) {
                    store.tryRemoveComponent(riderRef, MountedComponent.getComponentType());
                }
            }
        }
        instance.setActive(false);
        activeGiants.remove(instance);
    }

    /**
     * Look up a giant instance by its main entity ref.
     * Used by the tick system to check if an NPC entity is a tracked giant.
     */
    public GiantInstance getInstanceByMainRef(Ref<EntityStore> ref) {
        for (GiantInstance instance : activeGiants) {
            if (instance.isActive() && instance.getMainEntityRef().equals(ref)) {
                return instance;
            }
        }
        return null;
    }

    public List<GiantInstance> getActiveGiants() { return activeGiants; }
    public GiantDefinition getDefinition(String id) { return definitions.get(id); }

    public void setRiderToPartMap(Map<Ref<EntityStore>, Ref<EntityStore>> map) {
        this.riderToPartMap = map != null ? map : Collections.emptyMap();
    }

    public Map<Ref<EntityStore>, Ref<EntityStore>> getRiderToPartMap() {
        return riderToPartMap;
    }

    /**
     * Returns true if the given entity ref is one of our platform parts (giant body parts or standalone platform/mount).
     */
    public boolean isBodyPartRef(Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return false;
        if (ref.equals(standalonePlatformRef)) return true;
        if (ref.equals(standaloneMountRef)) return true;
        for (GiantInstance instance : activeGiants) {
            if (!instance.isActive()) continue;
            for (Ref<EntityStore> partRef : instance.getBodyPartEntities().values()) {
                if (partRef.equals(ref)) return true;
            }
        }
        return false;
    }
}
