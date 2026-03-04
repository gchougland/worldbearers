package com.hexvane.worldbearers.giant;

/**
 * Static registry of all giant definitions.
 * In production, these could be loaded from JSON config files.
 * For the spike, we define them in code.
 */
public class GiantDefinitions {

    public static GiantDefinition createTestGiant() {
        return GiantDefinition.builder("giant_test")
                .npcRole("Giant_Test")
                .scale(5.0f)

                // Walkable back surface - the main platform players stand on
                .addPart(new BodyPartDefinition(
                        "back", BodyPartDefinition.PartType.WALKABLE,
                        0.0, 6.5, 1.0,     // local offset: above center, behind
                        1.5, 0.5, 1.0,      // half-extents: wide, thin (platform), medium depth
                        1.0f
                ))

                // Walkable shoulders
                .addPart(new BodyPartDefinition(
                        "left_shoulder", BodyPartDefinition.PartType.WALKABLE,
                        -2.0, 7.5, 0.0,
                        0.8, 0.3, 0.8,
                        1.0f
                ))
                .addPart(new BodyPartDefinition(
                        "right_shoulder", BodyPartDefinition.PartType.WALKABLE,
                        2.0, 7.5, 0.0,
                        0.8, 0.3, 0.8,
                        1.0f
                ))

                // Climbable arms
                .addPart(new BodyPartDefinition(
                        "left_arm", BodyPartDefinition.PartType.CLIMBABLE,
                        -2.5, 5.0, 0.0,
                        0.5, 2.0, 0.5,
                        1.0f
                ))
                .addPart(new BodyPartDefinition(
                        "right_arm", BodyPartDefinition.PartType.CLIMBABLE,
                        2.5, 5.0, 0.0,
                        0.5, 2.0, 0.5,
                        1.0f
                ))

                // Weak point on back of neck
                .addPart(new BodyPartDefinition(
                        "weak_point_neck", BodyPartDefinition.PartType.WEAK_POINT,
                        0.0, 8.5, 0.5,
                        0.4, 0.4, 0.4,
                        3.0f  // 3x damage
                ))

                .build();
    }
}
