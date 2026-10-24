/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

/**
 * The capture provider vanilla's feature renderers write into while a {@link RemixSubmitReplay}
 * is building submits for Remix. Outside a replay it is empty and the renderers build their
 * own GPU vertex streams as usual. Render thread only.
 */
public final class CaptureVertexBuilders {

    private static RemixVertexConsumerProvider current;

    private CaptureVertexBuilders() {
    }

    /** The provider a replay is building into, or null outside a replay. */
    public static RemixVertexConsumerProvider current() {
        return current;
    }

    static void begin(RemixVertexConsumerProvider provider) {
        if (current != null) {
            throw new IllegalStateException("A submit replay is already open");
        }
        current = provider;
    }

    static void end() {
        current = null;
    }
}
