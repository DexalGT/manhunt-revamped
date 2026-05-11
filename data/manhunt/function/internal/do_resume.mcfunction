# Mark as "resuming" (state 4) to block double-calls; unfreeze tick so schedules fire
scoreboard players set $state mh_enabled 4

title @a times 0 22 3
title @a title {"text":"5","bold":true,"color":"yellow"}
tellraw @a {"text":"[Manhunt] Resuming in 5 seconds...","color":"yellow"}

schedule function manhunt:internal/resume_countdown_4 1s
