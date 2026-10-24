/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nvidia.rtxremix.minecraft.entity.CaptureVertexBuilders;
import com.nvidia.rtxremix.minecraft.entity.RemixVertexConsumerProvider;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Points a feature renderer's vertex builder at the Remix capture while a submit replay is
 * open, so the renderer's own code writes the vertices Remix captures. Outside a replay the
 * renderer stages its vertices for the GPU as usual.
 */
@Mixin(RenderTypeFeatureRenderer.class)
public abstract class RenderTypeFeatureRendererMixin {

    @Inject(method = "getVertexBuilder", at = @At("HEAD"), cancellable = true)
    private void rtxremix$captureVertexBuilder(RenderType renderType,
                                               CallbackInfoReturnable<VertexConsumer> cir) {
        RemixVertexConsumerProvider provider = CaptureVertexBuilders.current();
        if (provider != null) {
            cir.setReturnValue(provider.getBuffer(renderType));
        }
    }
}
