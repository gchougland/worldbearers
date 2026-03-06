package com.hexvane.worldbearers.systems;

import com.hexvane.worldbearers.giant.GiantManager;
import com.hexvane.worldbearers.giant.GiantManager.PlatformBounds;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Custom input for body-part platform mounts. Runs early (RootDependency).
 * For riders mounted to a body part:
 * - Mutates RelativeMovement in the queue to setY(0) so HandleMountInput applies (dx,0,dz) if it runs.
 * - If jump in queue: dismount and re-queue SetMovementStates so the engine applies jump.
 * - Else (standalone platform): apply (dx,0,dz) to body part position, clamp to platform bounds,
 *   set part Y to platform plane, set fixed attachment offset, clear queue.
 * Resync corrects body part position after HandleMountInput (clamp XZ, set Y).
 */
public class BodyPartMountInputSystem extends TickingSystem<EntityStore> {

    private final GiantManager giantManager;
    private volatile ComponentType<EntityStore, MountedComponent> mountedComponentType;
    private volatile ComponentType<EntityStore, PlayerInput> playerInputComponentType;

    public BodyPartMountInputSystem(GiantManager giantManager) {
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
        if (playerInputComponentType == null) {
            playerInputComponentType = PlayerInput.getComponentType();
        }

        World world = store.getExternalData().getWorld();
        if (world == null) return;

        Collection<PlayerRef> playerRefs = world.getPlayerRefs();
        for (PlayerRef playerRef : playerRefs) {
            Ref<EntityStore> riderRef = playerRef.getReference();
            if (riderRef == null || !riderRef.isValid()) continue;

            MountedComponent mounted = store.getComponent(riderRef, mountedComponentType);
            PlayerInput playerInput = store.getComponent(riderRef, playerInputComponentType);
            if (mounted == null || playerInput == null) continue;

            Ref<EntityStore> mountRef = mounted.getMountedToEntity();
            if (mountRef == null || !giantManager.isBodyPartRef(mountRef)) continue;

            TransformComponent partTransform = store.getComponent(mountRef, TransformComponent.getComponentType());
            if (partTransform == null) continue;

            List<PlayerInput.InputUpdate> queue = playerInput.getMovementUpdateQueue();

            // Jump: dismount and let engine apply jump
            PlayerInput.SetMovementStates jumpStates = null;
            for (PlayerInput.InputUpdate update : queue) {
                if (update instanceof PlayerInput.SetMovementStates set && set.movementStates().jumping) {
                    jumpStates = set;
                    break;
                }
            }
            if (jumpStates != null) {
                store.tryRemoveComponent(riderRef, MountedComponent.getComponentType());
                // Do not clear the queue so the engine can process the jump for the now-unmounted player
                continue;
            }

            // Zero Y in RelativeMovement so HandleMountInput applies (dx,0,dz) to mount if it runs
            for (PlayerInput.InputUpdate update : queue) {
                if (update instanceof PlayerInput.RelativeMovement rel) {
                    rel.setY(0);
                }
            }

            // Standalone only (phase 1): apply movement to mount entity, clamp to bounds, set fixed offset, clear queue
            Ref<EntityStore> standaloneMountRef = giantManager.getStandaloneMountRef();
            if (mountRef.equals(standaloneMountRef)) {
                PlatformBounds bounds = giantManager.getStandalonePlatformBounds();
                BoundingBox partBbox = store.getComponent(mountRef, BoundingBox.getComponentType());
                if (bounds != null && partBbox != null) {
                    Vector3d partPos = partTransform.getPosition();
                    double partHalfHeight = partBbox.getBoundingBox().max.y;
                    for (PlayerInput.InputUpdate update : queue) {
                        if (update instanceof PlayerInput.RelativeMovement rel) {
                            partPos = partTransform.getPosition();
                            partPos.add(rel.getX(), 0, rel.getZ());
                            partTransform.getPosition().assign(partPos);
                        }
                    }
                    partPos = partTransform.getPosition();
                    double clampX = Math.max(bounds.minX(), Math.min(bounds.maxX(), partPos.x));
                    double clampZ = Math.max(bounds.minZ(), Math.min(bounds.maxZ(), partPos.z));
                    double partY = bounds.topY() - partHalfHeight;
                    boolean walkedOff = (partPos.x != clampX) || (partPos.z != clampZ);
                    if (walkedOff) {
                        // Rider walked off: keep mount at the edge, dismount so they can step off
                        partTransform.getPosition().assign(clampX, partY, clampZ);
                        store.tryRemoveComponent(riderRef, MountedComponent.getComponentType());
                        queue.clear();
                        continue;
                    }
                    partTransform.getPosition().assign(clampX, partY, clampZ);
                    partPos = partTransform.getPosition();
                    double partTop = partPos.y + partBbox.getBoundingBox().max.y;
                    double minRiderY = giantManager.computeMinRiderYForPlatform(partTop);
                    mounted.getAttachmentOffset().assign(0f, (float) (minRiderY - partPos.y), 0f);
                    queue.clear();
                    continue;
                }
            }

            // Giant part or no bounds: clear queue so HandleMountInput does not move part (we already zeroed Y)
            queue.clear();
        }
    }
}
