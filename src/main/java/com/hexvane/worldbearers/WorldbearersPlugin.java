package com.hexvane.worldbearers;

import com.hexvane.worldbearers.commands.SpawnGiantCommand;
import com.hexvane.worldbearers.giant.GiantDefinitions;
import com.hexvane.worldbearers.giant.GiantManager;
import com.hexvane.worldbearers.systems.GiantBodyPartUpdateSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class WorldbearersPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private GiantManager giantManager;

    public WorldbearersPlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Worldbearers v%s initialized", this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        // Initialize giant system
        giantManager = new GiantManager();
        giantManager.registerDefinition(GiantDefinitions.createTestGiant());

        // Register commands
        this.getCommandRegistry().registerCommand(
                new WorldbearersCommand(this.getName(), this.getManifest().getVersion().toString()));
        this.getCommandRegistry().registerCommand(
                new SpawnGiantCommand(giantManager));

        // Register tick system for updating body part collision entity positions
        GiantBodyPartUpdateSystem bodyPartUpdateSystem = new GiantBodyPartUpdateSystem(giantManager);
        this.getEntityStoreRegistry().registerSystem(bodyPartUpdateSystem);
        LOGGER.atInfo().log("Registered giant body part update system");

        LOGGER.atInfo().log("Worldbearers setup complete - %d giant definitions registered",
                1); // hardcoded for spike
    }

    public GiantManager getGiantManager() {
        return giantManager;
    }
}
