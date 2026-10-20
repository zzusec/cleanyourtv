package com.gathin.tvhome;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppVisibilityTest {
    private static final String KEY = "hidden_apps";

    @Test public void emptyPreferencesHaveNoHiddenApps() {
        Preferences store = new Preferences();
        AppVisibility visibility = new AppVisibility(store.preferences);

        assertTrue(visibility.hidden().isEmpty());
        assertTrue(visibility.effective(Collections.<String>emptySet()).isEmpty());
        assertTrue(visibility.restore(Collections.<String>emptySet()));
        assertTrue(new AppVisibility(store.preferences).hidden().isEmpty());
        assertEquals(0, store.applies);
    }

    @Test public void hideCommitsSynchronouslyAndSurvivesRecreation() {
        Preferences store = new Preferences();

        assertTrue(new AppVisibility(store.preferences).hide("com.example.tools"));

        assertEquals(1, store.commits);
        assertEquals(0, store.applies);
        assertEquals(set("com.example.tools"), store.values.get(KEY));
        assertEquals(set("com.example.tools"), new AppVisibility(store.preferences).hidden());
    }

    @Test public void hidingMoreAppsPreservesExistingHiddenApps() {
        Preferences store = new Preferences();
        store.values.put(KEY, set("com.example.first"));
        AppVisibility visibility = new AppVisibility(store.preferences);

        assertTrue(visibility.hide("com.example.second"));
        assertTrue(visibility.hide("com.example.second"));

        assertEquals(set("com.example.first", "com.example.second"),
                new AppVisibility(store.preferences).hidden());
        assertEquals(0, store.applies);
    }

    @Test public void hiddenReturnsIndependentCopiesOfStoredSets() {
        Preferences store = new Preferences();
        store.values.put(KEY, set("com.example.tools"));
        AppVisibility visibility = new AppVisibility(store.preferences);
        Set<String> snapshot = visibility.hidden();

        snapshot.clear();
        snapshot.add("com.example.unrelated");

        assertEquals(set("com.example.tools"), visibility.hidden());
        assertEquals(set("com.example.tools"), store.values.get(KEY));
        assertTrue(visibility.hide("com.example.another"));
        assertEquals(set("com.example.unrelated"), snapshot);
    }

    @Test public void effectiveIsAnIndependentUnionWithoutChangingConfiguredApps() {
        Preferences store = new Preferences();
        store.values.put(KEY, set("com.example.shared", "com.example.user"));
        Set<String> configured = set("com.example.configured", "com.example.shared");
        AppVisibility visibility = new AppVisibility(store.preferences);

        Set<String> effective = visibility.effective(configured);
        assertEquals(set("com.example.configured", "com.example.shared", "com.example.user"),
                effective);
        effective.clear();
        effective.add("com.example.unrelated");

        assertEquals(set("com.example.configured", "com.example.shared"), configured);
        assertEquals(set("com.example.shared", "com.example.user"), visibility.hidden());
        assertEquals(set("com.example.configured", "com.example.shared", "com.example.user"),
                visibility.effective(configured));
        assertEquals(0, store.commits);
        assertEquals(0, store.applies);
    }

    @Test public void restoringOneAppKeepsOtherAppsHiddenAndSurvivesRecreation() {
        Preferences store = new Preferences();
        store.values.put(KEY, set("com.example.first", "com.example.second"));

        assertTrue(new AppVisibility(store.preferences).restore(set("com.example.first")));

        assertEquals(set("com.example.second"), new AppVisibility(store.preferences).hidden());
        assertEquals(1, store.commits);
        assertEquals(0, store.applies);
    }

    @Test public void batchRestoreRemovesOnlySelectedUserApps() {
        Preferences store = new Preferences();
        store.values.put(KEY, set("com.example.first", "com.example.second", "com.example.third"));
        Set<String> selected = set("com.example.first", "com.example.third", "com.example.absent");
        AppVisibility visibility = new AppVisibility(store.preferences);

        assertTrue(visibility.restore(selected));

        assertEquals(set("com.example.second"), new AppVisibility(store.preferences).hidden());
        assertEquals(set("com.example.first", "com.example.third", "com.example.absent"), selected);
        assertEquals(1, store.commits);
        assertEquals(0, store.applies);
        assertTrue(visibility.restore(set("com.example.second")));
        assertTrue(new AppVisibility(store.preferences).hidden().isEmpty());
    }

    @Test public void restoringUserChoicesDoesNotRemoveConfigurationHiddenApps() {
        Preferences store = new Preferences();
        store.values.put(KEY, set("com.example.shared", "com.example.user"));
        Set<String> configured = set("com.example.shared", "com.example.configured");
        AppVisibility visibility = new AppVisibility(store.preferences);

        assertTrue(visibility.restore(set("com.example.shared", "com.example.user",
                "com.example.configured")));

        assertTrue(new AppVisibility(store.preferences).hidden().isEmpty());
        assertEquals(set("com.example.shared", "com.example.configured"),
                new AppVisibility(store.preferences).effective(configured));
        assertEquals(set("com.example.shared", "com.example.configured"), configured);
    }

    @Test public void hideAndRestoreLeaveOtherLauncherSettingsUnchanged() {
        Preferences store = new Preferences();
        store.values.put("columns", 6);
        store.values.put("animations", false);
        store.values.put("advanced_effects", true);
        store.values.put("theme_signature", "123@456:old-theme");
        Map<String, Object> originalSettings = new HashMap<>(store.values);
        AppVisibility visibility = new AppVisibility(store.preferences);

        assertTrue(visibility.hide("com.example.tools"));
        assertTrue(visibility.restore(set("com.example.tools")));

        Map<String, Object> otherSettings = new HashMap<>(store.values);
        otherSettings.remove(KEY);
        assertEquals(originalSettings, otherSettings);
        assertTrue(new AppVisibility(store.preferences).hidden().isEmpty());
        assertEquals(0, store.applies);
    }

    @Test public void failedCommitsAreReportedForHideAndRestore() {
        Preferences hideStore = new Preferences();
        hideStore.commitResult = false;
        assertFalse(new AppVisibility(hideStore.preferences).hide("com.example.tools"));
        assertEquals(1, hideStore.commits);
        assertEquals(0, hideStore.applies);

        Preferences restoreStore = new Preferences();
        restoreStore.values.put(KEY, set("com.example.tools"));
        restoreStore.commitResult = false;
        assertFalse(new AppVisibility(restoreStore.preferences).restore(set("com.example.tools")));
        assertEquals(1, restoreStore.commits);
        assertEquals(0, restoreStore.applies);
    }

    private static Set<String> set(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }

    private static final class Preferences {
        final Map<String, Object> values = new HashMap<>();
        boolean commitResult = true;
        int commits;
        int applies;
        final SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getStringSet":
                            // Android returns the stored set itself; callers must copy before editing it.
                            return values.containsKey(args[0]) ? values.get(args[0]) : args[1];
                        case "edit":
                            return editor();
                        default:
                            throw new AssertionError("Unexpected preferences call: " + method.getName());
                    }
                });

        private SharedPreferences.Editor editor() {
            Map<String, Object> pending = new HashMap<>();
            Set<String> removed = new HashSet<>();
            boolean[] clear = {false};
            return (SharedPreferences.Editor) Proxy.newProxyInstance(
                    SharedPreferences.Editor.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "putStringSet":
                                pending.put((String) args[0], args[1] == null ? null
                                        : new HashSet<>((Set<?>) args[1]));
                                return proxy;
                            case "remove":
                                removed.add((String) args[0]);
                                return proxy;
                            case "clear":
                                clear[0] = true;
                                return proxy;
                            case "commit":
                            case "apply":
                                if (clear[0]) values.clear();
                                for (String key : removed) values.remove(key);
                                values.putAll(pending);
                                if ("commit".equals(method.getName())) {
                                    commits++;
                                    return commitResult;
                                }
                                applies++;
                                return null;
                            default:
                                throw new AssertionError("Unexpected editor call: " + method.getName());
                        }
                    });
        }
    }
}
