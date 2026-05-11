# ─────────────────────────────────────────────────────────────────────────────
# Internal: pick one runner per call (lowest mh_rng score that isn't yet tagged)
# Recurses until $runners_left reaches 0.
# ─────────────────────────────────────────────────────────────────────────────

# Find the player with the absolute lowest random score who isn't already a runner
# We tag them mh_pick_candidate temporarily to identify exactly one player.
tag @a remove mh_pick_candidate

# Select the player with the lowest mh_rng score (not yet a runner)
execute as @a[tag=!runner,sort=arbitrary,limit=1] \
    if score @s mh_rng <= @a[tag=!runner,sort=arbitrary] mh_rng \
    run tag @s add mh_pick_candidate

# Assign runner role
execute as @a[tag=mh_pick_candidate] run function manhunt:internal/assign_runner

tag @a remove mh_pick_candidate

# Decrement counter and recurse if more runners needed
scoreboard players remove $runners_left mh_swap_working 1
execute if score $runners_left mh_swap_working matches 1.. run function manhunt:internal/shuffle_pick_runner
