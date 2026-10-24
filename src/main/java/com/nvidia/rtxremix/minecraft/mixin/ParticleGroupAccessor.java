/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import java.util.Queue;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The live particles of one render group, which vanilla keeps to the group. */
@Mixin(ParticleGroup.class)
public interface ParticleGroupAccessor {
    @Accessor("particles")
    Queue<? extends Particle> rtxremix$getParticles();
}
