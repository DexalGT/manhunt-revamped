# ─────────────────────────────────────────────────────────────────────────────
# /function manhunt:start
#
# Starts a new Manhunt round using the roles already set by shuffle or swap.
# Requires roles to be assigned first (runner / hunter tags + teams).
#
# What it does:
#  - Clears inventories and resets health/hunger.
#  - Resets position-tracking scoreboards.
#  - Sets game state to "lead phase" (hunters are blinded/slowed).
#  - Shows a sidebar countdown.
#  - Resets death tracking.
# ─────────────────────────────────────────────────────────────────────────────

# ── Safety check: abort if no runners are assigned ───────────────────────────
execute unless entity @a[tag=runner] run tellraw @a {"text":"[Manhunt] ERROR: No runners assigned. Run /function manhunt:shuffle first.","color":"red"}
execute unless entity @a[tag=runner] run return fail

# ── Reset position-tracking scoreboards ──────────────────────────────────────
scoreboard players reset @a mh_x_o
scoreboard players reset @a mh_y_o
scoreboard players reset @a mh_z_o
scoreboard players reset @a mh_x_n
scoreboard players reset @a mh_y_n
scoreboard players reset @a mh_z_n
scoreboard players reset @a mh_rid

# ── Clear temp/state tags ────────────────────────────────────────────────────
tag @a remove mh_died
tag @a remove mh_closest
tag @a remove mh_not_in_nether

# ── Count alive runners ───────────────────────────────────────────────────────
scoreboard players set $p_left mh_p_left 0
execute as @a[tag=runner] run scoreboard players add $p_left mh_p_left 1

# ── Reset end-grace counter ───────────────────────────────────────────────────
scoreboard players set $end_grace mh_end 0

# ── Prepare players ──────────────────────────────────────────────────────────
gamemode survival @a
clear @a
effect give @a minecraft:saturation 5 255 true
effect give @a minecraft:instant_health 1 255 true

# ── Give compasses immediately to hunters ────────────────────────────────────
# (compass tracking starts after lead phase)

# ── Start lead phase ─────────────────────────────────────────────────────────
# Lead timer default: 30 seconds (configurable by changing the value below)
scoreboard players set " " mh_display 30
scoreboard objectives modify mh_display displayname "§e§lHead Start"
scoreboard objectives setdisplay sidebar mh_display

scoreboard players set $state mh_enabled 1
scoreboard players set $ticks mh_ticks 0

tellraw @a [{"text":"[Manhunt] ","color":"gold","bold":true},{"text":"Game starting! Hunters get a ","color":"white"},{"score":{"name":" ","objective":"mh_display"},"color":"yellow"},{"text":" second head start.","color":"white"}]
tellraw @a [{"text":"Runners: ","color":"red","bold":true},{"selector":"@a[tag=runner]"},{"text":"   Hunters: ","color":"blue","bold":true},{"selector":"@a[tag=hunter]"}]
