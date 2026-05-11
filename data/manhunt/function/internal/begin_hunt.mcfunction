# Transition from lead phase → active hunt phase
scoreboard objectives setdisplay sidebar
scoreboard players reset @a mh_rid
scoreboard players set $state mh_enabled 2
scoreboard players set $lead_timer mh_display 0

tellraw @a {"text":"The hunt has begun!","color":"red","bold":true}
