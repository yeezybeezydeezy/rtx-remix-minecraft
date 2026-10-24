/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.texture.BlockTextureManager;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import com.nvidia.rtxremix.minecraft.terrain.capture.SectionBlockData;
import com.nvidia.rtxremix.minecraft.terrain.capture.BlockQuadEmitter;
import com.nvidia.rtxremix.minecraft.terrain.capture.BlockDataQueue;
import com.nvidia.rtxremix.minecraft.terrain.VanillaSectionBuilds;
import com.nvidia.rtxremix.minecraft.profiling.ProfileScope;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts chunk section builds to capture block/fluid mesh data for
 * RTX Remix <b>inline</b>, piggybacking on MC's own block loop instead
 * of running a separate pass.
 *
 * <ul>
 *   <li>{@code getBlockState} redirect: the loop reads each position's state
 *       exactly once, air included, so this captures the opacity bitmap, face
 *       masks, and light entries for every block of the section.</li>
 *   <li>{@code getRenderShape} redirect: emits a MODEL block's quads directly
 *       to the Remix interleaved buffer and reports it invisible while
 *       capturing, so vanilla skips its layer buffers and tesselation for
 *       geometry that is never drawn.</li>
 *   <li>{@code FluidRenderer.tesselate} redirect: emits fluid quads directly,
 *       skipping GL vertex generation.</li>
 *   <li>RETURN inject: finalises the section mesh data and enqueues it
 *       for the render thread.</li>
 * </ul>
 */
@Mixin(SectionCompiler.class)
public class SectionBuilderMixin {

    private static final ThreadLocal<ProfileScope> rtxremix$tracyOuter = new ThreadLocal<>();
    private static final ThreadLocal<ProfileScope> rtxremix$tracyBlockLoop = new ThreadLocal<>();
    private static final ThreadLocal<BlockDataQueue.CaptureToken> rtxremix$blockDataCaptureToken = new ThreadLocal<>();

    /**
     * Whether this compile() is capturing for Remix, latched once at HEAD.
     *
     * <p>{@code rtxEnabled} is volatile so worker threads see a toggle promptly,
     * which means every redirect below would otherwise read it independently and
     * a toggle could land <em>between</em> two of them inside one build, routing
     * some blocks of a section into the Remix emitter and the rest into vanilla's
     * GL path. Reading one latched value keeps a build wholly in one mode;
     * {@code setRtxEnabled} forces a full terrain reload, so sections built in
     * the losing mode are rebuilt immediately afterwards.
     */
    private static final ThreadLocal<Boolean> rtxremix$captureForBuild =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Latched RTX capture state for the build in progress on this thread. */
    private static boolean rtxremix$capturing() {
        return rtxremix$captureForBuild.get();
    }

    @Shadow @Final
    private BlockStateModelSet blockModelSet;

    @Shadow @Final
    private FluidStateModelSet fluidModelSet;

    // HEAD: init per-section emission state
    @Inject(method = "compile", at = @At("HEAD"))
    private void rtxremix$onBuildHead(
            SectionPos sectionPos,
            RenderSectionRegion region,
            VertexSorting vertexSorting,
            SectionBufferBuilderPack builders,
            CallbackInfoReturnable<SectionCompiler.Results> cir) {

        // Latched before any early return below, and unconditionally, so a value
        // left over from this thread's previous build cannot decide this one.
        boolean capturing = VanillaSectionBuilds.beginBuild(RtxRemixMod::isRtxEnabled);
        rtxremix$captureForBuild.set(capturing);
        if (!capturing) return;

        rtxremix$blockDataCaptureToken.set(BlockDataQueue.beginCapture(sectionPos.getX(), sectionPos.getZ()));
        rtxremix$tracyOuter.set(ProfileScope.begin("SectionBuilder"));
        rtxremix$tracyBlockLoop.set(null);

        if (region == null) return;

        BlockTextureManager tex = null;
        RtxRemixMod mod = RtxRemixMod.getInstance();
        if (mod != null && mod.getRenderer() != null) {
            tex = mod.getRenderer().getTextureManager();
        }

        BlockQuadEmitter.threadLocal().beginSection(sectionPos, tex, this.blockModelSet, this.fluidModelSet);
        rtxremix$tracyBlockLoop.set(ProfileScope.begin("block loop"));
    }

    // getBlockState: capture opacity / face masks / lights for every position, air included
    @Redirect(method = "compile",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState rtxremix$captureBlock(RenderSectionRegion region, BlockPos pos) {
        BlockState state = region.getBlockState(pos);
        if (rtxremix$capturing()) {
            BlockQuadEmitter emitter = BlockQuadEmitter.current();
            if (emitter != null && emitter.isActive()) {
                emitter.processBlock(state, region, pos, state.isSolidRender());
            }
        }
        return state;
    }

    // While capturing, the block's quads are emitted here, so vanilla's raster
    // path for it (layer buffer, lighting, tesselation) is dead work. Reporting the
    // block invisible makes vanilla skip all of it. Vanilla asks only for non-air
    // blocks, and hands only MODEL blocks to the tesselator; ENTITYBLOCK_ANIMATED
    // blocks belong to their block-entity renderer.
    @Redirect(method = "compile",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"))
    private RenderShape rtxremix$renderShapeForVanilla(BlockState state,
            SectionPos sectionPos, RenderSectionRegion region) {
        RenderShape shape = BlockQuadEmitter.getRenderTypeCached(state);
        if (!rtxremix$capturing()) {
            return shape;
        }
        BlockQuadEmitter emitter = BlockQuadEmitter.current();
        if (shape == RenderShape.MODEL && emitter != null && emitter.isActive()) {
            emitter.emitBlock(state, emitter.currentBlockPos(), region);
        }
        return RenderShape.INVISIBLE;
    }

    // FluidRenderer.tesselate: emit fluid quads for Remix, skip GL
    @Redirect(method = "compile",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/renderer/block/FluidRenderer;tesselate(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/block/FluidRenderer$Output;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"))
    private void rtxremix$redirectFluid(FluidRenderer fluidRenderer,
            BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
            BlockState blockState, FluidState fluidState) {
        if (rtxremix$capturing()) {
            BlockQuadEmitter emitter = BlockQuadEmitter.threadLocal();
            if (!emitter.isActive()) return;
            emitter.emitFluid(pos, level, fluidState);
        } else {
            fluidRenderer.tesselate(level, pos, output, blockState, fluidState);
        }
    }

    // RETURN: finalise mesh data and enqueue
    @Inject(method = "compile", at = @At("RETURN"))
    private void rtxremix$onBuildReturn(
            SectionPos sectionPos,
            RenderSectionRegion region,
            VertexSorting vertexSorting,
            SectionBufferBuilderPack builders,
            CallbackInfoReturnable<SectionCompiler.Results> cir) {

        try {
            BlockQuadEmitter emitter = BlockQuadEmitter.threadLocal();
            if (!emitter.isActive()) return;

            ProfileScope blockScope = rtxremix$tracyBlockLoop.get();
            if (blockScope != null) blockScope.close();

            // The token is set at build HEAD before beginSection, so an active emitter
            // implies one exists.
            SectionBlockData blockData = emitter.finishSection(sectionPos);
            BlockDataQueue.enqueue(blockData, rtxremix$blockDataCaptureToken.get());
        } finally {
            ProfileScope outerScope = rtxremix$tracyOuter.get();
            if (outerScope != null) outerScope.close();
            rtxremix$tracyOuter.remove();
            rtxremix$tracyBlockLoop.remove();
            rtxremix$blockDataCaptureToken.remove();
            // A build that throws skips this inject and stays counted; vanilla turns a
            // section build exception into a client crash, so the stale count cannot
            // outlive the session.
            VanillaSectionBuilds.endBuild(rtxremix$capturing());
            rtxremix$captureForBuild.remove();
        }
    }
}
