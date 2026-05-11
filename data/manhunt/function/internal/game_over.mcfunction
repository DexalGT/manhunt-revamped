# Shared game-over routine: strip compasses, show title, reset state
execute as @a run function manhunt:internal/remove_compass
scoreboard players set $state    mh_enabled 0

scoreboard players set $end_grace mh_end    10
scoreboard players set $lead_timer mh_display 0
scoreboard objectives setdisplay sidebar
