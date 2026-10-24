/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.sky;

/**
 * The star quads vanilla baked into its sky renderer's star buffer, as packed xyz positions,
 * four vertices per star. Vanilla builds the buffer whenever it creates a sky renderer, which
 * it does before the first world frame.
 */
public final class VanillaStars {

    private static volatile float[] vertices = new float[0];

    private VanillaStars() {
    }

    public static void publish(float[] positions) {
        vertices = positions;
    }

    /** A copy of the published positions; empty until vanilla has built its stars. */
    public static float[] copyVertices() {
        return vertices.clone();
    }
}
