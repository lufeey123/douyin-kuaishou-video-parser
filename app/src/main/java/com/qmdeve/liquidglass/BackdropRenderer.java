package com.qmdeve.liquidglass;

import android.graphics.Canvas;
import android.view.View;

/** A bounded backdrop, independent of the foreground view's children. */
public interface BackdropRenderer {
    boolean update(View host, View source, float cornerRadius);
    void draw(Canvas canvas);
    void dispose();
}
