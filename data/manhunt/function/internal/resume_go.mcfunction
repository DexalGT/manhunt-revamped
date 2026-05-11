execute unless score $state mh_enabled matches 4 run return 0

scoreboard players operation $state mh_enabled = $prev_state mh_prev
effect clear @a minecraft:slowness
effect clear @a minecraft:mining_fatigue

title @a times 0 50 20
title @a title {"text":"RESUMED!","bold":true,"color":"green"}
tellraw @a {"text":"[Manhunt] Game resumed!","color":"green"}
