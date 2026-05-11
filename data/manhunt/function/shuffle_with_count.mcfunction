# ─────────────────────────────────────────────────────────────────────────────
# manhunt:shuffle_with_count  (macro – accepts {count:N})
#
# This is the real implementation called by both:
#   /function manhunt:shuffle              (default count=1 injected by shuffle.mcfunction)
#   /function manhunt:shuffle_with_count {count:N}   (direct OP call)
#
# How randomness works in vanilla 1.21:
#   We assign every online player a random score via a UUID fragment, then
#   use scoreboard sorting (lowest score first) to pick the first N as runners.
#   UUID[3] changes each tick so it acts as a per-call random value.
# ─────────────────────────────────────────────────────────────────────────────
$scoreboard players set $wanted_runners mh_runner_count $(count)

# ── Reset teams ───────────────────────────────────────────────────────────────
team join hunters @a
tag @a remove runner
tag @a remove hunter

# ── Assign a random score to every player ────────────────────────────────────
execute as @a store result score @s mh_rng run data get entity @s UUID[3]

# ── Pick runners: iterate from lowest random score upward ────────────────────
# We use a recursive helper that picks one player per call, up to <count> times.
scoreboard players operation $runners_left mh_swap_working = $wanted_runners mh_runner_count
function manhunt:internal/shuffle_pick_runner

# ── Everyone without the runner tag becomes a hunter ─────────────────────────
tag @a[tag=!runner] add hunter
team join hunters @a[tag=hunter]

# ── Announce results ─────────────────────────────────────────────────────────
tellraw @a [{"text":"[Manhunt] Roles assigned — ","color":"gold"},{"text":"Runners: ","color":"red","bold":true},{"selector":"@a[tag=runner]"},{"text":"  Hunters: ","color":"blue","bold":true},{"selector":"@a[tag=hunter]"}]
