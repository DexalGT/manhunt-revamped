package sirdexal.manhunt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ManhuntMod implements ModInitializer {
    public static final String MOD_ID = "sirdexal_manhunt";

    // Loaded once at startup from <server-root>/manhunt-reset-token.txt.
    public static final String RESET_TOKEN = loadResetToken();

    // Persistent team data + the live game brain.
    public static ManhuntData DATA;
    public static GameManager GAME;

    private static String loadResetToken() {
        Path tokenFile = FabricLoader.getInstance().getGameDir().resolve("manhunt-reset-token.txt");
        try {
            if (Files.exists(tokenFile)) {
                String token = Files.readString(tokenFile).strip();
                if (!token.isEmpty()) return token;
            }
        } catch (IOException e) {
            // warned in onInitialize once logging is up
        }
        return "CHANGE_THIS_DEFAULT_TOKEN";
    }

    @Override
    public void onInitialize() {
        Path manhuntDir = FabricLoader.getInstance().getGameDir().resolve("manhunt");
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        ManhuntLog.init(manhuntDir, version);

        ManhuntLog.info("========================================");
        ManhuntLog.info("Manhunt Revamped v{} by SirDexal — initializing (native mod)", version);
        ManhuntLog.info("Game directory: {}", FabricLoader.getInstance().getGameDir().toAbsolutePath());
        ManhuntLog.info("========================================");

        if ("CHANGE_THIS_DEFAULT_TOKEN".equals(RESET_TOKEN)) {
            ManhuntLog.warn("manhunt-reset-token.txt not found — /manhunt reset is using the INSECURE default token!");
        } else {
            ManhuntLog.info("Reset token loaded ({} chars) from manhunt-reset-token.txt", RESET_TOKEN.length());
        }

        try {
            DATA = ManhuntData.load();
            GAME = new GameManager(DATA);
            ManhuntLog.info("Persistent data loaded: {} known player(s), wantedRunners={}",
                    DATA.players.size(), DATA.wantedRunners);
        } catch (Exception e) {
            ManhuntLog.error("FATAL: failed to load persistent data during init", e);
            DATA = new ManhuntData();
            GAME = new GameManager(DATA);
        }

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                GAME.attachServer(server);
                ManhuntLog.info("Server started — scoreboard teams ensured, roles re-applied to online players.");
            } catch (Exception e) {
                ManhuntLog.error("Error during SERVER_STARTED setup", e);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ManhuntLog.info("Server stopping — saving persistent data.");
            try {
                DATA.save();
            } catch (Exception e) {
                ManhuntLog.error("Error saving data on shutdown", e);
            }
            ManhuntLog.close();
        });

        ServerPlayerEvents.JOIN.register(player -> {
            try {
                ManhuntLog.info("JOIN  {} (uuid={}) — role={} timesRunner={}",
                        player.getName().getString(), player.getUuid(),
                        DATA.getRole(player.getUuid()), DATA.getTimesRunner(player.getUuid()));
                GAME.onPlayerJoin(player);
            } catch (Exception e) {
                ManhuntLog.error("Error in JOIN handler for " + player.getName().getString(), e);
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            try {
                GAME.onPlayerRespawn(newPlayer);
            } catch (Exception e) {
                ManhuntLog.error("Error in AFTER_RESPAWN handler", e);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            try {
                if (entity instanceof ServerPlayerEntity player) {
                    ManhuntLog.debug("DEATH {} via {}", player.getName().getString(),
                            damageSource != null ? damageSource.getName() : "?");
                    GAME.onRunnerDeath(player);
                }
            } catch (Exception e) {
                ManhuntLog.error("Error in AFTER_DEATH handler", e);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                GAME.onEndTick(server);
            } catch (Exception e) {
                ManhuntLog.error("Unhandled exception in tick loop", e);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
            ManhuntLog.info("/manhunt command tree registered (env={})", environment.name());
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            if (player instanceof ServerPlayerEntity serverPlayer) {
                ItemStack stack = serverPlayer.getStackInHand(hand);
                if (GameManager.isReviveAnchor(stack)) {
                    if (GAME.getState() != GameManager.State.HUNT) {
                        serverPlayer.sendMessage(Text.literal("You can only use this during an active hunt!").formatted(Formatting.RED), false);
                        return ActionResult.CONSUME;
                    }
                    if (DATA.getRole(serverPlayer.getUuid()) != Role.RUNNER) {
                        serverPlayer.sendMessage(Text.literal("Only runners can use the Revive Anchor!").formatted(Formatting.RED), false);
                        return ActionResult.CONSUME;
                    }
                    boolean opened = GAME.openReviveScreen(serverPlayer, hand);
                    if (opened) {
                        return ActionResult.SUCCESS;
                    } else {
                        return ActionResult.CONSUME;
                    }
                }
            }
            return ActionResult.PASS;
        });

        ManhuntLog.info("Initialization complete.");
    }

    private static boolean isOp(ServerCommandSource src) {
        Entity entity = src.getEntity();
        if (entity instanceof ServerPlayerEntity player) {
            boolean op = src.getServer().getPlayerManager()
                    .isOperator(new PlayerConfigEntry(player.getGameProfile()));
            if (!op) ManhuntLog.debug("Permission denied for {} (not OP)", player.getName().getString());
            return op;
        }
        return true; // console / command blocks
    }

    @FunctionalInterface
    private interface CmdAction {
        void run(ServerCommandSource src) throws Exception;
    }

    /** Runs a command body with uniform logging + error reporting to console, file and player. */
    private int run(CommandContext<ServerCommandSource> ctx, String label, CmdAction action) {
        ServerCommandSource src = ctx.getSource();
        String caller = src.getName();
        ManhuntLog.info("CMD  /manhunt {}   (by {})", label, caller);
        try {
            action.run(src);
            return 1;
        } catch (Exception e) {
            ManhuntLog.error("Command '/manhunt " + label + "' FAILED (caller=" + caller + ")", e);
            src.sendError(Text.literal("[Manhunt] Command error: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "  (see server log / manhunt/logs.txt)"));
            return 0;
        }
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("manhunt")
            // Game Lifecycle / Control Branch
            .then(CommandManager.literal("game")
                .requires(ManhuntMod::isOp)
                .then(CommandManager.literal("start")
                    .executes(ctx -> run(ctx, "game start", src -> GAME.start())))
                .then(CommandManager.literal("stop")
                    .executes(ctx -> run(ctx, "game stop", src -> GAME.stop())))
                .then(CommandManager.literal("resume")
                    .executes(ctx -> run(ctx, "game resume", src -> GAME.resume())))
                .then(CommandManager.literal("abort")
                    .executes(ctx -> run(ctx, "game abort", src -> {
                        GAME.forceStopGame();
                        src.sendFeedback(() -> Text.literal("[Manhunt] Game aborted."), true);
                    })))
                .then(CommandManager.literal("confirm")
                    .executes(ctx -> run(ctx, "game confirm", src -> GAME.confirmRunnerWin(src))))
            )

            // Roles & Team Setup Branch
            .then(CommandManager.literal("roles")
                .requires(ManhuntMod::isOp)
                .then(CommandManager.literal("shuffle")
                    .executes(ctx -> run(ctx, "roles shuffle", src -> GAME.shuffle(DATA.wantedRunners)))
                    .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            int count = IntegerArgumentType.getInteger(ctx, "count");
                            return run(ctx, "roles shuffle " + count, src -> GAME.shuffle(count));
                        })))
                .then(CommandManager.literal("swap")
                    .executes(ctx -> run(ctx, "roles swap", src -> GAME.swap())))
                .then(CommandManager.literal("reset_history")
                    .executes(ctx -> run(ctx, "roles reset_history", src -> GAME.resetHistory())))
                .then(CommandManager.literal("manual")
                    .then(CommandManager.literal("runner")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(ctx -> {
                                ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                return run(ctx, "roles manual runner " + target.getName().getString(),
                                        src -> GAME.setRoleManual(target, Role.RUNNER));
                            })))
                    .then(CommandManager.literal("hunter")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                            .executes(ctx -> {
                                ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                return run(ctx, "roles manual hunter " + target.getName().getString(),
                                        src -> GAME.setRoleManual(target, Role.HUNTER));
                            })))
                    .then(CommandManager.literal("clear")
                        .executes(ctx -> run(ctx, "roles manual clear", src -> GAME.manualClear()))))
            )

            // World Control Branch
            .then(CommandManager.literal("world")
                .requires(ManhuntMod::isOp)
                .then(CommandManager.literal("reset")
                    .executes(ctx -> run(ctx, "world reset", src -> GAME.startResetCountdown()))
                    .then(CommandManager.literal("cancel")
                        .executes(ctx -> run(ctx, "world reset cancel", src -> GAME.cancelResetCountdown()))))
            )

            // Compass Branch (accessible to non-OPs, internally verified)
            .then(CommandManager.literal("compass")
                .executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    return run(ctx, "compass", s -> {
                        if (s.getEntity() instanceof ServerPlayerEntity player) {
                            GAME.giveCompassCommand(player, player);
                        } else {
                            s.sendError(Text.literal("This command must be run by a player."));
                        }
                    });
                })
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .requires(ManhuntMod::isOp)
                    .executes(ctx -> {
                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                        return run(ctx, "compass " + target.getName().getString(),
                                src -> GAME.giveCompassCommand(src.getEntity() instanceof ServerPlayerEntity p ? p : null, target));
                    })))
        );

        // Also register the standalone "/compass" command
        dispatcher.register(CommandManager.literal("compass")
            .executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                return run(ctx, "compass", s -> {
                    if (s.getEntity() instanceof ServerPlayerEntity player) {
                        GAME.giveCompassCommand(player, player);
                    } else {
                        s.sendError(Text.literal("This command must be run by a player."));
                    }
                });
            })
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .requires(ManhuntMod::isOp)
                .executes(ctx -> {
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                    return run(ctx, "compass " + target.getName().getString(),
                            src -> GAME.giveCompassCommand(src.getEntity() instanceof ServerPlayerEntity p ? p : null, target));
                }))
        );
    }
}
