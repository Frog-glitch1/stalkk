package com.stalker.client;

import com.stalker.StalkerMod;
import com.stalker.entity.StalkerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.util.Identifier;

/** Pale pinprick eyes that stay bright in total darkness. */
public class StalkerEyesFeature extends EyesFeatureRenderer<StalkerEntity, StalkerModel<StalkerEntity>> {
    private static final RenderLayer EYES = RenderLayer.getEyes(
            new Identifier(StalkerMod.MOD_ID, "textures/entity/stalker_eyes.png"));

    public StalkerEyesFeature(FeatureRendererContext<StalkerEntity, StalkerModel<StalkerEntity>> context) {
        super(context);
    }

    @Override
    public RenderLayer getEyesTexture() {
        return EYES;
    }
}
