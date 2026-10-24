/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.renderpearl.backend.opengl.GlBackend;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the OpenGL context the window is created with to what the Remix GL
 * interop needs, and logs the SDL handle once the window exists.
 *
 * <p>The OpenGL backend sets its context attributes immediately before it
 * creates the window, so the version written here replaces vanilla's 3.3 and
 * keeps its core profile and forward-compatible flag.
 */
@Mixin(GlBackend.class)
public abstract class WindowMixin {

    @Inject(
        method = "createWindow",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/sdl/SDLVideo;SDL_CreateWindow(Ljava/lang/CharSequence;IIJ)J"
        )
    )
    private void rtxremix$requireOpenGl45(String title, int width, int height, long flags,
            CallbackInfoReturnable<Long> cir) {
        // The GL interop imports Remix's images and semaphores through
        // EXT_memory_object and EXT_semaphore, and creates its textures with
        // direct state access.
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_MAJOR_VERSION, 4);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_MINOR_VERSION, 5);
        SDLVideo.SDL_GL_SetAttribute(
                SDLVideo.SDL_GL_CONTEXT_PROFILE_MASK, SDLVideo.SDL_GL_CONTEXT_PROFILE_CORE);
    }

    /** Logs the SDL window handle once the window exists. */
    @Inject(method = "createWindow", at = @At("RETURN"))
    private void rtxremix$onWindowCreated(String title, int width, int height, long flags,
            CallbackInfoReturnable<Long> cir) {
        RtxRemixMod.LOGGER.info("Window created with SDL handle: {}", cir.getReturnValueJ());
    }
}
