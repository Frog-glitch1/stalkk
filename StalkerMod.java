package com.stalker;

import com.stalker.entity.StalkerEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class StalkerMod implements ModInitializer {
    public static final String MOD_ID = "stalker";

    public static final EntityType<StalkerEntity> STALKER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "stalker"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, StalkerEntity::new)
                    .dimensions(EntityDimensions.fixed(0.7f, 2.9f))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(2)
                    .build());

    public static final Item STALKER_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "stalker_spawn_egg"),
            new SpawnEggItem(STALKER, 0xB8B5AC, 0x050505, new FabricItemSettings()));

    public static final Item FLASHLIGHT = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "flashlight"),
            new FlashlightItem(new FabricItemSettings().maxCount(1).maxDamage(FlashlightItem.MAX_CHARGE)));

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(STALKER, StalkerEntity.createStalkerAttributes());
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(STALKER_SPAWN_EGG));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(FLASHLIGHT));
        StalkerManager.init();
        FlashlightManager.init();
        NightmareManager.init();
        StalkerCommands.init();
    }
}
