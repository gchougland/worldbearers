package com.hexvane.worldbearers;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class WorldbearersPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public WorldbearersPlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Worldbearers v%s initialized", this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        this.getCommandRegistry().registerCommand(new WorldbearersCommand(this.getName(), this.getManifest().getVersion().toString()));
        LOGGER.atInfo().log("Worldbearers setup complete");
    }
}
