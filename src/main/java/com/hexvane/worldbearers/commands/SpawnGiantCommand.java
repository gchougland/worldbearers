package com.hexvane.worldbearers.commands;

import com.hexvane.worldbearers.giant.GiantInstance;
import com.hexvane.worldbearers.giant.GiantManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
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
 * Debug command to spawn a test giant at the player's location.
 * Extends AbstractPlayerCommand so execute() is called on the WorldThread,
 * which is required for Store.getComponent() and NPCPlugin.spawnNPC().
 * Usage: /spawngiant
 */
public class SpawnGiantCommand extends AbstractPlayerCommand {

    private final GiantManager giantManager;

    public SpawnGiantCommand(GiantManager giantManager) {
        super("spawngiant", "Spawns a test giant near your location.");
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
        // AbstractPlayerCommand guarantees we're on the WorldThread here.
        // store.getComponent() and spawnNPC() are both safe to call.
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(Message.raw("Could not determine player position."));
            return;
        }

        Vector3d playerPos = transform.getPosition();

        // Spawn 10 blocks ahead and to the side so it doesn't land on the player
        Vector3d spawnPos = new Vector3d(playerPos.x + 10, playerPos.y, playerPos.z + 10);
        Vector3f rotation = new Vector3f();

        String defId = "giant_test";

        GiantInstance instance = giantManager.spawnGiant(defId, store, spawnPos, rotation);
        if (instance != null) {
            ctx.sendMessage(Message.raw("Spawned giant '" + defId + "' with "
                    + instance.getBodyPartEntities().size() + " body part entities."));
        } else {
            ctx.sendMessage(Message.raw("Failed to spawn giant. Check server logs."));
        }
    }
}
