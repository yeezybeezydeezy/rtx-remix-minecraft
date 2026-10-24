/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.renderpearl.api.device.GpuBackend;
import com.mojang.renderpearl.backend.opengl.GlBackend;
import net.minecraft.client.PreferredGraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Minecraft on its OpenGL backend whatever graphics API the player
 * prefers. Remix composites through a GL-Vulkan interop on the window's GL
 * context, so a Vulkan-backed window would leave nothing to import Remix's
 * images into.
 */
@Mixin(PreferredGraphicsApi.class)
public abstract class PreferredGraphicsApiMixin {

    @Inject(method = "getBackendsToTry", at = @At("HEAD"), cancellable = true)
    private void rtxremix$openGlOnly(CallbackInfoReturnable<GpuBackend[]> cir) {
        cir.setReturnValue(new GpuBackend[]{new GlBackend()});
    }
}
