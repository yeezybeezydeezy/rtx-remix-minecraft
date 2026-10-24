/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Implemented on a sprite's animation state, which vanilla does not link back to its atlas sprite. */
public interface AnimatedSpriteOwner {
    void rtxremix$setSprite(TextureAtlasSprite sprite);
}
