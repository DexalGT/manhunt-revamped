# ─────────────────────────────────────────────────────────────────────────────
# MANHUNT  –  Internal Tick  (runs every game tick via #minecraft:tick)
# ─────────────────────────────────────────────────────────────────────────────

# ── Runner death detection ────────────────────────────────────────────────────
execute if score $state mh_enabled matches 2 as @a[team=runners,scores={mh_deaths=1..},tag=!mh_died] run function manhunt:internal/runner_died
scoreboard players set @a mh_deaths 0

# ── Per-second logic (20-tick throttle) ──────────────────────────────────────
scoreboard players add $ticks mh_ticks 1
execute if score $ticks mh_ticks matches 20.. run function manhunt:internal/second

# ── Drop any stray tracking compasses that land on the ground ────────────────
execute if score $state mh_enabled matches 2 as @e[type=item] if data entity @s Item.components.minecraft:custom_data.Manhunt_tracker run kill @s
