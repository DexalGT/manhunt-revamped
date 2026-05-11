# Per-second logic gate
scoreboard players set $ticks mh_ticks 0

# ── Countup lead phase (state 1) ──────────────────────────────────────────────
execute if score $state mh_enabled matches 1 run function manhunt:internal/lead_second

# ── Active hunt phase (state 2) ──────────────────────────────────────────────
execute if score $state mh_enabled matches 2 run function manhunt:internal/hunt_second
