package sirdexal.manhunt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.CommandFunctionManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class ManhuntMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("manhunt-revamped");

    // Loaded once at startup from <server-root>/manhunt-reset-token.txt.
    // If the file is missing the wipe_trigger command still works but logs a warning.
    public static final String RESET_TOKEN = loadResetToken();

    private static String loadResetToken() {
        Path tokenFile = FabricLoader.getInstance().getGameDir().resolve("manhunt-reset-token.txt");
        try {
            if (Files.exists(tokenFile)) {
                String token = Files.readString(tokenFile).strip();
                if (!token.isEmpty()) return token;
            }
        } catch (IOException e) {
            // Will warn after LOGGER is fully initialised — handled in onInitialize
        }
        return "CHANGE_THIS_DEFAULT_TOKEN";
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[Manhunt] ========================================");
        LOGGER.info("[Manhunt] Manhunt Revamped by SirDexal – loading");
        LOGGER.info("[Manhunt] ========================================");
        if ("CHANGE_THIS_DEFAULT_TOKEN".equals(RESET_TOKEN)) {
            LOGGER.warn("[Manhunt] manhunt-reset-token.txt not found — wipe trigger is using the insecure default token!");
        } else {
            LOGGER.info("[Manhunt] Reset token loaded from manhunt-reset-token.txt");
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
            LOGGER.info("[Manhunt] /manhunt commands registered (env={})", environment.name());
        });

        boolean[] initDone = {false};
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (initDone[0]) return;
            initDone[0] = true;
            ensureInit(server);
        });
    }

    private void ensureInit(MinecraftServer server) {
        LOGGER.info("[Manhunt] First tick – creating scoreboard objectives and teams...");
        ServerCommandSource src = server.getCommandSource();
        CommandDispatcher<ServerCommandSource> disp = server.getCommandManager().getDispatcher();

        for (String name : new String[]{
                "mh_enabled", "mh_ticks", "mh_end", "mh_p_left", "mh_runner_count",
                "mh_times_runner", "mh_swap_working", "mh_rng", "mh_rid", "mh_dst",
                "mh_min_dst", "mh_x_o", "mh_y_o", "mh_z_o", "mh_x_n", "mh_y_n", "mh_z_n",
                "mh_prev", "mh_display"}) {
            initCmd(disp, src, "scoreboard objectives add " + name + " dummy");
        }
        initCmd(disp, src, "scoreboard objectives add mh_deaths deathCount");

        initCmd(disp, src, "team add hunters");
        initCmd(disp, src, "team add runners");
        initCmd(disp, src, "team modify hunters color blue");
        initCmd(disp, src, "team modify hunters friendlyfire false");
        initCmd(disp, src, "team modify runners color red");
        initCmd(disp, src, "team modify runners friendlyfire false");

        initCmd(disp, src, "scoreboard players set $wanted_runners mh_runner_count 1");

        LOGGER.info("[Manhunt] Scoreboard objectives and teams ready");

        // Log every loaded manhunt:* function so we can verify the datapack contents in logs.
        CommandFunctionManager fnMgr = server.getCommandFunctionManager();
        String[] critical = {
            "manhunt:stop", "manhunt:start", "manhunt:resume", "manhunt:shuffle",
            "manhunt:swap", "manhunt:reset_history",
            "manhunt:internal/do_pause", "manhunt:internal/do_resume",
            "manhunt:internal/resume_go", "manhunt:internal/tick",
            "manhunt:internal/game_over"
        };
        LOGGER.info("[Manhunt] --- Function load check ---");
        for (String id : critical) {
            boolean present = fnMgr.getFunction(Identifier.of(id)).isPresent();
            if (present) {
                LOGGER.info("[Manhunt]   OK  {}", id);
            } else {
                LOGGER.error("[Manhunt]  MISSING  {} ← datapack may be incomplete!", id);
            }
        }
        LOGGER.info("[Manhunt] --- End function check ---");
    }

    private static void initCmd(CommandDispatcher<ServerCommandSource> disp, ServerCommandSource src, String cmd) {
        try {
            disp.execute(cmd, src);
        } catch (CommandSyntaxException e) {
            LOGGER.debug("[Manhunt] initCmd skipped (already exists?): {}", cmd);
        } catch (Exception e) {
            LOGGER.warn("[Manhunt] initCmd failed: {} → {}", cmd, e.getMessage());
        }
    }

    // Returns the current $state mh_enabled value (0=idle,1=lead,2=hunt,3=paused,4=resuming).
    // scoreboard players get returns the score as its Brigadier result integer.
    private static int getState(MinecraftServer server) {
        try {
            return server.getCommandManager().getDispatcher()
                    .execute("scoreboard players get $state mh_enabled", server.getCommandSource());
        } catch (Exception e) {
            return 0;
        }
    }

    // /tick requires permission level 3; datapack functions only get level 2.
    // Call it from Java using the server command source (level 4) instead.
    private static void runTick(MinecraftServer server, String sub) {
        try {
            server.getCommandManager().getDispatcher().execute("tick " + sub, server.getCommandSource());
            LOGGER.info("[Manhunt] tick {} OK", sub);
        } catch (CommandSyntaxException e) {
            LOGGER.warn("[Manhunt] tick {} failed: {}", sub, e.getMessage());
        }
    }

    private static boolean isOp(ServerCommandSource src) {
        Entity entity = src.getEntity();
        if (entity instanceof ServerPlayerEntity player) {
            boolean op = src.getServer().getPlayerManager()
                    .isOperator(new PlayerConfigEntry(player.getGameProfile()));
            LOGGER.debug("[Manhunt] Permission check for '{}': op={}", player.getName().getString(), op);
            return op;
        }
        return true;
    }

    // Execute a datapack function by its namespaced ID (e.g. "manhunt:shuffle").
    // Uses CommandFunctionManager directly — calling /function via the Brigadier
    // dispatcher was removed in 1.21.11 (throws UnsupportedOperationException).
    // Runs with the server's own command source (console-level permissions, @a
    // selects all online players, tick freeze and effect commands all work).
    private void runFunction(ServerCommandSource callerSource, String functionId) {
        LOGGER.info("[Manhunt] >>> Executing '{}'  caller='{}'", functionId, callerSource.getName());
        CommandFunctionManager manager = callerSource.getServer().getCommandFunctionManager();
        Optional<CommandFunction<ServerCommandSource>> fnOpt =
                manager.getFunction(Identifier.of(functionId));

        if (fnOpt.isEmpty()) {
            LOGGER.error("[Manhunt] <<< Function '{}' not found in datapack!", functionId);
            callerSource.sendError(Text.literal("[Manhunt] Function not found: " + functionId));
            return;
        }

        try {
            long t = System.currentTimeMillis();
            manager.execute(fnOpt.get(), callerSource.getServer().getCommandSource());
            LOGGER.info("[Manhunt] <<< '{}' OK  time={}ms", functionId, System.currentTimeMillis() - t);
        } catch (Exception e) {
            LOGGER.error("[Manhunt] <<< '{}' ERROR: {}", functionId, e.toString(), e);
            callerSource.sendError(Text.literal("[Manhunt] Error: " + e.getMessage()));
        }
    }

    // Macro functions cannot be executed via CommandFunctionManager without a
    // Procedure API that has no execute() method. Instead, Java sets the count
    // directly in the scoreboard via the Brigadier dispatcher (scoreboard
    // commands are not affected by the 1.21.11 function restriction), then
    // calls the non-macro do_shuffle function via the function manager.
    private void runShuffleWithCount(ServerCommandSource callerSource, int count) {
        LOGGER.info("[Manhunt] >>> shuffle count={}  caller='{}'", count, callerSource.getName());
        ServerCommandSource serverSrc = callerSource.getServer().getCommandSource();
        try {
            // Set $wanted_runners mh_runner_count = count via scoreboard command.
            // This is a plain scoreboard command, not /function, so dispatcher works.
            String scoreCmd = "scoreboard players set $wanted_runners mh_runner_count " + count;
            LOGGER.info("[Manhunt]     Scoreboard cmd: '{}'", scoreCmd);
            callerSource.getServer().getCommandManager().getDispatcher()
                    .execute(scoreCmd, serverSrc);
        } catch (CommandSyntaxException e) {
            LOGGER.error("[Manhunt] <<< scoreboard set failed: {}", e.getMessage());
            callerSource.sendError(Text.literal("[Manhunt] Could not set runner count: " + e.getMessage()));
            return;
        }
        // Now call the non-macro do_shuffle which reads mh_runner_count from scoreboard.
        runFunction(callerSource, "manhunt:internal/do_shuffle");
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        LOGGER.info("[Manhunt] Registering /manhunt command tree ...");

        dispatcher.register(CommandManager.literal("manhunt")
            .requires(ManhuntMod::isOp)

            .then(CommandManager.literal("shuffle")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt shuffle  ← '{}'", src.getName());
                    runFunction(src, "manhunt:shuffle");
                    return 1;
                })
                .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        ServerCommandSource src = context.getSource();
                        int count = IntegerArgumentType.getInteger(context, "count");
                        LOGGER.info("[Manhunt] /manhunt shuffle {}  ← '{}'", count, src.getName());
                        runShuffleWithCount(src, count);
                        return 1;
                    })
                )
            )

            .then(CommandManager.literal("swap")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt swap  ← '{}'", src.getName());
                    runFunction(src, "manhunt:swap");
                    return 1;
                })
            )

            .then(CommandManager.literal("start")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt start  ← '{}'", src.getName());
                    runFunction(src, "manhunt:start");
                    return 1;
                })
            )

            .then(CommandManager.literal("stop")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt stop  ← '{}'", src.getName());
                    runFunction(src, "manhunt:stop");
                    if (getState(src.getServer()) == 3) runTick(src.getServer(), "freeze");
                    return 1;
                })
            )

            .then(CommandManager.literal("reset_history")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt reset_history  ← '{}'", src.getName());
                    runFunction(src, "manhunt:reset_history");
                    return 1;
                })
            )

            .then(CommandManager.literal("resume")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt resume  ← '{}'", src.getName());
                    if (getState(src.getServer()) == 3) runTick(src.getServer(), "unfreeze");
                    runFunction(src, "manhunt:resume");
                    return 1;
                })
            )

            // Manual role assignment — lets an OP build teams without shuffle/swap.
            // /manhunt manual runner <player>  — make a specific player runner
            // /manhunt manual hunter <player>  — make a specific player hunter
            // /manhunt manual clear            — reset everyone to hunter, no runners
            .then(CommandManager.literal("manual")
                .then(CommandManager.literal("runner")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            ServerCommandSource src = context.getSource();
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                            String name = target.getName().getString();
                            ServerCommandSource srv = src.getServer().getCommandSource();
                            CommandDispatcher<ServerCommandSource> d = src.getServer().getCommandManager().getDispatcher();
                            LOGGER.info("[Manhunt] /manhunt manual runner {}  ← '{}'", name, src.getName());
                            initCmd(d, srv, "tag " + name + " remove hunter");
                            initCmd(d, srv, "tag " + name + " add runner");
                            initCmd(d, srv, "team join runners " + name);
                            initCmd(d, srv, "scoreboard players add " + name + " mh_times_runner 1");
                            Text msg = Text.literal("[Manhunt] " + name + " is now a runner.");
                            src.getServer().getPlayerManager().getPlayerList().forEach(p -> p.sendMessage(msg));
                            return 1;
                        })
                    )
                )
                .then(CommandManager.literal("hunter")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            ServerCommandSource src = context.getSource();
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                            String name = target.getName().getString();
                            ServerCommandSource srv = src.getServer().getCommandSource();
                            CommandDispatcher<ServerCommandSource> d = src.getServer().getCommandManager().getDispatcher();
                            LOGGER.info("[Manhunt] /manhunt manual hunter {}  ← '{}'", name, src.getName());
                            initCmd(d, srv, "tag " + name + " remove runner");
                            initCmd(d, srv, "tag " + name + " add hunter");
                            initCmd(d, srv, "team join hunters " + name);
                            Text msg = Text.literal("[Manhunt] " + name + " is now a hunter.");
                            src.getServer().getPlayerManager().getPlayerList().forEach(p -> p.sendMessage(msg));
                            return 1;
                        })
                    )
                )
                .then(CommandManager.literal("clear")
                    .executes(context -> {
                        ServerCommandSource src = context.getSource();
                        ServerCommandSource srv = src.getServer().getCommandSource();
                        CommandDispatcher<ServerCommandSource> d = src.getServer().getCommandManager().getDispatcher();
                        LOGGER.info("[Manhunt] /manhunt manual clear  ← '{}'", src.getName());
                        initCmd(d, srv, "tag @a remove runner");
                        initCmd(d, srv, "tag @a remove hunter");
                        initCmd(d, srv, "tag @a add hunter");
                        initCmd(d, srv, "team join hunters @a");
                        Text msg = Text.literal("[Manhunt] All roles cleared — everyone is hunter.");
                        src.getServer().getPlayerManager().getPlayerList().forEach(p -> p.sendMessage(msg));
                        return 1;
                    })
                )
            )

            // OP-only manual reset: broadcasts a warning to all players then emits
            // the secure trigger line that the external watcher detects to stop the
            // server, wipe worlds, and restart with a fresh map.
            .then(CommandManager.literal("reset")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt reset  ← '{}'", src.getName());
                    Text notice = Text.literal("[Manhunt] World reset incoming — server will restart shortly!");
                    src.getServer().getPlayerManager().getPlayerList()
                        .forEach(p -> p.sendMessage(notice));
                    LOGGER.info("[MANHUNT-RESET] TOKEN:{}", RESET_TOKEN);
                    return 1;
                })
            )
        );

        LOGGER.info("[Manhunt] /manhunt command tree ready");
    }
}
