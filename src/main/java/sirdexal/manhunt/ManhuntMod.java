package sirdexal.manhunt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ManhuntMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("manhunt-revamped");

    @Override
    public void onInitialize() {
        LOGGER.info("[Manhunt] Manhunt Revamped by SirDexal – initializing");
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
            LOGGER.info("[Manhunt] Commands registered");
        });
    }

    // Runs a datapack function using the server console source (always level-4 so
    // /function permission is never a problem). Errors are logged AND sent back to
    // whoever ran the /manhunt command so they know something went wrong.
    private void runFunction(ServerCommandSource callerSource, String command) {
        LOGGER.info("[Manhunt] Executing '{}'", command);
        try {
            ServerCommandSource funcSource = callerSource.getServer().getCommandSource().withSilent();
            int result = callerSource.getServer().getCommandManager().getDispatcher().execute(command, funcSource);
            LOGGER.info("[Manhunt] '{}' succeeded (result={})", command, result);
        } catch (CommandSyntaxException e) {
            LOGGER.error("[Manhunt] '{}' failed: {}", command, e.getMessage());
            callerSource.sendError(Text.literal("[Manhunt] Function failed: " + e.getMessage()));
        }
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("manhunt")
            .requires(src -> src.hasPermissionLevel(2))

            .then(CommandManager.literal("shuffle")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt shuffle called by '{}'", src.getName());
                    runFunction(src, "function manhunt:shuffle");
                    return 1;
                })
                .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        ServerCommandSource src = context.getSource();
                        int count = IntegerArgumentType.getInteger(context, "count");
                        LOGGER.info("[Manhunt] /manhunt shuffle {} called by '{}'", count, src.getName());
                        runFunction(src, "function manhunt:shuffle_with_count {count:" + count + "}");
                        return 1;
                    })
                )
            )

            .then(CommandManager.literal("swap")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt swap called by '{}'", src.getName());
                    runFunction(src, "function manhunt:swap");
                    return 1;
                })
            )

            .then(CommandManager.literal("start")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt start called by '{}'", src.getName());
                    runFunction(src, "function manhunt:start");
                    return 1;
                })
            )

            .then(CommandManager.literal("stop")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt stop called by '{}'", src.getName());
                    runFunction(src, "function manhunt:stop");
                    return 1;
                })
            )

            .then(CommandManager.literal("reset_history")
                .executes(context -> {
                    ServerCommandSource src = context.getSource();
                    LOGGER.info("[Manhunt] /manhunt reset_history called by '{}'", src.getName());
                    runFunction(src, "function manhunt:reset_history");
                    return 1;
                })
            )
        );
    }
}
