package com.gathin.tvhome;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;

/**
 * Drawable helpers that work on Android 4.4 through 14.
 *
 * android:tint, Drawable.setTint and android:drawableTint are API 21/23, and the launcher runs on
 * Android 4.4 (Xiaomi Mi TV 2) and Android 7 (Sony BRAVIA 2015). setColorFilter with a SRC_IN
 * PorterDuff mode does the same job on every version.
 */
final class Tint {

    private Tint() {
    }

    /** Tints a drawable with a flat colour. */
    static void set(Drawable d, int color) {
        if (d == null) return;
        d.mutate();
        d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    /** The colour res/color/pill_text resolves to for a focused or unfocused pill. */
    static int stateColor(Context ctx, boolean focused) {
        android.content.res.ColorStateList list = ctx.getResources().getColorStateList(R.color.pill_text);
        int[] state = focused ? new int[]{android.R.attr.state_focused} : new int[0];
        return list.getColorForState(state, list.getDefaultColor());
    }

    /** getDrawable with the density of the resources, for API 19 (getDrawable(int) is 21). */
    @SuppressWarnings("deprecation")
    static Drawable get(Context ctx, int res) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) return ctx.getDrawable(res);
        return ctx.getResources().getDrawable(res);
    }
}
