package com.hexvane.worldbearers.giant;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime state for a spawned giant entity.
 * Tracks the main NPC entity reference and all child collision entities.
 */
public class GiantInstance {

    private final GiantDefinition definition;
    private final Ref<EntityStore> mainEntityRef;
    private final Map<String, Ref<EntityStore>> bodyPartEntities;
    private boolean active;

    public GiantInstance(GiantDefinition definition, Ref<EntityStore> mainEntityRef) {
        this.definition = definition;
        this.mainEntityRef = mainEntityRef;
        this.bodyPartEntities = new HashMap<>();
        this.active = true;
    }

    public GiantDefinition getDefinition() { return definition; }
    public Ref<EntityStore> getMainEntityRef() { return mainEntityRef; }
    public Map<String, Ref<EntityStore>> getBodyPartEntities() { return bodyPartEntities; }
    public boolean isActive() { return active; }

    public void addBodyPartEntity(String partName, Ref<EntityStore> entityRef) {
        bodyPartEntities.put(partName, entityRef);
    }

    public Ref<EntityStore> getBodyPartEntity(String partName) {
        return bodyPartEntities.get(partName);
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
