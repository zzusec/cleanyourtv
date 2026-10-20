package com.gathin.tvhome;

import android.content.SharedPreferences;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** User-hidden icons, independent of theme/layout choices and package enabled state. */
final class AppVisibility {
    private static final String KEY = "hidden_apps";
    private final SharedPreferences preferences;

    AppVisibility(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    Set<String> hidden() {
        return new HashSet<>(preferences.getStringSet(KEY, Collections.emptySet()));
    }

    Set<String> effective(Collection<String> configuredHidden) {
        Set<String> hidden = hidden();
        hidden.addAll(configuredHidden);
        return hidden;
    }

    boolean hide(String packageName) {
        Set<String> hidden = hidden();
        hidden.add(packageName);
        return preferences.edit().putStringSet(KEY, hidden).commit();
    }

    boolean restore(Collection<String> packageNames) {
        Set<String> hidden = hidden();
        hidden.removeAll(packageNames);
        return preferences.edit().putStringSet(KEY, hidden).commit();
    }
}
