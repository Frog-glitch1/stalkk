package com.stalker.client;

import com.stalker.StalkerMod;
import com.stalker.entity.StalkerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public class StalkerRenderer extends MobEntityRenderer<StalkerEntity, StalkerModel<StalkerEntity>> {
    private static final Identifier TEXTURE = new Identifier(StalkerMod.MOD_ID, "textures/entity/stalker.png");

    public StalkerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new StalkerModel<>(ctx.getPart(StalkerClient.MODEL_LAYER)), 0.35f);
        this.addFeature(new StalkerEyesFeature(this));
    }

    @Override
    public Identifier getTexture(StalkerEntity entity) {
        return TEXTURE;
    }
}
