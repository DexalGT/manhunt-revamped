# /function manhunt:stop
# Ends the current game cleanly from operator command
execute as @a run function manhunt:internal/remove_compass
scoreboard players set $state mh_enabled 0
scoreboard objectives setdisplay sidebar
title @a title {"text":"Game Stopped","bold":true,"color":"gray"}
tellraw @a {"text":"[Manhunt] Game stopped by operator.","color":"gray"}
