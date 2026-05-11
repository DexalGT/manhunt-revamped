# ── Lead phase: blind + slow hunters, tick down timer ────────────────────────
effect give @a[team=hunters] minecraft:slowness 2 255 true
effect give @a[team=hunters] minecraft:blindness 2 255 true
effect give @a[team=hunters] minecraft:mining_fatigue 2 255 true
effect give @a[team=hunters] minecraft:weakness 2 255 true

scoreboard players remove $lead_timer mh_display 1

# Milestone title announcements (values checked after decrement)
execute if score $lead_timer mh_display matches 30 run title @a times 0 60 20
execute if score $lead_timer mh_display matches 30 run title @a title {"text":"30 seconds","bold":true,"color":"yellow"}
execute if score $lead_timer mh_display matches 30 run title @a subtitle {"text":"Hunters still blinded","color":"white"}
execute if score $lead_timer mh_display matches 15 run title @a times 0 60 20
execute if score $lead_timer mh_display matches 15 run title @a title {"text":"15 seconds","bold":true,"color":"red"}
execute if score $lead_timer mh_display matches 15 run title @a subtitle {"text":"Get ready hunters!","color":"white"}

# 5-4-3-2-1 countdown
execute if score $lead_timer mh_display matches 5 run title @a times 0 22 3
execute if score $lead_timer mh_display matches 5 run title @a title {"text":"5","bold":true,"color":"red"}
execute if score $lead_timer mh_display matches 4 run title @a title {"text":"4","bold":true,"color":"red"}
execute if score $lead_timer mh_display matches 3 run title @a title {"text":"3","bold":true,"color":"red"}
execute if score $lead_timer mh_display matches 2 run title @a title {"text":"2","bold":true,"color":"red"}
execute if score $lead_timer mh_display matches 1 run title @a title {"text":"1","bold":true,"color":"red"}

# When timer reaches 0 → transition to hunt phase
execute if score $lead_timer mh_display matches ..0 run function manhunt:internal/begin_hunt
