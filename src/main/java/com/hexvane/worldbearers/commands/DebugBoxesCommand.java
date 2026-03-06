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

/**
 * Toggles debug drawing of giant body part collision boxes.
 * Usage: /debugboxes
 */
public class DebugBoxesCommand extends AbstractPlayerCommand {

    private final GiantManager giantManager;

    public DebugBoxesCommand(GiantManager giantManager) {
        super("debugboxes", "Toggle debug display of body part collision boxes.");
        this.setPermissionGroup(GameMode.Creative);
        this.giantManager = giantManager;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        boolean now = !giantManager.isDebugBodyPartBoxes();
        giantManager.setDebugBodyPartBoxes(now);
        ctx.sendMessage(Message.raw("Body part collision box debug: " + (now ? "ON" : "OFF")));
    }
}
