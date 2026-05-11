# Internal non-macro entry point for the Java /manhunt shuffle <count> command.
# Java sets $wanted_runners mh_runner_count via scoreboard before calling this.
# Identical to shuffle_with_count but skips the macro line since the count is
# already in the scoreboard.

# ── Reset teams ───────────────────────────────────────────────────────────────
team join hunters @a
tag @a remove runner
tag @a remove hunter

# ── Assign a random score to every player ────────────────────────────────────
execute as @a store result score @s mh_rng run data get entity @s UUID[3]

# ── Pick runners: iterate from lowest random score upward ────────────────────
scoreboard players operation $runners_left mh_swap_working = $wanted_runners mh_runner_count
function manhunt:internal/shuffle_pick_runner

# ── Everyone without the runner tag becomes a hunter ─────────────────────────
tag @a[tag=!runner] add hunter
team join hunters @a[tag=hunter]

# ── Announce results ─────────────────────────────────────────────────────────
tellraw @a [{"text":"[Manhunt] Roles assigned — ","color":"gold"},{"text":"Runners: ","color":"red","bold":true},{"selector":"@a[tag=runner]"},{"text":"  Hunters: ","color":"blue","bold":true},{"selector":"@a[tag=hunter]"}]
