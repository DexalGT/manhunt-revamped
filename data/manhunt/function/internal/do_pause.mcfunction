# Save current game state (1 = lead, 2 = hunt) then switch to paused (3)
scoreboard players operation $prev_state mh_prev = $state mh_enabled
scoreboard players set $state mh_enabled 3

# Freeze all players
effect give @a minecraft:slowness 7200 127 true
effect give @a minecraft:mining_fatigue 7200 127 true

title @a times 0 40 10
title @a title {"text":"PAUSED","bold":true,"color":"gray"}
tellraw @a {"text":"[Manhunt] Game paused. Use /manhunt resume to continue.","color":"gray"}
