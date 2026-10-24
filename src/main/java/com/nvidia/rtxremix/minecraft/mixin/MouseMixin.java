/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.input.RemixMouseAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the shared Remix overlay input policy to Minecraft's mouse callbacks.
 *
 * The gate blocks Minecraft only while the overlay capture session owns input.
 */
@Mixin(MouseHandler.class)
public abstract class MouseMixin implements RemixMouseAccess {
    private static final double rtxremix$HIDDEN_CURSOR_COORDINATE = -Double.MAX_VALUE;

    @Shadow @Final
    private Minecraft minecraft;

    @Shadow
    private @Nullable MouseButtonInfo activeButton;

    @Shadow
    public abstract void onButton(long handle, MouseButtonInfo rawButtonInfo, int action);

    private boolean rtxremix$releasingActiveButton;

    @Override
    public void rtxremix$releaseActiveButton() {
        MouseButtonInfo button = activeButton;
        if (button == null) {
            return;
        }

        rtxremix$releasingActiveButton = true;
        try {
            onButton(minecraft.getWindow().handle(), button, InputConstants.RELEASE);
        } finally {
            rtxremix$releasingActiveButton = false;
        }
    }

    @Shadow
    private double xpos;

    @Shadow
    private double ypos;

    @Shadow
    private boolean ignoreFirstMove;

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void rtxremix$blockMovementWhileMenuOpen(
            long handle, double cursorX, double cursorY, double xrel, double yrel, CallbackInfo ci) {
        if (RtxRemixMod.getRemixInputGate().isCaptureSessionOpen()) {
            xpos = cursorX;
            ypos = cursorY;
            ignoreFirstMove = false;
            ci.cancel();
        }
    }

    /* Screen rendering derives hover state from these coordinates even without a movement event. */
    @Inject(method = "xpos", at = @At("HEAD"), cancellable = true)
    private void rtxremix$hideXWhileMenuOpen(CallbackInfoReturnable<Double> cir) {
        if (RtxRemixMod.getRemixInputGate().isCaptureSessionOpen()) {
            cir.setReturnValue(rtxremix$HIDDEN_CURSOR_COORDINATE);
        }
    }

    @Inject(method = "ypos", at = @At("HEAD"), cancellable = true)
    private void rtxremix$hideYWhileMenuOpen(CallbackInfoReturnable<Double> cir) {
        if (RtxRemixMod.getRemixInputGate().isCaptureSessionOpen()) {
            cir.setReturnValue(rtxremix$HIDDEN_CURSOR_COORDINATE);
        }
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void rtxremix$blockLockWhileMenuOpen(CallbackInfo ci) {
        if (RtxRemixMod.getRemixInputGate().isCaptureSessionOpen()) {
            ci.cancel();
        }
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void rtxremix$blockClickWhileMenuOpen(
            long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        boolean syntheticRelease = rtxremix$releasingActiveButton
                && action == InputConstants.RELEASE
                && rawButtonInfo == activeButton;
        if (RtxRemixMod.getRemixInputGate().isCaptureSessionOpen() && !syntheticRelease) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void rtxremix$blockScrollWhileMenuOpen(long handle, double horizontal, double vertical, CallbackInfo ci) {
        if (RtxRemixMod.getRemixInputGate().isCaptureSessionOpen()) {
            ci.cancel();
        }
    }
}
