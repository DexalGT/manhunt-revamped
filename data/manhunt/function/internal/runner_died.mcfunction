# Runner died → put them in spectator, mark as dead, decrement alive counter
scoreboard players remove $p_left mh_p_left 1
gamemode spectator @s
tag @s add mh_died
