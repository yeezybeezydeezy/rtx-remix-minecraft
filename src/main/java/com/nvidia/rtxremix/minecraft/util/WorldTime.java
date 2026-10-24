/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Reads and sets the time of day, which Minecraft keeps as each dimension's default world
 * clock rather than as a per-level day time.
 */
public final class WorldTime {

    private WorldTime() {
    }

    /** Total ticks on the level's default clock; zero for a dimension without one. */
    public static long dayTime(Level level) {
        return level.getDefaultClockTime();
    }

    /**
     * Sets the default clock of every dimension the server runs. Dimensions sharing a clock
     * are set once per dimension, to the same value. Must run on the server thread; the
     * server syncs the new time to its clients.
     */
    public static void setDayTime(MinecraftServer server, long ticks) {
        for (ServerLevel world : server.getAllLevels()) {
            world.dimensionType().defaultClock()
                    .ifPresent(clock -> server.clockManager().setTotalTicks(clock, ticks));
        }
    }
}
