package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * An ImageView whose drawable is tinted from a colour state list as the view's own state changes.
 *
 * android:tint and Drawable.setTint are API 21, and android:drawableTint is API 23; this launcher runs
 * on Android 4.4 (Xiaomi Mi TV 2). A SRC_IN colour filter does the same on every version, and
 * drawableStateChanged() is where the focused/unfocused colour is picked up, including for views
 * that mirror their parent's state (android:duplicateParentState).
 */
public class TintedImageView extends ImageView {

    private ColorStateList tint;
    /** The drawable set through any of the ImageView setters; ImageView.getDrawable() is API 21. */
    private Drawable current;

    public TintedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** The colour list to tint with, from a selector such as res/color/pill_text.xml. */
    public void setTintColors(ColorStateList list) {
        tint = list;
        applyTint();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        applyTint();
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        current = drawable;
        super.setImageDrawable(drawable);
        applyTint();
    }

    @Override
    public void setImageResource(int resId) {
        current = getResources().getDrawable(resId);
        super.setImageDrawable(current);
        applyTint();
    }

    private void applyTint() {
        // ImageView.getDrawable() is API 21; the framework's field-free getter is getDrawable(int),
        // which is not what we want here, so hold our own reference on the way in.
        Drawable d = current;
        if (d == null) return;
        if (tint == null) {
            d.clearColorFilter();
            return;
        }
        d.mutate();
        d.setColorFilter(tint.getColorForState(getDrawableState(), tint.getDefaultColor()), PorterDuff.Mode.SRC_IN);
    }
}
