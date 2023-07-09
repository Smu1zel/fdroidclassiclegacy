/*
 * Copyright (C) 2010-12  Ciaran Gultnieks, ciaran@ciarang.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.fdroid.fdroid;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.StrictMode;
import android.util.Log;

import androidx.collection.LongSparseArray;
import androidx.multidex.MultiDexApplication;

import com.nostra13.universalimageloader.cache.disc.DiskCache;
import com.nostra13.universalimageloader.cache.disc.impl.UnlimitedDiskCache;
import com.nostra13.universalimageloader.cache.disc.impl.ext.LruDiskCache;
import com.nostra13.universalimageloader.core.DefaultConfigurationFactory;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

import org.fdroid.fdroid.Preferences.Theme;
import org.fdroid.fdroid.data.AppProvider;
import org.fdroid.fdroid.data.InstalledAppProviderService;
import org.fdroid.fdroid.data.Repo;
import org.fdroid.fdroid.data.RepoProvider;
import org.fdroid.fdroid.net.ImageLoaderForUIL;
import org.ligi.tracedroid.TraceDroid;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import info.guardianproject.netcipher.NetCipher;
import info.guardianproject.netcipher.proxy.OrbotHelper;

public class FDroidApp extends MultiDexApplication {

    private static final String TAG = "FDroidApp";

    private static Theme curTheme = Theme.follow_system;

    public void reloadTheme() {
        curTheme = Preferences.get().getTheme();
    }

    public void applyTheme(Activity activity) {
        activity.setTheme(getCurThemeResId(isNightMode()));
    }

    public static Context getInstance() {
        return instance;
    }

    private static FDroidApp instance;

    private ExecutorService databaseExecutor;
    private ExecutorService imageCacheExecutor;

    public void applyDialogTheme(Activity activity) {
        activity.setTheme(getCurDialogThemeResId());
    }

    public static int getCurThemeResId(Boolean isNightMode) {
        switch (curTheme) {
            case follow_system:
                return isNightMode ? R.style.AppThemeDark : R.style.AppThemeLight;
            case light:
                return R.style.AppThemeLight;
            case dark:
                return R.style.AppThemeDark;
            case night:
                return R.style.AppThemeNight;
            default:
                return R.style.AppThemeLight;
        }
    }

    public Boolean isNightMode() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int getCurDialogThemeResId() {
        switch (curTheme) {
            case light:
                return R.style.MinWithDialogBaseThemeLight;
            case dark:
                return R.style.MinWithDialogBaseThemeDark;
            case night:
                return R.style.MinWithDialogBaseThemeNight;
            default:
                return R.style.MinWithDialogBaseThemeLight;
        }
    }

    /**
     * Force reload the {@link Activity} to make theme changes take effect.
     *
     * @param activity the {@code Activity} to force reload
     */
    public static void forceChangeTheme(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null) { // when launched as LAUNCHER
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.finish();
        activity.overridePendingTransition(0, 0);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }

    public static int getTimeout() {
        return timeout;
    }

    public static String getMirror(String urlString, long repoId) throws IOException {
        return getMirror(urlString, RepoProvider.Helper.findById(getInstance(), repoId));
    }

    public static String getMirror(String urlString, Repo repo) throws IOException {
        if (repo.hasMirrors()) {
            String lastWorkingMirror = lastWorkingMirrorArray.get(repo.getId());
            if (lastWorkingMirror == null) {
                lastWorkingMirror = repo.address;
            }
            if (numTries <= 0) {
                if (timeout == 10000) {
                    timeout = 30000;
                    numTries = Integer.MAX_VALUE;
                } else if (timeout == 30000) {
                    timeout = 60000;
                    numTries = Integer.MAX_VALUE;
                } else {
                    Utils.debugLog(TAG, "Mirrors: Giving up");
                    throw new IOException("Ran out of mirrors");
                }
            }
            if (numTries == Integer.MAX_VALUE) {
                numTries = repo.getMirrorCount();
            }
            String mirror = repo.getMirror(lastWorkingMirror);
            if (mirror == null) {
                throw new IOException("No mirrors available");
            }
            String newUrl = urlString.replace(lastWorkingMirror, mirror);
            Utils.debugLog(TAG, "Trying mirror " + mirror + " after " + lastWorkingMirror + " failed," +
                    " timeout=" + timeout / 1000 + "s");
            lastWorkingMirrorArray.put(repo.getId(), mirror);
            numTries--;
            return newUrl;
        } else {
            throw new IOException("No mirrors available");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        TraceDroid.init(this);
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }

        databaseExecutor = Executors.newSingleThreadExecutor();
        imageCacheExecutor = Executors.newFixedThreadPool(getThreadPoolSize());

        Preferences.setup(this);
        curTheme = Preferences.get().getTheme();
        Preferences.get().configureProxy();

        InstalledAppProviderService.compareToPackageManager(this);

        final Context context = this;
        Preferences.get().registerUnstableUpdatesChangeListener(
                () -> AppProvider.Helper.calcSuggestedApks(context)
        );

        CleanCacheService.schedule(this);

        UpdateService.schedule(getApplicationContext());

        DiskCache diskCache;
        long available = Utils.getImageCacheDirAvailableMemory(this);
        long memFree = Utils.getImageCacheDirTotalMemory(this);
        int percentageFree;
        if (memFree == 0) {
            percentageFree = 0;
        } else {
            percentageFree = Utils.getPercent(available, memFree);
        }
        if (percentageFree > 5) {
            diskCache = new UnlimitedDiskCache(Utils.getImageCacheDir(this));
        } else {
            Log.i(TAG, "Switching to LruDiskCache(" + available / 2L + ") to save disk space!");
            try {
                diskCache = new LruDiskCache(Utils.getImageCacheDir(this),
                        DefaultConfigurationFactory.createFileNameGenerator(),
                        available / 2L);
            } catch (IOException e) {
                diskCache = new UnlimitedDiskCache(Utils.getImageCacheDir(this));
            }
        }

        ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(getApplicationContext())
                .imageDownloader(new ImageLoaderForUIL(getApplicationContext()))
                .defaultDisplayImageOptions(Utils.getDefaultDisplayImageOptionsBuilder().build())
                .diskCache(diskCache)
                .threadPoolSize(getThreadPoolSize())
                .taskExecutorForCachedImages(imageCacheExecutor)
                .build();
        ImageLoader.getInstance().init(config);

        configureTor(Preferences.get().isTorEnabled());
    }

    /**
     * Return the number of threads Universal Image Loader should use, based on
     * the total RAM in the device.  Devices with lots of RAM can do lots of
     * parallel operations for fast icon loading.
     */
    private int getThreadPoolSize() {
        if (Build.VERSION.SDK_INT >= 16) {
            ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            if (activityManager != null) {
                activityManager.getMemoryInfo(memInfo);
                return (int) Math.max(1, Math.min(16, memInfo.totalMem / 256 / 1024 / 1024));
            }
        }
        return 2;
    }

    public static ExecutorService getDatabaseExecutor() {
        return instance.databaseExecutor;
    }

    public static ExecutorService getImageCacheExecutor() {
        return instance.imageCacheExecutor;
    }

    private static final LongSparseArray<String> lastWorkingMirrorArray = new LongSparseArray<>(1);
    private static volatile int numTries = Integer.MAX_VALUE;
    private static volatile int timeout = 10000;

    public static void resetMirrorVars() {
        // Reset last working mirror, numtries, and timeout
        for (int i = 0; i < lastWorkingMirrorArray.size(); i++) {
            lastWorkingMirrorArray.removeAt(i);
        }
        numTries = Integer.MAX_VALUE;
        timeout = 10000;
    }

    private static boolean useTor;

    /**
     * Set the proxy settings based on whether Tor should be enabled or not.
     */
    private static void configureTor(boolean enabled) {
        useTor = enabled;
        if (useTor) {
            NetCipher.useTor();
        } else {
            NetCipher.clearProxy();
        }
    }

    public static void checkStartTor(Context context) {
        if (useTor) {
            OrbotHelper.requestStartTor(context);
        }
    }

    public static boolean isUsingTor() {
        return useTor;
    }
}
