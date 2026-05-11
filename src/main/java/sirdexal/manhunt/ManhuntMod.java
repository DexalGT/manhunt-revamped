package sirdexal.manhunt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.Entity;
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

import java.util.Optional;

public class ManhuntMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("manhunt-revamped");

    @Override
    public void onInitialize() {
        LOGGER.info("[Manhunt] ========================================");
        LOGGER.info("[Manhunt] Manhunt Revamped by SirDexal – loading");
        LOGGER.info("[Manhunt] ========================================");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
            LOGGER.info("[Manhunt] /manhunt commands registered (env={})", environment.name());
        });
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
        );

        LOGGER.info("[Manhunt] /manhunt command tree ready");
    }
}
