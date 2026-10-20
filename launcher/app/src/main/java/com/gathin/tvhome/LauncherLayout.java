package com.gathin.tvhome;

/** Pure geometry helpers for the tvOS-style home layout. */
final class LauncherLayout {
    private LauncherLayout() {
    }

    /**
     * Leaves a compact shelf above the dock instead of stretching it to fill every spare pixel.
     * Tall TV panels otherwise put the first app row halfway down the screen.
     */
    static int gridTopPadding(int gridHeight, int dockRowHeight, int rowsBelowDockHeight, float density) {
        int available = Math.max(0, gridHeight - dockRowHeight - rowsBelowDockHeight);
        int maxGap = Math.round(120 * density);
        return Math.min(available, maxGap);
    }
}
