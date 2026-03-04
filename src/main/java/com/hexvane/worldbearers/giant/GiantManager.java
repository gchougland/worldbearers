package com.hexvane.worldbearers.giant;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
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
}
