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

/** Toggle all platform debug (boxes, feet, riders, log). Usage: /platformdebug */
public class PlatformDebugCommand extends AbstractPlayerCommand {

    private final GiantManager giantManager;

    public PlatformDebugCommand(GiantManager giantManager) {
        super("platformdebug", "Toggle all platform debug (boxes, feet, riders, log).");
        this.setPermissionGroup(GameMode.Creative);
        this.giantManager = giantManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        boolean now = !giantManager.isDebugBodyPartBoxes();
        giantManager.setDebugBodyPartBoxes(now);
        giantManager.setDebugFeet(now);
        giantManager.setDebugRiders(now);
        giantManager.setDebugLog(now);
        ctx.sendMessage(Message.raw("Platform debug (all): " + (now ? "ON" : "OFF")));
    }
}
