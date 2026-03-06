package com.hexvane.worldbearers.commands;

import com.hexvane.worldbearers.giant.GiantManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/** Toggle feet AABB debug draw. Usage: /platformfeet */
public class PlatformFeetCommand extends AbstractPlayerCommand {

    private final GiantManager giantManager;

    public PlatformFeetCommand(GiantManager giantManager) {
        super("platformfeet", "Toggle debug draw of player feet AABB (cyan).");
        this.setPermissionGroup(GameMode.Creative);
        this.giantManager = giantManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        boolean now = !giantManager.isDebugFeet();
        giantManager.setDebugFeet(now);
        ctx.sendMessage(Message.raw("Platform feet debug: " + (now ? "ON" : "OFF")));
    }
}
