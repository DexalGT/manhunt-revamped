package sirdexal.manhunt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ManhuntMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("manhunt-revamped");

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
            // warned in onInitialize
        }
        return "CHANGE_THIS_DEFAULT_TOKEN";
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[Manhunt] ========================================");
        LOGGER.info("[Manhunt] Manhunt Revamped by SirDexal – loading (mod build)");
        LOGGER.info("[Manhunt] ========================================");
        if ("CHANGE_THIS_DEFAULT_TOKEN".equals(RESET_TOKEN)) {
            LOGGER.warn("[Manhunt] manhunt-reset-token.txt not found — wipe trigger is using the insecure default token!");
        } else {
            LOGGER.info("[Manhunt] Reset token loaded from manhunt-reset-token.txt");
        }

        DATA = ManhuntData.load();
        GAME = new GameManager(DATA);
        LOGGER.info("[Manhunt] Loaded persistent team data: {} players, wantedRunners={}",
                DATA.players.size(), DATA.wantedRunners);

        // Attach the server + (re)create teams when it starts.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            GAME.attachServer(server);
            LOGGER.info("[Manhunt] Server started — teams ensured, roles re-applied.");
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DATA.save());

        // Re-apply persisted role/team on (re)join — survives a world reset.
        ServerPlayerEvents.JOIN.register(player -> GAME.onPlayerJoin(player));

        // Eliminated runners respawn as spectators.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> GAME.onPlayerRespawn(newPlayer));

        // Runner death detection.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                GAME.onRunnerDeath(player);
            }
        });

        // The whole game loop.
        ServerTickEvents.END_SERVER_TICK.register(server -> GAME.onEndTick(server));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
            LOGGER.info("[Manhunt] /manhunt commands registered (env={})", environment.name());
        });
    }

    private static boolean isOp(ServerCommandSource src) {
        Entity entity = src.getEntity();
        if (entity instanceof ServerPlayerEntity player) {
            return src.getServer().getPlayerManager()
                    .isOperator(new PlayerConfigEntry(player.getGameProfile()));
        }
        return true; // console / command blocks
    }

    private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("manhunt")
            .requires(ManhuntMod::isOp)

            .then(CommandManager.literal("shuffle")
                .executes(ctx -> { GAME.shuffle(DATA.wantedRunners); return 1; })
                .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                    .executes(ctx -> { GAME.shuffle(IntegerArgumentType.getInteger(ctx, "count")); return 1; })))

            .then(CommandManager.literal("swap")
                .executes(ctx -> { GAME.swap(); return 1; }))

            .then(CommandManager.literal("start")
                .executes(ctx -> { GAME.start(); return 1; }))

            .then(CommandManager.literal("stop")
                .executes(ctx -> { GAME.stop(); return 1; }))

            .then(CommandManager.literal("resume")
                .executes(ctx -> { GAME.resume(); return 1; }))

            .then(CommandManager.literal("reset_history")
                .executes(ctx -> { GAME.resetHistory(); return 1; }))

            .then(CommandManager.literal("abort")
                .executes(ctx -> {
                    GAME.forceStopGame();
                    ctx.getSource().sendFeedback(() -> Text.literal("[Manhunt] Game aborted."), true);
                    return 1;
                }))

            .then(CommandManager.literal("manual")
                .then(CommandManager.literal("runner")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                            GAME.setRoleManual(EntityArgumentType.getPlayer(ctx, "player"), Role.RUNNER);
                            return 1;
                        })))
                .then(CommandManager.literal("hunter")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                            GAME.setRoleManual(EntityArgumentType.getPlayer(ctx, "player"), Role.HUNTER);
                            return 1;
                        })))
                .then(CommandManager.literal("clear")
                    .executes(ctx -> { GAME.manualClear(); return 1; })))

            // OP-only world reset: emits the secure token the external watcher detects.
            // Team data persists across the reset (stored outside the world folders).
            .then(CommandManager.literal("reset")
                .executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    LOGGER.info("[Manhunt] /manhunt reset  ← '{}'", src.getName());
                    DATA.save();
                    Text notice = Text.literal("[Manhunt] World reset incoming — server will restart shortly! (Teams are kept.)");
                    src.getServer().getPlayerManager().getPlayerList().forEach(p -> p.sendMessage(notice, false));
                    LOGGER.info("[MANHUNT-RESET] TOKEN:{}", RESET_TOKEN);
                    return 1;
                }))
        );
    }
}
