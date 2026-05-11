# Helper: update $best and tag the closest runner (nether)
execute if score $best mh_dst > @s mh_dst run tag @e remove mh_closest
execute if score $best mh_dst > @s mh_dst run tag @s add mh_closest
execute if score $best mh_dst > @s mh_dst run scoreboard players operation $best mh_dst = @s mh_dst
