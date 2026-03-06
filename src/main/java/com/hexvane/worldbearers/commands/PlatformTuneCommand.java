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

/** Set tunable feet AABB params. Usage: /platformtune &lt;param&gt; &lt;value&gt; or /platformtune reset */
public class PlatformTuneCommand extends AbstractPlayerCommand {

    private final GiantManager giantManager;

    public PlatformTuneCommand(GiantManager giantManager) {
        super("platformtune", "Set platform tunables: feetHalfWidth, feetHalfHeight, feetHalfDepth, feetYOffsetBelowCenter, or reset.");
        this.setPermissionGroup(GameMode.Creative);
        this.giantManager = giantManager;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String input = ctx.getInputString();
        int firstSpace = input.indexOf(' ');
        String rest = firstSpace < 0 ? "" : input.substring(firstSpace + 1).trim();
        if (rest.isEmpty()) {
            ctx.sendMessage(Message.raw("Usage: /platformtune <param> <value> or /platformtune reset. Params: feetHalfWidth, feetHalfHeight, feetHalfDepth, feetYOffsetBelowCenter, syncYNudge"));
            return;
        }
        String[] parts = rest.split(" ", 2);
        String param = parts[0].trim();
        if ("reset".equalsIgnoreCase(param)) {
            giantManager.resetTunables();
            ctx.sendMessage(Message.raw("Tunables reset to defaults."));
            return;
        }
        if (parts.length < 2) {
            ctx.sendMessage(Message.raw("Usage: /platformtune " + param + " <value>"));
            return;
        }
        double value;
        try {
            value = Double.parseDouble(parts[1].trim());
        } catch (NumberFormatException e) {
            ctx.sendMessage(Message.raw("Invalid number: " + parts[1]));
            return;
        }
        switch (param) {
            case "feetHalfWidth" -> {
                giantManager.setFeetHalfWidth(value);
                ctx.sendMessage(Message.raw("Set feetHalfWidth to " + value));
            }
            case "feetHalfHeight" -> {
                giantManager.setFeetHalfHeight(value);
                ctx.sendMessage(Message.raw("Set feetHalfHeight to " + value));
            }
            case "feetHalfDepth" -> {
                giantManager.setFeetHalfDepth(value);
                ctx.sendMessage(Message.raw("Set feetHalfDepth to " + value));
            }
            case "feetYOffsetBelowCenter" -> {
                giantManager.setFeetYOffsetBelowCenter(value);
                ctx.sendMessage(Message.raw("Set feetYOffsetBelowCenter to " + value));
            }
            case "syncYNudge" -> {
                giantManager.setSyncYNudge(value);
                ctx.sendMessage(Message.raw("Set syncYNudge to " + value));
            }
            default -> ctx.sendMessage(Message.raw("Unknown param: " + param + ". Use: feetHalfWidth, feetHalfHeight, feetHalfDepth, feetYOffsetBelowCenter, syncYNudge, or reset."));
        }
    }
}
