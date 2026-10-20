package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Draws a rounded rectangle on any Android version the launcher supports.
 *
 * Both Canvas.drawRoundRect overloads that take a Paint are API 21; only the RectF form
 * drawRoundRect(RectF, rx, ry, Paint) is API 1. The launcher runs on Android 4.4 (Xiaomi Mi TV 2)
 * as well, so every rounded rect goes through here.
 */
final class RoundRect {

    /** Reused across frames: all drawing happens on the UI thread. */
    private static final RectF TEMP = new RectF();

    private RoundRect() {
    }

    static void draw(Canvas c, float left, float top, float right, float bottom, float radius, Paint paint) {
        TEMP.set(left, top, right, bottom);
        c.drawRoundRect(TEMP, radius, radius, paint);
    }
}
