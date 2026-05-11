# ─────────────────────────────────────────────────────────────────────────────
# Internal: pick one runner per call (lowest mh_rng score that isn't yet tagged)
# Recurses until $runners_left reaches 0.
# ─────────────────────────────────────────────────────────────────────────────

tag @a remove mh_pick_candidate

# Find the absolute minimum mh_rng score among non-runners
scoreboard players set $min mh_rng 2147483647
execute as @a[tag=!runner] run scoreboard players operation $min mh_rng < @s mh_rng

# Tag anyone who has this minimum score
execute as @a[tag=!runner] if score @s mh_rng = $min mh_rng run tag @s add mh_pick_candidate

# Assign runner role to exactly one of them (resolves ties arbitrarily)
execute as @a[tag=mh_pick_candidate,limit=1,sort=arbitrary] run function manhunt:internal/assign_runner

tag @a remove mh_pick_candidate

# Decrement counter and recurse if more runners needed
scoreboard players remove $runners_left mh_swap_working 1
execute if score $runners_left mh_swap_working matches 1.. run function manhunt:internal/shuffle_pick_runner
