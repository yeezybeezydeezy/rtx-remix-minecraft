/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.terrain.ChunkBuilderBlockEntityAccess;
import com.nvidia.rtxremix.minecraft.terrain.SectionLeftRenderSetQueue;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into vanilla's render section lifecycle.
 *
 * <ul>
 *   <li><b>setSectionNode / reset:</b> Clear the per-slot block-entity flag when
 *       vanilla reuses or empties a section.
 *   <li><b>setSectionMesh:</b> Tracks whether the section contains block
 *       entities and flags a mesh with no renderable layers as a removal
 *       candidate. The render thread validates the live world section before
 *       removing Remix terrain, because while RTX captures terrain every
 *       vanilla mesh is empty, and vanilla can publish temporary empty data.
 * </ul>
 *
 * <p>Vanilla publishes a compiled mesh from the worker that compiled it, so the
 * flag is volatile and the removal queue is concurrent.
 */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class BuiltChunkMixin implements ChunkBuilderBlockEntityAccess {

    @Shadow
    public abstract long getSectionNode();

    private volatile boolean rtxremix$hasBlockEntities;

    @Override
    public boolean rtxremix$hasBlockEntities() {
        return rtxremix$hasBlockEntities;
    }

    @Inject(method = "setSectionNode(J)V", at = @At("HEAD"))
    private void rtxremix$onSetSectionNode(long sectionNode, CallbackInfo ci) {
        rtxremix$hasBlockEntities = false;
    }

    @Inject(method = "setSectionMesh", at = @At("HEAD"))
    private void rtxremix$onSectionMeshSet(SectionMesh mesh, CallbackInfoReturnable<SectionMesh> cir) {
        rtxremix$hasBlockEntities = !mesh.getRenderableBlockEntities().isEmpty();

        if (mesh.hasRenderableLayers()) {
            return;
        }
        long node = getSectionNode();
        if (node == SectionPos.asLong(-1, -1, -1)) {
            return;
        }
        SectionLeftRenderSetQueue.enqueueEmptyCandidate(
                SectionPos.x(node), SectionPos.y(node), SectionPos.z(node));
    }

    @Inject(method = "reset()V", at = @At("HEAD"))
    private void rtxremix$onClear(CallbackInfo ci) {
        rtxremix$hasBlockEntities = false;
    }
}
