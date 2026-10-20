package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Applies the Outfit typefaces to every TextView in a layout.
 *
 * android:fontFamily="@font/outfit" needs API 26, and the launcher runs on Android 4.4 (Xiaomi Mi TV 2)
 * and Android 7 (Sony BRAVIA 2015) too. The layouts therefore carry android:tag="font:light" and the
 * font is loaded here from assets/fonts, which every Android version reads the same way. The four
 * weights are static instances of the variable font (tools/fonts.py), since Android 7 and older would
 * draw the default instance of a variable font whatever weight the file asks for.
 */
final class Fonts {

    private static final SparseArray<Typeface> CACHE = new SparseArray<>();
    private static String lastFile;
    private static Typeface lastFace;

    private Fonts() {
    }

    /** Sets the typeface on every tagged TextView under root; a view without a tag keeps the system font. */
    static void apply(View root) {
        apply(root, null);
    }

    /**
     * @param override when not null, every tagged TextView gets this face instead of its tag's
     *                 (the screensaver uses the same tag names as the launcher)
     */
    static void apply(View root, String override) {
        if (root == null) return;
        if (root instanceof TextView) {
            TextView tv = (TextView) root;
            String name = override != null ? override : tagName(tv);
            if (name != null) {
                Typeface face = get(root.getContext(), name);
                if (face != null) tv.setTypeface(face);
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i), override);
        }
    }

    private static String tagName(View v) {
        Object tag = v.getTag();
        if (!(tag instanceof String)) return null;
        String s = (String) tag;
        return s.startsWith("font:") ? s.substring(5) : null;
    }

    /** The face for a tag suffix ("light", "regular", "medium", "extralight"), or null. */
    static Typeface get(Context ctx, String name) {
        String file = "fonts/outfit_" + name + ".ttf";
        synchronized (Fonts.class) {
            if (file.equals(lastFile)) return lastFace;
            Typeface face = CACHE.get(name.hashCode());
            if (face == null) {
                try {
                    face = Typeface.createFromAsset(ctx.getApplicationContext().getAssets(), file);
                } catch (Exception e) {
                    return null;   // a stripped asset: the system font is a usable fallback
                }
                CACHE.put(name.hashCode(), face);
            }
            lastFile = file;
            lastFace = face;
            return face;
        }
    }
}
