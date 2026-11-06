package com.example.videoparser.glass;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** A quiet, static backdrop: gradients are rebuilt only when the bounds change. */
public final class AuroraDrawable extends Drawable {
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blue = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lilac = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AuroraDrawable() {}

    @Override protected void onBoundsChange(Rect bounds) {
        float x = bounds.left, y = bounds.top;
        float w = Math.max(1, bounds.width()), h = Math.max(1, bounds.height());
        base.setShader(new LinearGradient(x, y, x + w, y + h,
                new int[]{0xFFF0F6F9, 0xFFF4F8F7, 0xFFF1F5F8}, null, Shader.TileMode.CLAMP));
        blue.setShader(new RadialGradient(x - .10f*w, y + .08f*h, Math.max(w*1.05f, 1),
                0x809DBFDC, 0x009DBFDC, Shader.TileMode.CLAMP));
        mint.setShader(new RadialGradient(x + 1.1f*w, y + .48f*h, Math.max(w*.95f, 1),
                0x7098CFC5, 0x0098CFC5, Shader.TileMode.CLAMP));
        lilac.setShader(new RadialGradient(x + .12f*w, y + .9f*h, Math.max(w*.8f, 1),
                0x30A0BCD0, 0x00A0BCD0, Shader.TileMode.CLAMP));
        light.setShader(new RadialGradient(x + .5f*w, y + .20f*h, Math.max(w*.7f, 1),
                0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }
    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        canvas.drawRect(bounds, base);
        canvas.drawRect(bounds, blue);
        canvas.drawRect(bounds, mint);
        canvas.drawRect(bounds, lilac);
        canvas.drawRect(bounds, light);
    }
    @Override public void setAlpha(int alpha) {
        base.setAlpha(alpha); blue.setAlpha(alpha); mint.setAlpha(alpha);
        lilac.setAlpha(alpha); light.setAlpha(alpha); invalidateSelf();
    }
    @Override public void setColorFilter(ColorFilter filter) {
        base.setColorFilter(filter); blue.setColorFilter(filter); mint.setColorFilter(filter);
        lilac.setColorFilter(filter); light.setColorFilter(filter); invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
