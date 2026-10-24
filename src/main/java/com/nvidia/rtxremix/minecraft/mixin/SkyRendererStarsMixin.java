/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import com.nvidia.rtxremix.minecraft.sky.VanillaStars;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

/**
 * Hands the star positions vanilla bakes into its star buffer to the Remix sky, so Remix
 * draws exactly vanilla's star field. The positions exist on the CPU only while the buffer
 * is being built.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererStarsMixin {

    @Inject(
            method = "buildStars",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/MeshData;drawState()Lcom/mojang/blaze3d/vertex/MeshData$DrawState;"))
    private void rtxremix$captureStarVertices(
            CallbackInfoReturnable<?> cir, @Local MeshData mesh) {
        MeshData.DrawState parameters = mesh.drawState();
        VertexFormatElement position = parameters.format().getElement(DefaultVertexFormat.POSITION_SEMANTIC_NAME);
        if (position == null) {
            VanillaStars.publish(new float[0]);
            return;
        }

        int positionOffset = position.offset();
        int vertexCount = parameters.vertexCount();
        int stride = parameters.format().getVertexSize();
        ByteBuffer vertices = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
        float[] positions = new float[vertexCount * 3];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int source = vertex * stride + positionOffset;
            int destination = vertex * 3;
            positions[destination] = vertices.getFloat(source);
            positions[destination + 1] = vertices.getFloat(source + Float.BYTES);
            positions[destination + 2] = vertices.getFloat(source + 2 * Float.BYTES);
        }
        VanillaStars.publish(positions);
    }
}
