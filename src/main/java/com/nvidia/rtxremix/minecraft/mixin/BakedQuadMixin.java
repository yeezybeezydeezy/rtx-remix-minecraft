/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.entity.BakedQuadStaticGeometryCache;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Arrays;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.model.geom.builders.UVPair;
import org.joml.Vector3fc;

/** Stores the bake-time geometry digest used by Remix entity mesh pooling. */
@Mixin(BakedQuad.class)
public class BakedQuadMixin implements BakedQuadStaticGeometryCache {

    private int[] rtxremix$vertexData;

    @Override
    public int[] rtxremix$vertexData() {
        int[] data = rtxremix$vertexData;
        if (data == null) {
            BakedQuad quad = (BakedQuad) (Object) this;
            Vector3fc normal = quad.direction().getUnitVec3f();
            int packedNormal = (((int) (normal.x() * 127.0f)) & 0xFF)
                    | ((((int) (normal.y() * 127.0f)) & 0xFF) << 8)
                    | ((((int) (normal.z() * 127.0f)) & 0xFF) << 16);
            data = new int[32];
            for (int v = 0; v < 4; v++) {
                int off = v * 8;
                Vector3fc position = quad.position(v);
                long uv = quad.packedUV(v);
                data[off] = Float.floatToRawIntBits(position.x());
                data[off + 1] = Float.floatToRawIntBits(position.y());
                data[off + 2] = Float.floatToRawIntBits(position.z());
                data[off + 3] = 0xFFFFFFFF;
                data[off + 4] = Float.floatToRawIntBits(UVPair.unpackU(uv));
                data[off + 5] = Float.floatToRawIntBits(UVPair.unpackV(uv));
                data[off + 7] = packedNormal;
            }
            rtxremix$vertexData = data;
        }
        return data;
    }

    private int[] rtxremix$staticGeometrySnapshot;
    private long rtxremix$staticGeometryHash;
    private int rtxremix$staticGeometryBoundsMin;
    private int rtxremix$staticGeometryBoundsMax;

    @Override
    public boolean rtxremix$matchesStaticGeometry(int[] vertexData,
                                                   int texcoordBoundsMin,
                                                   int texcoordBoundsMax) {
        return rtxremix$staticGeometryBoundsMin == texcoordBoundsMin
                && rtxremix$staticGeometryBoundsMax == texcoordBoundsMax
                && Arrays.equals(rtxremix$staticGeometrySnapshot, vertexData);
    }

    @Override
    public long rtxremix$getStaticGeometryHash() {
        return rtxremix$staticGeometryHash;
    }

    @Override
    public void rtxremix$setStaticGeometryHash(long hash,
                                                int[] vertexData,
                                                int texcoordBoundsMin,
                                                int texcoordBoundsMax) {
        if (rtxremix$staticGeometrySnapshot == null
                || rtxremix$staticGeometrySnapshot.length != vertexData.length) {
            rtxremix$staticGeometrySnapshot = Arrays.copyOf(vertexData, vertexData.length);
        } else {
            System.arraycopy(vertexData, 0, rtxremix$staticGeometrySnapshot, 0,
                    vertexData.length);
        }
        rtxremix$staticGeometryBoundsMin = texcoordBoundsMin;
        rtxremix$staticGeometryBoundsMax = texcoordBoundsMax;
        rtxremix$staticGeometryHash = hash;
    }
}
