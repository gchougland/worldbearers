package com.hexvane.worldbearers.giant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Defines the body layout of a giant, including all body parts and their properties.
 * This is the static definition - runtime tracking is handled by GiantInstance.
 */
public class GiantDefinition {

    private final String id;
    private final String npcRoleName;
    private final List<BodyPartDefinition> bodyParts;
    private final float scale;

    private GiantDefinition(Builder builder) {
        this.id = builder.id;
        this.npcRoleName = builder.npcRoleName;
        this.bodyParts = Collections.unmodifiableList(builder.bodyParts);
        this.scale = builder.scale;
    }

    public String getId() { return id; }
    public String getNpcRoleName() { return npcRoleName; }
    public List<BodyPartDefinition> getBodyParts() { return bodyParts; }
    public float getScale() { return scale; }

    /**
     * Returns only body parts that need collision child entities (walkable and climbable surfaces).
     */
    public List<BodyPartDefinition> getCollidableBodyParts() {
        List<BodyPartDefinition> result = new ArrayList<>();
        for (BodyPartDefinition part : bodyParts) {
            if (part.getType() == BodyPartDefinition.PartType.WALKABLE
                    || part.getType() == BodyPartDefinition.PartType.CLIMBABLE) {
                result.add(part);
            }
        }
        return result;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private String npcRoleName;
        private final List<BodyPartDefinition> bodyParts = new ArrayList<>();
        private float scale = 1.0f;

        private Builder(String id) {
            this.id = id;
        }

        public Builder npcRole(String roleName) {
            this.npcRoleName = roleName;
            return this;
        }

        public Builder scale(float scale) {
            this.scale = scale;
            return this;
        }

        public Builder addPart(BodyPartDefinition part) {
            this.bodyParts.add(part);
            return this;
        }

        public GiantDefinition build() {
            return new GiantDefinition(this);
        }
    }
}
