/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import java.util.Map;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Sprite sets keyed by particle type id. The value class is private in vanilla, so
 * callers read it through the public {@code SpriteSet} interface it implements.
 */
@Mixin(ParticleResources.class)
public interface ParticleResourcesAccessor {
    @Accessor("spriteSets")
    Map<Identifier, ?> rtxremix$getSpriteSets();
}
