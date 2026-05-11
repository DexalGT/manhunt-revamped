# ── Active hunt: compass tracking, game-over checks ──────────────────────────

# Stamp runner UUID the first time we see them (for compass targeting)
execute as @e[team=runners] unless score @s mh_rid matches -2147483647.. run execute store result score @s mh_rid run data get entity @s UUID[0]

# Give tracking compass to every hunter (idempotent – won't duplicate)
execute as @a[team=hunters] run function manhunt:internal/give_compass

# Grab runner positions (stored in mh_x_o/z_o for overworld, mh_x_n/z_n for nether)
function manhunt:internal/grab_position

# Update hunter compass lodestones
execute as @a[team=hunters] at @s if predicate manhunt:in_overworld run function manhunt:internal/update_compass_overworld
execute as @a[team=hunters] at @s if predicate manhunt:in_nether run function manhunt:internal/update_compass_nether

# ── Game-over checks ──────────────────────────────────────────────────────────
# All runners died
execute if score $p_left mh_p_left matches ..0 unless entity @a[team=runners,tag=!mh_died] run function manhunt:internal/hunters_win

# No hunters left
execute unless entity @a[team=hunters] run function manhunt:internal/runners_win

# Ender Dragon killed (grace counter: stays high while dragon lives, counts down after death)
# Keep grace at 10 every second the dragon is alive in the_end
execute in minecraft:the_end if entity @e[type=minecraft:ender_dragon] run scoreboard players set $end_grace mh_end 10
# Count down once dragon is gone
execute if score $end_grace mh_end matches 1.. run scoreboard players remove $end_grace mh_end 1
# Trigger win after ~10s of confirmed no-dragon in the_end
execute if score $end_grace mh_end matches 0 if score $state mh_enabled matches 2 in minecraft:the_end unless entity @e[type=minecraft:ender_dragon] run function manhunt:internal/runners_win
