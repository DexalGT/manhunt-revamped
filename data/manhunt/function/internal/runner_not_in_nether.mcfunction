# Runner has not been in the Nether yet – tell the hunter once, then suppress.
execute run function manhunt:internal/remove_compass
tellraw @s[tag=!mh_not_in_nether] {"text":"The runner hasn't entered the Nether yet...","color":"gold"}
tag @s add mh_not_in_nether
