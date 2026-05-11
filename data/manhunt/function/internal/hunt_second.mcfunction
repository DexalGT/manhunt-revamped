# ── Active hunt: compass tracking, game-over checks ──────────────────────────

# Stamp runner UUID the first time we see them (for compass targeting)
execute as @e[team=runners] unless score @s mh_rid matches -2147483647.. run execute store result score @s mh_rid run data get entity @s UUID[0]

# Give tracking compass to every hunter (idempotent – won't duplicate)
execute as @a[team=hunters] run function manhunt:internal/give_compass

# Grab runner positions (stored in mh_x_o/z_o for overworld, mh_x_n/z_n for nether)
function manhunt:internal/grab_position

# Update hunter compass lodestones
execute as @a[team=hunters] at @s if predicate manhunt:in_overworld run function manhunt:internal/update_compass_overworld
execute as @a[team=hunters] at @s if predicate manhunt:in_nether    run function manhunt:internal/update_compass_nether

# ── Game-over checks ──────────────────────────────────────────────────────────
# All runners died
execute if score $p_left mh_p_left matches ..0 unless entity @a[team=runners,tag=!mh_died] run function manhunt:internal/hunters_win

# No hunters left
execute unless entity @a[team=hunters] run function manhunt:internal/runners_win

# Ender Dragon killed (with grace-period counter to avoid false positives)
execute in minecraft:the_end as @a[predicate=manhunt:in_end] if score $state mh_enabled matches 2 run scoreboard players add $end_grace mh_end 0
execute if score $end_grace mh_end matches 1.. in minecraft:the_end run scoreboard players remove $end_grace mh_end 1
execute if score $end_grace mh_end matches 0 unless entity @e[type=minecraft:ender_dragon] if score $state mh_enabled matches 2 run function manhunt:internal/runners_win
