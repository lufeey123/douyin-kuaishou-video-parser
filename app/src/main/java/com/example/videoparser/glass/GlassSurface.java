package com.example.videoparser.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import com.qmdeve.liquidglass.BackdropRenderer;
import com.qmdeve.liquidglass.BackdropRenderers;

/** Foreground stays sharp; only the separate source behind this card is sampled. */
public final class GlassSurface extends FrameLayout {
    private static final ThreadLocal<Boolean> CAPTURING = new ThreadLocal<>();
    private final ViewGroup source;
    private final float radius;
    private final Drawable finish;
    private final Drawable liveFinish;
    private final int[] hostLocation = new int[2];
    private final int[] sourceLocation = new int[2];
    private int lastX = Integer.MIN_VALUE, lastY, lastSourceX, lastSourceY, lastScrollX, lastScrollY;
    private boolean liveReady;
    private BackdropRenderer renderer;
    private ViewTreeObserver observer;
    private boolean failed;
    private boolean permanentlyReleased;
    private boolean firstFrame = true;
    private final ViewTreeObserver.OnPreDrawListener listener = this::updateBackdrop;

    private boolean updateBackdrop() {
        if (renderer != null && !Boolean.TRUE.equals(CAPTURING.get())) {
            try {
                getLocationOnScreen(hostLocation);
                source.getLocationOnScreen(sourceLocation);
                boolean changed = firstFrame || source.isDirty()
                        || hostLocation[0] != lastX || hostLocation[1] != lastY
                        || sourceLocation[0] != lastSourceX || sourceLocation[1] != lastSourceY
                        || source.getScrollX() != lastScrollX || source.getScrollY() != lastScrollY;
                lastX = hostLocation[0]; lastY = hostLocation[1];
                lastSourceX = sourceLocation[0]; lastSourceY = sourceLocation[1];
                lastScrollX = source.getScrollX(); lastScrollY = source.getScrollY();
                CAPTURING.set(Boolean.TRUE);
                boolean ready = renderer.update(this, source, radius);
                liveReady = ready;
                if (ready && changed) {
                    firstFrame = false;
                    invalidate();
                }
            } catch (RuntimeException | LinkageError error) {
                fail();
            } finally {
                CAPTURING.remove();
            }
        }
        return true;
    }

    public GlassSurface(Context context, ViewGroup source, float cornerRadiusDp) {
        super(context);
        this.source = source;
        radius = cornerRadiusDp * getResources().getDisplayMetrics().density;
        finish = material(context, cornerRadiusDp);
        liveFinish = new Material(radius, true);
        setWillNotDraw(false);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    /** Lightweight static glass for long result lists; no backdrop capture. */
    public static Drawable material(Context context, float radiusDp) {
        return new Material(radiusDp * context.getResources().getDisplayMetrics().density, false);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (permanentlyReleased || failed || source == null || containsSelf()) return;
        try {
            renderer = BackdropRenderers.create(this);
            if (renderer != null) {
                observer = getViewTreeObserver();
                observer.addOnPreDrawListener(listener);
            }
        } catch (RuntimeException | LinkageError error) {
            fail();
        }
    }

    private boolean containsSelf() {
        // An ancestor source would include this surface and form a recursive capture.
        ViewParent current = this;
        while (current instanceof View) {
            if (current == source) return true;
            current = current.getParent();
        }
        return false;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        finish.setBounds(0, 0, w, h);
        liveFinish.setBounds(0, 0, w, h);
        firstFrame = true;
        invalidateOutline();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (renderer != null && !Boolean.TRUE.equals(CAPTURING.get())) {
            try {
                renderer.draw(canvas);
            } catch (RuntimeException | LinkageError error) {
                fail();
            }
        }
        if (liveReady && canvas.isHardwareAccelerated() && !Boolean.TRUE.equals(CAPTURING.get())) {
            liveFinish.draw(canvas);
        } else {
            finish.draw(canvas);
        }
    }

    private void fail() {
        failed = true;
        detachRenderer();
        invalidate();
    }

    private void detachRenderer() {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(listener);
        observer = null;
        if (renderer != null) {
            try { renderer.dispose(); } catch (RuntimeException | LinkageError ignored) {}
            renderer = null;
        }
        firstFrame = true;
        liveReady = false;
    }

    @Override protected void onDetachedFromWindow() {
        detachRenderer();
        super.onDetachedFromWindow();
    }

    /** Idempotent terminal cleanup when the owning panel or activity is destroyed. */
    public void release() {
        permanentlyReleased = true;
        detachRenderer();
    }

    private static final class Material extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float radius;
        private final boolean live;
        Material(float radius, boolean live) {
            this.radius = radius;
            this.live = live;
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(1.3f);
        }
        @Override protected void onBoundsChange(android.graphics.Rect bounds) {
            rect.set(bounds.left + .8f, bounds.top + .8f, bounds.right - .8f, bounds.bottom - .8f);
            float bottom = Math.max(bounds.top + 1, bounds.bottom);
            fill.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bottom,
                    live ? new int[]{0x66FFFFFF, 0x33F4FCFF, 0x4DECF8F9}
                            : new int[]{0xDBFFFFFF, 0xA6FFFFFF, 0xC7F5FCFC},
                    new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
            rim.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bottom,
                    new int[]{0xFFFFFFFF, 0xB3FFFFFF, 0x38517680}, null, Shader.TileMode.CLAMP));
        }
        @Override public void draw(Canvas canvas) {
            canvas.drawRoundRect(rect, radius, radius, fill);
            canvas.drawRoundRect(rect, radius, radius, rim);
        }
        @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); rim.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { fill.setColorFilter(filter); rim.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
