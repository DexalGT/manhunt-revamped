package sirdexal.manhunt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("manhunt")
            // Require OP (level 2) - handled by the underlying /function command automatically
            
            // /manhunt shuffle
            .then(CommandManager.literal("shuffle")
                .executes(context -> {
                    context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource().withSilent(), "function manhunt:shuffle");
                    return 1;
                })
                // /manhunt shuffle <count>
                .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        int count = IntegerArgumentType.getInteger(context, "count");
                        context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource().withSilent(), "function manhunt:shuffle_with_count {count:" + count + "}");
                        return 1;
                    })
                )
            )
            
            // /manhunt swap
            .then(CommandManager.literal("swap")
                .executes(context -> {
                    context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource().withSilent(), "function manhunt:swap");
                    return 1;
                })
            )
            
            // /manhunt start
            .then(CommandManager.literal("start")
                .executes(context -> {
                    context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource().withSilent(), "function manhunt:start");
                    return 1;
                })
            )
            
            // /manhunt stop
            .then(CommandManager.literal("stop")
                .executes(context -> {
                    context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource().withSilent(), "function manhunt:stop");
                    return 1;
                })
            )
            
            // /manhunt reset_history
            .then(CommandManager.literal("reset_history")
                .executes(context -> {
                    context.getSource().getServer().getCommandManager().executeWithPrefix(context.getSource().withSilent(), "function manhunt:reset_history");
                    return 1;
                })
            )
        );
    }
}
