package com.hexvane.worldbearers;

import com.hexvane.worldbearers.commands.DebugBoxesCommand;
import com.hexvane.worldbearers.commands.PlatformAutoAdjustCommand;
import com.hexvane.worldbearers.commands.PlatformDebugCommand;
import com.hexvane.worldbearers.commands.PlatformDumpCommand;
import com.hexvane.worldbearers.commands.PlatformFeetCommand;
import com.hexvane.worldbearers.commands.PlatformLogCommand;
import com.hexvane.worldbearers.commands.PlatformRidersCommand;
import com.hexvane.worldbearers.commands.PlatformTuneCommand;
import com.hexvane.worldbearers.commands.SpawnPlatformCommand;
import com.hexvane.worldbearers.commands.SpawnGiantCommand;
import com.hexvane.worldbearers.giant.GiantDefinitions;
import com.hexvane.worldbearers.giant.GiantManager;
import com.hexvane.worldbearers.systems.BodyPartDebugBoxesSystem;
import com.hexvane.worldbearers.systems.BodyPartMountInputSystem;
import com.hexvane.worldbearers.systems.GiantBodyPartUpdateSystem;
import com.hexvane.worldbearers.systems.PlatformCarryEarlySyncSystem;
import com.hexvane.worldbearers.systems.PlatformCarryMountSystem;
import com.hexvane.worldbearers.systems.PlatformCarryResyncSystem;
import com.hexvane.worldbearers.systems.PlatformMountGravityOverrideSystem;
import com.hexvane.worldbearers.systems.PlatformDebugVisualsSystem;
import com.hexvane.worldbearers.systems.PlatformDetectionSystem;
import com.hexvane.worldbearers.npc.BuilderMotionControllerPlatform;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.event.PluginSetupEvent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.BuilderFactory;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;

public class WorldbearersPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private GiantManager giantManager;

    public WorldbearersPlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Worldbearers v%s initialized", this.getManifest().getVersion().toString());
    }

    private static boolean platformMotionControllerRegistered;

    @Override
    protected void setup() {
        // Defer Platform motion controller registration until NPC plugin is ready (NPCPlugin.get() is null until then)
        HytaleServer.get().getEventBus().registerGlobal(PluginSetupEvent.class, event -> {
            if (event.getPlugin() instanceof NPCPlugin) {
                registerPlatformMotionController();
            }
        });
        // If NPC plugin already finished setup before us (e.g. we load after it), register now
        if (NPCPlugin.get() != null) {
            registerPlatformMotionController();
        }

        // Initialize giant system
        giantManager = new GiantManager();
        giantManager.registerDefinition(GiantDefinitions.createTestGiant());

        // Register commands
        this.getCommandRegistry().registerCommand(
                new WorldbearersCommand(this.getName(), this.getManifest().getVersion().toString()));
        this.getCommandRegistry().registerCommand(
                new SpawnGiantCommand(giantManager));
        this.getCommandRegistry().registerCommand(new SpawnPlatformCommand(giantManager));
        this.getCommandRegistry().registerCommand(
                new DebugBoxesCommand(giantManager));
        this.getCommandRegistry().registerCommand(new PlatformDebugCommand(giantManager));
        this.getCommandRegistry().registerCommand(new PlatformFeetCommand(giantManager));
        this.getCommandRegistry().registerCommand(new PlatformRidersCommand(giantManager));
        this.getCommandRegistry().registerCommand(new PlatformDumpCommand(giantManager));
        this.getCommandRegistry().registerCommand(new PlatformLogCommand(giantManager));
        this.getCommandRegistry().registerCommand(new PlatformTuneCommand(giantManager));
        this.getCommandRegistry().registerCommand(new PlatformAutoAdjustCommand(giantManager));

        // Register tick system for updating body part collision entity positions
        GiantBodyPartUpdateSystem bodyPartUpdateSystem = new GiantBodyPartUpdateSystem(giantManager);
        this.getEntityStoreRegistry().registerSystem(bodyPartUpdateSystem);
        LOGGER.atInfo().log("Registered giant body part update system");

        // Option B: platform detection (feet AABB vs body part)
        this.getEntityStoreRegistry().registerSystem(new PlatformDetectionSystem(giantManager));
        // Early sync: pin standalone mount to platform plane at tick start, then sync rider positions
        this.getEntityStoreRegistry().registerSystem(new PlatformCarryEarlySyncSystem(giantManager));
        // Option 2: mount + rider sync + custom input (attachmentOffset)
        this.getEntityStoreRegistry().registerSystem(new PlatformCarryMountSystem(giantManager));
        this.getEntityStoreRegistry().registerSystem(new BodyPartMountInputSystem(giantManager));
        this.getEntityStoreRegistry().registerSystem(new PlatformCarryResyncSystem(giantManager));
        this.getEntityStoreRegistry().registerSystem(new PlatformMountGravityOverrideSystem(giantManager));
        this.getEntityStoreRegistry().registerSystem(new BodyPartDebugBoxesSystem(giantManager));
        this.getEntityStoreRegistry().registerSystem(new PlatformDebugVisualsSystem(giantManager));
        // Option 1 (delta application) available as PlatformCarryDeltaSystem if mount path is disabled

        LOGGER.atInfo().log("Worldbearers setup complete - %d giant definitions registered",
                1); // hardcoded for spike
    }

    public GiantManager getGiantManager() {
        return giantManager;
    }

    private static void registerPlatformMotionController() {
        if (platformMotionControllerRegistered) {
            return;
        }
        BuilderFactory<MotionController> mcFactory = NPCPlugin.get().getBuilderManager().getFactory(MotionController.class);
        mcFactory.add("Platform", BuilderMotionControllerPlatform::new);
        platformMotionControllerRegistered = true;
        LOGGER.atInfo().log("Registered Platform motion controller with NPC plugin");
    }
}
