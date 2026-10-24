/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nvidia.rtxremix.minecraft.RtxRemixCommonMod;
import com.nvidia.rtxremix.minecraft.block.CopperBeaconBlock;
import com.nvidia.rtxremix.minecraft.block.CopperBeaconBlockEntity;
import com.nvidia.rtxremix.minecraft.block.CopperBeaconPose;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;

/** Renders the independently rotating yoke and lamp head above the static mount. */
public final class CopperBeaconBlockEntityRenderer
        implements BlockEntityRenderer<CopperBeaconBlockEntity, CopperBeaconBlockEntityRenderer.State> {

    public static final ModelLayerLocation BODY_MODEL_LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(RtxRemixCommonMod.MOD_ID, "copper_beacon"), "body");
    public static final ModelLayerLocation LENS_MODEL_LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(RtxRemixCommonMod.MOD_ID, "copper_beacon"), "lens");
    private static final SpriteId COPPER_TEXTURE = new SpriteId(
            TextureAtlas.LOCATION_BLOCKS,
            Identifier.withDefaultNamespace("block/copper_block"));
    static final Identifier LENS_SPRITE_ID =
            Identifier.fromNamespaceAndPath(RtxRemixCommonMod.MOD_ID, "block/copper_beacon_lens");
    static final Identifier UNLIT_LENS_SPRITE_ID =
            Identifier.fromNamespaceAndPath(RtxRemixCommonMod.MOD_ID, "block/copper_beacon_lens_unlit");
    private static final SpriteId LENS_TEXTURE = new SpriteId(
            TextureAtlas.LOCATION_BLOCKS,
            LENS_SPRITE_ID);
    private static final SpriteId UNLIT_LENS_TEXTURE = new SpriteId(
            TextureAtlas.LOCATION_BLOCKS,
            UNLIT_LENS_SPRITE_ID);

    private final BeaconModel body;
    private final BeaconModel lensModel;
    private final SpriteGetter sprites;
    private final CopperBeaconPose.Pose pose = new CopperBeaconPose.Pose();

    public CopperBeaconBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        body = new BeaconModel(context.bakeLayer(BODY_MODEL_LAYER));
        lensModel = new BeaconModel(context.bakeLayer(LENS_MODEL_LAYER));
        sprites = context.sprites();
    }

    public static LayerDefinition getBodyTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();
        PartDefinition yokeData = root.addOrReplaceChild(
                "yoke",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0f, -6.0f, -1.0f, 3.0f, 2.0f, 3.0f)
                        .texOffs(0, 0).addBox(0.0f, -6.0f, -1.0f, 3.0f, 2.0f, 3.0f)
                        .texOffs(0, 0).addBox(-5.0f, -5.0f, -1.0f, 2.0f, 8.0f, 3.0f)
                        .texOffs(0, 0).addBox(3.0f, -5.0f, -1.0f, 2.0f, 8.0f, 3.0f),
                PartPose.offset(8.0f, 10.0f, 8.0f));
        yokeData.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0f, -4.0f, -3.0f, 4.0f, 8.0f, 1.0f)
                        .texOffs(0, 0).addBox(0.0f, -4.0f, -3.0f, 4.0f, 8.0f, 1.0f)
                        .texOffs(0, 0).addBox(-4.0f, -4.0f, 3.0f, 4.0f, 8.0f, 1.0f)
                        .texOffs(0, 0).addBox(0.0f, -4.0f, 3.0f, 4.0f, 8.0f, 1.0f)
                        .texOffs(0, 0).addBox(-4.0f, -3.0f, -2.0f, 1.0f, 6.0f, 5.0f)
                        .texOffs(0, 0).addBox(3.0f, -3.0f, -2.0f, 1.0f, 6.0f, 5.0f)
                        .texOffs(0, 0).addBox(-3.0f, -4.0f, -2.0f, 3.0f, 1.0f, 5.0f)
                        .texOffs(0, 0).addBox(0.0f, -4.0f, -2.0f, 3.0f, 1.0f, 5.0f)
                        .texOffs(0, 0).addBox(-3.0f, 3.0f, -2.0f, 3.0f, 1.0f, 5.0f)
                        .texOffs(0, 0).addBox(0.0f, 3.0f, -2.0f, 3.0f, 1.0f, 5.0f)
                        .texOffs(0, 0).addBox(-6.0f, -1.0f, -1.0f, 2.0f, 2.0f, 3.0f)
                        .texOffs(0, 0).addBox(4.0f, -1.0f, -1.0f, 2.0f, 2.0f, 3.0f)
                        .texOffs(0, 0).addBox(-4.0f, -4.0f, -4.0f, 4.0f, 1.0f, 1.0f)
                        .texOffs(0, 0).addBox(0.0f, -4.0f, -4.0f, 4.0f, 1.0f, 1.0f)
                        .texOffs(0, 0).addBox(-4.0f, 3.0f, -4.0f, 4.0f, 1.0f, 1.0f)
                        .texOffs(0, 0).addBox(0.0f, 3.0f, -4.0f, 4.0f, 1.0f, 1.0f)
                        .texOffs(0, 0).addBox(-4.0f, -3.0f, -4.0f, 1.0f, 6.0f, 1.0f)
                        .texOffs(0, 0).addBox(3.0f, -3.0f, -4.0f, 1.0f, 6.0f, 1.0f),
                PartPose.ZERO);
        return LayerDefinition.create(modelData, 16, 16);
    }

    public static LayerDefinition getLensTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();
        root.addOrReplaceChild(
                "lens",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0f, -3.0f, -4.01f, 6.0f, 6.0f, 0.0f),
                PartPose.offset(8.0f, 10.0f, 8.0f));
        return LayerDefinition.create(modelData, 6, 6);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            CopperBeaconBlockEntity beacon,
            State state,
            float partialTicks,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(beacon, state, breakProgress);
        BlockState blockState = beacon.getBlockState();
        double animationTick = CopperBeaconAnimationClock.sample(beacon.getLevel(), partialTicks);
        state.up = blockState.getValue(CopperBeaconBlock.MOUNT);
        state.forward = blockState.getValue(BlockStateProperties.FACING);
        state.yawAngle = beacon.yawAngleAt(animationTick);
        state.pitchAngle = beacon.pitchAngleAt(animationTick);
        state.lensTexture = lensTexture(beacon.isActive(), beacon.getIntensityMultiplier());
        state.lensTint = beacon.getLensTint();
    }

    @Override
    public void submit(
            State state,
            PoseStack matrices,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);
        CopperBeaconPose.evaluate(
                state.up,
                state.forward,
                state.yawAngle,
                state.pitchAngle,
                pose);
        matrices.rotate(pose.mountingRotation());
        matrices.translate(-0.5, -0.5, -0.5);
        state.modelYaw = pose.modelYawRadians();
        state.modelPitch = pose.modelPitchRadians();

        TextureAtlasSprite copper = sprites.get(COPPER_TEXTURE);
        collector.submitModel(body, state, matrices, RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS),
                state.lightCoords, OverlayTexture.NO_OVERLAY, -1, new CopperStripMapping(copper), 0);
        collector.submitModel(lensModel, state, matrices, RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS),
                state.lightCoords, OverlayTexture.NO_OVERLAY, state.lensTint,
                sprites.get(state.lensTexture), 0);
        matrices.popPose();
    }

    static Quaternionf mountingRotation(Direction upDirection, Direction forwardDirection) {
        return CopperBeaconPose.mountingRotation(
                upDirection, forwardDirection, new Quaternionf());
    }

    static void applyArticulation(
            ModelPart yoke,
            ModelPart head,
            ModelPart lens,
            CopperBeaconPose.Pose pose) {
        yoke.yRot = pose.modelYawRadians();
        yoke.xRot = 0.0f;
        head.yRot = 0.0f;
        head.xRot = pose.modelPitchRadians();
        lens.yRot = pose.modelYawRadians();
        lens.xRot = pose.modelPitchRadians();
    }

    static SpriteId lensTexture(boolean active, float intensityMultiplier) {
        return active && intensityMultiplier > 0.0f
                ? LENS_TEXTURE
                : UNLIT_LENS_TEXTURE;
    }

    static float copperTextureV(float v) {
        return 0.25f + v * 0.5f;
    }

    /** What one beacon renders this frame, extracted from its block entity. */
    public static final class State extends BlockEntityRenderState {
        Direction up = Direction.UP;
        Direction forward = Direction.NORTH;
        float yawAngle;
        float pitchAngle;
        float modelYaw;
        float modelPitch;
        SpriteId lensTexture = UNLIT_LENS_TEXTURE;
        int lensTint = -1;
    }

    /**
     * The body or lens model, articulated from its state when it is built: rendering is
     * deferred, so a pose written onto the shared parts at submit would be overwritten by
     * the next beacon's before either was drawn.
     */
    private static final class BeaconModel extends Model<State> {
        private final ModelPart yoke;
        private final ModelPart head;
        private final ModelPart lens;

        BeaconModel(ModelPart root) {
            super(root, RenderTypes::entitySolid);
            yoke = root.hasChild("yoke") ? root.getChild("yoke") : null;
            head = yoke != null ? yoke.getChild("head") : null;
            lens = root.hasChild("lens") ? root.getChild("lens") : null;
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
            if (yoke != null) {
                yoke.yRot = state.modelYaw;
                yoke.xRot = 0.0f;
                head.yRot = 0.0f;
                head.xRot = state.modelPitch;
            }
            if (lens != null) {
                lens.yRot = state.modelYaw;
                lens.xRot = state.modelPitch;
            }
        }
    }

    /**
     * The copper block sprite with its vertical texture coordinate squeezed into the middle
     * half, which keeps resource-pack details along the texture edges while excluding all
     * four corners, where stylized copper textures commonly place rivets.
     */
    private record CopperStripMapping(TextureAtlasSprite sprite) implements UvMapping {
        @Override
        public float getU(float offset) {
            return sprite.getU(offset);
        }

        @Override
        public float getV(float offset) {
            return sprite.getV(copperTextureV(offset));
        }
    }
}
