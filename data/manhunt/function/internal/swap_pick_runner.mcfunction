# ─────────────────────────────────────────────────────────────────────────────
# Internal: iterative minimum-selection for swap.
# Each call picks the one player with the LOWEST mh_times_runner score
# that hasn't already been tagged as runner in this swap cycle.
# Recurses until $runners_left reaches 0.
# ─────────────────────────────────────────────────────────────────────────────

tag @a remove mh_pick_candidate

# Find the absolute minimum mh_times_runner score among non-runners
scoreboard players set $min mh_times_runner 2147483647
execute as @a[tag=!runner] run scoreboard players operation $min mh_times_runner < @s mh_times_runner

# Tag anyone who has this minimum score
execute as @a[tag=!runner] if score @s mh_times_runner = $min mh_times_runner run tag @s add mh_pick_candidate

# Assign runner role to exactly one of them (resolves ties arbitrarily)
execute as @a[tag=mh_pick_candidate,limit=1,sort=arbitrary] run function manhunt:internal/assign_runner

tag @a remove mh_pick_candidate

# Recurse
scoreboard players remove $runners_left mh_swap_working 1
execute if score $runners_left mh_swap_working matches 1.. run function manhunt:internal/swap_pick_runner
