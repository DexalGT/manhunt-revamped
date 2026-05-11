package sirdexal.manhunt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // Returns true for console/RCON/command-blocks (no entity), or for players that
    // are in the server's op list. hasPermissionLevel() was removed from
    // ServerCommandSource in 1.21.x so we check via PlayerManager.isOperator().
    private static boolean isOp(ServerCommandSource src) {
        Entity entity = src.getEntity();
        if (entity instanceof ServerPlayerEntity player) {
            boolean op = src.getServer().getPlayerManager().isOperator(player.getGameProfile());
            LOGGER.debug("[Manhunt] Permission check for '{}': op={}", player.getName().getString(), op);
            return op;
        }
        // console / RCON / command blocks – always allowed
        return true;
    }

    // Execute a datapack function string via the Brigadier dispatcher.
    // Uses the server's own console source (always level-4) so /function never
    // rejects the call due to missing permission.
    private void runFunction(ServerCommandSource callerSource, String command) {
        LOGGER.info("[Manhunt] >>> Dispatching: '{}'", command);
        LOGGER.info("[Manhunt]     Caller: '{}'  Server: '{}'",
                callerSource.getName(),
                callerSource.getServer().getName());
        try {
            ServerCommandSource funcSource = callerSource.getServer().getCommandSource().withSilent();
            long before = System.currentTimeMillis();
            int result = callerSource.getServer().getCommandManager().getDispatcher().execute(command, funcSource);
            long elapsed = System.currentTimeMillis() - before;
            LOGGER.info("[Manhunt] <<< '{}' OK  result={} time={}ms", command, result, elapsed);
        } catch (CommandSyntaxException e) {
            LOGGER.error("[Manhunt] <<< '{}' FAILED: {}", command, e.getMessage());
            LOGGER.error("[Manhunt]     Cause type : {}", e.getType().toString());
            callerSource.sendError(Text.literal("[Manhunt] Function error: " + e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("[Manhunt] <<< '{}' UNEXPECTED EXCEPTION: {}", command, e.toString(), e);
            callerSource.sendError(Text.literal("[Manhunt] Unexpected error – check server logs"));
        }
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        LOGGER.info("[Manhunt] Registering /manhunt command tree ...");

        dispatcher.register(CommandManager.literal("manhunt")
            .requires(ManhuntMod::isOp)

            .then(CommandManager.literal("shuffle")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt shuffle  ← '{}'", src.getName());
                    runFunction(src, "function manhunt:shuffle");
                    return 1;
                })
                .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        ServerCommandSource src = context.getSource();
                        int count = IntegerArgumentType.getInteger(context, "count");
                        LOGGER.info("[Manhunt] /manhunt shuffle {}  ← '{}'", count, src.getName());
                        runFunction(src, "function manhunt:shuffle_with_count {count:" + count + "}");
                        return 1;
                    })
                )
            )

            .then(CommandManager.literal("swap")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt swap  ← '{}'", src.getName());
                    runFunction(src, "function manhunt:swap");
                    return 1;
                })
            )

            .then(CommandManager.literal("start")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt start  ← '{}'", src.getName());
                    runFunction(src, "function manhunt:start");
                    return 1;
                })
            )

            .then(CommandManager.literal("stop")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt stop  ← '{}'", src.getName());
                    runFunction(src, "function manhunt:stop");
                    return 1;
                })
            )

            .then(CommandManager.literal("reset_history")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt reset_history  ← '{}'", src.getName());
                    runFunction(src, "function manhunt:reset_history");
                    return 1;
                })
            )
        );

        LOGGER.info("[Manhunt] /manhunt command tree ready");
    }
}
