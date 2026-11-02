package com.stalker.client;

import com.stalker.StalkerMod;
import com.stalker.FlashlightItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

public class StalkerClient implements ClientModInitializer {
    public static final EntityModelLayer MODEL_LAYER =
            new EntityModelLayer(new Identifier(StalkerMod.MOD_ID, "stalker"), "main");

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(MODEL_LAYER, StalkerModel::getTexturedModelData);
        EntityRendererRegistry.register(StalkerMod.STALKER, StalkerRenderer::new);
        ModelPredicateProviderRegistry.register(StalkerMod.FLASHLIGHT, new Identifier(StalkerMod.MOD_ID, "on"),
                (stack, world, entity, seed) -> FlashlightItem.isOn(stack) ? 1.0f : 0.0f);
    }
}
