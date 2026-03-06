package com.hexvane.worldbearers.npc;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.validators.DoubleSingleValidator;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.movement.controllers.builders.BuilderMotionControllerBase;
import com.hypixel.hytale.server.spawning.SpawnTestResult;
import com.hypixel.hytale.server.spawning.SpawningContext;
import javax.annotation.Nonnull;

/**
 * Builder for the Platform motion controller. Used by giant body part NPCs
 * so the engine does not apply gravity or movement; position is driven by our systems.
 */
public class BuilderMotionControllerPlatform extends BuilderMotionControllerBase {

    @Nonnull
    @Override
    public MotionControllerPlatform build(@Nonnull BuilderSupport builderSupport) {
        return new MotionControllerPlatform(builderSupport, this);
    }

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Static platform; no gravity or movement (position driven externally)";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return getShortDescription();
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    public BuilderMotionControllerPlatform readConfig(@Nonnull JsonElement data) {
        // Base constructor calls getMaxHorizontalSpeed(); the base readCommonConfig does not set it (Walk/Fly set it in their readConfig).
        this.getDouble(
                data,
                "MaxHorizontalSpeed",
                this.maxHorizontalSpeed,
                0.0,
                DoubleSingleValidator.greaterEqual0(),
                BuilderDescriptorState.Stable,
                "Maximum horizontal speed (0 for static platform)",
                null
        );
        return this;
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public Class<MotionController> category() {
        return (Class<MotionController>) (Class<?>) MotionController.class;
    }

    @Nonnull
    @Override
    public Class<? extends MotionController> getClassType() {
        return MotionControllerPlatform.class;
    }

    @Nonnull
    @Override
    public SpawnTestResult canSpawn(@Nonnull SpawningContext context) {
        return SpawnTestResult.TEST_OK;
    }
}
