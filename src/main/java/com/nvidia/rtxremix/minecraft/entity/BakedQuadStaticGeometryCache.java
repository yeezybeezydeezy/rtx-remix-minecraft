/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

/**
 * Cache attached to vanilla baked quads by a mixin.
 *
 * <p>The digest contains only bake-time geometry. Per-render tint and brightness
 * remain outside the cache and are folded into the mesh key on every draw.
 */
public interface BakedQuadStaticGeometryCache {

    /**
     * The quad packed as 1.21's baked vertex data: eight ints per vertex, holding
     * position x, y, z and texture u, v as float bits at offsets 0, 1, 2, 4 and 5,
     * white at 3, and the face normal as signed bytes at 7. Built on first use and
     * shared, so callers must not write to it.
     */
    int[] rtxremix$vertexData();

    boolean rtxremix$matchesStaticGeometry(int[] vertexData,
                                            int texcoordBoundsMin,
                                            int texcoordBoundsMax);

    long rtxremix$getStaticGeometryHash();

    void rtxremix$setStaticGeometryHash(long hash,
                                         int[] vertexData,
                                         int texcoordBoundsMin,
                                         int texcoordBoundsMax);
}
