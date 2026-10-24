/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import com.nvidia.rtxremix.minecraft.texture.GlyphPageMirror;
import java.nio.ByteBuffer;
import net.minecraft.client.gui.font.FontTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives every glyph atlas page a {@link GlyphPageMirror} and copies each glyph into it as
 * the glyph is added.
 *
 * <p>{@code add} is the one place a page is written: it finds the glyph a slot and has the
 * glyph upload itself there. Glyphs reach the GPU through several primitives, some of them
 * a copy from a GPU buffer that never holds the pixels on the CPU, so the mirror reads the
 * slot back from the page's texture once the upload has been issued. A glyph is added once,
 * on first use, so the readback is rare.
 */
@Mixin(FontTexture.class)
public abstract class GlyphAtlasTextureMixin implements GlyphPageMirror.Owner {

    @Shadow @Final private boolean colored;

    private GlyphPageMirror rtxremix$mirror;

    @Override
    public GlyphPageMirror rtxremix$glyphPageMirror() {
        // Created on first use: colored is assigned by the constructor, so a field
        // initialiser could not read it.
        if (rtxremix$mirror == null) {
            rtxremix$mirror = new GlyphPageMirror(!colored);
        }
        return rtxremix$mirror;
    }

    @WrapOperation(
        method = "add",
        at = @At(value = "INVOKE",
                 target = "Lcom/mojang/blaze3d/font/GlyphBitmap;upload(IILcom/mojang/renderpearl/api/textures/GpuTexture;)V")
    )
    private void rtxremix$mirrorGlyphUpload(GlyphBitmap glyph, int x, int y, GpuTexture texture,
                                            Operation<Void> original) {
        original.call(glyph, x, y, texture);
        if (!(texture instanceof GlTexture glTexture)) {
            return;
        }
        int width = glyph.getPixelWidth();
        int height = glyph.getPixelHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int channels = colored ? 4 : 1;
        int format = colored ? GL11.GL_RGBA : GL30.GL_RED;
        int byteCount = width * height * channels;
        ByteBuffer pixels = MemoryUtil.memAlloc(byteCount);
        // Tightly packed rows: a one-channel glyph's rows are rarely a multiple of four bytes.
        int previousAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL45.glGetTextureSubImage(glTexture.glId(), 0, x, y, 0, width, height, 1,
                    format, GL11.GL_UNSIGNED_BYTE, pixels);
            rtxremix$glyphPageMirror().blit(x, y, width, height, channels, pixels, width * channels);
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousAlignment);
            MemoryUtil.memFree(pixels);
        }
    }
}
