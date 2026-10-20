package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private SharedPreferences choices;
    private AppVisibility visibility;
    private final TextView[] columnChoices = new TextView[4];
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView hiddenAppsChoice;
    private int columns;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        Fonts.apply(findViewById(android.R.id.content));
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        visibility = new AppVisibility(choices);
        Config config = Config.load(this);
        columns = config.columns;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        hiddenAppsChoice = findViewById(R.id.hiddenAppsChoice);
        hiddenAppsChoice.setOnClickListener(v -> showHiddenApps());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove("animations").remove("advanced_effects").commit();
            Config current = Config.load(this);
            columns = current.columns;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            setResult(RESULT_OK);
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        refresh();
        columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        hiddenAppsChoice.setText(getString(R.string.hidden_apps_count, visibility.hidden().size()));
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private void showHiddenApps() {
        List<String> packages = new ArrayList<>(visibility.hidden());
        if (packages.isEmpty()) {
            Toast.makeText(this, R.string.no_hidden_apps, Toast.LENGTH_SHORT).show();
            return;
        }
        PackageManager pm = getPackageManager();
        Map<String, String> labels = new HashMap<>();
        for (String pkg : packages) {
            String label = pkg;
            try {
                label = Cards.cleanLabel(String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))), pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
                // Keep uninstalled apps selectable so stale hidden records can also be removed.
            }
            labels.put(pkg, label);
        }
        Collections.sort(packages, (a, b) -> labels.get(a).compareToIgnoreCase(labels.get(b)));
        CharSequence[] names = new CharSequence[packages.size()];
        for (int i = 0; i < names.length; i++) {
            String pkg = packages.get(i);
            names[i] = labels.get(pkg) + "\n" + pkg;
        }
        Set<String> selected = new HashSet<>();
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.restore_hidden_apps)
                .setMultiChoiceItems(names, new boolean[names.length], (window, which, checked) -> {
                    if (checked) selected.add(packages.get(which));
                    else selected.remove(packages.get(which));
                    ((AlertDialog) window).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!selected.isEmpty());
                })
                .setPositiveButton(R.string.restore_selected, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(window -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!visibility.restore(selected)) {
                    Toast.makeText(this, R.string.visibility_not_saved, Toast.LENGTH_LONG).show();
                    return;
                }
                setResult(RESULT_OK);
                refresh();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains("animations")
                || choices.contains("advanced_effects");
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
