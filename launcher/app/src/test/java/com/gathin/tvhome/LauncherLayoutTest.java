package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LauncherLayoutTest {
    @Test public void tallTvKeepsTheShelfCompact() {
        assertEquals(240, LauncherLayout.gridTopPadding(880, 220, 0, 2f));
    }

    @Test public void shortTvUsesOnlyTheRemainingSpace() {
        assertEquals(100, LauncherLayout.gridTopPadding(320, 220, 0, 2f));
    }

    @Test public void overflowingGridDoesNotGetNegativePadding() {
        assertEquals(0, LauncherLayout.gridTopPadding(200, 220, 80, 2f));
    }
}
