# /manhunt resume — unpauses a game that was stopped with /manhunt stop.

execute unless score $state mh_enabled matches 3 run tellraw @a {"text":"[Manhunt] Game is not paused.","color":"gray"}
execute unless score $state mh_enabled matches 3 run return 0

function manhunt:internal/do_resume
