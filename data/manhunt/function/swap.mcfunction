# ─────────────────────────────────────────────────────────────────────────────
# /function manhunt:swap
#
# Rotates runner roles based on mh_times_runner history.
#
# Algorithm:
#  1. Reads the saved runner count from $runner_count mh_runner_count.
#  2. Temporarily boosts every current runner's mh_times_runner score by a
#     large sentinel (1000000) so they are always higher than any hunter.
#  3. Runs a "pick minimum" loop that selects exactly <count> players with the
#     lowest mh_times_runner scores (i.e., players who have been runner least).
#  4. Those players become the new runners (+1 to their counter).
#  5. Everyone else becomes the new hunters.
#  6. Restores the sentinel offset from the old runners' scores.
# ─────────────────────────────────────────────────────────────────────────────

# Guard: if no runner count saved, default to 1
execute unless score $runner_count mh_runner_count matches 1.. run scoreboard players set $runner_count mh_runner_count 1

# ── Step 1: Boost current runners' scores so they can't be chosen ────────────
scoreboard players add @a[tag=runner] mh_times_runner 1000000

# ── Step 2: Ensure all players have at least a 0 score ───────────────────────
execute as @a unless score @s mh_times_runner matches -2147483647.. run scoreboard players set @s mh_times_runner 0

# ── Step 3: Pick new runners ──────────────────────────────────────────────────
# Reset old tags
tag @a remove runner
tag @a remove hunter
team join hunters @a

scoreboard players operation $runners_left mh_swap_working = $runner_count mh_runner_count
function manhunt:internal/swap_pick_runner

# ── Step 4: Tag everyone else as hunter ──────────────────────────────────────
tag @a[tag=!runner] add hunter
team join hunters @a[tag=hunter]

# ── Step 5: Restore boosted scores for the old runners (now hunters) ─────────
# Any hunter whose score is >= 1000000 had the sentinel applied; strip it.
execute as @a[tag=hunter,scores={mh_times_runner=1000000..}] run scoreboard players remove @s mh_times_runner 1000000

# ── Announce ─────────────────────────────────────────────────────────────────
tellraw @a [{"text":"[Manhunt] Roles swapped — ","color":"gold"},{"text":"Runners: ","color":"red","bold":true},{"selector":"@a[tag=runner]"},{"text":"  Hunters: ","color":"blue","bold":true},{"selector":"@a[tag=hunter]"}]
