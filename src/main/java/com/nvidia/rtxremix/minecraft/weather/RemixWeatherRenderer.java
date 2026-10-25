/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.weather;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Draws rain and snow with GL directly onto the composited Remix colour image,
 * depth-testing against the interop depth texture so weather is occluded by
 * path-traced geometry.
 *
 * <p>Minecraft 26.3 extracts the weather columns into a {@link WeatherRenderState}
 * and draws them inside the level frame graph, which Remix world rendering
 * skips. This renderer builds the same column quads from that state and draws
 * them into its own framebuffer over the main render target's colour texture,
 * with a depth buffer seeded from the Remix depth. Rain refracts and reflects
 * the Remix image; snow is drawn as vanilla's particle pipeline would.
 *
 * <p>Render thread only, and after Remix has presented its image into GL. Every
 * piece of GL state it changes is restored, and its textures bind to units
 * above the ones Minecraft's GL state cache tracks.
 */
public final class RemixWeatherRenderer {
    private static final float NEAR_PLANE = 0.05f;
    private static final float FAR_PLANE = 1000.0f;
    private static final Identifier RAIN_LOCATION = Identifier.withDefaultNamespace("textures/environment/rain.png");
    private static final Identifier SNOW_LOCATION = Identifier.withDefaultNamespace("textures/environment/snow.png");
    private static final String SHADER_ROOT = "/assets/rtx-remix-renderer/shaders/weather/";

    /** Units past the twelve that GlStateManager caches, so its bindings stay valid. */
    private static final int UNIT_WEATHER = 13;
    private static final int UNIT_REMIX_COLOR = 14;
    private static final int UNIT_LIGHTMAP = 15;

    /** Position, uv, colour and light as floats. */
    private static final int FLOATS_PER_VERTEX = 3 + 2 + 4 + 2;
    private static final int VERTICES_PER_COLUMN = 6;

    private static final int GL_CLIP_DEPTH_MODE = 0x935D;
    private static final int GL_ZERO_TO_ONE = 0x935F;

    /** Vanilla's column orientation table, from the WeatherEffectRenderer constructor. */
    private final float[] columnSizeX = new float[1024];
    private final float[] columnSizeZ = new float[1024];

    private Matrix4f modelView;
    private float fovYDegrees;
    private float aspect;

    private Programs programs;
    private int vertexArray;
    private int vertexBuffer;
    private int vertexBufferBytes;
    private int framebuffer;
    private int depthRenderbuffer;
    private int framebufferColorTexture;
    private int framebufferWidth;
    private int framebufferHeight;
    private FloatBuffer vertices;

    private int lastDrawCount;
    private int lastQuadCount;

    public RemixWeatherRenderer() {
        for (int z = 0; z < 32; z++) {
            for (int x = 0; x < 32; x++) {
                float deltaX = x - 16;
                float deltaZ = z - 16;
                float distance = Mth.length(deltaX, deltaZ);
                columnSizeX[z * 32 + x] = -deltaZ / distance;
                columnSizeZ[z * 32 + x] = deltaX / distance;
            }
        }
    }

    public void updateCamera(Camera camera, float fovYDegrees, float aspect) {
        modelView = new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf()));
        this.fovYDegrees = fovYDegrees;
        this.aspect = aspect;
    }

    /**
     * Draws this frame's weather over the main render target.
     *
     * @param finalColorTexture the Remix colour image, top row first
     * @param depthTexture      the Remix depth image, top row first
     */
    public void render(int finalColorTexture, int depthTexture) {
        lastDrawCount = 0;
        lastQuadCount = 0;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || modelView == null) {
            return;
        }
        LevelRenderState levelState = client.gameRenderer.gameRenderState().levelRenderState;
        WeatherRenderState weather = levelState.weatherRenderState;
        int rainColumns = weather.rainColumns.size();
        int snowColumns = weather.snowColumns.size();
        if (rainColumns + snowColumns == 0) {
            return;
        }
        if (finalColorTexture <= 0 || depthTexture <= 0) {
            return;
        }

        RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
        int targetColor = glId(mainTarget.getColorTexture());
        int rainTexture = glId(client.getTextureManager().getTexture(RAIN_LOCATION));
        int snowTexture = glId(client.getTextureManager().getTexture(SNOW_LOCATION));
        GpuTextureView lightmapView = client.gameRenderer.levelLightmap();
        int lightmapTexture = lightmapView == null ? 0 : glId(lightmapView.texture());
        if (targetColor == 0 || rainTexture == 0 || snowTexture == 0 || lightmapTexture == 0) {
            return;
        }

        if (programs == null) {
            programs = new Programs();
        }
        ensureFramebuffer(targetColor, mainTarget.width, mainTarget.height);
        Vec3 cameraPos = levelState.cameraRenderState.pos;
        int vertexCount = buildVertices(weather, cameraPos);
        uploadVertices(vertexCount);

        boolean zeroToOne = GL11.glGetInteger(GL_CLIP_DEPTH_MODE) == GL_ZERO_TO_ONE;
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(fovYDegrees), aspect, NEAR_PLANE, FAR_PLANE, zeroToOne);

        try (SavedGlState ignored = new SavedGlState()) {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL11.glViewport(0, 0, framebufferWidth, framebufferHeight);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);

            seedDepth(depthTexture);

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(false);
            GL11.glColorMask(true, true, true, true);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);

            GL30.glBindVertexArray(vertexArray);
            bindTexture(UNIT_REMIX_COLOR, finalColorTexture, programs.linearClamp);
            bindTexture(UNIT_LIGHTMAP, lightmapTexture, programs.linearClamp);
            if (rainColumns > 0) {
                bindTexture(UNIT_WEATHER, rainTexture, programs.nearestRepeat);
                programs.useRain(modelView, projection);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, rainColumns * VERTICES_PER_COLUMN);
                lastDrawCount++;
            }
            if (snowColumns > 0) {
                bindTexture(UNIT_WEATHER, snowTexture, programs.nearestRepeat);
                programs.useSnow(modelView, projection);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, rainColumns * VERTICES_PER_COLUMN,
                        snowColumns * VERTICES_PER_COLUMN);
                lastDrawCount++;
            }
            bindTexture(UNIT_WEATHER, 0, 0);
            bindTexture(UNIT_REMIX_COLOR, 0, 0);
            bindTexture(UNIT_LIGHTMAP, 0, 0);
        }
        lastQuadCount = rainColumns + snowColumns;
    }

    /** Writes the Remix depth into the framebuffer's depth buffer, colour writes off. */
    private void seedDepth(int depthTexture) {
        // OpenGL suppresses depth writes entirely when the depth test is disabled.
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        GL11.glDepthMask(true);
        GL11.glColorMask(false, false, false, false);
        GL11.glDisable(GL11.GL_BLEND);
        bindTexture(UNIT_WEATHER, depthTexture, programs.nearestClamp);
        programs.useDepthSeed(framebufferWidth, framebufferHeight);
        GL30.glBindVertexArray(programs.emptyVertexArray);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    /** The two triangles of each column quad, in vanilla's prepareInstances order. */
    private int buildVertices(WeatherRenderState weather, Vec3 cameraPos) {
        int columns = weather.rainColumns.size() + weather.snowColumns.size();
        int floats = columns * VERTICES_PER_COLUMN * FLOATS_PER_VERTEX;
        if (vertices == null || vertices.capacity() < floats) {
            if (vertices != null) {
                MemoryUtil.memFree(vertices);
            }
            vertices = MemoryUtil.memAllocFloat(Math.max(floats, 4096));
        }
        vertices.clear();
        appendColumns(weather.rainColumns, cameraPos, 1.0f, weather.radius, weather.intensity);
        appendColumns(weather.snowColumns, cameraPos, 0.8f, weather.radius, weather.intensity);
        vertices.flip();
        return columns * VERTICES_PER_COLUMN;
    }

    private void appendColumns(List<WeatherEffectRenderer.ColumnInstance> columns, Vec3 cameraPos,
                               float maxAlpha, int radius, float intensity) {
        float radiusSq = radius * radius;
        for (WeatherEffectRenderer.ColumnInstance column : columns) {
            float relativeX = (float) (column.x() + 0.5 - cameraPos.x);
            float relativeZ = (float) (column.z() + 0.5 - cameraPos.z);
            float distanceSq = Mth.lengthSquared(relativeX, relativeZ);
            float alpha = Mth.lerp(Math.min(distanceSq / radiusSq, 1.0f), maxAlpha, 0.5f) * intensity;
            int tableX = Mth.clamp(column.x() - Mth.floor(cameraPos.x) + 16, 0, 31);
            int tableZ = Mth.clamp(column.z() - Mth.floor(cameraPos.z) + 16, 0, 31);
            int index = tableZ * 32 + tableX;
            float halfSizeX = columnSizeX[index] / 2.0f;
            float halfSizeZ = columnSizeZ[index] / 2.0f;
            float x0 = relativeX - halfSizeX;
            float x1 = relativeX + halfSizeX;
            float y1 = (float) (column.topY() - cameraPos.y);
            float y0 = (float) (column.bottomY() - cameraPos.y);
            float z0 = relativeZ - halfSizeZ;
            float z1 = relativeZ + halfSizeZ;
            float u0 = column.uOffset();
            float u1 = column.uOffset() + 1.0f;
            float v0 = column.bottomY() * 0.25f + column.vOffset();
            float v1 = column.topY() * 0.25f + column.vOffset();
            float blockLight = column.lightCoords() & 0xFFFF;
            float skyLight = (column.lightCoords() >>> 16) & 0xFFFF;
            // Quad corners a b c d, as two triangles a b c and a c d.
            vertex(x0, y1, z0, u0, v0, alpha, blockLight, skyLight);
            vertex(x1, y1, z1, u1, v0, alpha, blockLight, skyLight);
            vertex(x1, y0, z1, u1, v1, alpha, blockLight, skyLight);
            vertex(x0, y1, z0, u0, v0, alpha, blockLight, skyLight);
            vertex(x1, y0, z1, u1, v1, alpha, blockLight, skyLight);
            vertex(x0, y0, z0, u0, v1, alpha, blockLight, skyLight);
        }
    }

    private void vertex(float x, float y, float z, float u, float v, float alpha, float blockLight, float skyLight) {
        vertices.put(x).put(y).put(z).put(u).put(v)
                .put(1.0f).put(1.0f).put(1.0f).put(alpha)
                .put(blockLight).put(skyLight);
    }

    private void uploadVertices(int vertexCount) {
        int bytes = vertexCount * FLOATS_PER_VERTEX * Float.BYTES;
        if (vertexArray == 0) {
            vertexArray = GL45.glCreateVertexArrays();
            vertexBuffer = GL45.glCreateBuffers();
            int stride = FLOATS_PER_VERTEX * Float.BYTES;
            GL45.glVertexArrayVertexBuffer(vertexArray, 0, vertexBuffer, 0, stride);
            int[] sizes = {3, 2, 4, 2};
            int offset = 0;
            for (int attribute = 0; attribute < sizes.length; attribute++) {
                GL45.glEnableVertexArrayAttrib(vertexArray, attribute);
                GL45.glVertexArrayAttribFormat(vertexArray, attribute, sizes[attribute], GL11.GL_FLOAT, false, offset);
                GL45.glVertexArrayAttribBinding(vertexArray, attribute, 0);
                offset += sizes[attribute] * Float.BYTES;
            }
        }
        if (bytes > vertexBufferBytes) {
            vertexBufferBytes = Math.max(bytes, vertexBufferBytes * 2);
            GL45.glNamedBufferData(vertexBuffer, vertexBufferBytes, GL15.GL_STREAM_DRAW);
        }
        GL45.glNamedBufferSubData(vertexBuffer, 0, vertices);
    }

    private void ensureFramebuffer(int colorTexture, int width, int height) {
        if (framebuffer != 0 && framebufferColorTexture == colorTexture
                && framebufferWidth == width && framebufferHeight == height) {
            return;
        }
        releaseFramebuffer();
        framebuffer = GL45.glCreateFramebuffers();
        depthRenderbuffer = GL45.glCreateRenderbuffers();
        GL45.glNamedRenderbufferStorage(depthRenderbuffer, GL30.GL_DEPTH_COMPONENT32F, width, height);
        GL45.glNamedFramebufferTexture(framebuffer, GL30.GL_COLOR_ATTACHMENT0, colorTexture, 0);
        GL45.glNamedFramebufferRenderbuffer(framebuffer, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRenderbuffer);
        int status = GL45.glCheckNamedFramebufferStatus(framebuffer, GL30.GL_DRAW_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            releaseFramebuffer();
            throw new IllegalStateException("Weather framebuffer is incomplete: 0x" + Integer.toHexString(status));
        }
        framebufferColorTexture = colorTexture;
        framebufferWidth = width;
        framebufferHeight = height;
    }

    private void releaseFramebuffer() {
        if (framebuffer != 0) {
            GL30.glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
        if (depthRenderbuffer != 0) {
            GL30.glDeleteRenderbuffers(depthRenderbuffer);
            depthRenderbuffer = 0;
        }
        framebufferColorTexture = 0;
    }

    private static void bindTexture(int unit, int texture, int sampler) {
        GL45.glBindTextureUnit(unit, texture);
        GL33.glBindSampler(unit, sampler);
    }

    private static int glId(AbstractTexture texture) {
        return texture == null ? 0 : glId(texture.getTexture());
    }

    private static int glId(GpuTexture texture) {
        return texture instanceof GlTexture glTexture ? glTexture.glId() : 0;
    }

    public int getLastDrawCount() {
        return lastDrawCount;
    }

    public int getLastQuadCount() {
        return lastQuadCount;
    }

    public void cleanup() {
        releaseFramebuffer();
        if (vertexArray != 0) {
            GL30.glDeleteVertexArrays(vertexArray);
            GL15.glDeleteBuffers(vertexBuffer);
            vertexArray = 0;
            vertexBuffer = 0;
            vertexBufferBytes = 0;
        }
        if (programs != null) {
            programs.destroy();
            programs = null;
        }
        if (vertices != null) {
            MemoryUtil.memFree(vertices);
            vertices = null;
        }
    }

    /** The GL state the weather pass changes, captured on construction and restored on close. */
    private static final class SavedGlState implements AutoCloseable {
        private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int[] viewport = getIntegers(GL11.GL_VIEWPORT, 4);
        private final boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        private final int blendSourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int blendDestinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int blendSourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int blendDestinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        private final int blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        private final boolean[] colorMask = getColorMask();

        @Override
        public void close() {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vertexArray);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            setEnabled(GL11.GL_DEPTH_TEST, depthTest);
            setEnabled(GL11.GL_BLEND, blend);
            setEnabled(GL11.GL_CULL_FACE, cull);
            setEnabled(GL11.GL_SCISSOR_TEST, scissor);
            GL11.glDepthMask(depthMask);
            GL11.glDepthFunc(depthFunction);
            GL14.glBlendFuncSeparate(blendSourceRgb, blendDestinationRgb, blendSourceAlpha, blendDestinationAlpha);
            GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
            GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        }

        private static void setEnabled(int capability, boolean enabled) {
            if (enabled) GL11.glEnable(capability);
            else GL11.glDisable(capability);
        }

        private static int[] getIntegers(int name, int count) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer values = stack.mallocInt(count);
                GL11.glGetIntegerv(name, values);
                int[] result = new int[count];
                values.get(result);
                return result;
            }
        }

        private static boolean[] getColorMask() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer values = stack.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, values);
                return new boolean[]{values.get(0) != 0, values.get(1) != 0, values.get(2) != 0, values.get(3) != 0};
            }
        }
    }

    /** The rain, snow and depth-seeding programs and the samplers they read with. */
    private static final class Programs {
        private static final String DEPTH_SEED_VERTEX = """
                #version 330 core
                void main() {
                    vec2 position = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                    gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);
                }
                """;
        private static final String DEPTH_SEED_FRAGMENT = """
                #version 330 core
                uniform sampler2D RemixDepth;
                uniform vec2 FramebufferSize;
                void main() {
                    vec2 uv = vec2(gl_FragCoord.x / FramebufferSize.x, 1.0 - gl_FragCoord.y / FramebufferSize.y);
                    gl_FragDepth = texture(RemixDepth, uv).r;
                }
                """;

        private final int rain;
        private final int snow;
        private final int depthSeed;
        private final int emptyVertexArray;
        private final int linearClamp;
        private final int nearestClamp;
        private final int nearestRepeat;

        private Programs() {
            String vertex = readShader("weather.vsh");
            rain = link("rain", vertex, readShader("rain.fsh"));
            snow = link("snow", vertex, readShader("snow.fsh"));
            depthSeed = link("depth seed", DEPTH_SEED_VERTEX, DEPTH_SEED_FRAGMENT);
            emptyVertexArray = GL45.glCreateVertexArrays();
            linearClamp = sampler(GL11.GL_LINEAR, GL12.GL_CLAMP_TO_EDGE);
            nearestClamp = sampler(GL11.GL_NEAREST, GL12.GL_CLAMP_TO_EDGE);
            nearestRepeat = sampler(GL11.GL_NEAREST, GL11.GL_REPEAT);

            for (int program : new int[]{rain, snow}) {
                GL45.glProgramUniform1i(program, GL20.glGetUniformLocation(program, "Sampler0"), UNIT_WEATHER);
                GL45.glProgramUniform1i(program, GL20.glGetUniformLocation(program, "Sampler1"), UNIT_REMIX_COLOR);
                GL45.glProgramUniform1i(program, GL20.glGetUniformLocation(program, "Sampler2"), UNIT_LIGHTMAP);
            }
            GL45.glProgramUniform1i(rain, GL20.glGetUniformLocation(rain, "UseLightmap"), 0);
            GL45.glProgramUniform1i(snow, GL20.glGetUniformLocation(snow, "UseLightmap"), 1);
            GL45.glProgramUniform1i(depthSeed, GL20.glGetUniformLocation(depthSeed, "RemixDepth"), UNIT_WEATHER);
        }

        private void useRain(Matrix4f modelView, Matrix4f projection) {
            setMatrices(rain, modelView, projection);
            GL20.glUseProgram(rain);
        }

        private void useSnow(Matrix4f modelView, Matrix4f projection) {
            setMatrices(snow, modelView, projection);
            GL20.glUseProgram(snow);
        }

        private void useDepthSeed(int width, int height) {
            GL45.glProgramUniform2f(depthSeed, GL20.glGetUniformLocation(depthSeed, "FramebufferSize"), width, height);
            GL20.glUseProgram(depthSeed);
        }

        private static void setMatrices(int program, Matrix4f modelView, Matrix4f projection) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer matrix = stack.mallocFloat(16);
                GL45.glProgramUniformMatrix4fv(program, GL20.glGetUniformLocation(program, "ModelViewMat"), false,
                        modelView.get(matrix));
                GL45.glProgramUniformMatrix4fv(program, GL20.glGetUniformLocation(program, "ProjMat"), false,
                        projection.get(matrix));
            }
        }

        private static int sampler(int filter, int wrap) {
            int sampler = GL45.glCreateSamplers();
            GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, filter);
            GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, filter);
            GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_S, wrap);
            GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_T, wrap);
            return sampler;
        }

        private void destroy() {
            GL20.glDeleteProgram(rain);
            GL20.glDeleteProgram(snow);
            GL20.glDeleteProgram(depthSeed);
            GL30.glDeleteVertexArrays(emptyVertexArray);
            GL33.glDeleteSamplers(linearClamp);
            GL33.glDeleteSamplers(nearestClamp);
            GL33.glDeleteSamplers(nearestRepeat);
        }

        private static String readShader(String name) {
            try (InputStream in = RemixWeatherRenderer.class.getResourceAsStream(SHADER_ROOT + name)) {
                if (in == null) {
                    throw new IllegalStateException("Missing weather shader " + name);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read weather shader " + name, e);
            }
        }

        private static int link(String name, String vertexSource, String fragmentSource) {
            int vertex = compile(name, GL20.GL_VERTEX_SHADER, vertexSource);
            int fragment = 0;
            try {
                fragment = compile(name, GL20.GL_FRAGMENT_SHADER, fragmentSource);
                int program = GL20.glCreateProgram();
                GL20.glAttachShader(program, vertex);
                GL20.glAttachShader(program, fragment);
                GL20.glLinkProgram(program);
                if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                    String log = GL20.glGetProgramInfoLog(program);
                    GL20.glDeleteProgram(program);
                    throw new IllegalStateException("Failed to link " + name + " shader: " + log);
                }
                return program;
            } finally {
                GL20.glDeleteShader(vertex);
                if (fragment != 0) {
                    GL20.glDeleteShader(fragment);
                }
            }
        }

        private static int compile(String name, int type, String source) {
            int shader = GL20.glCreateShader(type);
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetShaderInfoLog(shader);
                GL20.glDeleteShader(shader);
                throw new IllegalStateException("Failed to compile " + name + " shader: " + log);
            }
            return shader;
        }
    }
}
