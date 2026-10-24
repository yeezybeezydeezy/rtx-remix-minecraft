/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.weather;

import net.minecraft.client.Camera;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The rain and snow overlay drawn onto the composited Remix colour image,
 * depth-tested against the interop depth texture so weather is occluded by
 * path-traced geometry.
 *
 * <p>On Minecraft 26.3 this overlay is not drawn yet. The 1.21 implementation
 * captured vanilla's weather meshes from {@code renderSnowAndRain} and redrew them
 * with a core shader; 26.3 extracts weather into a render state, draws it through
 * render pipelines and no longer has core shaders, so the overlay needs a
 * pipeline of its own. Remix still applies its weather conf layers, so surfaces
 * wet and the sky darkens in rain; only the falling streaks and flakes are
 * missing.
 *
 * <p>Render thread only.
 */
public final class RemixWeatherRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemixWeatherRenderer.class);

    private boolean notPortedLogged;

    public void updateCamera(Camera camera, float fovYDegrees, float aspect) {
    }

    public void render(int finalColorTexture, int depthTexture) {
        if (!notPortedLogged) {
            notPortedLogged = true;
            LOGGER.info("Rain and snow streaks are not drawn over the Remix image on this "
                    + "Minecraft version; Remix weather lighting still applies");
        }
    }

    public int getLastDrawCount() {
        return 0;
    }

    public int getLastQuadCount() {
        return 0;
    }

    public void cleanup() {
    }
}
