package com.hexvane.worldbearers;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * Base command for the Worldbearers mod.
 * Usage: /worldbearers
 */
public class WorldbearersCommand extends CommandBase {
    private final String pluginName;
    private final String pluginVersion;

    public WorldbearersCommand(String pluginName, String pluginVersion) {
        super("worldbearers", "Worldbearers mod info and commands.");
        this.setPermissionGroup(GameMode.Adventure);
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("Worldbearers v" + pluginVersion + " - Colossal creatures await."));
    }
}
