/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nvidia.rtxremix.minecraft.entity.HeldItemEffectsRenderer;
import com.nvidia.rtxremix.minecraft.entity.HeldItemFlameSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.core.particles.SimpleParticleType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Submits a hand-held item's flame and smoke with its first layer, while the
 * pose stack still holds the item's block-space pose, display transform
 * included. One hook covers the first-person view model, other players and any
 * mob holding a torch.
 */
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public abstract class ItemLayerRenderStateMixin {

    @Shadow
    private ItemTransform itemTransform;

    @Shadow
    @Final
    ItemStackRenderState this$0;

    @Inject(
            method = "submit",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void rtxremix$submitHeldFlame(PoseStack poseStack, SubmitNodeCollector collector,
                                          int lightCoords, int overlayCoords, int outlineColor,
                                          CallbackInfo ci) {
        SimpleParticleType flame = ((HeldItemFlameSource) this$0).rtxremix$claimHeldFlame();
        if (flame != null) {
            HeldItemEffectsRenderer.submit(poseStack, collector,
                    ((ItemStackRenderStateAccessor) this$0).rtxremix$getDisplayContext(),
                    flame, itemTransform.scale().x());
        }
    }
}
