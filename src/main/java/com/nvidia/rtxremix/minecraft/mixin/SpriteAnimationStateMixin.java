/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.nvidia.rtxremix.minecraft.texture.AnimatedSpriteOwner;
import com.nvidia.rtxremix.minecraft.texture.AtlasAnimationForwarder;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards each animated block sprite's frame changes to Remix. Minecraft 26.3
 * animates atlas sprites on the GPU, drawing pre-uploaded frame textures into the
 * atlas, so there is no CPU upload to observe; instead, when a tick advances a
 * sprite to a different frame, the new frame's pixels are forwarded from the
 * sprite's source image. Interpolated sprites forward the frame they blend from,
 * which changes at the same ticks.
 */
@Mixin(SpriteContents.AnimationState.class)
public abstract class SpriteAnimationStateMixin implements AnimatedSpriteOwner {

    @Shadow private int frame;
    @Shadow private boolean isDirty;
    @Shadow @Final private SpriteContents.AnimatedTexture animationInfo;

    private TextureAtlasSprite rtxremix$sprite;

    @Override
    public void rtxremix$setSprite(TextureAtlasSprite sprite) {
        rtxremix$sprite = sprite;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void rtxremix$onFrameAdvanced(CallbackInfo ci) {
        TextureAtlasSprite sprite = rtxremix$sprite;
        if (!isDirty || sprite == null) return;

        SpriteContents contents = sprite.contents();
        NativeImage image = ((SpriteContentsAccessor) contents).getImage();
        if (image == null) return;

        int width = contents.width();
        int height = contents.height();
        int imgWidth = image.getWidth();
        int imgHeight = image.getHeight();
        int framesPerRow = Math.max(1, imgWidth / width);
        int frameIndex = animationInfo.frames.get(frame).index;
        int srcX = (frameIndex % framesPerRow) * width;
        int srcY = (frameIndex / framesPerRow) * height;

        long basePtr = ((NativeImageAccessor) (Object) image).getPointer();
        long pixelPtr = basePtr + ((long) srcY * imgWidth + srcX) * 4;
        int rowPitch = imgWidth * 4;

        AtlasAnimationForwarder.addPatch(contents.name(), sprite.getX(), sprite.getY(), srcX, srcY,
                                         width, height, imgWidth, imgHeight, pixelPtr, rowPitch);
    }
}
