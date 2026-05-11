# ─────────────────────────────────────────────────────────────────────────────
# Internal: iterative minimum-selection for swap.
# Each call picks the one player with the LOWEST mh_times_runner score
# that hasn't already been tagged as runner in this swap cycle.
# Recurses until $runners_left reaches 0.
# ─────────────────────────────────────────────────────────────────────────────

tag @a remove mh_pick_candidate

# Find the player with the absolute minimum mh_times_runner score (not yet runner)
# The trick: a player qualifies as "minimum" only if no other non-runner player
# has a strictly lower score. We check that condition per-player.
execute as @a[tag=!runner] \
    unless entity @a[tag=!runner,scores={mh_times_runner=..2147483646}] \
    if score @s mh_times_runner < @a[tag=!runner] mh_times_runner \
    run tag @s add mh_pick_candidate

# Fallback: if the above found nobody (all tied), just pick any one
execute unless entity @a[tag=mh_pick_candidate] \
    as @a[tag=!runner,limit=1,sort=arbitrary] \
    run tag @s add mh_pick_candidate

# Assign runner role to the chosen player (increments mh_times_runner)
execute as @a[tag=mh_pick_candidate] run function manhunt:internal/assign_runner

tag @a remove mh_pick_candidate

# Recurse
scoreboard players remove $runners_left mh_swap_working 1
execute if score $runners_left mh_swap_working matches 1.. run function manhunt:internal/swap_pick_runner
