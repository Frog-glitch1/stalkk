package com.stalker;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Testing commands (op only): /stalker summon | stage <0-3> | reset */
public final class StalkerCommands {
    private StalkerCommands() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("stalker")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("summon").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            boolean ok = StalkerManager.forceSpawn(p);
                            ctx.getSource().sendFeedback(() -> Text.literal(ok
                                    ? "Something is watching you."
                                    : "No valid spot found nearby. Try again somewhere darker."), false);
                            return ok ? 1 : 0;
                        }))
                        .then(CommandManager.literal("stage")
                                .then(CommandManager.argument("stage", IntegerArgumentType.integer(0, 3))
                                        .executes(ctx -> {
                                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                            int stage = IntegerArgumentType.getInteger(ctx, "stage");
                                            StalkerManager.setStage(p, stage);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Stalker stage set to " + stage), false);
                                            return stage;
                                        })))
                        .then(CommandManager.literal("reset").executes(ctx -> {
                            StalkerManager.reset(ctx.getSource().getPlayerOrThrow());
                            ctx.getSource().sendFeedback(() -> Text.literal("Stalker reset."), false);
                            return 1;
                        }))));
    }
}
