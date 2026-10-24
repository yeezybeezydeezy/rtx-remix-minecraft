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

    private ProfileScope rtxremix$renderScope;

    @Inject(method = "render", at = @At("HEAD"))
    private void rtxremix$renderHead(CallbackInfo ci) {
        if (rtxremix$renderScope != null) {
            rtxremix$renderScope.close();
        }
        rtxremix$renderScope = ProfileScope.begin("WorldRenderer.render");
        RtxRemixMod.onWorldRenderStart();
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
                    && lightingProvider.lightOnInSection(SectionPos.of(cx, sy, cz))) {
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
