/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.sky;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector4fc;

/**
 * Reads the sky, celestial and cloud state that Minecraft keeps as environment attributes
 * sampled at the camera, in the forms the Remix sky, light and cloud code consume.
 *
 * <p>Every value is read through the main camera's attribute probe, the same source vanilla's
 * sky and cloud extraction reads, so Remix agrees with the vanilla sky a biome or timeline
 * would draw.
 */
public final class CelestialState {

    /** Which kind of sky a dimension draws. */
    public enum SkyType {
        /** A sky with a sun, a moon and stars that follow the time of day. */
        NORMAL,
        /** The End's fixed dark sky. */
        END,
        /** No sky at all, as in the Nether. */
        NONE
    }

    private CelestialState() {
    }

    public static SkyType skyType(ClientLevel world) {
        return switch (world.dimensionType().skybox()) {
            case OVERWORLD -> SkyType.NORMAL;
            case END -> SkyType.END;
            case NONE -> SkyType.NONE;
        };
    }

    /**
     * Position in the day cycle as a fraction in [0, 1), where 0 is noon and 0.5 midnight. The
     * sun's angle about the east-west axis is this fraction of a full turn.
     */
    public static float timeOfDay(float tickDelta) {
        float degrees = probe().getValue(EnvironmentAttributes.SUN_ANGLE, tickDelta);
        float fraction = degrees / 360.0f;
        return fraction - (float) Math.floor(fraction);
    }

    public static float starBrightness(float tickDelta) {
        return probe().getValue(EnvironmentAttributes.STAR_BRIGHTNESS, tickDelta);
    }

    /** Moon phase index in [0, 8), 0 being the full moon. */
    public static int moonPhase(float tickDelta) {
        return probe().getValue(EnvironmentAttributes.MOON_PHASE, tickDelta).index();
    }

    /** Cloud colour as RGBA in [0, 1]; a zero alpha means the dimension draws no clouds. */
    public static Vector4fc cloudColor(float tickDelta) {
        return probe().getValue(EnvironmentAttributes.CLOUD_COLOR, tickDelta);
    }

    public static float cloudHeight(float tickDelta) {
        return probe().getValue(EnvironmentAttributes.CLOUD_HEIGHT, tickDelta);
    }

    private static EnvironmentAttributeProbe probe() {
        return Minecraft.getInstance().gameRenderer.mainCamera().attributeProbe();
    }
}
