# /manhunt stop — pauses the active game and freezes all players.
# Use /manhunt resume to continue.

execute if score $state mh_enabled matches 3 run tellraw @a {"text":"[Manhunt] Already paused. Use /manhunt resume to continue.","color":"gray"}
execute if score $state mh_enabled matches 3 run return 0

execute if score $state mh_enabled matches 0 run tellraw @a {"text":"[Manhunt] No active game to stop.","color":"gray"}
execute if score $state mh_enabled matches 0 run return 0

function manhunt:internal/do_pause
