// SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform sampler2D Sampler2;
uniform int UseLightmap;

out vec2 texCoord;
out vec4 vertexColor;
out vec3 viewPosition;

void main() {
    viewPosition = (ModelViewMat * vec4(Position, 1.0)).xyz;
    gl_Position = ProjMat * vec4(viewPosition, 1.0);
    texCoord = UV0;
    // Minecraft's sample_lightmap: block and sky light are 0..240 in steps of 16.
    vec4 light = UseLightmap != 0
        ? texture(Sampler2, clamp(UV2 / 256.0 + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)))
        : vec4(1.0);
    vertexColor = Color * light;
}
