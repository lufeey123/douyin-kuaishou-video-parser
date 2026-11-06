/*
 * MIT License
 *
 * Copyright (c) 2025-2026 Donny Yang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ===========================================
 * Project: AndroidLiquidGlassView
 * Author: Donny Yang
 * GitHub: https://github.com/QmDeve/AndroidLiquidGlassView
 * WebSite: https://liquidglass.qmdeve.com
 * ===========================================
 */

// Adapted for ShiYing from commit d4238c557e46271f29ec0e3d3ee211bcf58dc2e0.
// Changes: bounded host-sized capture, screen coordinates, API 31 blur fallback,
// isolated API classes, guaranteed recording cleanup and no AndroidX dependency.
package com.qmdeve.liquidglass;

import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import com.example.videoparser.R;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class BackdropRenderers {
    private BackdropRenderers() {}
    public static BackdropRenderer create(View host) {
        if (Build.VERSION.SDK_INT >= 33) return new Liquid(host);
        if (Build.VERSION.SDK_INT >= 31) return new Blur(host);
        return null;
    }

    private static class Blur implements BackdropRenderer {
        final RenderNode node = new RenderNode("ShiYing glass backdrop");
        final float density;
        private final int[] hostLocation = new int[2];
        private final int[] sourceLocation = new int[2];
        private int width, height;
        private boolean ready;
        Blur(View host) {
            density = host.getResources().getDisplayMetrics().density;
        }
        void effects(int w, int h, float corner) {
            float blur = Math.min(24f, 7f * density);
            node.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
        }
        public boolean update(View host, View source, float corner) {
            int w = host.getWidth(), h = host.getHeight();
            if (w <= 0 || h <= 0 || w > 4096 || h > 4096 || (long) w * h > 4000000L
                    || source.getWidth() == 0 || source.getHeight() == 0) {
                ready = false;
                return false;
            }
            if (w != width || h != height) {
                node.setPosition(0, 0, w, h);
                effects(w, h, corner);
                width = w;
                height = h;
            }
            host.getLocationOnScreen(hostLocation);
            source.getLocationOnScreen(sourceLocation);
            Canvas recording = node.beginRecording(w, h);
            try {
                recording.clipRect(0, 0, w, h);
                recording.translate(sourceLocation[0] - hostLocation[0], sourceLocation[1] - hostLocation[1]);
                source.draw(recording);
            } finally {
                node.endRecording();
            }
            ready = true;
            return true;
        }
        public void draw(Canvas canvas) {
            if (ready && canvas.isHardwareAccelerated()) canvas.drawRenderNode(node);
        }
        public void dispose() {
            ready = false;
            node.setRenderEffect(null);
            node.discardDisplayList();
        }
    }

    private static final class Liquid extends Blur {
        final RuntimeShader shader;
        Liquid(View host) {
            super(host);
            StringBuilder code = new StringBuilder();
            try (InputStream stream = host.getResources().openRawResource(R.raw.liquidglass_effect);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) code.append(line).append('\n');
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load glass material", e);
            }
            shader = new RuntimeShader(code.toString());
        }
        @Override void effects(int w, int h, float corner) {
            shader.setFloatUniform("size", w, h);
            shader.setFloatUniform("offset", 0f, 0f);
            shader.setFloatUniform("cornerRadii", corner, corner, corner, corner);
            shader.setFloatUniform("refractionHeight", Math.min(18f * density, Math.min(w, h) * .35f));
            // Inward refraction keeps samples inside the bounded recording.
            shader.setFloatUniform("refractionAmount", -24f * density);
            shader.setFloatUniform("depthEffect", .15f);
            shader.setFloatUniform("chromaticAberration", .18f);
            shader.setFloatUniform("contrast", 0f);
            shader.setFloatUniform("whitePoint", 0f);
            shader.setFloatUniform("chromaMultiplier", 1f);
            shader.setFloatUniform("tintColor", .96f, .99f, 1f);
            shader.setFloatUniform("tintAlpha", .18f);
            RenderEffect effect = RenderEffect.createRuntimeShaderEffect(shader, "content");
            float blur = Math.min(24f, 6f * density);
            node.setRenderEffect(RenderEffect.createChainEffect(effect,
                    RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)));
        }
    }
}
