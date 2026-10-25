/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import com.nvidia.rtxremix.minecraft.mixin.RenderTypeFeatureRendererAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.LeashFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.MovingBlockFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;

/**
 * Turns what a renderer submits into captured vertices, by running vanilla's own feature
 * renderers against the capture provider.
 *
 * <p>Minecraft renders an entity, block entity or held item in two steps: its renderer
 * submits models, items, text and custom geometry to a {@link SubmitNodeCollector}, and a
 * feature renderer per kind of submit later writes their vertices for the GPU. A replay
 * gives the renderer a private {@link SubmitNodeStorage} to submit into, and then hands
 * every collected submit to a feature renderer of its own while {@link CaptureVertexBuilders}
 * points the renderers' vertex builders at the capture provider. The vertices reaching
 * Remix are therefore exactly the ones vanilla would draw, including text, item glint and
 * entity flames, and model parts still reach the capture's cuboid hook as model parts.
 *
 * <p>Shadows, shape outlines, particles and gizmos are not replayed: Remix traces its own
 * shadows, and the others are not scene geometry. Render thread only.
 */
public final class RemixSubmitReplay {

    private final SubmitNodeStorage storage = new RemixSubmitNodeStorage();
    private final FeatureRendererMap renderers = new FeatureRendererMap();
    private final List<SubmitNode> pending = new ArrayList<>();
    private final List<SubmitNode> group = new ArrayList<>();

    public RemixSubmitReplay() {
        renderers.put(FlameFeatureRenderer.TYPE, new FlameFeatureRenderer());
        renderers.put(ModelFeatureRenderer.TYPE, new ModelFeatureRenderer());
        renderers.put(TextFeatureRenderer.TYPE, new TextFeatureRenderer());
        renderers.put(LeashFeatureRenderer.TYPE, new LeashFeatureRenderer());
        renderers.put(ItemFeatureRenderer.TYPE, new ItemFeatureRenderer());
        renderers.put(CustomFeatureRenderer.TYPE, new CustomFeatureRenderer());
        renderers.put(BlockModelFeatureRenderer.TYPE, new BlockModelFeatureRenderer());
        renderers.put(MovingBlockFeatureRenderer.TYPE, new MovingBlockFeatureRenderer());
    }

    /** The collector a renderer submits into for the next {@link #replayInto}. */
    public SubmitNodeCollector collector() {
        return storage;
    }

    /**
     * Builds every submit collected since the last replay into {@code provider}, in the
     * order vanilla would draw them, and empties the collector.
     */
    public void replayInto(RemixVertexConsumerProvider provider) {
        storage.drainPhases(phase -> phase.sortInto((submit, strictlyOrdered) -> pending.add(submit)));
        if (pending.isEmpty()) {
            return;
        }
        FeatureFrameContext context = frameContext();
        CaptureVertexBuilders.begin(provider);
        try {
            FeatureRendererType<?> groupType = null;
            for (SubmitNode submit : pending) {
                if (submit.featureType() != groupType) {
                    buildGroup(context, groupType);
                    groupType = submit.featureType();
                }
                group.add(submit);
            }
            buildGroup(context, groupType);
        } finally {
            CaptureVertexBuilders.end();
            pending.clear();
            group.clear();
        }
    }

    private void buildGroup(FeatureFrameContext context, FeatureRendererType<?> type) {
        if (type == null || group.isEmpty()) {
            return;
        }
        try {
            FeatureRenderer<?> renderer = renderers.get(type);
            if (renderer instanceof RenderTypeFeatureRendererAccessor builder) {
                builder.rtxremix$buildGroup(context, group);
            }
        } finally {
            group.clear();
        }
    }

    private static FeatureFrameContext frameContext() {
        Minecraft client = Minecraft.getInstance();
        // The vertex stream and lightmap belong to the GPU path the replay replaces, so
        // the feature renderers never read them while building a group.
        return new FeatureFrameContext(
                client.gameRenderer.gameRenderState().optionsRenderState,
                client.font,
                client.getModelManager().getBlockStateModelSet(),
                client.getBlockColors(),
                client.getTextureManager(),
                client.getAtlasManager(),
                null,
                null);
    }
}
