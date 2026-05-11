# Give tracking compass to the hunter executing this function (idempotent)
execute unless items entity @s inventory.* minecraft:compass[minecraft:custom_data={Manhunt_tracker:1b}] \
    unless items entity @s hotbar.* minecraft:compass[minecraft:custom_data={Manhunt_tracker:1b}] \
    run give @s minecraft:compass[minecraft:custom_data={Manhunt_tracker:1b}]
