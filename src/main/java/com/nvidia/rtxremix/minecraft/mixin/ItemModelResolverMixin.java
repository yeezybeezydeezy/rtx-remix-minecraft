/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.entity.HeldItemFlame;
import com.nvidia.rtxremix.minecraft.entity.HeldItemFlameSource;
import com.nvidia.rtxremix.minecraft.light.HeldLightState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Decides, while the stack and its holder are still known, whether a hand-held
 * item carries a burning flame, and records it on the item's render state for
 * the submit that has the item's pose; see {@link HeldItemFlameSource}.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {

    @Inject(method = "updateForTopItem", at = @At("RETURN"))
    private void rtxremix$recordHeldFlame(ItemStackRenderState output, ItemStack item,
                                          ItemDisplayContext displayContext, Level level,
                                          ItemOwner owner, int seed, CallbackInfo ci) {
        SimpleParticleType flame = null;
        if (!item.isEmpty() && HeldItemFlame.isHandMode(displayContext)) {
            flame = HeldItemFlame.particleFor(item);
            // An item_display posed with a hand transform has no living holder,
            // and nothing in HeldLightState can put out a prop, so it burns.
            if (flame != null && owner instanceof LivingEntity holder && !HeldLightState.isLit(holder, item)) {
                flame = null;
            }
        }
        ((HeldItemFlameSource) output).rtxremix$setHeldFlame(flame);
    }
}
