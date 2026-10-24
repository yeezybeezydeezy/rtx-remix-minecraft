/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.renderpearl.backend.opengl.GlSurface;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.RemixRenderer;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the buffer swap inside the OpenGL surface's {@code present} to
 * Remix's present, so Vulkan owns the swapchain whenever the renderer is
 * initialized. Before that, and if initialization fails, the SDL swap runs
 * unchanged.
 */
@Mixin(GlSurface.class)
public abstract class RenderSystemMixin {

    @Redirect(
        method = "present",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_GL_SwapWindow(J)Z")
    )
    private boolean rtxremix$redirectSwapBuffers(long window) {
        RemixRenderer renderer = RtxRemixMod.renderer;
        if (renderer != null && renderer.isInitialized()) {
            renderer.presentFrameVk();
            return true;
        }
        return SDLVideo.SDL_GL_SwapWindow(window);
    }
}
