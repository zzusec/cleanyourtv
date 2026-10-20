package com.gathin.tvhome;

import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The API 21+ half of {@link Platform}: TvInputManager queries and its callback.
 * Only loaded once {@link Platform#tvInputs()} is true; Android 4.4 never sees these types.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
final class PlatformTv {

    /** One row of the Inputs menu, with everything the UI needs already resolved. */
    static final class Row {
        final String id;
        final String label;
        /** True for HDMI, false for AV and the other passthrough inputs. */
        final boolean hdmi;
        /** -1 when the state is not worth showing (Sony reports every port as connected). */
        final int state;

        Row(String id, String label, boolean hdmi, int state) {
            this.id = id;
            this.label = label;
            this.hdmi = hdmi;
            this.state = state;
        }
    }

    /** The passthrough inputs plus the best tuner id, or empty on a TV without TvInputManager. */
    static final class Inputs {
        final List<Row> rows;
        final String tunerId;

        Inputs(List<Row> rows, String tunerId) {
            this.rows = rows;
            this.tunerId = tunerId;
        }
    }

    static final int STATE_CONNECTED = TvInputManager.INPUT_STATE_CONNECTED;
    static final int STATE_DISCONNECTED = TvInputManager.INPUT_STATE_DISCONNECTED;

    private PlatformTv() {
    }

    static List<TvInputInfo> inputs(Context ctx) {
        TvInputManager tim = manager(ctx);
        if (tim == null) return Collections.emptyList();
        try {
            List<TvInputInfo> list = tim.getTvInputList();
            return list != null ? list : Collections.<TvInputInfo>emptyList();
        } catch (Exception e) {
            // Not a TV, or a firmware with the TV input service stubbed out: no inputs to list.
            return Collections.emptyList();
        }
    }

    static int inputState(Context ctx, String inputId) {
        TvInputManager tim = manager(ctx);
        if (tim == null) return -1;
        try {
            return tim.getInputState(inputId);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * The TV input manager, or null where the device has no such service. A non-TV Android device
     * (the emulator, a phone) answers getSystemService with a manager whose binder is null, so the
     * first call into it throws NullPointerException; the check has to be a real call, not a null test.
     */
    private static TvInputManager manager(Context ctx) {
        try {
            TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return null;
            tim.getTvInputList();   // throws when the service is missing
            return tim;
        } catch (Exception e) {
            return null;
        }
    }

    /** The channel URI that switches to a passthrough input (HDMI, AV). */
    static android.net.Uri passthroughUri(String inputId) {
        return android.media.tv.TvContract.buildChannelUriForPassthroughInput(inputId);
    }

    /**
     * The Inputs menu contents: HDMI ports first in port order, then AV and the rest, plus the tuner
     * to prefer for Live TV. Built here so MainActivity never touches a TvInputInfo on Android 4.4.
     */
    static Inputs menu(Context ctx) {
        List<TvInputInfo> all = new ArrayList<>(inputs(ctx));
        Collections.sort(all, (a, b) -> {
            boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
            if (ha != hb) return ha ? -1 : 1;
            return String.valueOf(a.loadLabel(ctx)).compareToIgnoreCase(String.valueOf(b.loadLabel(ctx)));
        });
        // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
        // so drop the HDMI status then. A TV with every port really in use loses it too.
        int hdmiPorts = 0, hdmiConnected = 0;
        for (TvInputInfo info : all) {
            if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
            hdmiPorts++;
            if (inputState(ctx, info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
        }
        boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;

        String tunerId = null;
        boolean tunerIsHw = false;
        List<Row> rows = new ArrayList<>();
        for (TvInputInfo info : all) {
            if (info.getType() == TvInputInfo.TYPE_TUNER
                    && (info.getServiceInfo().applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                // Prefer a hardware tuner: the framework names those ".../HW<n>". System apps such as
                // AirPlay also register software inputs of type TUNER and may sort first (seen on TCL G05).
                boolean hw = info.getId().matches(".*/HW\\d+$");
                if (tunerId == null || (hw && !tunerIsHw) || (hw == tunerIsHw && info.getId().compareTo(tunerId) < 0)) {
                    tunerId = info.getId();
                    tunerIsHw = hw;
                }
            }
            if (!info.isPassthroughInput()) continue;
            CharSequence l = info.loadLabel(ctx);
            String label = l == null ? info.getId() : l.toString();
            boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
            if (hdmi && !label.toUpperCase(java.util.Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
            int state = hdmi && hdmiStateBogus ? -1 : inputState(ctx, info.getId());
            rows.add(new Row(info.getId(), label, hdmi, state));
        }
        return new Inputs(rows, tunerId);
    }

    /** A callback that pings the runnable whenever an input is added or removed. */
    static final class Watcher implements Platform.TvWatcher {
        private TvInputManager manager;
        private TvInputManager.TvInputCallback callback;

        @Override
        public void register(Context ctx, Runnable onChange) {
            if (callback != null) return;
            manager = PlatformTv.manager(ctx);
            if (manager == null) return;
            callback = new TvInputManager.TvInputCallback() {
                @Override
                public void onInputAdded(String inputId) {
                    onChange.run();
                }

                @Override
                public void onInputRemoved(String inputId) {
                    onChange.run();
                }
            };
            try {
                manager.registerCallback(callback, new Handler(Looper.getMainLooper()));
            } catch (Exception e) {
                manager = null;
                callback = null;
            }
        }

        @Override
        public void unregister(Context ctx) {
            try {
                if (manager != null && callback != null) manager.unregisterCallback(callback);
            } catch (Exception ignored) {
                // The service went away with the TV input stack; nothing to undo.
            }
            manager = null;
            callback = null;
        }
    }
}
