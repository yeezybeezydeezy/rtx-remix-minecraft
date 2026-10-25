/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import net.minecraft.core.particles.SimpleParticleType;
import org.jspecify.annotations.Nullable;

/**
 * Implemented on an item's render state. Minecraft 26.3 resolves the item stack
 * into that state when the frame is extracted and submits the state later, when
 * the stack and its holder are gone, so the flame is decided at extraction and
 * carried here to the submit that has the item's pose.
 */
public interface HeldItemFlameSource {

    /** The burning flame's particle, or null when the item has no lit flame. */
    void rtxremix$setHeldFlame(@Nullable SimpleParticleType flame);

    /** The flame for the first layer submitted since the last call to {@link #rtxremix$beginSubmit}, then null. */
    @Nullable SimpleParticleType rtxremix$claimHeldFlame();

    void rtxremix$beginSubmit();
}
