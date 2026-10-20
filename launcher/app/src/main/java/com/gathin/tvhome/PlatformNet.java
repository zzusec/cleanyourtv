package com.gathin.tvhome;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

/**
 * The API 21+ half of {@link Platform}: network queries and the network callback.
 * Only loaded once {@link Platform#networkCallbacks()} is true; Android 4.4 never sees these types.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
final class PlatformNet {

    private PlatformNet() {
    }

    static Platform.Net net(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return new Platform.Net(false, false, false, false, null, true);
        boolean vpn = false, online = false, wifi = false, ethernet = false;
        for (Network net : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            if (caps == null) continue;
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                vpn = true;
                continue;
            }
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
            online = true;
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
        }
        return new Platform.Net(online, ethernet, wifi, vpn, wifi ? ssid(ctx) : null, true);
    }

    /** The saved Wi-Fi network's name, or null when the system will not tell us. */
    @SuppressWarnings("deprecation")
    static String ssid(Context ctx) {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
            String ssid = info != null ? info.getSSID() : null;
            if (ssid == null) return null;
            ssid = ssid.replace("\"", "").trim();
            if (ssid.isEmpty() || ssid.contains("unknown") || ssid.equals("0x")) return null;
            return ssid;
        } catch (Exception e) {
            return null;
        }
    }

    /** A callback that pings the runnable whenever a network comes or goes. */
    static final class Watcher implements Platform.NetWatcher {
        private ConnectivityManager.NetworkCallback callback;

        @Override
        public void register(Context ctx, Runnable onChange) {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null || callback != null) return;
            callback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    onChange.run();
                }

                @Override
                public void onLost(Network network) {
                    onChange.run();
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback);
            } else {
                // Android 5/6 have no default-network callback: watch the transports the pill names.
                cm.registerNetworkCallback(new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback);
                cm.registerNetworkCallback(new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET).build(), callback);
            }
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), callback);
        }

        @Override
        public void unregister(Context ctx) {
            if (callback == null) return;
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                try {
                    // One callback object backs every request, so this removes all of them.
                    cm.unregisterNetworkCallback(callback);
                } catch (Exception ignored) {
                }
            }
            callback = null;
        }
    }
}
