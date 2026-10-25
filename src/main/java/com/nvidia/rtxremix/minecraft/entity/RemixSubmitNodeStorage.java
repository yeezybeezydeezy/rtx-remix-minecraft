/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.entity;

import net.minecraft.client.renderer.SubmitNodeStorage;

/**
 * The collector a {@link RemixSubmitReplay} hands to renderers. Its type is how a
 * submit hook tells the Remix capture from vanilla's own rendering, so geometry
 * meant only for Remix, such as a held item's flame, is never drawn by vanilla.
 */
public final class RemixSubmitNodeStorage extends SubmitNodeStorage {
}
