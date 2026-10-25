// SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

#version 330 core

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;
in vec3 viewPosition;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord) * vertexColor;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = color;
}
