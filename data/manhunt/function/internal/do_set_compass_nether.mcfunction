# Write nether coords to storage and fire the macro
execute store result storage manhunt:compass_data X int 1 run scoreboard players get @e[tag=mh_closest,limit=1] mh_x_n
execute store result storage manhunt:compass_data Y int 1 run scoreboard players get @e[tag=mh_closest,limit=1] mh_y_n
execute store result storage manhunt:compass_data Z int 1 run scoreboard players get @e[tag=mh_closest,limit=1] mh_z_n

execute unless score @s mh_rid = @e[tag=mh_closest,limit=1] mh_rid run tellraw @s [{"text":"Now tracking: ","bold":true,"color":"gold"},{"selector":"@e[tag=mh_closest,limit=1]"}]
scoreboard players operation @s mh_rid = @e[tag=mh_closest,limit=1] mh_rid

function manhunt:internal/set_compass_nether with storage manhunt:compass_data
