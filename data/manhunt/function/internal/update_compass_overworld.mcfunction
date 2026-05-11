# Update compass for a hunter standing in the Overworld
# Finds the closest runner and sets all compasses in every slot to that position.

tag @s add mh_tracker_temp

# ── Distance² from this hunter to each runner ────────────────────────────────
execute as @e[team=runners] store result score @s mh_dst run data get entity @e[tag=mh_tracker_temp,limit=1] Pos[0]
execute as @e[team=runners] run scoreboard players operation @s mh_dst -= @s mh_x_o
execute as @e[team=runners] run scoreboard players operation @s mh_dst *= @s mh_dst

execute as @e[team=runners] store result score @s mh_y_o_tmp run data get entity @e[tag=mh_tracker_temp,limit=1] Pos[1]
execute as @e[team=runners] run scoreboard players operation @s mh_y_o_tmp -= @s mh_y_o
execute as @e[team=runners] run scoreboard players operation @s mh_y_o_tmp *= @s mh_y_o_tmp

execute as @e[team=runners] store result score @s mh_z_o_tmp run data get entity @e[tag=mh_tracker_temp,limit=1] Pos[2]
execute as @e[team=runners] run scoreboard players operation @s mh_z_o_tmp -= @s mh_z_o
execute as @e[team=runners] run scoreboard players operation @s mh_z_o_tmp *= @s mh_z_o_tmp

execute as @e[team=runners] run scoreboard players operation @s mh_dst += @s mh_y_o_tmp
execute as @e[team=runners] run scoreboard players operation @s mh_dst += @s mh_z_o_tmp

# ── Find the closest runner ───────────────────────────────────────────────────
scoreboard players set $best mh_dst 2147483647
execute as @e[team=runners] run function manhunt:internal/find_closest_overworld

# ── Notify hunter if target changed ──────────────────────────────────────────
execute unless score @s mh_rid = @e[tag=mh_closest,limit=1] mh_rid run tellraw @s [{"text":"Now tracking: ","bold":true,"color":"gold"},{"selector":"@e[tag=mh_closest,limit=1]"}]
scoreboard players operation @s mh_rid = @e[tag=mh_closest,limit=1] mh_rid

# ── Write the closest runner's position into storage for the macro ────────────
execute store result storage manhunt:compass_data X int 1 run scoreboard players get @e[tag=mh_closest,limit=1] mh_x_o
execute store result storage manhunt:compass_data Y int 1 run scoreboard players get @e[tag=mh_closest,limit=1] mh_y_o
execute store result storage manhunt:compass_data Z int 1 run scoreboard players get @e[tag=mh_closest,limit=1] mh_z_o

function manhunt:internal/set_compass_overworld with storage manhunt:compass_data

tag @s remove mh_tracker_temp
