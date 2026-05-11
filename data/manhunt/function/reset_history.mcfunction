# /function manhunt:reset_history
# Resets the times_been_runner history for all players (for a fresh rotation cycle)
scoreboard players reset @a mh_times_runner
tellraw @a {"text":"[Manhunt] Runner history reset.","color":"gold"}
