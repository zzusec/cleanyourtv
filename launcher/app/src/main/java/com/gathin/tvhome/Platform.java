package com.gathin.tvhome;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;

/**
 * Version dispatch for the network and TV-input queries the home screen uses.
 *
 * ConnectivityManager.NetworkCallback, NetworkCapabilities, TvInputManager and
 * WifiInfo.getSSID are all API 21 or newer. On Android 4.4 (Xiaomi Mi TV 2) the classes do not
 * exist at all, so every reference to them lives in {@link PlatformNet} or {@link PlatformTv},
 * which this class only loads after the version check passes.
 */
final class Platform {

    /** Watches the network; null on 4.4, where the pill is refreshed when Home resumes. */
    interface NetWatcher {
        void register(Context ctx, Runnable onChange);

        void unregister(Context ctx);
    }

    /** Watches TV inputs coming and going; null where there is no TvInputManager. */
    interface TvWatcher {
        void register(Context ctx, Runnable onChange);

        void unregister(Context ctx);
    }

    /** What the network pill shows: connection type, Wi-Fi name, and whether a VPN is up. */
    static final class Net {
        final boolean online;
        final boolean ethernet;
        final boolean wifi;
        final boolean vpn;
        final String ssid;
        /** False on 4.4, which cannot tell a VPN from the active network. */
        final boolean vpnKnown;

        Net(boolean online, boolean ethernet, boolean wifi, boolean vpn, String ssid, boolean vpnKnown) {
            this.online = online;
            this.ethernet = ethernet;
            this.wifi = wifi;
            this.vpn = vpn;
            this.ssid = ssid;
            this.vpnKnown = vpnKnown;
        }
    }

    private Platform() {
    }

    static boolean networkCallbacks() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    static boolean tvInputs() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    static NetWatcher netWatcher() {
        return networkCallbacks() ? new PlatformNet.Watcher() : null;
    }

    static TvWatcher tvWatcher() {
        return tvInputs() ? new PlatformTv.Watcher() : null;
    }

    /** The active connection, read the Android 4.4 way where NetworkCapabilities is missing. */
    @SuppressWarnings("deprecation")
    static Net net(Context ctx) {
        if (networkCallbacks()) return PlatformNet.net(ctx);
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo info = cm != null ? cm.getActiveNetworkInfo() : null;
        boolean online = info != null && info.isConnected();
        boolean wifi = online && info.getType() == ConnectivityManager.TYPE_WIFI;
        boolean ethernet = online && info.getType() == ConnectivityManager.TYPE_ETHERNET;
        return new Net(online, ethernet, wifi, false, wifi ? ssidLegacy(ctx) : null, false);
    }

    /** The Wi-Fi name on 4.4: WifiInfo.getSSID is API 1, only the NetworkCapabilities walk is newer. */
    @SuppressWarnings("deprecation")
    private static String ssidLegacy(Context ctx) {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            android.net.wifi.WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
            String ssid = info != null ? info.getSSID() : null;
            if (ssid == null) return null;
            ssid = ssid.replace("\"", "").trim();
            if (ssid.isEmpty() || ssid.contains("unknown") || ssid.equals("0x")) return null;
            return ssid;
        } catch (Exception e) {
            return null;
        }
    }

    /** The leanback launch intent (API 21+), else the ordinary one; both may be null. */
    static android.content.Intent launchIntent(android.content.pm.PackageManager pm, String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        android.content.Intent i = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            i = pm.getLeanbackLaunchIntentForPackage(pkg);
        }
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    /** True when the TV reports at least one passthrough input (HDMI, AV); false on 4.4 and boxes. */
    static boolean hasPassthroughInputs(Context ctx) {
        if (!tvInputs()) return false;
        return !PlatformTv.menu(ctx).rows.isEmpty();
    }
}
