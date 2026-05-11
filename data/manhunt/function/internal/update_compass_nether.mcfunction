# Update compass for a hunter standing in the Nether
# Targets the closest runner's nether-side coordinates.
# If the runner has never been in the Nether all three coords are 0 → show a
# "not in Nether yet" message instead of pointing the compass at (0,0,0).

tag @s add mh_tracker_temp

# ── Distance² from this hunter to each runner (nether coords) ────────────────
execute as @e[team=runners] store result score @s mh_dst run data get entity @e[tag=mh_tracker_temp,limit=1] Pos[0]
execute as @e[team=runners] run scoreboard players operation @s mh_dst -= @s mh_x_n
execute as @e[team=runners] run scoreboard players operation @s mh_dst *= @s mh_dst

execute as @e[team=runners] store result score @s mh_y_n_tmp run data get entity @e[tag=mh_tracker_temp,limit=1] Pos[1]
execute as @e[team=runners] run scoreboard players operation @s mh_y_n_tmp -= @s mh_y_n
execute as @e[team=runners] run scoreboard players operation @s mh_y_n_tmp *= @s mh_y_n_tmp

execute as @e[team=runners] store result score @s mh_z_n_tmp run data get entity @e[tag=mh_tracker_temp,limit=1] Pos[2]
execute as @e[team=runners] run scoreboard players operation @s mh_z_n_tmp -= @s mh_z_n
execute as @e[team=runners] run scoreboard players operation @s mh_z_n_tmp *= @s mh_z_n_tmp

execute as @e[team=runners] run scoreboard players operation @s mh_dst += @s mh_y_n_tmp
execute as @e[team=runners] run scoreboard players operation @s mh_dst += @s mh_z_n_tmp

# ── Find the closest runner ───────────────────────────────────────────────────
scoreboard players set $best mh_dst 2147483647
execute as @e[team=runners] run function manhunt:internal/find_closest_nether

# ── Handle runner not yet in Nether (all coords = 0) ─────────────────────────
execute if score @e[tag=mh_closest,limit=1] mh_x_n matches 0 if score @e[tag=mh_closest,limit=1] mh_y_n matches 0 if score @e[tag=mh_closest,limit=1] mh_z_n matches 0 run function manhunt:internal/runner_not_in_nether

execute unless score @e[tag=mh_closest,limit=1] mh_x_n matches 0 run function manhunt:internal/do_set_compass_nether
execute unless score @e[tag=mh_closest,limit=1] mh_z_n matches 0 run function manhunt:internal/do_set_compass_nether

tag @s remove mh_tracker_temp
