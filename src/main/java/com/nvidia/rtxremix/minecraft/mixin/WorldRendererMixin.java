/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.profiling.ProfileScope;
import com.nvidia.rtxremix.minecraft.terrain.WorldRendererTerrainAccess;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.nvidia.rtxremix.minecraft.RemixRenderer;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;

/**
 * Reroutes vanilla's world render into the Remix capture while a Remix world is
 * active.
 *
 * <p>Minecraft renders a level in three stages: it extracts render state, has
 * every entity, block entity and particle submit its geometry, and then runs a
 * frame graph of GPU passes over the result. The first two stages stay vanilla,
 * because the Remix capture is fed from them and section compiles and occlusion
 * updates run around them. While Remix draws the world, the frame graph is not
 * executed: every pass in it draws what Remix owns (sky, terrain, entity
 * features, particles, clouds, weather), and the main target keeps the clear
 * {@code GameRenderer} gave it until the Remix image is composited onto it.
 *
 * <p>Section rebuild scheduling is exposed to the terrain pipeline through
 * {@link WorldRendererTerrainAccess}.
 */
@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixin implements WorldRendererTerrainAccess {

    @Shadow
    private @Nullable ViewArea viewArea;

    @Shadow
    private @Nullable SectionRenderDispatcher sectionRenderDispatcher;

    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    private ProfileScope rtxremix$renderScope;

    @Inject(method = "render", at = @At("HEAD"))
    private void rtxremix$renderHead(
            GraphicsResourceAllocator resourceAllocator,
            boolean renderOutline,
            CameraRenderState cameraState,
            GpuBufferSlice terrainFog,
            Vector4f fogColor,
            boolean shouldRenderSky,
            boolean consistentDepthRequired,
            CallbackInfo ci) {
        if (rtxremix$renderScope != null) {
            rtxremix$renderScope.close();
        }
        rtxremix$renderScope = ProfileScope.begin("WorldRenderer.render");
        RtxRemixMod.onWorldRenderStart(renderOutline ? levelRenderState.blockOutlineRenderState : null);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V"))
    private void rtxremix$skipVanillaWorldDraw(
            FrameGraphBuilder frame,
            GraphicsResourceAllocator resourceAllocator,
            FrameGraphBuilder.Inspector inspector) {
        if (RtxRemixMod.isRemixWorldRendering()) {
            return;
        }
        frame.execute(resourceAllocator, inspector);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void rtxremix$renderReturn(CallbackInfo ci) {
        RtxRemixMod.onWorldRenderEnd();
        if (rtxremix$renderScope != null) {
            rtxremix$renderScope.close();
            rtxremix$renderScope = null;
        }
    }

    /**
     * Leaves entities to the Remix entity pass while Remix draws them. The pass captures
     * every entity in range itself, visible or not, because a path tracer needs the ones
     * vanilla culls for shadows and reflections, so vanilla's submit would only build
     * GPU geometry nothing draws.
     */
    @WrapOperation(
            method = "submitEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void rtxremix$leaveEntitiesToRemix(
            EntityRenderDispatcher dispatcher,
            EntityRenderState state,
            CameraRenderState camera,
            double x,
            double y,
            double z,
            PoseStack poseStack,
            SubmitNodeCollector output,
            Operation<Void> original) {
        if (RtxRemixMod.isRemixWorldRendering() && RtxRemixMod.isEntitiesActive()) {
            return;
        }
        original.call(dispatcher, state, camera, x, y, z, poseStack, output);
    }

    /**
     * Captures block entities where vanilla submits them, instead of submitting them for
     * OpenGL. The block entity's own renderer submits at the origin into the capture, so
     * its model is evaluated once.
     */
    @WrapOperation(
            method = "submitBlockEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void rtxremix$captureBlockEntityInPlace(
            BlockEntityRenderDispatcher dispatcher,
            BlockEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            Operation<Void> original) {
        RtxRemixMod mod = RtxRemixMod.getInstance();
        RemixRenderer renderer = mod == null ? null : mod.getRenderer();
        ClientLevel level = Minecraft.getInstance().level;
        BlockEntity blockEntity = level == null ? null : level.getBlockEntity(state.blockPos);
        boolean captured = renderer != null
                && blockEntity != null
                && renderer.renderBlockEntityInPlace(blockEntity,
                        (captureMatrices, captureCollector) ->
                                original.call(dispatcher, state, captureMatrices, captureCollector, camera));
        if (!captured) {
            original.call(dispatcher, state, poseStack, collector, camera);
        }
    }

    /** Captures a block's breaking crack where vanilla submits it, as a terrain decal. */
    @WrapOperation(
            method = "submitBlockDestroyAnimation",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitBreakingBlockModel(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/util/List;IZ)V"))
    private void rtxremix$captureBlockDamageInPlace(
            SubmitNodeCollector collector,
            PoseStack poseStack,
            List<BlockStateModelPart> parts,
            int progress,
            boolean translucent,
            Operation<Void> original,
            @Local BlockBreakingRenderState state) {
        RtxRemixMod mod = RtxRemixMod.getInstance();
        RemixRenderer renderer = mod == null ? null : mod.getRenderer();
        BlockPos pos = state.blockPos();
        Vec3 offset = state.blockState().getOffset(pos);
        boolean captured = renderer != null
                && renderer.renderBlockDamageInPlace(pos, progress, (matrices, consumer) -> {
                    matrices.translate(offset.x, offset.y, offset.z);
                    QuadInstance instance = new QuadInstance();
                    for (BlockStateModelPart part : parts) {
                        for (Direction direction : rtxremix$ALL_FACES) {
                            for (BakedQuad quad : part.getQuads(direction)) {
                                consumer.putBakedQuad(matrices.last(), quad, instance);
                            }
                        }
                    }
                });
        if (!captured) {
            original.call(collector, poseStack, parts, progress, translucent);
        }
    }

    private static final Direction[] rtxremix$ALL_FACES = {
            null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    @Override
    public LongSet rtxremix$builtSectionPositions() {
        LongOpenHashSet built = new LongOpenHashSet();
        if (viewArea == null) {
            return built;
        }
        for (SectionRenderDispatcher.RenderSection section : viewArea.sections) {
            if (section.getSectionMesh() == CompiledSectionMesh.UNCOMPILED) {
                continue;
            }
            built.add(section.getSectionNode());
        }
        return built;
    }

    @Override
    public LongSet rtxremix$queueSectionsForRemix(LongSet sections) {
        ClientLevel level = Minecraft.getInstance().level;
        if (viewArea == null || sectionRenderDispatcher == null || level == null || sections.isEmpty()) {
            return LongSets.emptySet();
        }

        // Same gate as vanilla's compile scheduling: a section built before its
        // light is ready would bake bad light.
        LevelLightEngine lightingProvider = level.getLightEngine();

        ArrayList<SectionRenderDispatcher.RenderSection> loadedSections = new ArrayList<>();
        for (SectionRenderDispatcher.RenderSection section : viewArea.sections) {
            long node = section.getSectionNode();
            if (!sections.contains(node)) {
                continue;
            }
            int cx = SectionPos.x(node);
            int sy = SectionPos.y(node);
            int cz = SectionPos.z(node);
            LevelChunk worldChunk = level.getChunkSource().getChunk(cx, cz, false);
            if (worldChunk == null) {
                continue;
            }
            int index = worldChunk.getSectionIndexFromSectionY(sy);
            if (index >= 0 && index < worldChunk.getSectionsCount()
                    && !worldChunk.getSection(index).hasOnlyAir()
                    && lightingProvider.lightOnInColumn(SectionPos.asLong(cx, 0, cz))) {
                loadedSections.add(section);
            }
        }

        // Flag nearby sections first so visible holes close promptly while the
        // Remix scene repopulates.
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        double cameraX = camera.position().x;
        double cameraY = camera.position().y;
        double cameraZ = camera.position().z;
        loadedSections.sort(Comparator.comparingDouble(section -> {
            long node = section.getSectionNode();
            double dx = SectionPos.sectionToBlockCoord(SectionPos.x(node)) + 8.0 - cameraX;
            double dy = SectionPos.sectionToBlockCoord(SectionPos.y(node)) + 8.0 - cameraY;
            double dz = SectionPos.sectionToBlockCoord(SectionPos.z(node)) + 8.0 - cameraZ;
            return dx * dx + dy * dy + dz * dz;
        }));

        // Vanilla compiles a dirty section once its occlusion walk reaches it.
        LongOpenHashSet queued = new LongOpenHashSet(loadedSections.size());
        for (SectionRenderDispatcher.RenderSection section : loadedSections) {
            long node = section.getSectionNode();
            Minecraft.getInstance().levelExtractor.setSectionDirty(
                    SectionPos.x(node), SectionPos.y(node), SectionPos.z(node));
            queued.add(node);
        }
        return queued;
    }

    @Override
    public void rtxremix$scheduleResidentSectionRebuild(int cx, int sy, int cz) {
        if (viewArea == null || viewArea.sections.getValue(SectionPos.asLong(cx, sy, cz)) == null) {
            return;
        }
        Minecraft.getInstance().levelExtractor.setSectionDirty(cx, sy, cz);
    }
}
