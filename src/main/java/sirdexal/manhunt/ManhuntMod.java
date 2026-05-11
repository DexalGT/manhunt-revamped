package sirdexal.manhunt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.CommandFunctionManager;
import net.minecraft.server.function.MacroException;
import net.minecraft.server.function.Procedure;
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

    // isOperator() was changed to take PlayerConfigEntry in 1.21.11.
    // We construct one from the GameProfile since that constructor still exists.
    private static boolean isOp(ServerCommandSource src) {
        Entity entity = src.getEntity();
        if (entity instanceof ServerPlayerEntity player) {
            boolean op = src.getServer().getPlayerManager()
                    .isOperator(new PlayerConfigEntry(player.getGameProfile()));
            LOGGER.debug("[Manhunt] Permission check for '{}': op={}", player.getName().getString(), op);
            return op;
        }
        return true; // console / RCON / command blocks always allowed
    }

    // Execute a plain (non-macro) datapack function.
    private void runFunction(ServerCommandSource callerSource, String functionId) {
        LOGGER.info("[Manhunt] >>> Executing '{}'  caller='{}'", functionId, callerSource.getName());
        CommandFunctionManager manager = callerSource.getServer().getCommandFunctionManager();
        Optional<CommandFunction<ServerCommandSource>> fnOpt = manager.getFunction(Identifier.of(functionId));

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

    // Execute a macro datapack function with NbtCompound arguments.
    // withMacroReplaced() substitutes the macro variables and returns a Procedure
    // which is then passed back to the function manager for execution.
    private void runMacroFunction(ServerCommandSource callerSource, String functionId, NbtCompound args) {
        LOGGER.info("[Manhunt] >>> Executing macro '{}'  caller='{}'  args={}",
                functionId, callerSource.getName(), args);
        CommandFunctionManager manager = callerSource.getServer().getCommandFunctionManager();
        Optional<CommandFunction<ServerCommandSource>> fnOpt = manager.getFunction(Identifier.of(functionId));

        if (fnOpt.isEmpty()) {
            LOGGER.error("[Manhunt] <<< Macro function '{}' not found in datapack!", functionId);
            callerSource.sendError(Text.literal("[Manhunt] Function not found: " + functionId));
            return;
        }

        try {
            long t = System.currentTimeMillis();
            Procedure<ServerCommandSource> procedure =
                    fnOpt.get().withMacroReplaced(args, manager.getDispatcher());
            manager.execute(procedure, manager.getScheduledCommandSource());
            LOGGER.info("[Manhunt] <<< macro '{}' OK  time={}ms", functionId, System.currentTimeMillis() - t);
        } catch (MacroException e) {
            LOGGER.error("[Manhunt] <<< '{}' MACRO ERROR: {}", functionId, e.getMessage());
            callerSource.sendError(Text.literal("[Manhunt] Macro error: " + e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("[Manhunt] <<< '{}' ERROR: {}", functionId, e.toString(), e);
            callerSource.sendError(Text.literal("[Manhunt] Error: " + e.getMessage()));
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
                    runFunction(src, "manhunt:shuffle");
                    return 1;
                })
                .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        ServerCommandSource src = context.getSource();
                        int count = IntegerArgumentType.getInteger(context, "count");
                        LOGGER.info("[Manhunt] /manhunt shuffle {}  ← '{}'", count, src.getName());
                        NbtCompound args = new NbtCompound();
                        args.putInt("count", count);
                        runMacroFunction(src, "manhunt:shuffle_with_count", args);
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
