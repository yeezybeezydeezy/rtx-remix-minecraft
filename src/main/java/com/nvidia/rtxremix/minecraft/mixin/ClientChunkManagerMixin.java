/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.terrain.SkippedSectionRebuilds;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;

/**
 * Section rebuild bookkeeping raised by the client chunk manager while RTX capture is on.
 */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkManagerMixin {

    /**
     * Drops section rebuilds requested only because the client light engine changed a
     * section's light.
     *
     * <p>Vanilla re-meshes such a section so its baked lightmap follows. While RTX is enabled
     * the section builder captures every section for Remix and reports its blocks invisible
     * to vanilla, so vanilla's section buffers are empty and Remix terrain carries no baked
     * light; the rebuild would only recapture identical geometry, publish an identical page,
     * and retire the old one. The gate is the capture gate SectionBuilderMixin latches, not
     * whether Remix is drawing the world: vanilla has no terrain geometry to relight whenever
     * capture is on, including with world rendering toggled off or the renderer
     * uninitialized. Toggling RTX off reloads the world renderer, which rebuilds every
     * section with vanilla light again.
     */
    @Inject(method = "onLightUpdate", at = @At("HEAD"), cancellable = true)
    private void rtxremix$skipLightOnlyRebuild(LightLayer type, SectionPos pos, CallbackInfo ci) {
        if (RtxRemixMod.isRtxEnabled()) {
            SkippedSectionRebuilds.recordLightOnly();
            ci.cancel();
        }
    }
}
