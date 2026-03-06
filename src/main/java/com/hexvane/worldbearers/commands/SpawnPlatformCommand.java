package com.hexvane.worldbearers.commands;

import com.hexvane.worldbearers.giant.GiantManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Spawns a single standalone platform (no giant) for testing. Platform is placed
 * a few blocks above the player. Replaces any previously spawned standalone platform.
 * Usage: /spawnplatform
 */
public class SpawnPlatformCommand extends AbstractPlayerCommand {

    private final GiantManager giantManager;

    public SpawnPlatformCommand(GiantManager giantManager) {
        super("spawnplatform", "Spawn a single platform for testing (no giant).");
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
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(Message.raw("Could not determine player position."));
            return;
        }
        Vector3d playerPos = transform.getPosition();
        Vector3d spawnPos = new Vector3d(playerPos.x, playerPos.y + 3, playerPos.z);
        Ref<EntityStore> platformRef = giantManager.spawnStandalonePlatform(store, spawnPos);
        if (platformRef != null) {
            ctx.sendMessage(Message.raw("Spawned standalone platform 3 blocks above you. Use /debugboxes to see it."));
        } else {
            ctx.sendMessage(Message.raw("Failed to spawn platform. Check server logs."));
        }
    }
}
