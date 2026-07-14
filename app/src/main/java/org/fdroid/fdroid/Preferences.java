package org.fdroid.fdroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import info.guardianproject.netcipher.NetCipher;

/**
 * Handles shared preferences for FDroid, looking after the names of
 * preferences, default values and caching. Needs to be setup in the FDroidApp
 * (using {@link Preferences#setup(android.content.Context)} before it gets
 * accessed via the {@link org.fdroid.fdroid.Preferences#get()}
 * singleton method.
 */
public final class Preferences implements SharedPreferences.OnSharedPreferenceChangeListener {

    //This is more like a button, not a preference
    public static final String RESET_TRANSIENT = "resetTransient";
    public static final String LANGUAGE_IN_SYSTEM_SETTINGS = "languageSystem";

    private static final String TAG = "Preferences";

    private final SharedPreferences preferences;

    private Preferences(Context context) {
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    public static final String PREF_UPD_INTERVAL = "updateInterval";
    public static final String PREF_UPD_WIFI_ONLY = "updateOnWifiOnly";
    public static final String PREF_AUTO_DOWNLOAD_INSTALL_UPDATES = "updateAutoDownload";
    public static final String PREF_UPD_NOTIFY = "updateNotify";
    public static final String PREF_UPD_HISTORY = "updateHistoryDays";
    public static final String PREF_INCOMP_VER = "incompatibleVersions";
    public static final String PREF_THEME = "theme";
    public static final String PREF_IGN_TOUCH = "ignoreTouchscreen";
    public static final String PREF_KEEP_CACHE_TIME = "keepCacheFor";
    public static final String PREF_UNSTABLE_UPDATES = "unstableUpdates";
    public static final String PREF_EXPERT = "expert";
    public static final String PREF_PRIVILEGED_INSTALLER = "privilegedInstaller";
    public static final String PREF_LANGUAGE = "language";
    public static final String PREF_USE_TOR = "useTor";
    public static final String PREF_ENABLE_PROXY = "enableProxy";
    public static final String PREF_PROXY_HOST = "proxyHost";
    public static final String PREF_PROXY_PORT = "proxyPort";
    public static final String PREF_ON_DEMAND_SCREENSHOTS = "screenshotsOnDemand";
    public static final String PREF_DISABLE_PULL_TO_REFRESH = "disablePullToRefresh";

    private static final int DEFAULT_UPD_HISTORY = 14;
    private static final boolean DEFAULT_PRIVILEGED_INSTALLER = true;
    private static final long DEFAULT_KEEP_CACHE_TIME = TimeUnit.DAYS.toMillis(1);
    private static final boolean DEFAULT_UNSTABLE_UPDATES = false;
    private static final boolean DEFAULT_INCOMP_VER = false;
    private static final boolean DEFAULT_EXPERT = false;
    private static final boolean DEFAULT_ENABLE_PROXY = false;
    public static final String DEFAULT_THEME = "follow_system";
    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public static final String DEFAULT_PROXY_HOST = "127.0.0.1";
    public static final int DEFAULT_PROXY_PORT = 8118;

    public enum Theme {
        light,
        dark,
        night,
        follow_system,
    }



    private final List<ChangeListener> updateHistoryListeners = new ArrayList<>();
    private final List<ChangeListener> unstableUpdatesListeners = new ArrayList<>();
    private final List<ChangeListener> privextListeners = new ArrayList<>();

    /**
     * Whether to use the Privileged Installer, based on if it is installed.  Only the disabled
     * state is stored as a preference since the enabled state is based entirely on the presence
     * of the Privileged Extension.  The preference provides a way to disable using the
     * Privileged Extension even though its installed.
     *
     * @see org.fdroid.fdroid.views.fragments.PreferencesFragment#initPrivilegedInstallerPreference()
     */
    public boolean isPrivilegedInstallerEnabled() {
        return preferences.getBoolean(PREF_PRIVILEGED_INSTALLER, DEFAULT_PRIVILEGED_INSTALLER);
    }

    /**
     * Old preference replaced by {@link #PREF_KEEP_CACHE_TIME}
     */
    private static final String PREF_CACHE_APK = "cacheDownloaded";

    /**
     * Time in millis to keep cached files.  Anything that has been around longer will be deleted
     */
    public long getKeepCacheTime() {
        String value = preferences.getString(PREF_KEEP_CACHE_TIME,
                String.valueOf(DEFAULT_KEEP_CACHE_TIME));

        // the first time this was migrated, it was botched, so reset to default
        switch (value) {
            case "3600":
            case "86400":
            case "604800":
            case "2592000":
            case "31449600":
            case "2147483647":
                SharedPreferences.Editor editor = preferences.edit();
                editor.remove(PREF_KEEP_CACHE_TIME);
                editor.apply();
                return Preferences.DEFAULT_KEEP_CACHE_TIME;
        }

        if (preferences.contains(PREF_CACHE_APK)) {
            if (preferences.getBoolean(PREF_CACHE_APK, false)) {
                value = String.valueOf(Long.MAX_VALUE);
            }
            SharedPreferences.Editor editor = preferences.edit();
            editor.remove(PREF_CACHE_APK);
            editor.putString(PREF_KEEP_CACHE_TIME, value);
            editor.apply();
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return DEFAULT_KEEP_CACHE_TIME;
        }
    }

    public boolean getUnstableUpdates() {
        return preferences.getBoolean(PREF_UNSTABLE_UPDATES, DEFAULT_UNSTABLE_UPDATES);
    }

    public boolean showIncompatibleVersions() {
        return preferences.getBoolean(PREF_INCOMP_VER, DEFAULT_INCOMP_VER);
    }

    public boolean expertMode() {
        return preferences.getBoolean(PREF_EXPERT, DEFAULT_EXPERT);
    }

    public Theme getTheme() {
        return Theme.valueOf(preferences.getString(Preferences.PREF_THEME, Preferences.DEFAULT_THEME));
    }

    public boolean isUpdateNotificationEnabled() {
        return preferences.getBoolean(PREF_UPD_NOTIFY, true);
    }

    public boolean isAutoDownloadEnabled() {
        return preferences.getBoolean(PREF_AUTO_DOWNLOAD_INSTALL_UPDATES, false);
    }

    public boolean isUpdateOnlyOnUnmeteredNetworks() {
        return preferences.getBoolean(PREF_UPD_WIFI_ONLY, false);
    }

    public boolean onlyShowScreenshotsOnDemand() {
        return preferences.getBoolean(PREF_ON_DEMAND_SCREENSHOTS, false);
    }

    public boolean pullToRefreshEnabled() {
        return !preferences.getBoolean(PREF_DISABLE_PULL_TO_REFRESH, false);
    }

    /**
     * This preference's default is set dynamically based on whether Orbot is
     * installed. If Orbot is installed, default to using Tor, the user can still override
     */
    public boolean isTorEnabled() {
        // TODO enable once Orbot can auto-start after first install
        //return preferences.getBoolean(PREF_USE_TOR, OrbotHelper.requestStartTor(context));
        return preferences.getBoolean(PREF_USE_TOR, false);
    }

    private boolean isProxyEnabled() {
        return preferences.getBoolean(PREF_ENABLE_PROXY, DEFAULT_ENABLE_PROXY);
    }

    /**
     * Configure the proxy settings based on whether its enabled and set up. This must be
     * run once at app startup, then whenever any of these settings changes.
     */
    public void configureProxy() {
        if (isProxyEnabled()) {
            // if "Use Tor" is set, NetCipher will ignore these proxy settings
            SocketAddress sa = new InetSocketAddress(getProxyHost(), getProxyPort());
            NetCipher.setProxy(new Proxy(Proxy.Type.HTTP, sa));
        }
    }

    public String getProxyHost() {
        return preferences.getString(PREF_PROXY_HOST, DEFAULT_PROXY_HOST);
    }

    public int getProxyPort() {
        final String port = preferences.getString(PREF_PROXY_PORT, String.valueOf(DEFAULT_PROXY_PORT));
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException e) {
            // hack until this can be a number-only preference
            try {
                return Integer.parseInt(port.replaceAll("[^0-9]", ""));
            } catch (Exception e1) {
                return DEFAULT_PROXY_PORT;
            }
        }
    }

    /**
     * Calculate the cutoff date we'll use for What's New and Recently
     * Updated...
     */
    public Date calcMaxHistory() {
        final String daysString = preferences.getString(PREF_UPD_HISTORY, Integer.toString(DEFAULT_UPD_HISTORY));
        int maxHistoryDays;
        try {
            maxHistoryDays = Integer.parseInt(daysString);
        } catch (NumberFormatException e) {
            maxHistoryDays = DEFAULT_UPD_HISTORY;
        }
        Calendar recent = Calendar.getInstance();
        recent.add(Calendar.DAY_OF_YEAR, -maxHistoryDays);
        return recent.getTime();
    }


    public void registerPrivextChangeListener(ChangeListener listener) {
        privextListeners.add(listener);
    }

    public void registerUnstableUpdatesChangeListener(ChangeListener listener) {
        unstableUpdatesListeners.add(listener);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Utils.debugLog(TAG, "Invalidating preference '" + key + "'.");

        switch (key) {
            case PREF_PRIVILEGED_INSTALLER:
                for (ChangeListener listener : privextListeners) {
                    listener.onPreferenceChange();
                }
                break;
            case PREF_UPD_HISTORY:
                for (ChangeListener listener : updateHistoryListeners) {
                    listener.onPreferenceChange();
                }
                break;
            case PREF_UNSTABLE_UPDATES:
                for (ChangeListener listener : unstableUpdatesListeners) {
                    listener.onPreferenceChange();
                }
                break;
        }
    }

    public void registerUpdateHistoryListener(ChangeListener listener) {
        updateHistoryListeners.add(listener);
    }

    public interface ChangeListener {
        void onPreferenceChange();
    }

    private static Preferences instance;

    /**
     * Needs to be setup before anything else tries to access it.
     */
    public static void setup(Context context) {
        if (instance != null) {
            final String error = "Attempted to reinitialize preferences after it " +
                    "has already been initialized in FDroidApp";
            Log.e(TAG, error);
            throw new RuntimeException(error);
        }
        instance = new Preferences(context);
    }

    public static Preferences get() {
        if (instance == null) {
            final String error = "Attempted to access preferences before it " +
                    "has been initialized in FDroidApp";
            Log.e(TAG, error);
            throw new RuntimeException(error);
        }
        return instance;
    }

}
