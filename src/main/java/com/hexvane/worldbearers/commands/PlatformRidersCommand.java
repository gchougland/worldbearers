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

/** Toggle rider-part link lines (yellow). Usage: /platformriders */
public class PlatformRidersCommand extends AbstractPlayerCommand {

    private final GiantManager giantManager;

    public PlatformRidersCommand(GiantManager giantManager) {
        super("platformriders", "Toggle debug draw of rider-part link lines (yellow).");
        this.setPermissionGroup(GameMode.Creative);
        this.giantManager = giantManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        boolean now = !giantManager.isDebugRiders();
        giantManager.setDebugRiders(now);
        ctx.sendMessage(Message.raw("Platform riders debug: " + (now ? "ON" : "OFF")));
    }
}
