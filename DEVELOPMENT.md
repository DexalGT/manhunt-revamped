# Manhunt Revamped — Developer Guide

Everything you need to understand, build, and extend the mod. This is the internal companion
to the user-facing [README](README.md).

---

## 1. What this is

A **server-side Fabric mod** for Minecraft **1.21.11** that implements the Manhunt minigame
entirely in Java. It started life as a datapack; all `.mcfunction` logic has been ported to
Java. There are **no mixins** — every per-tick behaviour is driven from a single
`ServerTickEvents.END_SERVER_TICK` handler.

### Toolchain

| | |
|---|---|
| Minecraft | `1.21.11` |
| Yarn mappings | `1.21.11+build.5` |
| Fabric Loader | `0.19.2` |
| Fabric API | `0.141.3+1.21.11` |
| Fabric Loom | `1.16-SNAPSHOT` |
| Gradle | `9.4.0` (wrapper) |
| Java | **21** |

### Runtime dependencies

- **Fabric Loader** and **Fabric API** (declared in `fabric.mod.json`). The mod uses these
  Fabric API entrypoints/events: `CommandRegistrationCallback`, `ServerTickEvents`,
  `ServerPlayerEvents` (`JOIN` / `AFTER_RESPAWN`), `ServerLivingEntityEvents` (`AFTER_DEATH`),
  and `ServerLifecycleEvents` (`SERVER_STARTED` / `SERVER_STOPPING`).
- Nothing else. No other library mods.

---

## 2. Project layout

```
manhunt_datapack/                 ← git repo root
├── .github/workflows/
│   ├── build.yml                 ← CI: builds the jar on every push to main
│   └── release.yml               ← CI: publishes a GitHub release
├── src/main/java/sirdexal/manhunt/
│   ├── ManhuntMod.java           ← ModInitializer: events + command tree
│   ├── GameManager.java          ← the entire game (state machine, compass, freeze, win)
│   ├── ManhuntData.java          ← Gson-backed persistent store
│   └── Role.java                 ← NONE / RUNNER / HUNTER enum
├── src/main/resources/           ← (none required; no mixins)
├── fabric.mod.json               ← mod metadata + dependencies + entrypoint
├── pack.mcmeta                   ← legacy datapack descriptor (harmless leftover)
├── build.gradle / gradle.properties / settings.gradle
└── gradle/wrapper/               ← Gradle wrapper (9.4.0)
```

---

## 3. Architecture

### 3.1 Class responsibilities

- **`ManhuntMod`** — the only `ModInitializer`. It loads the persistent data, creates the
  single `GameManager`, registers all Fabric events, and wires the `/manhunt` Brigadier
  command tree. It owns the OP check (`PlayerManager.isOperator` with a fresh
  `PlayerConfigEntry`) and the reset token loaded from `manhunt-reset-token.txt`.
- **`GameManager`** — the brain. Holds the live game state and implements every behaviour.
  All gameplay logic lives here.
- **`ManhuntData`** — a plain POJO serialized to JSON with Gson. The single source of truth
  for **who has which role** and **how many times each player has been a runner**.
- **`Role`** — `NONE`, `RUNNER`, `HUNTER`.

### 3.2 State machine

`GameManager.State` mirrors the old datapack `$state` fake-player:

```
IDLE ──/manhunt start──▶ LEAD ──(45s timer hits 0)──▶ HUNT ──(win)──▶ IDLE
                          │                             │
                    /manhunt stop                 /manhunt stop
                          ▼                             ▼
                       PAUSED ◀───────────────────── PAUSED
                          │
                   /manhunt resume
                          ▼
                      RESUMING ──(5s countdown)──▶ (back to LEAD or HUNT)
```

- **`LEAD`** — hunters get Blindness/Slowness/Mining-Fatigue/Weakness (amp 255) re-applied
  each second; the lead timer counts down with milestone titles (30s, 15s, 5-4-3-2-1).
- **`HUNT`** — compasses update, win conditions are checked each second.
- **`PAUSED`** — players are frozen (see §3.4).
- **`RESUMING`** — players stay frozen through a 5-second countdown, then return to the
  pre-pause state.

### 3.3 The tick loop

`onEndTick(server)` runs every server tick:

1. If `PAUSED` / `RESUMING`, call `lockFrozenPlayers()` **every tick** (freeze must be tight).
2. Increment a tick counter; every **20 ticks (1 second)** run the per-second work:
   - the world-reset countdown (if active),
   - then `secondTick()`, which dispatches to `leadSecond()`, `huntSecond()` or
     `resumeSecond()` based on state.

This is why nothing relies on `/tick freeze` or datapack `schedule` — the loop fires even
when the game is otherwise frozen.

### 3.4 Freeze (the pause fix)

The original `/tick freeze` approach never held players because tick-freeze stops *mobs and
blocks*, not player input. The fix:

- **Slowness amplifier 255** drops the movement-speed attribute to zero → players can't walk.
- **Velocity is zeroed every tick** → kills knockback, sliding and fall speed.
- **Mining Fatigue 255** stops block breaking; **Blindness** signals the pause.
- `/tick freeze` is *also* issued (best-effort, in a try/catch) as a bonus to freeze mobs, but
  nothing depends on it.

Effects are cleared on resume / game over.

### 3.5 Compass tracking

Native lodestone targeting — no item NBT macros.

- A tracking compass is a `minecraft:compass` whose `CUSTOM_DATA` contains the key
  `Manhunt_tracker` (`GameManager.COMPASS_TAG`).
- It is handed out **once** (at hunt start, and on join if a hunt is live), guarded by
  `hasCompass()` so it's never duplicated.
- Each second, `updateHunterCompass()` finds the nearest **trackable** runner and writes a
  `LodestoneTrackerComponent` (via `GlobalPos`) onto every tracker compass in the hunter's
  main inventory **and** offhand. The held compass is in the main inventory list, so it
  updates in place.
- **Cross-dimension mapping:** if the runner is in a different dimension, coordinates are
  scaled (Overworld↔Nether 8:1). End↔non-End can't be mapped, so the compass shows
  *"Runner in another dimension"*.

### 3.6 Win conditions (checked each second during `HUNT`)

- **Hunters win** — `aliveRunnerCount() == 0` (all runners eliminated).
- **Runners win** — no hunters online, **or** the Ender Dragon dies.
  - Dragon detection: while a runner is in the End, count living `EnderDragonEntity`s. Only
    after a dragon has been *seen alive* and then disappears for ~3 seconds do runners win —
    this avoids a false trigger during End generation before the dragon spawns.

### 3.7 Death handling

- `ServerLivingEntityEvents.AFTER_DEATH` → `onRunnerDeath()`: marks the runner eliminated and
  announces it. If that was the last runner, hunters win.
- `ServerPlayerEvents.AFTER_RESPAWN` → `onPlayerRespawn()`: eliminated runners come back as
  spectators.

### 3.8 Persistence

`ManhuntData` ⇄ `<gameDir>/manhunt/teams.json`:

```json
{
  "wantedRunners": 1,
  "players": {
    "<uuid>": { "name": "Steve", "role": "RUNNER", "timesRunner": 3 }
  }
}
```

- Saved on every role change, on `/manhunt reset`, and on server stop.
- Lives **outside** the world folders, so it survives the reset watcher's wipe.
- On `SERVER_STARTED` and on player `JOIN`, roles are re-applied to the vanilla scoreboard
  teams (`runners` red / `hunters` blue, friendly-fire off) for nametag colours.

### 3.9 Logging (`ManhuntLog`)

Every Manhunt message goes to **both** the server console/`latest.log` (via SLF4J, prefixed
`[Manhunt]`) **and** a dedicated file at **`<gameDir>/manhunt/logs.txt`**:

- On each server start, the previous `logs.txt` is rotated to `logs-<yyyyMMdd-HHmmss>.txt` and a
  fresh per-instance file is opened with a session header.
- File lines are timestamped: `[HH:mm:ss.SSS] [LEVEL] message`. `DEBUG` always goes to the
  file (so it's "crazy detailed") but only reaches the console if console debug is enabled.
- `ManhuntLog.error(msg, throwable)` writes the full stack trace to both sinks.
- **Every command** runs through `ManhuntMod.run(...)`, which logs the invocation + caller and,
  on exception, logs the stack trace **and** sends the error to the player who ran it.
- All Fabric event handlers and the tick loop are wrapped in try/catch → `ManhuntLog.error`, so
  a thrown exception is always visible and never silently swallowed.
- The world-reset token line is emitted via `ManhuntLog.slf4j()` directly (no `[Manhunt]`
  prefix) so the watcher's regex still matches it exactly.

### 3.10 Reset countdown

`/manhunt reset` sets `resetCountdown = 10`; the per-second loop broadcasts a title + chat each
second and fires `triggerReset()` at zero. `triggerReset()` saves data and logs the exact line
the watcher matches:

```
[MANHUNT-RESET] TOKEN:<token-from-manhunt-reset-token.txt>
```

`/manhunt reset cancel` sets it back to `-1`.

---

## 4. Building

### Via CI (recommended)

Every push to `main` triggers `.github/workflows/build.yml`. Download the jar from the
**Actions** tab artifacts (or a tagged release).

### Locally

Requires **Java 21**.

```bash
./gradlew build
# → build/libs/manhunt-revamped-1.0.0.jar
```

> **Windows gotcha:** if `gradlew.bat` fails with *"Could not find or load main class
> org.gradle.wrapper.GradleWrapperMain"*, it's because the project path contains a space or a
> non-ASCII character, which breaks the batch script's classpath. Invoke the wrapper jar
> directly instead:
>
> ```powershell
> $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
> & "$env:JAVA_HOME\bin\java.exe" -cp "gradle\wrapper\gradle-wrapper.jar" `
>     org.gradle.wrapper.GradleWrapperMain build --no-daemon --console=plain
> ```

First build downloads Gradle + Minecraft + Yarn mappings (~3–4 min); subsequent builds ~30s.

---

## 5. Deployment

The production server runs on Pelican (Wings daemon).

1. Build the jar (CI or local).
2. Upload `manhunt-revamped-1.0.0.jar` to the server's `mods/` folder (alongside Fabric API).
3. Restart the server.

For the auto world-reset, the Python **watcher** (not in this repo) tails the server log for
the `[MANHUNT-RESET] TOKEN:...` line and performs the wipe/restart via the Wings power API.

---

## 6. Extending it — recipes

- **Change the lead-phase length** → `GameManager.LEAD_SECONDS`.
- **Change the reset countdown** → the `10` in `startResetCountdown()`.
- **Change the freeze effects** → `applyFreezeEffects()` / `clearFreezeEffects()`.
- **Change the compass custom-data key** → `GameManager.COMPASS_TAG`.
- **Add a command** → add a `.then(CommandManager.literal(...))` branch in
  `ManhuntMod.registerCommands()` and a matching method on `GameManager`.
- **Verifying a yarn mapping without building** → browse the published javadoc at
  `https://maven.fabricmc.net/docs/yarn-1.21.11+build.5/`, or check the reference mod that
  ships in `../reference_manhunt/src` (same MC version).

---

## 7. Gotchas / lessons learned

- `velocityModified` does **not** exist on `ServerPlayerEntity` in this Yarn build — use
  `setVelocity(...)` alone.
- `ServerLivingEntityEvents` lives in `net.fabricmc.fabric.api.entity.event.v1`, **not**
  `event.entity.living`.
- The 1.21.11 teleport API was overhauled (`PlayerPosition`); the freeze deliberately avoids
  teleport and relies on Slowness 255 + velocity zeroing instead.
- `RegistryKey<World>` comparisons use `.equals()` (not `==`) to be safe.
- Don't `git add -A` after a local build — the Loom cache has paths too long for Git on
  Windows. `.gitignore` already excludes `.gradle/` and `build/`.
