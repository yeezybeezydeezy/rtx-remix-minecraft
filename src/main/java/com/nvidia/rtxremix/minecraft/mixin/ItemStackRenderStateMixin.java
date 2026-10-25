/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nvidia.rtxremix.minecraft.entity.HeldItemFlameSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carries a hand-held item's flame from extraction to submit; see {@link HeldItemFlameSource}. */
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements HeldItemFlameSource {

    private @Nullable SimpleParticleType rtxremix$heldFlame;
    private boolean rtxremix$heldFlameClaimed;

    @Override
    public void rtxremix$setHeldFlame(@Nullable SimpleParticleType flame) {
        rtxremix$heldFlame = flame;
    }

    @Override
    public @Nullable SimpleParticleType rtxremix$claimHeldFlame() {
        if (rtxremix$heldFlameClaimed) {
            return null;
        }
        rtxremix$heldFlameClaimed = true;
        return rtxremix$heldFlame;
    }

    @Override
    public void rtxremix$beginSubmit() {
        rtxremix$heldFlameClaimed = false;
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void rtxremix$clearHeldFlame(CallbackInfo ci) {
        rtxremix$heldFlame = null;
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void rtxremix$beginSubmit(PoseStack poseStack, SubmitNodeCollector collector,
                                      int lightCoords, int overlayCoords, int outlineColor,
                                      CallbackInfo ci) {
        rtxremix$beginSubmit();
    }
}
