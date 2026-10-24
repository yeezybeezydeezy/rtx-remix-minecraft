/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.util;

import com.nvidia.rtxremix.minecraft.mixin.TextureAtlasSpriteAccessor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Where a sprite's own pixels sit in its atlas. On Minecraft 26.3 every sprite is
 * stitched with a padding border, so {@link TextureAtlasSprite#getX()} is the
 * corner of the padded cell and the pixels the sprite's UVs address start
 * {@code padding} texels further in.
 */
public final class SpritePixels {

    private SpritePixels() {
    }

    public static int x(TextureAtlasSprite sprite) {
        return sprite.getX() + padding(sprite);
    }

    public static int y(TextureAtlasSprite sprite) {
        return sprite.getY() + padding(sprite);
    }

    private static int padding(TextureAtlasSprite sprite) {
        return ((TextureAtlasSpriteAccessor) sprite).rtxremix$getPadding();
    }
}
