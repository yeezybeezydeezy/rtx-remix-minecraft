/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.ARGB;

/**
 * Reads {@link NativeImage} pixels in the packed ABGR layout the texture code works in, which is
 * the image's own memory order read as a little-endian int. {@code NativeImage} exposes that
 * read privately and hands out ARGB publicly.
 */
public final class NativeImagePixels {

    private NativeImagePixels() {
    }

    /** The pixel at (x, y) of an RGBA image as {@code 0xAABBGGRR}. */
    public static int abgr(NativeImage image, int x, int y) {
        return ARGB.toABGR(image.getPixel(x, y));
    }
}
