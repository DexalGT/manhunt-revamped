package sirdexal.manhunt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
            manager.execute(fnOpt.get(), manager.getScheduledCommandSource());
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
                    runFunction(src, "manhunt:resume");
                    return 1;
                })
            )

            // Called by game_over.mcfunction at the end of every match.
            // Emits the unique trigger line that the external Node.js watcher detects
            // to wipe worlds and restart the server.
            // Restricted to non-player sources so it cannot be fired from in-game chat.
            .then(CommandManager.literal("wipe_trigger")
                .requires(src -> src.getEntity() == null)
                .executes(context -> {
                    LOGGER.info("[MANHUNT-RESET] TOKEN:{}", RESET_TOKEN);
                    return 1;
                })
            )
        );

        LOGGER.info("[Manhunt] /manhunt command tree ready");
    }
}
