package com.stalker.client;

import com.stalker.entity.StalkerEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * Cave Dweller body plan: ~2.9 blocks tall, rail-thin torso, arms that hang almost to the floor,
 * a long hollow-eyed head with a jaw that hangs open, and a four-legged crawl when it hunts.
 * Texture layout (64x64) is documented in tools/make_textures.py.
 */
public class StalkerModel<T extends StalkerEntity> extends SinglePartEntityModel<T> {
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public StalkerModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.jaw = head.getChild("jaw");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        // Hips sit at y=4 (feet are at y=24). Everything upper-body hangs off "body" so it leans as one piece.
        ModelPartData body = root.addChild("body",
                ModelPartBuilder.create().uv(0, 16).cuboid(-2.5f, -16f, -1.5f, 5f, 16f, 3f),
                ModelTransform.pivot(0f, 4f, 0f));

        ModelPartData head = body.addChild("head",
                ModelPartBuilder.create().uv(0, 0).cuboid(-3f, -10f, -3f, 6f, 10f, 6f),
                ModelTransform.pivot(0f, -16f, 0f));

        head.addChild("jaw",
                ModelPartBuilder.create().uv(24, 0).cuboid(-2.5f, 0f, -5f, 5f, 4f, 5f),
                ModelTransform.pivot(0f, -1.5f, 2.5f));

        ModelPartData rightArm = body.addChild("right_arm",
                ModelPartBuilder.create().uv(16, 16).cuboid(-1f, 0f, -1f, 2f, 22f, 2f),
                ModelTransform.pivot(-3.5f, -15f, 0f));
        ModelPartData rightHand = rightArm.addChild("hand",
                ModelPartBuilder.create().uv(32, 16).cuboid(-1.5f, 0f, -1.5f, 3f, 4f, 3f),
                ModelTransform.pivot(0f, 22f, 0f));
        rightHand.addChild("claws",
                ModelPartBuilder.create().uv(32, 23).cuboid(-1.5f, 4f, -0.5f, 3f, 5f, 1f),
                ModelTransform.NONE);

        ModelPartData leftArm = body.addChild("left_arm",
                ModelPartBuilder.create().uv(16, 16).cuboid(-1f, 0f, -1f, 2f, 22f, 2f),
                ModelTransform.pivot(3.5f, -15f, 0f));
        ModelPartData leftHand = leftArm.addChild("hand",
                ModelPartBuilder.create().uv(32, 16).cuboid(-1.5f, 0f, -1.5f, 3f, 4f, 3f),
                ModelTransform.pivot(0f, 22f, 0f));
        leftHand.addChild("claws",
                ModelPartBuilder.create().uv(32, 23).cuboid(-1.5f, 4f, -0.5f, 3f, 5f, 1f),
                ModelTransform.NONE);

        root.addChild("right_leg",
                ModelPartBuilder.create().uv(44, 16).cuboid(-1f, 0f, -1f, 2f, 20f, 2f),
                ModelTransform.pivot(-2f, 4f, 0f));
        root.addChild("left_leg",
                ModelPartBuilder.create().uv(44, 16).cuboid(-1f, 0f, -1f, 2f, 20f, 2f),
                ModelTransform.pivot(2f, 4f, 0f));

        return TexturedModelData.of(data, 64, 64);
    }

    @Override
    public ModelPart getPart() {
        return root;
    }

    @Override
    public void setAngles(T entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.getPart().traverse().forEach(ModelPart::resetTransform);

        float yawRad = headYaw * MathHelper.RADIANS_PER_DEGREE;
        float pitchRad = headPitch * MathHelper.RADIANS_PER_DEGREE;
        float swing = MathHelper.cos(limbAngle * 0.6662f) * limbDistance;
        int stage = entity.getStage();

        if (entity.isCrawling()) {
            // Hunting pose: torso nearly horizontal, hands planted, head snapped up to stare at you.
            float lean = 1.2f;
            float gait = MathHelper.cos(limbAngle * 0.8f) * limbDistance;

            body.pitch = lean;
            body.yaw = MathHelper.sin(animationProgress * 0.9f) * 0.06f;

            head.pitch = -lean * 0.85f + pitchRad * 0.5f;
            head.yaw = yawRad * 0.8f;
            head.roll = MathHelper.sin(animationProgress * 0.7f) * 0.12f;

            rightArm.pitch = -0.6f + gait * 1.1f;
            leftArm.pitch = -0.6f - gait * 1.1f;
            rightArm.roll = 0.15f;
            leftArm.roll = -0.15f;

            rightLeg.pitch = -gait * 0.9f;
            leftLeg.pitch = gait * 0.9f;

            jaw.pitch = 0.9f + MathHelper.sin(animationProgress * 0.9f) * 0.2f;
        } else {
            // Standing: too tall, too still, head always turned toward you and cocked slightly wrong.
            body.pitch = 0.12f + (stage >= 2 ? 0.08f : 0f);

            head.pitch = pitchRad * 0.8f - 0.1f;
            head.yaw = yawRad;
            head.roll = MathHelper.sin(animationProgress * 0.03f) * 0.1f + 0.08f;

            rightArm.pitch = -swing * 0.8f + (stage >= 2 ? -0.15f : 0f);
            leftArm.pitch = swing * 0.8f + (stage >= 2 ? -0.15f : 0f);
            rightArm.roll = 0.05f + MathHelper.sin(animationProgress * 0.05f) * 0.02f;
            leftArm.roll = -0.05f - MathHelper.sin(animationProgress * 0.05f) * 0.02f;

            rightLeg.pitch = swing * 1.0f;
            leftLeg.pitch = -swing * 1.0f;

            jaw.pitch = 0.2f + (MathHelper.sin(animationProgress * 0.07f) + 1f) * 0.08f;
        }
    }
}
