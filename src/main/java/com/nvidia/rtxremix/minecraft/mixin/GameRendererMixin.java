/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nvidia.rtxremix.minecraft.RemixRenderer;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.profiling.ProfileScope;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moves the first-person arm/item draw into Remix while preserving Minecraft's
 * own hand animation and selection logic, submits and composites the Remix frame
 * before the screen effects, brackets the GUI tail of its render method with a
 * profiling scope, and has input capture follow the Remix menus at the start of
 * each frame.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Redirect(
            method = "renderItemInHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V"
            ))
    private void rtxremix$captureFirstPersonModel(
            FirstPersonHandsAndItemsRenderer handsRenderer,
            float tickDelta,
            PoseStack matrices,
            SubmitNodeCollector collector,
            PlayerRenderState playerState,
            FirstPersonHandsAndItemsRenderState handsState) {
        RemixRenderer renderer = RtxRemixMod.renderer;
        if (!RtxRemixMod.shouldCaptureFirstPersonModel()) {
            handsRenderer.submitHandsWithItems(tickDelta, matrices, collector, playerState, handsState);
            return;
        }

        renderer.captureFirstPersonModel(handsRenderer, tickDelta, matrices, playerState, handsState);
    }

    /**
     * After the first-person model, which renderItemInHand captured, and before the
     * screen effects (fire, powder snow, the portal and nausea overlays) draw over the
     * frame.
     */
    @Inject(
            method = "render3dHud",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submit(FLnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Z)V"
            ))
    private void rtxremix$submitBeforeScreenOverlays(CallbackInfo ci) {
        RtxRemixMod.submitRemixFrameIfReady();
    }

    /** Covers any path where the 3D HUD is skipped. */
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void rtxremix$submitAtRenderWorldTail(CallbackInfo ci) {
        RtxRemixMod.submitRemixFrameIfReady();
    }

    /**
     * The GUI tail of GameRenderer.render: HUD, in-game screen or overlay, toasts,
     * and the final GUI flush. WorldRender.END accounts for the world half of the
     * frame.
     */
    private ProfileScope rtxremix$guiScope;

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"
            ))
    private void rtxremix$beginGuiScope(CallbackInfo ci) {
        rtxremix$closeGuiScope();
        if (ProfileScope.isJavaScopeEnabled()) {
            rtxremix$guiScope = ProfileScope.begin("GameRenderer.render.gui")
                    .plotOnClose("Java.GameRenderer.render.gui.ms");
        }
    }

    /** Before any screen draws, so the frame's hover state already matches input capture. */
    @Inject(method = "render", at = @At("HEAD"))
    private void rtxremix$followRemixMenuVisibility(CallbackInfo ci) {
        RtxRemixMod.followRemixMenuVisibility();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void rtxremix$endGuiScope(CallbackInfo ci) {
        rtxremix$closeGuiScope();
    }

    private void rtxremix$closeGuiScope() {
        ProfileScope scope = rtxremix$guiScope;
        if (scope != null) {
            rtxremix$guiScope = null;
            scope.close();
        }
    }
}
