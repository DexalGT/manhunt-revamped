package sirdexal.manhunt;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Native game logic for Manhunt — a full Java port of the old datapack functions.
 *
 * <p>States mirror the old {@code $state mh_enabled} fake-player:
 * 0 = idle, 1 = lead phase, 2 = active hunt, 3 = paused, 4 = resuming.</p>
 */
public class GameManager {
    public static final String COMPASS_TAG = "Manhunt_tracker";
    private static final int LEAD_SECONDS = 45;

    public enum State { IDLE, LEAD, HUNT, PAUSED, RESUMING }

    private final ManhuntData data;
    private MinecraftServer server;

    private State state = State.IDLE;
    private State prevState = State.IDLE; // state to return to after a resume

    private int tickCounter = 0;     // counts server ticks for the 20-tick second gate
    private int leadTimer = 0;       // seconds left in the lead phase
    private int resumeSecondsLeft = 0;
    private int resetCountdown = -1; // seconds left before a world reset fires; -1 = inactive

    // Runner death tracking for the current hunt.
    private final Set<UUID> deadRunners = new HashSet<>();

    // Ender dragon win detection.
    private boolean dragonSeenAlive = false;
    private int endGrace = 0;

    // Per-hunter last tracked runner (so we only announce target changes once).
    private final Map<UUID, UUID> lastTracked = new HashMap<>();

    public GameManager(ManhuntData data) {
        this.data = data;
    }

    public void attachServer(MinecraftServer server) {
        this.server = server;
        ensureTeams();
        reapplyAllTeams();
        ManhuntLog.info("GameManager attached to server. State={}, online={}", state, online().size());
    }

    public State getState() {
        return state;
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Teams (vanilla scoreboard teams, just for nametag colour / friendly fire)
    // ───────────────────────────────────────────────────────────────────────────
    public void ensureTeams() {
        if (server == null) return;
        ServerScoreboard sb = server.getScoreboard();
        Team hunters = sb.getTeam("hunters");
        if (hunters == null) hunters = sb.addTeam("hunters");
        hunters.setColor(Formatting.BLUE);
        hunters.setFriendlyFireAllowed(false);

        Team runners = sb.getTeam("runners");
        if (runners == null) runners = sb.addTeam("runners");
        runners.setColor(Formatting.RED);
        runners.setFriendlyFireAllowed(false);
    }

    private void applyTeam(ServerPlayerEntity player) {
        if (server == null) return;
        ensureTeams();
        ServerScoreboard sb = server.getScoreboard();
        String name = player.getNameForScoreboard();
        Role role = data.getRole(player.getUuid());

        Team current = sb.getScoreHolderTeam(name);
        if (current != null) sb.removeScoreHolderFromTeam(name, current);

        if (role == Role.RUNNER) {
            sb.addScoreHolderToTeam(name, sb.getTeam("runners"));
        } else if (role == Role.HUNTER) {
            sb.addScoreHolderToTeam(name, sb.getTeam("hunters"));
        }
        ManhuntLog.debug("applyTeam: {} -> {}", name, role);
    }

    public void reapplyAllTeams() {
        if (server == null) return;
        for (ServerPlayerEntity p : online()) applyTeam(p);
    }

    /** Re-apply a (re)joining player's persisted role so resets don't lose teams. */
    public void onPlayerJoin(ServerPlayerEntity player) {
        applyTeam(player);
        // If a hunt is live and this player is a hunter, hand them a tracking compass.
        if (state == State.HUNT && data.getRole(player.getUuid()) == Role.HUNTER) {
            giveCompass(player);
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Role assignment — shuffle (random) and swap (rotate by history)
    // ───────────────────────────────────────────────────────────────────────────
    public void shuffle(int count) {
        if (server == null) return;
        data.wantedRunners = Math.max(1, count);

        List<ServerPlayerEntity> players = new ArrayList<>(online());
        if (players.isEmpty()) {
            broadcast(Text.literal("[Manhunt] No players online to shuffle.").formatted(Formatting.GRAY));
            return;
        }
        // Shuffle deterministically-from-server-rng free: use server world random.
        java.util.Random rng = new java.util.Random(server.getOverworld().getTime() * 31 + players.size());
        java.util.Collections.shuffle(players, rng);

        int wanted = Math.min(data.wantedRunners, players.size());
        List<ServerPlayerEntity> chosen = players.subList(0, wanted);
        ManhuntLog.info("SHUFFLE: wanted={} from {} online -> runners {}", wanted, players.size(),
                chosen.stream().map(p -> p.getName().getString()).toList());
        assignRoles(chosen, players, true);
        announceRoles("Roles assigned");
        data.save();
    }

    public void swap() {
        if (server == null) return;
        List<ServerPlayerEntity> players = new ArrayList<>(online());
        if (players.isEmpty()) {
            broadcast(Text.literal("[Manhunt] No players online to swap.").formatted(Formatting.GRAY));
            return;
        }

        // Sort by rotation fairness: fewest times-as-runner first, but push the
        // CURRENT runners to the back so the role rotates away from them.
        players.sort(Comparator
                .comparingInt((ServerPlayerEntity p) -> data.getRole(p.getUuid()) == Role.RUNNER ? 1 : 0)
                .thenComparingInt(p -> data.getTimesRunner(p.getUuid())));

        int wanted = Math.min(Math.max(1, data.wantedRunners), players.size());
        List<ServerPlayerEntity> chosen = players.subList(0, wanted);
        ManhuntLog.info("SWAP: fairness order (name:timesRunner)=[{}] -> new runners {}",
                players.stream().map(p -> p.getName().getString() + ":" + data.getTimesRunner(p.getUuid()))
                        .reduce((a, b) -> a + ", " + b).orElse(""),
                chosen.stream().map(p -> p.getName().getString()).toList());
        assignRoles(chosen, players, true);
        announceRoles("Roles swapped");
        data.save();
    }

    /** Manual single-player role assignment. */
    public void setRoleManual(ServerPlayerEntity player, Role role) {
        UUID uuid = player.getUuid();
        String name = player.getName().getString();
        ManhuntLog.info("MANUAL: {} {} -> {}", name, data.getRole(uuid), role);
        if (role == Role.RUNNER && data.getRole(uuid) != Role.RUNNER) {
            data.incrementTimesRunner(uuid, name);
        }
        data.setRole(uuid, name, role);
        applyTeam(player);
        data.save();
        if (role == Role.RUNNER) {
            broadcast(Text.literal("[Manhunt] " + name + " is now a runner.").formatted(Formatting.RED));
        } else if (role == Role.HUNTER) {
            broadcast(Text.literal("[Manhunt] " + name + " is now a hunter.").formatted(Formatting.BLUE));
        }
    }

    public void manualClear() {
        for (ServerPlayerEntity p : online()) {
            data.setRole(p.getUuid(), p.getName().getString(), Role.HUNTER);
            applyTeam(p);
        }
        data.save();
        broadcast(Text.literal("[Manhunt] All roles cleared — everyone is hunter.").formatted(Formatting.GOLD));
    }

    public void resetHistory() {
        data.resetHistory();
        data.save();
        broadcast(Text.literal("[Manhunt] Runner history reset.").formatted(Formatting.GOLD));
    }

    private void assignRoles(List<ServerPlayerEntity> runners, List<ServerPlayerEntity> all, boolean countHistory) {
        Set<UUID> runnerSet = new HashSet<>();
        for (ServerPlayerEntity r : runners) runnerSet.add(r.getUuid());

        for (ServerPlayerEntity p : all) {
            UUID uuid = p.getUuid();
            String name = p.getName().getString();
            Role old = data.getRole(uuid);
            if (runnerSet.contains(uuid)) {
                if (countHistory && old != Role.RUNNER) {
                    data.incrementTimesRunner(uuid, name);
                }
                data.setRole(uuid, name, Role.RUNNER);
            } else {
                data.setRole(uuid, name, Role.HUNTER);
            }
            ManhuntLog.info("  role {}: {} -> {} (timesRunner={})", name, old,
                    data.getRole(uuid), data.getTimesRunner(uuid));
            applyTeam(p);
        }
    }

    private void announceRoles(String header) {
        List<String> runners = new ArrayList<>();
        List<String> hunters = new ArrayList<>();
        for (ServerPlayerEntity p : online()) {
            Role r = data.getRole(p.getUuid());
            if (r == Role.RUNNER) runners.add(p.getName().getString());
            else if (r == Role.HUNTER) hunters.add(p.getName().getString());
        }
        Text msg = Text.literal("[Manhunt] " + header + " — ").formatted(Formatting.GOLD)
                .append(Text.literal("Runners: ").formatted(Formatting.RED, Formatting.BOLD))
                .append(Text.literal(String.join(", ", runners) + "  ").formatted(Formatting.WHITE))
                .append(Text.literal("Hunters: ").formatted(Formatting.BLUE, Formatting.BOLD))
                .append(Text.literal(String.join(", ", hunters)).formatted(Formatting.WHITE));
        broadcast(msg);
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Start / stop / resume
    // ───────────────────────────────────────────────────────────────────────────
    public boolean start() {
        if (server == null) return false;

        boolean hasRunner = false;
        for (ServerPlayerEntity p : online()) {
            if (data.getRole(p.getUuid()) == Role.RUNNER) { hasRunner = true; break; }
        }
        if (!hasRunner) {
            ManhuntLog.warn("START aborted: no runners assigned among {} online players.", online().size());
            broadcast(Text.literal("[Manhunt] ERROR: No runners assigned. Run /manhunt shuffle first.")
                    .formatted(Formatting.RED));
            return false;
        }
        ManhuntLog.info("START: {} -> LEAD. {} players, lead={}s.", state, online().size(), LEAD_SECONDS);

        deadRunners.clear();
        lastTracked.clear();
        dragonSeenAlive = false;
        endGrace = 0;

        for (ServerPlayerEntity p : online()) {
            p.changeGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.setHealth(p.getMaxHealth());
            p.getHungerManager().setFoodLevel(20);
            p.getHungerManager().setSaturationLevel(5f);
            clearFreezeEffects(p);
        }

        leadTimer = LEAD_SECONDS;
        tickCounter = 0;
        state = State.LEAD;

        for (ServerPlayerEntity p : online()) {
            showTitle(p, Text.literal("MANHUNT").formatted(Formatting.DARK_RED, Formatting.BOLD),
                    Text.literal("Hunters blinded — " + LEAD_SECONDS + " second head start").formatted(Formatting.YELLOW),
                    0, 80, 20);
        }
        broadcast(Text.literal("[Manhunt] ").formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal("Game starting! Hunters get a " + LEAD_SECONDS + " second head start.")
                        .formatted(Formatting.WHITE)));
        announceRoles("Round started");
        return true;
    }

    public void stop() {
        if (state == State.PAUSED || state == State.RESUMING) {
            broadcast(Text.literal("[Manhunt] Already paused. Use /manhunt resume to continue.").formatted(Formatting.GRAY));
            return;
        }
        if (state == State.IDLE) {
            broadcast(Text.literal("[Manhunt] No active game to stop.").formatted(Formatting.GRAY));
            return;
        }
        prevState = state;
        state = State.PAUSED;
        ManhuntLog.info("STOP: pausing (was {}). Freezing {} players.", prevState, online().size());
        for (ServerPlayerEntity p : online()) {
            p.setVelocity(0, 0, 0);
            applyFreezeEffects(p);
        }
        tickFreeze(true);
        for (ServerPlayerEntity p : online()) {
            showTitle(p, Text.literal("PAUSED").formatted(Formatting.GRAY, Formatting.BOLD), null, 0, 40, 10);
        }
        broadcast(Text.literal("[Manhunt] Game paused. Use /manhunt resume to continue.").formatted(Formatting.GRAY));
    }

    public void resume() {
        if (state == State.RESUMING) {
            broadcast(Text.literal("[Manhunt] Already resuming!").formatted(Formatting.YELLOW));
            return;
        }
        if (state != State.PAUSED) {
            broadcast(Text.literal("[Manhunt] Game is not paused.").formatted(Formatting.GRAY));
            return;
        }
        ManhuntLog.info("RESUME: starting 5s countdown (will return to {}).", prevState);
        state = State.RESUMING;
        resumeSecondsLeft = 5;
        tickCounter = 0;
        for (ServerPlayerEntity p : online()) {
            showTitle(p, Text.literal("5").formatted(Formatting.YELLOW, Formatting.BOLD), null, 0, 22, 3);
        }
        broadcast(Text.literal("[Manhunt] Resuming in 5 seconds...").formatted(Formatting.YELLOW));
    }

    private void resumeGo() {
        state = prevState == State.IDLE ? State.HUNT : prevState;
        ManhuntLog.info("RESUME complete -> {}.", state);
        tickFreeze(false);
        for (ServerPlayerEntity p : online()) {
            clearFreezeEffects(p);
            showTitle(p, Text.literal("RESUMED!").formatted(Formatting.GREEN, Formatting.BOLD), null, 0, 50, 20);
        }
        broadcast(Text.literal("[Manhunt] Game resumed!").formatted(Formatting.GREEN));
    }

    public void forceStopGame() {
        ManhuntLog.info("ABORT: forcing game to IDLE (was {}).", state);
        state = State.IDLE;
        tickFreeze(false);
        for (ServerPlayerEntity p : online()) clearFreezeEffects(p);
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Tick loop (replaces the #minecraft:tick / second.mcfunction logic)
    // ───────────────────────────────────────────────────────────────────────────
    public void onEndTick(MinecraftServer server) {
        if (this.server == null) attachServer(server);

        if (state == State.PAUSED || state == State.RESUMING) {
            lockFrozenPlayers();
        }

        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            if (resetCountdown > 0) resetCountdownSecond();
            secondTick();
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  World-reset countdown (so a misclick on /manhunt reset can be cancelled)
    // ───────────────────────────────────────────────────────────────────────────
    public void startResetCountdown() {
        if (resetCountdown > 0) {
            broadcast(Text.literal("[Manhunt] A reset is already counting down (" + resetCountdown
                    + "s left). Use /manhunt reset cancel to stop it.").formatted(Formatting.YELLOW));
            return;
        }
        resetCountdown = 10;
        ManhuntLog.warn("RESET countdown STARTED ({}s). Use /manhunt reset cancel to abort.", resetCountdown);
        broadcastResetWarning(resetCountdown);
    }

    public void cancelResetCountdown() {
        if (resetCountdown <= 0) {
            broadcast(Text.literal("[Manhunt] No world reset is in progress.").formatted(Formatting.GRAY));
            return;
        }
        ManhuntLog.info("RESET countdown CANCELLED (was at {}s).", resetCountdown);
        resetCountdown = -1;
        broadcastTitle(Text.literal("RESET CANCELLED").formatted(Formatting.GREEN, Formatting.BOLD), null, 0, 40, 10);
        broadcast(Text.literal("[Manhunt] World reset cancelled.").formatted(Formatting.GREEN));
    }

    private void resetCountdownSecond() {
        resetCountdown--;
        if (resetCountdown <= 0) {
            resetCountdown = -1;
            triggerReset();
        } else {
            broadcastResetWarning(resetCountdown);
        }
    }

    private void broadcastResetWarning(int seconds) {
        broadcastTitle(
                Text.literal("RESET IN " + seconds).formatted(Formatting.RED, Formatting.BOLD),
                Text.literal("/manhunt reset cancel to stop").formatted(Formatting.YELLOW),
                0, 25, 5);
        broadcast(Text.literal("[Manhunt] World reset in " + seconds + "s — /manhunt reset cancel to stop.")
                .formatted(Formatting.RED));
    }

    private void triggerReset() {
        data.save();
        broadcastTitle(Text.literal("RESETTING").formatted(Formatting.DARK_RED, Formatting.BOLD), null, 0, 60, 20);
        broadcast(Text.literal("[Manhunt] World reset incoming — server will restart shortly! (Teams are kept.)")
                .formatted(Formatting.RED, Formatting.BOLD));
        ManhuntLog.info("World-reset countdown finished — emitting trigger token for the watcher.");
        // The external watcher matches this EXACT log line (no other prefix) to run the
        // wipe/restart, so emit it straight through SLF4J without the [Manhunt] prefix.
        ManhuntLog.slf4j().info("[MANHUNT-RESET] TOKEN:{}", ManhuntMod.RESET_TOKEN);
    }

    private void secondTick() {
        switch (state) {
            case LEAD -> leadSecond();
            case HUNT -> huntSecond();
            case RESUMING -> resumeSecond();
            default -> {}
        }
    }

    private void leadSecond() {
        for (ServerPlayerEntity p : online()) {
            if (data.getRole(p.getUuid()) == Role.HUNTER) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 255, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 255, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 255, true, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 255, true, false, false));
            }
        }

        leadTimer--;
        if (leadTimer == 30) broadcastTitle(Text.literal("30 seconds").formatted(Formatting.YELLOW, Formatting.BOLD),
                Text.literal("Hunters still blinded").formatted(Formatting.WHITE), 0, 60, 20);
        else if (leadTimer == 15) broadcastTitle(Text.literal("15 seconds").formatted(Formatting.RED, Formatting.BOLD),
                Text.literal("Get ready hunters!").formatted(Formatting.WHITE), 0, 60, 20);
        else if (leadTimer >= 1 && leadTimer <= 5)
            broadcastTitle(Text.literal(String.valueOf(leadTimer)).formatted(Formatting.RED, Formatting.BOLD), null, 0, 22, 3);

        if (leadTimer == 30 || leadTimer == 15 || (leadTimer >= 0 && leadTimer <= 5))
            ManhuntLog.debug("LEAD countdown: {}s left", leadTimer);
        if (leadTimer <= 0) beginHunt();
    }

    private void beginHunt() {
        state = State.HUNT;
        ManhuntLog.info("BEGIN HUNT: lead phase over. runners={} hunters={}", totalRunnerCount(), onlineHunterCount());
        for (ServerPlayerEntity p : online()) {
            if (data.getRole(p.getUuid()) == Role.HUNTER) {
                p.removeStatusEffect(StatusEffects.BLINDNESS);
                p.removeStatusEffect(StatusEffects.SLOWNESS);
                p.removeStatusEffect(StatusEffects.MINING_FATIGUE);
                p.removeStatusEffect(StatusEffects.WEAKNESS);
                giveCompass(p);
            }
            showTitle(p, Text.literal("GO!").formatted(Formatting.GREEN, Formatting.BOLD), null, 0, 50, 20);
        }
        broadcast(Text.literal("The hunt has begun!").formatted(Formatting.RED, Formatting.BOLD));
    }

    private void huntSecond() {
        // Update every hunter's existing tracking compass in place (held one
        // included — it lives in the main inventory list). We do NOT hand out a
        // new compass here; that happens once at hunt start / on join, so a
        // hunter who threw theirs away just goes without rather than getting
        // spammed a fresh one every second.
        for (ServerPlayerEntity hunter : online()) {
            if (data.getRole(hunter.getUuid()) == Role.HUNTER && !hunter.isSpectator()) {
                try {
                    updateHunterCompass(hunter);
                } catch (Exception e) {
                    ManhuntLog.error("Compass update failed for hunter " + hunter.getName().getString(), e);
                }
            }
        }

        ManhuntLog.debug("HUNT tick: aliveRunners={} totalRunners={} hunters={} endGrace={}",
                aliveRunnerCount(), totalRunnerCount(), onlineHunterCount(), endGrace);

        // Win check: all runners dead.
        if (aliveRunnerCount() == 0 && totalRunnerCount() > 0) {
            huntersWin();
            return;
        }
        // Win check: no hunters online.
        if (onlineHunterCount() == 0) {
            runnersWin();
            return;
        }
        // Win check: ender dragon defeated (only while a runner is in the End).
        checkDragon();
    }

    private void resumeSecond() {
        resumeSecondsLeft--;
        if (resumeSecondsLeft <= 0) {
            resumeGo();
        } else {
            broadcastTitle(Text.literal(String.valueOf(resumeSecondsLeft)).formatted(Formatting.YELLOW, Formatting.BOLD), null, 0, 22, 3);
        }
    }

    private void checkDragon() {
        if (server == null) return;
        ServerWorld end = server.getWorld(World.END);
        if (end == null) return;

        boolean runnerInEnd = false;
        for (ServerPlayerEntity p : online()) {
            if (data.getRole(p.getUuid()) == Role.RUNNER
                    && p.getEntityWorld().getRegistryKey().equals(World.END)) {
                runnerInEnd = true;
                break;
            }
        }
        if (!runnerInEnd) { endGrace = 0; return; }

        boolean dragonAlive = false;
        for (Entity e : end.iterateEntities()) {
            if (e instanceof EnderDragonEntity dragon && dragon.isAlive()) {
                dragonAlive = true;
                break;
            }
        }
        if (dragonAlive) {
            dragonSeenAlive = true;
            endGrace = 0;
        } else if (dragonSeenAlive) {
            endGrace++;
            ManhuntLog.info("Dragon gone from End for {}s (grace 3) — runner is in the End.", endGrace);
            if (endGrace >= 3) {
                ManhuntLog.info("Ender Dragon confirmed defeated.");
                runnersWin();
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Win / death handling
    // ───────────────────────────────────────────────────────────────────────────
    public void onRunnerDeath(ServerPlayerEntity player) {
        if (state != State.HUNT) return;
        if (data.getRole(player.getUuid()) != Role.RUNNER) return;
        if (deadRunners.contains(player.getUuid())) return;

        deadRunners.add(player.getUuid());
        ManhuntLog.info("RUNNER ELIMINATED: {} (alive runners now {}/{})", player.getName().getString(),
                aliveRunnerCount(), totalRunnerCount());
        broadcast(Text.literal("[Manhunt] " + player.getName().getString() + " has been eliminated!")
                .formatted(Formatting.RED));

        if (aliveRunnerCount() == 0) huntersWin();
    }

    /** Called on respawn — eliminated runners come back as spectators. */
    public void onPlayerRespawn(ServerPlayerEntity player) {
        if (deadRunners.contains(player.getUuid())) {
            ManhuntLog.info("Eliminated runner {} respawned -> SPECTATOR.", player.getName().getString());
            player.changeGameMode(GameMode.SPECTATOR);
        }
    }

    private void huntersWin() {
        ManhuntLog.info("GAME OVER: HUNTERS WIN.");
        broadcastTitle(Text.literal("HUNTERS WIN!").formatted(Formatting.BLUE, Formatting.BOLD), null, 0, 80, 20);
        broadcast(Text.literal("The hunters have won!").formatted(Formatting.BLUE, Formatting.BOLD));
        gameOver();
    }

    private void runnersWin() {
        ManhuntLog.info("GAME OVER: RUNNERS WIN.");
        broadcastTitle(Text.literal("RUNNERS WIN!").formatted(Formatting.RED, Formatting.BOLD), null, 0, 80, 20);
        broadcast(Text.literal("The runners have won!").formatted(Formatting.RED, Formatting.BOLD));
        gameOver();
    }

    private void gameOver() {
        state = State.IDLE;
        for (ServerPlayerEntity p : online()) removeCompass(p);
        endGrace = 0;
        dragonSeenAlive = false;
        ManhuntLog.info("State reset to IDLE; tracking compasses removed.");
    }

    private int aliveRunnerCount() {
        int c = 0;
        for (ServerPlayerEntity p : online()) {
            if (data.getRole(p.getUuid()) == Role.RUNNER && !deadRunners.contains(p.getUuid())) c++;
        }
        return c;
    }

    private int totalRunnerCount() {
        int c = 0;
        for (ServerPlayerEntity p : online()) if (data.getRole(p.getUuid()) == Role.RUNNER) c++;
        return c;
    }

    private int onlineHunterCount() {
        int c = 0;
        for (ServerPlayerEntity p : online()) if (data.getRole(p.getUuid()) == Role.HUNTER) c++;
        return c;
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Freeze (real player lock — fixes the broken /tick freeze pause)
    // ───────────────────────────────────────────────────────────────────────────
    private void lockFrozenPlayers() {
        // Slowness amplifier 255 reduces the movement-speed attribute to zero, so
        // players can't walk at all (the old datapack relied on the same trick).
        // Zeroing velocity each tick also kills knockback, sliding and fall speed,
        // so this is a real freeze — no fragile per-tick teleport required.
        for (ServerPlayerEntity p : online()) {
            p.setVelocity(0, 0, 0);
            p.fallDistance = 0;
            if (!p.hasStatusEffect(StatusEffects.SLOWNESS)) applyFreezeEffects(p);
        }
    }

    private void applyFreezeEffects(ServerPlayerEntity p) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 1000000, 255, true, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 1000000, 255, true, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 1000000, 1, true, false, false));
    }

    private void clearFreezeEffects(ServerPlayerEntity p) {
        p.removeStatusEffect(StatusEffects.SLOWNESS);
        p.removeStatusEffect(StatusEffects.MINING_FATIGUE);
        p.removeStatusEffect(StatusEffects.BLINDNESS);
    }

    /** Also freezes mobs/blocks/time via /tick freeze as a bonus (player lock is the real fix). */
    private void tickFreeze(boolean freeze) {
        if (server == null) return;
        try {
            server.getCommandManager().getDispatcher()
                    .execute("tick " + (freeze ? "freeze" : "unfreeze"), server.getCommandSource());
            ManhuntLog.debug("Issued /tick {} (bonus mob freeze)", freeze ? "freeze" : "unfreeze");
        } catch (Exception e) {
            ManhuntLog.debug("tick {} skipped: {}", freeze ? "freeze" : "unfreeze", e.toString());
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Compass tracking (native lodestone targeting, no datapack macros)
    // ───────────────────────────────────────────────────────────────────────────
    private void giveCompass(ServerPlayerEntity hunter) {
        if (hasCompass(hunter)) return;
        ItemStack compass = new ItemStack(Items.COMPASS);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(COMPASS_TAG, true);
        compass.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        compass.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Tracking Compass").formatted(Formatting.GOLD));
        hunter.getInventory().insertStack(compass);
    }

    private boolean hasCompass(ServerPlayerEntity hunter) {
        for (ItemStack s : hunter.getInventory().getMainStacks()) {
            if (isTracker(s)) return true;
        }
        return isTracker(hunter.getOffHandStack());
    }

    private boolean isTracker(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.COMPASS) return false;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        return cd != null && cd.copyNbt().contains(COMPASS_TAG);
    }

    private void removeCompass(ServerPlayerEntity player) {
        for (ItemStack s : player.getInventory().getMainStacks()) {
            if (isTracker(s)) s.setCount(0);
        }
        if (isTracker(player.getOffHandStack())) player.getOffHandStack().setCount(0);
    }

    private void updateHunterCompass(ServerPlayerEntity hunter) {
        RegistryKey<World> hunterDim = hunter.getEntityWorld().getRegistryKey();

        // Pick the trackable runner closest to the hunter (after cross-dimension mapping).
        ServerPlayerEntity bestRunner = null;
        GlobalPos bestTarget = null;
        double bestDist = Double.MAX_VALUE;

        for (ServerPlayerEntity runner : online()) {
            if (data.getRole(runner.getUuid()) != Role.RUNNER) continue;
            if (deadRunners.contains(runner.getUuid())) continue;
            GlobalPos target = mapTarget(hunterDim, runner);
            if (target == null) continue;
            double dx = target.pos().getX() - hunter.getX();
            double dy = target.pos().getY() - hunter.getY();
            double dz = target.pos().getZ() - hunter.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                bestRunner = runner;
                bestTarget = target;
            }
        }

        if (bestRunner == null || bestTarget == null) {
            setCompassName(hunter, Text.literal("Runner in another dimension").formatted(Formatting.GRAY));
            return;
        }

        // Announce target change once.
        UUID prev = lastTracked.get(hunter.getUuid());
        if (prev == null || !prev.equals(bestRunner.getUuid())) {
            lastTracked.put(hunter.getUuid(), bestRunner.getUuid());
            ManhuntLog.info("COMPASS: {} now tracking {} (target dim={}, pos={})",
                    hunter.getName().getString(), bestRunner.getName().getString(),
                    bestTarget.dimension().getValue(), bestTarget.pos());
            hunter.sendMessage(Text.literal("Now tracking: ").formatted(Formatting.GOLD, Formatting.BOLD)
                    .append(Text.literal(bestRunner.getName().getString()).formatted(Formatting.GOLD)), false);
        }

        LodestoneTrackerComponent tracker = new LodestoneTrackerComponent(Optional.of(bestTarget), false);
        Text name = Text.literal("Tracking " + bestRunner.getName().getString()).formatted(Formatting.GOLD);
        for (ItemStack s : hunter.getInventory().getMainStacks()) {
            if (isTracker(s)) {
                s.set(DataComponentTypes.LODESTONE_TRACKER, tracker);
                s.set(DataComponentTypes.CUSTOM_NAME, name);
            }
        }
        ItemStack off = hunter.getOffHandStack();
        if (isTracker(off)) {
            off.set(DataComponentTypes.LODESTONE_TRACKER, tracker);
            off.set(DataComponentTypes.CUSTOM_NAME, name);
        }
    }

    /** Map a runner's position into the hunter's dimension (overworld/nether scale). */
    private GlobalPos mapTarget(RegistryKey<World> hunterDim, ServerPlayerEntity runner) {
        RegistryKey<World> runnerDim = runner.getEntityWorld().getRegistryKey();
        BlockPos rp = runner.getBlockPos();

        if (hunterDim.equals(runnerDim)) {
            return GlobalPos.create(hunterDim, rp);
        }
        if (hunterDim.equals(World.OVERWORLD) && runnerDim.equals(World.NETHER)) {
            return GlobalPos.create(World.OVERWORLD, new BlockPos(rp.getX() * 8, rp.getY(), rp.getZ() * 8));
        }
        if (hunterDim.equals(World.NETHER) && runnerDim.equals(World.OVERWORLD)) {
            return GlobalPos.create(World.NETHER, new BlockPos(rp.getX() / 8, rp.getY(), rp.getZ() / 8));
        }
        // End ↔ anything else can't be sensibly mapped.
        return null;
    }

    private void setCompassName(ServerPlayerEntity hunter, Text name) {
        for (ItemStack s : hunter.getInventory().getMainStacks()) {
            if (isTracker(s)) s.set(DataComponentTypes.CUSTOM_NAME, name);
        }
        if (isTracker(hunter.getOffHandStack())) hunter.getOffHandStack().set(DataComponentTypes.CUSTOM_NAME, name);
    }

    // ───────────────────────────────────────────────────────────────────────────
    //  Messaging helpers
    // ───────────────────────────────────────────────────────────────────────────
    private List<ServerPlayerEntity> online() {
        if (server == null) return List.of();
        return server.getPlayerManager().getPlayerList();
    }

    private void broadcast(Text msg) {
        for (ServerPlayerEntity p : online()) p.sendMessage(msg, false);
        ManhuntLog.info("BROADCAST: {}", msg.getString());
    }

    private void broadcastTitle(Text title, Text subtitle, int in, int stay, int out) {
        for (ServerPlayerEntity p : online()) showTitle(p, title, subtitle, in, stay, out);
    }

    private void showTitle(ServerPlayerEntity p, Text title, Text subtitle, int in, int stay, int out) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(in, stay, out));
        if (subtitle != null) p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        p.networkHandler.sendPacket(new TitleS2CPacket(title));
    }
}
