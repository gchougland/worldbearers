package com.hexvane.worldbearers.commands;

import com.hexvane.worldbearers.giant.GiantInstance;
import com.hexvane.worldbearers.giant.GiantManager;
import com.hexvane.worldbearers.platform.CollisionMath;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;

/** One-shot dump: giants, body parts, riderToPart, and executing player position/feet/on-part. Usage: /platformdump */
public class PlatformDumpCommand extends AbstractPlayerCommand {

    private final GiantManager giantManager;

    public PlatformDumpCommand(GiantManager giantManager) {
        super("platformdump", "Dump platform state (giants, riders, your position/feet/on-part).");
        this.setPermissionGroup(GameMode.Creative);
        this.giantManager = giantManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        StringBuilder out = new StringBuilder();
        int n = 0;
        for (GiantInstance g : giantManager.getActiveGiants()) {
            if (!g.isActive()) continue;
            n++;
        }
        out.append("--- Platform dump ---\n");
        out.append("Active giants: ").append(n).append("\n");
        for (GiantInstance g : giantManager.getActiveGiants()) {
            if (!g.isActive()) continue;
            out.append("  Giant ").append(g.getDefinition().getId()).append(" body parts: ");
            for (Map.Entry<String, Ref<EntityStore>> e : g.getBodyPartEntities().entrySet()) {
                out.append(e.getKey()).append("(ref ").append(e.getValue().getIndex()).append(") ");
            }
            out.append("\n");
        }
        Map<Ref<EntityStore>, Ref<EntityStore>> riderToPart = giantManager.getRiderToPartMap();
        out.append("Rider -> part: ").append(riderToPart.size()).append(" entries\n");
        for (Map.Entry<Ref<EntityStore>, Ref<EntityStore>> e : riderToPart.entrySet()) {
            String partName = giantManager.getBodyPartName(e.getValue());
            out.append("  ref ").append(e.getKey().getIndex()).append(" -> ").append(partName != null ? partName : "?").append("\n");
        }
        // Executing player
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        if (t != null) {
            Vector3d pos = t.getPosition();
            Box feet = CollisionMath.feetAABB(pos,
                    giantManager.getFeetHalfWidth(), giantManager.getFeetHalfHeight(),
                    giantManager.getFeetHalfDepth(), giantManager.getFeetYOffsetBelowCenter());
            out.append("You: pos ").append(String.format("%.2f, %.2f, %.2f", pos.x, pos.y, pos.z));
            out.append(" | feet AABB min ").append(String.format("%.2f, %.2f, %.2f", feet.min.x, feet.min.y, feet.min.z));
            out.append(" max ").append(String.format("%.2f, %.2f, %.2f", feet.max.x, feet.max.y, feet.max.z));
            Ref<EntityStore> onPart = riderToPart.get(ref);
            if (onPart != null) {
                String partName = giantManager.getBodyPartName(onPart);
                out.append(" | on part: ").append(partName != null ? partName : "?");
            } else {
                out.append(" | on part: no");
            }
            out.append("\n");
        }
        ctx.sendMessage(Message.raw(out.toString()));
    }
}
