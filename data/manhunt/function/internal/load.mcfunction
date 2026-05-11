# ─────────────────────────────────────────────────────────────────────────────
# MANHUNT  –  Internal Load
# Runs once on datapack (re)load via #minecraft:load
# ─────────────────────────────────────────────────────────────────────────────

# ── Scoreboards ───────────────────────────────────────────────────────────────
scoreboard objectives add mh_enabled         dummy
scoreboard objectives add mh_ticks           dummy
scoreboard objectives add mh_end             dummy
scoreboard objectives add mh_p_left          dummy
scoreboard objectives add mh_runner_count    dummy   # saved count from last shuffle/swap
scoreboard objectives add mh_times_runner    dummy   # times a player has been runner (swap history)
scoreboard objectives add mh_swap_working    dummy   # internal counter for swap loop
scoreboard objectives add mh_rng             dummy   # random seed scratchpad
scoreboard objectives add mh_rid             dummy   # runner UUID fragment
scoreboard objectives add mh_dst             dummy
scoreboard objectives add mh_min_dst         dummy
scoreboard objectives add mh_x_o             dummy
scoreboard objectives add mh_y_o             dummy
scoreboard objectives add mh_z_o             dummy
scoreboard objectives add mh_x_n             dummy
scoreboard objectives add mh_y_n             dummy
scoreboard objectives add mh_z_n             dummy
scoreboard objectives add mh_prev            dummy
scoreboard objectives add mh_deaths          deathCount

# Sidebar display objective (blank name so it looks clean)
scoreboard objectives add mh_display         dummy
scoreboard objectives modify mh_display displayname ""

# ── Teams ─────────────────────────────────────────────────────────────────────
team add hunters
team add runners
execute unless score $init mh_prev matches 1 run function manhunt:internal/first_load

# ── Defaults (only if not already initialised) ────────────────────────────────
execute unless score $runner_count mh_runner_count matches -2147483647.. run scoreboard players set $runner_count mh_runner_count 1

tellraw @a {"text":"[Manhunt] Datapack loaded.","color":"gold","bold":true}
