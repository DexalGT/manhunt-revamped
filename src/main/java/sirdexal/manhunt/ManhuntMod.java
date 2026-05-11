package sirdexal.manhunt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

public class ManhuntMod implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    private void runFunction(ServerCommandSource source, String command) {
        try {
            source.getServer().getCommandManager().getDispatcher().execute(command, source.withSilent());
        } catch (CommandSyntaxException ignored) {}
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("manhunt")
            .then(CommandManager.literal("shuffle")
                .executes(context -> {
                    runFunction(context.getSource(), "function manhunt:shuffle");
                    return 1;
                })
                .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        int count = IntegerArgumentType.getInteger(context, "count");
                        runFunction(context.getSource(), "function manhunt:shuffle_with_count {count:" + count + "}");
                        return 1;
                    })
                )
            )
            .then(CommandManager.literal("swap")
                .executes(context -> {
                    runFunction(context.getSource(), "function manhunt:swap");
                    return 1;
                })
            )
            .then(CommandManager.literal("start")
                .executes(context -> {
                    runFunction(context.getSource(), "function manhunt:start");
                    return 1;
                })
            )
            .then(CommandManager.literal("stop")
                .executes(context -> {
                    runFunction(context.getSource(), "function manhunt:stop");
                    return 1;
                })
            )
            .then(CommandManager.literal("reset_history")
                .executes(context -> {
                    runFunction(context.getSource(), "function manhunt:reset_history");
                    return 1;
                })
            )
        );
    }
}
