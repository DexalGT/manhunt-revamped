# ── Lead phase: blind + slow hunters, tick down timer ────────────────────────
effect give @a[team=hunters] minecraft:slowness 2 255 true
effect give @a[team=hunters] minecraft:blindness 2 255 true
effect give @a[team=hunters] minecraft:mining_fatigue 2 255 true
effect give @a[team=hunters] minecraft:weakness 2 255 true

scoreboard players remove $lead_timer mh_display 1

# When timer reaches 0 → transition to hunt phase
execute if score $lead_timer mh_display matches ..0 run function manhunt:internal/begin_hunt
