/*
 * Copyright (C) 2010-12  Ciaran Gultnieks, ciaran@ciarang.com
 * Copyright (C) 2013-15 Daniel Martí <mvdan@mvdan.cc>
 * Copyright (C) 2013 Stefan Völkel, bd@bc-bd.org
 * Copyright (C) 2015 Nico Alt, nicoalt@posteo.org
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

package org.fdroid.fdroid.views.appdetails;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Html;
import android.text.Layout;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.ListFragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;

import org.fdroid.fdroid.FDroidApp;
import org.fdroid.fdroid.Preferences;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.Apk;
import org.fdroid.fdroid.data.ApkProvider;
import org.fdroid.fdroid.data.App;
import org.fdroid.fdroid.data.AppPrefs;
import org.fdroid.fdroid.data.AppPrefsProvider;
import org.fdroid.fdroid.data.AppProvider;
import org.fdroid.fdroid.data.Schema;
import org.fdroid.fdroid.installer.InstallManagerService;
import org.fdroid.fdroid.installer.Installer;
import org.fdroid.fdroid.installer.InstallerFactory;
import org.fdroid.fdroid.installer.InstallerService;
import org.fdroid.fdroid.net.Downloader;
import org.fdroid.fdroid.net.DownloaderService;
import org.fdroid.fdroid.privileged.views.AppDiff;
import org.fdroid.fdroid.privileged.views.AppSecurityPermissions;
import org.fdroid.fdroid.views.ScreenShotAdapter;
import org.fdroid.fdroid.views.ScreenShotsActivity;

import java.util.Locale;

public class AppDetails extends AppCompatActivity {

    private static final String TAG = "AppDetails";

    private static final int REQUEST_PERMISSION_DIALOG = 3;
    private static final int REQUEST_UNINSTALL_DIALOG = 4;

    public static final String EXTRA_APPID = "appid";
    public static final String EXTRA_FROM = "from";
    public static final String EXTRA_HINT_SEARCHING = "searching";

    private ApkListAdapter adapter;

    /**
     * Check if {@code packageName} is currently visible to the user.
     */
    public static boolean isAppVisible(String packageName) {
        return packageName != null && packageName.equals(visiblePackageName);
    }

    private static String visiblePackageName;

    static class ViewHolder {
        TextView versionCode;
        TextView version;
        TextView status;
        TextView repository;
        TextView size;
        TextView api;
        TextView incompatibleReasons;
        TextView buildtype;
        TextView added;
        TextView nativecode;
    }

    // observer to update view when package has been installed/deleted
    private AppObserver myAppObserver;

    class AppObserver extends ContentObserver {

        AppObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            onAppChanged();
        }

    }


    private App app;
    private PackageManager packageManager;
    private String activeDownloadUrlString;
    private LocalBroadcastManager localBroadcastManager;

    private AppPrefs startingPrefs;

    private final Context context = this;

    private AppDetailsHeaderFragment headerFragment;

    /**
     * Stores relevant data that we want to keep track of when destroying the activity
     * with the expectation of it being recreated straight away (e.g. after an
     * orientation change). One of the major things is that we want the download thread
     * to stay active, but for it not to trigger any UI stuff (e.g. progress bar)
     * between the activity being destroyed and recreated.
     */
    private static class ConfigurationChangeHelper {

        public final String urlString;
        public final App app;

        ConfigurationChangeHelper(String urlString, App app) {
            this.urlString = urlString;
            this.app = app;
        }
    }

    /**
     * Attempt to extract the packageName from the intent which launched this activity.
     *
     * @return May return null, if we couldn't find the packageName. This should
     * never happen as AppDetails is only to be called by the FDroid activity
     * and not externally.
     */
    private String getPackageNameFromIntent(Intent intent) {
        if (!intent.hasExtra(EXTRA_APPID)) {
            Log.e(TAG, "No package name found in the intent!");
            return null;
        }

        return intent.getStringExtra(EXTRA_APPID);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        FDroidApp fdroidApp = (FDroidApp) getApplication();
        fdroidApp.applyTheme(this);

        super.onCreate(savedInstanceState);

        // Must be called *after* super.onCreate(), as that is where the action bar
        // compat implementation is assigned in the ActionBarActivity base class.
        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_FROM)) {
            setTitle(intent.getStringExtra(EXTRA_FROM));
        }

        packageManager = getPackageManager();

        // Get the preferences we're going to use in this Activity...
        ConfigurationChangeHelper previousData = (ConfigurationChangeHelper) getLastCustomNonConfigurationInstance();
        if (previousData != null) {
            Utils.debugLog(TAG, "Recreating view after configuration change.");
            activeDownloadUrlString = previousData.urlString;
            if (activeDownloadUrlString != null) {
                Utils.debugLog(TAG, "Download was in progress before the configuration change, so we will start to listen to its events again.");
            }
            app = previousData.app;
            setApp(app);
        } else {
            if (!reset(getPackageNameFromIntent(intent))) {
                finish();
                return;
            }
        }

        // Set up the list...
        adapter = new ApkListAdapter(this, app);

        // Wait until all other initialization before doing this, because it will create the
        // fragments, which rely on data from the activity that is set earlier in this method.
        setContentView(R.layout.app_details);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Check for the presence of a view which only exists in the landscape view.
        // This seems to be the preferred way to interrogate the view, rather than
        // to check the orientation. I guess this is because views can be dynamically
        // chosen based on more than just orientation (e.g. large screen sizes).
        View onlyInLandscape = findViewById(R.id.app_summary_container);

        AppDetailsListFragment listFragment =
                (AppDetailsListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_app_list);
        if (onlyInLandscape == null) {
            listFragment.setupSummaryHeader();
        } else {
            listFragment.removeSummaryHeader();
        }

        localBroadcastManager = LocalBroadcastManager.getInstance(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // register observer to know when install status changes
        myAppObserver = new AppObserver(new Handler());
        getContentResolver().registerContentObserver(
                AppProvider.getHighestPriorityMetadataUri(app.packageName),
                true,
                myAppObserver);
    }

    @Override
    protected void onResume() {
        App newApp = AppProvider.Helper.findHighestPriorityMetadata(getContentResolver(), app.packageName);
        if (newApp.isInstalled() != app.isInstalled()) {
            setApp(newApp);
        }
        super.onResume();
    }

    @Override
    protected void onResumeFragments() {
        // Must be called before super.onResumeFragments(), as the fragments depend on the active
        // url being correctly set in order to know whether or not to show the download progress bar.
        calcActiveDownloadUrlString(app.packageName);

        super.onResumeFragments();

        headerFragment = (AppDetailsHeaderFragment) getSupportFragmentManager().findFragmentById(R.id.header);
        refreshApkList();
        supportInvalidateOptionsMenu();
        if (DownloaderService.isQueuedOrActive(activeDownloadUrlString)) {
            registerDownloaderReceiver();
        }
        visiblePackageName = app.packageName;
    }

    /**
     * Remove progress listener, suppress progress bar, set downloadHandler to null.
     */
    private void cleanUpFinishedDownload() {
        activeDownloadUrlString = null;
        if (headerFragment != null) {
            headerFragment.removeProgress();
        }
        unregisterDownloaderReceiver();
    }

    protected void onStop() {
        super.onStop();
        getContentResolver().unregisterContentObserver(myAppObserver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        visiblePackageName = null;
        // save the active URL for this app in case we come back
        getPreferences(MODE_PRIVATE)
                .edit()
                .putString(getPackageNameFromIntent(getIntent()), activeDownloadUrlString)
                .apply();
        if (app != null && !app.getPrefs(this).equals(startingPrefs)) {
            Utils.debugLog(TAG, "Updating 'ignore updates', as it has changed since we started the activity...");
            AppPrefsProvider.Helper.update(this, app, app.getPrefs(this));
        }
        unregisterDownloaderReceiver();
    }

    private void unregisterDownloaderReceiver() {
        if (localBroadcastManager == null) {
            return;
        }
        localBroadcastManager.unregisterReceiver(downloadReceiver);
    }

    private void registerDownloaderReceiver() {
        if (activeDownloadUrlString != null) { // if a download is active
            String url = activeDownloadUrlString;
            localBroadcastManager.registerReceiver(downloadReceiver,
                    DownloaderService.getIntentFilter(url));
        }
    }

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Downloader.ACTION_STARTED:
                    if (headerFragment != null) {
                        headerFragment.startProgress();
                    }
                    break;
                case Downloader.ACTION_PROGRESS:
                    if (headerFragment != null) {
                        headerFragment.updateProgress(intent.getLongExtra(Downloader.EXTRA_BYTES_READ, -1),
                                intent.getLongExtra(Downloader.EXTRA_TOTAL_BYTES, -1));
                    }
                    break;
                case Downloader.ACTION_COMPLETE:
                    // Starts the install process one the download is complete.
                    cleanUpFinishedDownload();
                    localBroadcastManager.registerReceiver(installReceiver,
                            Installer.getInstallIntentFilter(intent.getData()));
                    break;
                case Downloader.ACTION_INTERRUPTED:
                    if (intent.hasExtra(Downloader.EXTRA_ERROR_MESSAGE)) {
                        String msg = intent.getStringExtra(Downloader.EXTRA_ERROR_MESSAGE)
                                + " " + intent.getDataString();
                        Toast.makeText(context, R.string.download_error, Toast.LENGTH_SHORT).show();
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                    } else { // user canceled
                        Toast.makeText(context, R.string.details_notinstalled, Toast.LENGTH_LONG).show();
                    }
                    cleanUpFinishedDownload();
                    break;
                case Downloader.ACTION_CONNECTION_FAILED:
                    //TODO: This probably is weird when we actually use another mirror. We should use InstallManagerService here instead.
                    cleanUpFinishedDownload();
                    break;
                default:
                    throw new RuntimeException("intent action not handled!");
            }
        }
    };

    private final BroadcastReceiver installReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Installer.ACTION_INSTALL_STARTED:
                    headerFragment.startProgress(false);
                    headerFragment.showIndeterminateProgress(getString(R.string.installing));
                    break;
                case Installer.ACTION_INSTALL_COMPLETE:
                    headerFragment.removeProgress();

                    localBroadcastManager.unregisterReceiver(this);
                    break;
                case Installer.ACTION_INSTALL_INTERRUPTED:
                    headerFragment.removeProgress();
                    onAppChanged();

                    String errorMessage =
                            intent.getStringExtra(Installer.EXTRA_ERROR_MESSAGE);

                    if (!TextUtils.isEmpty(errorMessage)) {
                        Log.e(TAG, "install aborted with errorMessage: " + errorMessage);

                        String title = String.format(
                                getString(R.string.install_error_notify_title),
                                app.name);

                        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(AppDetails.this);
                        alertBuilder.setTitle(title);
                        alertBuilder.setMessage(errorMessage);
                        alertBuilder.setNeutralButton(android.R.string.ok, null);
                        alertBuilder.create().show();
                    }

                    localBroadcastManager.unregisterReceiver(this);
                    break;
                case Installer.ACTION_INSTALL_USER_INTERACTION:
                    PendingIntent installPendingIntent =
                            intent.getParcelableExtra(Installer.EXTRA_USER_INTERACTION_PI);

                    try {
                        installPendingIntent.send();
                    } catch (PendingIntent.CanceledException e) {
                        Log.e(TAG, "PI canceled", e);
                    }

                    break;
                default:
                    throw new RuntimeException("intent action not handled!");
            }
        }
    };

    private final BroadcastReceiver uninstallReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Installer.ACTION_UNINSTALL_STARTED:
                    headerFragment.startProgress(false);
                    headerFragment.showIndeterminateProgress(getString(R.string.uninstalling));
                    break;
                case Installer.ACTION_UNINSTALL_COMPLETE:
                    headerFragment.removeProgress();
                    onAppChanged();

                    localBroadcastManager.unregisterReceiver(this);
                    break;
                case Installer.ACTION_UNINSTALL_INTERRUPTED:
                    headerFragment.removeProgress();

                    String errorMessage =
                            intent.getStringExtra(Installer.EXTRA_ERROR_MESSAGE);

                    if (!TextUtils.isEmpty(errorMessage)) {
                        Log.e(TAG, "uninstall aborted with errorMessage: " + errorMessage);

                        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(AppDetails.this);
                        Uri uri = intent.getData();
                        if (uri == null) {
                            alertBuilder.setTitle(getString(R.string.uninstall_error_notify_title, ""));
                        } else {
                            alertBuilder.setTitle(getString(R.string.uninstall_error_notify_title,
                                    uri.getSchemeSpecificPart()));
                        }
                        alertBuilder.setMessage(errorMessage);
                        alertBuilder.setNeutralButton(android.R.string.ok, null);
                        alertBuilder.create().show();
                    }

                    localBroadcastManager.unregisterReceiver(this);
                    break;
                case Installer.ACTION_UNINSTALL_USER_INTERACTION:
                    PendingIntent uninstallPendingIntent =
                            intent.getParcelableExtra(Installer.EXTRA_USER_INTERACTION_PI);

                    try {
                        uninstallPendingIntent.send();
                    } catch (PendingIntent.CanceledException e) {
                        Log.e(TAG, "PI canceled", e);
                    }

                    break;
                default:
                    throw new RuntimeException("intent action not handled!");
            }
        }
    };

    private void onAppChanged() {
        if (!reset(app.packageName)) {
            this.finish();
            return;
        }

        refreshApkList();
        refreshHeader();
        supportInvalidateOptionsMenu();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return new ConfigurationChangeHelper(activeDownloadUrlString, app);
    }

    @Override
    protected void onDestroy() {
        unregisterDownloaderReceiver();
        super.onDestroy();
    }

    // Reset the display and list contents. Used when entering the activity, and
    // also when something has been installed/uninstalled.
    // Return true if the app was found, false otherwise.
    private boolean reset(String packageName) {

        Utils.debugLog(TAG, "Getting application details for " + packageName);
        App newApp = null;

        calcActiveDownloadUrlString(packageName);

        if (!TextUtils.isEmpty(packageName)) {
            newApp = AppProvider.Helper.findHighestPriorityMetadata(getContentResolver(), packageName);
        }

        setApp(newApp);

        return this.app != null;
    }

    private void calcActiveDownloadUrlString(String packageName) {
        String urlString = getPreferences(MODE_PRIVATE).getString(packageName, null);
        if (DownloaderService.isQueuedOrActive(urlString)) {
            activeDownloadUrlString = urlString;
        } else {
            // this URL is no longer active, remove it
            getPreferences(MODE_PRIVATE).edit().remove(packageName).apply();
        }
    }

    /**
     * If passed null, this will show a message to the user ("Could not find app ..." or something
     * like that) and then finish the activity.
     */
    private void setApp(App newApp) {
        if (newApp == null) {
            Toast.makeText(this, R.string.no_such_app, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        app = newApp;

        startingPrefs = app.getPrefs(this).createClone();
    }

    private void refreshApkList() {
        adapter.notifyDataSetChanged();
    }

    private void refreshHeader() {
        if (headerFragment != null) {
            headerFragment.updateViews();
        }
    }

    private void tryOpenUri(String uri, String rawdata) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        if (intent.resolveActivity(packageManager) == null) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("F-Droid Link", rawdata);
            clipboard.setPrimaryClip(clip);

            Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(intent);
    }

    private void navigateUp() {
        NavUtils.navigateUpFromSameTask(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.appdetails, menu);

        // Launch/Run button; Don't show when the main button say "Run"
        menu.findItem(R.id.action_launch).setVisible(packageManager.getLaunchIntentForPackage(app.packageName) != null
                && app.canAndWantToUpdate(this));

        // Uninstall button
        menu.findItem(R.id.action_uninstall).setVisible(app.isInstalled() && app.isUninstallable(this.context));

        // AppInfo button
        menu.findItem(R.id.action_appsettings).setVisible(app.isInstalled());

        //IgnoreAllUpdates button
        menu.findItem(R.id.action_ignore_all_updates)
                .setChecked(app.getPrefs(context).ignoreAllUpdates);

        //IgnoreThisUpdate button
        MenuItem item = menu.findItem(R.id.action_ignore_this_updates);
        item.setChecked(app.getPrefs(context).ignoreThisUpdate >= app.suggestedVersionCode);
        item.setVisible(app.hasUpdates());

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            if (getIntent().hasExtra(EXTRA_HINT_SEARCHING)) {
                finish();
            } else {
                navigateUp();
            }
            return true;
        } else if (itemId == R.id.action_launch) {
            launchApk(app.packageName);
            return true;
        } else if (itemId == R.id.action_share) {
            shareApp(app);
            return true;
        } else if (itemId == R.id.action_uninstall) {
            uninstallApk();
            return true;
        } else if (itemId == R.id.action_appsettings) {
            openAppInfo();
            return true;
        } else if (itemId == R.id.action_ignore_all_updates) {
            app.getPrefs(this).ignoreAllUpdates ^= true;
            item.setChecked(app.getPrefs(this).ignoreAllUpdates);
            return true;
        } else if (itemId == R.id.action_ignore_this_updates) {
            if (app.getPrefs(this).ignoreThisUpdate >= app.suggestedVersionCode) {
                app.getPrefs(this).ignoreThisUpdate = 0;
            } else {
                app.getPrefs(this).ignoreThisUpdate = app.suggestedVersionCode;
            }
            item.setChecked(app.getPrefs(this).ignoreThisUpdate > 0);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openAppInfo() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", app.packageName, null));
        startActivity(intent);
    }

    // Install the version of this app denoted by 'app.curApk'.
    private void install(final Apk apk) {
        if (isFinishing()) {
            return;
        }

        if (!apk.compatible) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.installIncompatible);
            builder.setPositiveButton(R.string.yes,
                    (dialog, whichButton) -> initiateInstall(apk));
            builder.setNegativeButton(R.string.no,
                    (dialog, whichButton) -> {
                    });
            AlertDialog alert = builder.create();
            alert.show();
            return;
        }
        if (app.installedSig != null && apk.sig != null
                && !apk.sig.equals(app.installedSig)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.SignatureMismatch).setPositiveButton(
                    R.string.ok,
                    (dialog, id) -> dialog.cancel());
            AlertDialog alert = builder.create();
            alert.show();
            return;
        }
        initiateInstall(apk);
    }

    private void initiateInstall(Apk apk) {
        Installer installer = InstallerFactory.create(this, apk);
        Intent intent = installer.getPermissionScreen();
        if (intent != null) {
            // permission screen required
            Utils.debugLog(TAG, "permission screen required");
            startActivityForResult(intent, REQUEST_PERMISSION_DIALOG);
            return;
        }

        startInstall(apk);
    }

    private void startInstall(Apk apk) {
        activeDownloadUrlString = apk.getCanonicalUrl();
        registerDownloaderReceiver();
        InstallManagerService.queue(this, app, apk);
    }

    /**
     * Uninstall the app from the current screen.  Since there are many ways
     * to uninstall an app, including from Google Play, {@code adb uninstall},
     * or Settings -> Apps, this method cannot ever be sure that the app isn't
     * already being uninstalled.  So it needs to check that we can actually
     * get info on the installed app, otherwise, just call it interrupted and
     * quit.
     *
     * @see <a href="https://gitlab.com/fdroid/fdroidclient/issues/1435">issue #1435</a>
     */
    public void uninstallApk() {
        Apk apk = app.installedApk;
        if (apk == null) {
            apk = app.getInstalledApk(this);
            if (apk == null) {
                Log.d(TAG, "Couldn't find installed apk for " + app.packageName);
                Toast.makeText(this, R.string.uninstall_error_unknown, Toast.LENGTH_SHORT).show();
                uninstallReceiver.onReceive(this, new Intent(Installer.ACTION_UNINSTALL_INTERRUPTED));
                return;
            }
            app.installedApk = apk;
        }
        Installer installer = InstallerFactory.create(this, apk);
        Intent intent = installer.getUninstallScreen();
        if (intent != null) {
            // uninstall screen required
            Utils.debugLog(TAG, "screen screen required");
            startActivityForResult(intent, REQUEST_UNINSTALL_DIALOG);
            return;
        }
        startUninstall();
    }

    private void startUninstall() {
        localBroadcastManager.registerReceiver(uninstallReceiver,
                Installer.getUninstallIntentFilter(app.packageName));
        InstallerService.uninstall(context, app.installedApk);
    }

    private void launchApk(String packageName) {
        Intent intent = packageManager.getLaunchIntentForPackage(packageName);
        startActivity(intent);
    }

    private void shareApp(App app) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");

        shareIntent.putExtra(Intent.EXTRA_SUBJECT, app.name);
        shareIntent.putExtra(Intent.EXTRA_TEXT, app.name + " (" + app.summary + ") - https://f-droid.org/app/" + app.packageName);

        startActivity(Intent.createChooser(shareIntent, getString(R.string.menu_share)));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_PERMISSION_DIALOG:
                if (resultCode == Activity.RESULT_OK) {
                    Uri uri = data.getData();
                    Apk apk = ApkProvider.Helper.findByUri(this, uri, Schema.ApkTable.Cols.ALL);
                    startInstall(apk);
                }
                break;
            case REQUEST_UNINSTALL_DIALOG:
                if (resultCode == Activity.RESULT_OK) {
                    startUninstall();
                }
                break;
        }
    }

    private App getApp() {
        return app;
    }

    private ApkListAdapter getApks() {
        return adapter;
    }

    public static class AppDetailsSummaryFragment extends Fragment implements ScreenShotAdapter.ScreenshotClickListener {

        final Preferences prefs;
        private AppDetails appDetails;
        private static final int MAX_LINES = 5;
        private static boolean viewAllDescription;
        private TextView description;
        private TextView viewMoreButton;
        private ViewGroup permissionListView;
        private TextView permissionHeader;
        private CharSequence descriptionText;
        private CharSequence shortDescriptionText;
        private final View.OnClickListener expanderPermissions = new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (permissionListView.getVisibility() == View.GONE) {
                    permissionListView.setVisibility(View.VISIBLE);
                    permissionHeader.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(getActivity(), R.drawable.ic_lock_24dp_grey600), null, ContextCompat.getDrawable(getActivity(), R.drawable.ic_expand_less_grey600), null);
                } else {
                    permissionListView.setVisibility(View.GONE);
                    permissionHeader.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(getActivity(), R.drawable.ic_lock_24dp_grey600), null, ContextCompat.getDrawable(getActivity(), R.drawable.ic_expand_more_grey600), null);
                }
            }
        };
        private ViewGroup layoutLinks;

        public AppDetailsSummaryFragment() {
            prefs = Preferences.get();
        }

        @Override
        public void onAttach(@NonNull Context activity) {
            super.onAttach(activity);
            appDetails = (AppDetails) activity;
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            super.onCreateView(inflater, container, savedInstanceState);
            View summaryView = inflater.inflate(R.layout.app_details_summary, container, false);
            setupView(summaryView);
            return summaryView;
        }

        @Override
        public void onResume() {
            super.onResume();
            updateViews(getView());
        }

        // The HTML formatter adds "\n\n" at the end of every paragraph. This
        // is desired between paragraphs, but not at the end of the whole
        // string as it adds unwanted spacing at the end of the TextView.
        // Remove all trailing newlines.
        // Use this function instead of a trim() as that would require
        // converting to String and thus losing formatting (e.g. bold).
        private static CharSequence trimNewlines(CharSequence s) {
            if (s == null || s.length() < 1) {
                return s;
            }
            int i;
            for (i = s.length() - 1; i >= 0; i--) {
                if (s.charAt(i) != '\n') {
                    break;
                }
            }
            if (i == s.length() - 1) {
                return s;
            }
            return s.subSequence(0, i + 1);
        }

        private ViewGroup layoutLinksContent;
        private final View.OnClickListener expanderLinks = new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                TextView linksHeader = layoutLinks.findViewById(R.id.information);

                if (layoutLinksContent.getVisibility() == View.GONE) {
                    layoutLinksContent.setVisibility(View.VISIBLE);
                    linksHeader.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(getActivity(), R.drawable.ic_website), null, ContextCompat.getDrawable(getActivity(), R.drawable.ic_expand_less_grey600), null);
                } else {
                    layoutLinksContent.setVisibility(View.GONE);
                    linksHeader.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(getActivity(), R.drawable.ic_website), null, ContextCompat.getDrawable(getActivity(), R.drawable.ic_expand_more_grey600), null);
                }
            }
        };

        private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
            public void onClick(View v) {
                String url = null;
                String rawdata = null;
                App app = appDetails.getApp();
                int id = v.getId();
                if (id == R.id.website) {
                    url = app.webSite;
                } else if (id == R.id.email) {
                    final String subject = Uri.encode(getString(R.string.app_details_subject, app.name));
                    url = "mailto:" + app.authorEmail + "?subject=" + subject;
                    rawdata = app.authorEmail;
                } else if (id == R.id.source) {
                    url = app.sourceCode;
                } else if (id == R.id.issues) {
                    url = app.issueTracker;
                } else if (id == R.id.changelog) {
                    url = app.changelog;
                } else if (id == R.id.translate) {
                    url = app.translation;
                } else if (id == R.id.liberapay) {
                    url = app.getLiberapayUri();
                } else if (id == R.id.opencollective) {
                    url = app.getOpenCollectiveUri();
                } else if (id == R.id.donate) {
                    url = app.donate;
                } else if (id == R.id.flattr) {
                    url = app.getFlattrUri();
                }
                if (url != null) {
                    ((AppDetails) getActivity()).tryOpenUri(url, rawdata);
                }
            }
        };

        private final View.OnClickListener expanderDescription = new View.OnClickListener() {
            public void onClick(View v) {
                if (viewAllDescription) {
                    description.setMaxLines(Integer.MAX_VALUE);
                    description.setText(descriptionText);
                    viewMoreButton.setText(getString(R.string.less));
                } else {
                    description.setMaxLines(MAX_LINES);
                    description.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                    viewMoreButton.setText(R.string.more);
                    //Workaround for weird scroll behaviour :-/
                    description.setText(shortDescriptionText);
                }
                viewAllDescription ^= true;
            }
        };

        private void setupView(final View view) {
            App app = appDetails.getApp();
            // Expandable description
            description = view.findViewById(R.id.description);
            TextView whatsNewView = view.findViewById(R.id.whats_new);
            RecyclerView screenshots = view.findViewById(R.id.screnshot_list);
            TextView show_screenshots_button = view.findViewById(R.id.show_screenshots);
            if (Preferences.get().onlyShowScreenshotsOnDemand() && app.getAllScreenshots(getContext()).length != 0) {
                show_screenshots_button.setVisibility(View.VISIBLE);
                screenshots.setVisibility(View.GONE);
                show_screenshots_button.setOnClickListener(v -> {
                    show_screenshots_button.setVisibility(View.GONE);
                    screenshots.setVisibility(View.VISIBLE);
                });
            }
            ScreenShotAdapter screenshotAdapter = new ScreenShotAdapter(getContext(), app.getAllScreenshots(getContext()));
            screenshotAdapter.setClickListener(this);
            screenshots.setAdapter(screenshotAdapter);
            viewMoreButton = view.findViewById(R.id.view_more_description);
            permissionHeader = view.findViewById(R.id.permissions);
            permissionListView = view.findViewById(R.id.permission_list);
            descriptionText = trimNewlines(Html.fromHtml(app.description, null, new Utils.HtmlTagHandler()));
            description.setText(descriptionText);
            // If description has more than five lines
            description.setMaxLines(MAX_LINES);
            description.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            viewAllDescription = true;
            viewMoreButton.setOnClickListener(expanderDescription);
            description.post(() -> {
                if (description.getLineCount() <= MAX_LINES) {
                    viewMoreButton.setVisibility(View.GONE);
                } else {
                    Layout layout = description.getLayout();
                    int start = layout.getLineStart(0);
                    int height = description.getHeight();
                    int endLine = layout.getLineForVertical(height);
                    int end = layout.getLineEnd(endLine - 1);
                    shortDescriptionText = trimNewlines(description.getText().subSequence(start, end));
                    description.setText(shortDescriptionText);
                    viewMoreButton.setVisibility(View.VISIBLE);
                }
            });

            // App ID
            final TextView packageNameView = view.findViewById(R.id.package_name);
            if (prefs.expertMode()) {
                packageNameView.setText(app.packageName);
            } else {
                packageNameView.setVisibility(View.GONE);
            }

            //WhatsNew
            if (TextUtils.isEmpty(app.whatsNew)) {
                whatsNewView.setVisibility(View.GONE);
            } else {
                Locale locale = getResources().getConfiguration().locale;

                StringBuilder sbWhatsNew = new StringBuilder();
                sbWhatsNew.append(whatsNewView.getContext().getString(R.string.details_new_in_version,
                        app.getSuggestedVersionName()).toUpperCase(locale));
                sbWhatsNew.append("\n\n");
                sbWhatsNew.append(trimNewlines(app.whatsNew));
                whatsNewView.setText(sbWhatsNew);
                whatsNewView.setVisibility(View.VISIBLE);
            }

            // Summary
            final TextView summaryView = view.findViewById(R.id.summary);
            summaryView.setText(app.summary);

            layoutLinks = view.findViewById(R.id.ll_information);
            layoutLinksContent = layoutLinks.findViewById(R.id.ll_information_content);

            final TextView linksHeader = view.findViewById(R.id.information);
            linksHeader.setOnClickListener(expanderLinks);

            if (app.hasDonationOptions()) {
                linksHeader.setText(R.string.links_and_donations);
            }

            // Website button
            View tv = view.findViewById(R.id.website);
            if (!TextUtils.isEmpty(app.webSite)) {
                tv.setOnClickListener(mOnClickListener);
            } else {
                tv.setVisibility(View.GONE);
            }

            // Email button
            tv = view.findViewById(R.id.email);
            if (!TextUtils.isEmpty(app.authorEmail)) {
                tv.setOnClickListener(mOnClickListener);
            } else {
                tv.setVisibility(View.GONE);
            }

            // Source button
            tv = view.findViewById(R.id.source);
            if (!TextUtils.isEmpty(app.sourceCode)) {
                tv.setOnClickListener(mOnClickListener);
            } else {
                tv.setVisibility(View.GONE);
            }

            // Issues button
            tv = view.findViewById(R.id.issues);
            if (!TextUtils.isEmpty(app.issueTracker)) {
                tv.setOnClickListener(mOnClickListener);
            } else {
                tv.setVisibility(View.GONE);
            }

            // Changelog button
            tv = view.findViewById(R.id.changelog);
            if (!TextUtils.isEmpty(app.changelog)) {
                tv.setOnClickListener(mOnClickListener);
            } else {
                tv.setVisibility(View.GONE);
            }

            // Translate button
            tv = view.findViewById(R.id.translate);
            if (!TextUtils.isEmpty(app.translation)) {
                tv.setOnClickListener(mOnClickListener);
            } else {
                tv.setVisibility(View.GONE);
            }

            // Liberapay button
            tv = view.findViewById(R.id.liberapay);
            if (!TextUtils.isEmpty(app.getLiberapayUri())) {
                tv.setOnClickListener(mOnClickListener);
            } else {
                tv.setVisibility(View.GONE);
            }

            // Open Collective button
            tv = view.findViewById(R.id.opencollective);
            if (!TextUtils.isEmpty(app.getOpenCollectiveUri())) {
                tv.setOnClickListener(mOnClickListener);
            } else {
                tv.setVisibility(View.GONE);
            }

            // Donate button
            tv = view.findViewById(R.id.donate);
            if (!TextUtils.isEmpty(app.donate)) {
                tv.setOnClickListener(mOnClickListener);
            } else {
                tv.setVisibility(View.GONE);
            }

            // Flattr
            tv = view.findViewById(R.id.flattr);
            if (!TextUtils.isEmpty(app.flattrID)) {
                tv.setOnClickListener(mOnClickListener);
            } else {
                tv.setVisibility(View.GONE);
            }

            Apk curApk = null;
            for (int i = 0; i < appDetails.getApks().getCount(); i++) {
                final Apk apk = appDetails.getApks().getItem(i);
                if (apk.versionCode == app.suggestedVersionCode) {
                    curApk = apk;
                    break;
                }
            }

            // Expandable permissions
            final boolean curApkCompatible = curApk != null && curApk.compatible;
            if (!appDetails.getApks().isEmpty() && (curApkCompatible || prefs.showIncompatibleVersions())) {
                // build and set the string once
                buildPermissionInfo();
                permissionHeader.setOnClickListener(expanderPermissions);

            } else {
                permissionHeader.setVisibility(View.GONE);
            }

            // Anti features
            final TextView antiFeaturesView = view.findViewById(R.id.antifeatures);
            if (app.antiFeatures != null) {
                StringBuilder sb = new StringBuilder();
                for (String af : app.antiFeatures) {
                    String afdesc = descAntiFeature(af);
                    sb.append("\t• ").append(afdesc).append('\n');
                }
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 1);
                    antiFeaturesView.setText(sb.toString());
                } else {
                    antiFeaturesView.setVisibility(View.GONE);
                }
            } else {
                antiFeaturesView.setVisibility(View.GONE);
            }

            updateViews(view);
        }

        private void buildPermissionInfo() {
            AppDiff appDiff = new AppDiff(appDetails, appDetails.getApks().getItem(0));
            AppSecurityPermissions perms = new AppSecurityPermissions(appDetails, appDiff.apkPackageInfo);

            permissionListView.addView(perms.getPermissionsView(AppSecurityPermissions.WHICH_ALL));
        }

        private String descAntiFeature(String af) {
            switch (af) {
                case "Ads":
                    return getString(R.string.antiadslist);
                case "Tracking":
                    return getString(R.string.antitracklist);
                case "NonFreeNet":
                    return getString(R.string.antinonfreenetlist);
                case "NonFreeAdd":
                    return getString(R.string.antinonfreeadlist);
                case "NonFreeDep":
                    return getString(R.string.antinonfreedeplist);
                case "UpstreamNonFree":
                    return getString(R.string.antiupstreamnonfreelist);
                case "NonFreeAssets":
                    return getString(R.string.antinonfreeassetslist);
                case "DisabledAlgorithm":
                    return getString(R.string.antidisabledalgorithmlist);
                case "KnownVuln":
                    return getString(R.string.antiknownvulnlist);
                case "NoSourceSince":
                    return getString(R.string.antinosourcesince);
                default:
                    return af;
            }
        }

        void updateViews(View view) {
            if (view == null) {
                Log.e(TAG, "AppDetailsSummaryFragment.updateViews(): view == null. Oops.");
                return;
            }

            App app = appDetails.getApp();
            TextView signatureView = view.findViewById(R.id.signature);
            if (prefs.expertMode() && !TextUtils.isEmpty(app.installedSig)) {
                signatureView.setVisibility(View.VISIBLE);
                signatureView.setText(getString(R.string.signed, app.installedSig));
            } else {
                signatureView.setVisibility(View.GONE);
            }
        }

        @Override
        public void onItemClick(View view, int position) {
            this.startActivity(ScreenShotsActivity.getStartIntent(getContext(), appDetails.getApp().packageName, position));
        }
    }

    public static class AppDetailsHeaderFragment extends Fragment implements View.OnClickListener {

        private AppDetails appDetails;
        private Button btMain;
        private ProgressBar progressBar;
        private TextView progressSize;
        private TextView progressPercent;
        private ImageButton cancelButton;
        final DisplayImageOptions displayImageOptions;
        public static boolean installed;
        static boolean updateWanted;

        public AppDetailsHeaderFragment() {
            displayImageOptions = new DisplayImageOptions.Builder()
                    .cacheInMemory(true)
                    .cacheOnDisk(true)
                    .imageScaleType(ImageScaleType.NONE)
                    .showImageOnLoading(R.drawable.ic_repo_app_default)
                    .showImageForEmptyUri(R.drawable.ic_repo_app_default)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .build();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.app_details_header, container, false);
            setupView(view);
            return view;
        }

        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
            appDetails = (AppDetails) context;
        }

        private void setupView(View view) {
            App app = appDetails.getApp();

            // Set the icon...
            ImageView iv = view.findViewById(R.id.icon);
            Utils.setIconFromRepoOrPM(app, iv, iv.getContext());
            iv.setOnLongClickListener(v -> {
                if (app.isInstalled()) {
                    appDetails.openAppInfo();
                    return true;
                } else {
                    return false;
                }
            });

            // Set the title
            TextView tv = view.findViewById(R.id.title);
            tv.setText(app.name);

            btMain = view.findViewById(R.id.btn_main);
            progressBar = view.findViewById(R.id.progress_bar);
            progressSize = view.findViewById(R.id.progress_size);
            progressPercent = view.findViewById(R.id.progress_percentage);
            cancelButton = view.findViewById(R.id.cancel);
            progressBar.setIndeterminate(false);
            cancelButton.setOnClickListener(this);

            updateViews(view);
        }

        @Override
        public void onResume() {
            super.onResume();
            updateViews();
            restoreProgressBarOnResume();
        }

        /**
         * After resuming the fragment, decide whether or not we need to show the progress bar.
         * Also, put an appropriate message depending on whether or not the download is active or
         * just queued.
         * <p>
         * NOTE: this can't be done in the `updateViews` method as it currently stands. The reason
         * is because that method gets called all the time, for all sorts of reasons. The progress
         * bar is updated with actual progress values in response to async broadcasts. If we always
         * tried to force the progress bar in `updateViews`, it would override the values that were
         * set by the async progress broadcasts.
         */
        private void restoreProgressBarOnResume() {
            if (appDetails.activeDownloadUrlString != null) {
                // We don't actually know what the current progress is, so this will show an indeterminate
                // progress bar until the first progress/complete event we receive.
                if (DownloaderService.isQueuedOrActive(appDetails.activeDownloadUrlString)) {
                    showIndeterminateProgress(getString(R.string.download_pending));
                } else {
                    showIndeterminateProgress("");
                }
            }
        }

        /**
         * Displays empty, indeterminate progress bar and related views.
         */
        void startProgress() {
            startProgress(true);
        }

        void startProgress(boolean allowCancel) {
            cancelButton.setVisibility(allowCancel ? View.VISIBLE : View.GONE);
            if (isAdded()) {
                showIndeterminateProgress(getString(R.string.download_pending));
                updateViews();
            }
        }

        private void showIndeterminateProgress(String message) {
            setProgressVisible(true);
            progressBar.setIndeterminate(true);
            progressSize.setText(message);
            progressPercent.setText("");
        }

        /**
         * Updates progress bar and captions to new values (in bytes).
         */
        void updateProgress(long bytesDownloaded, long totalBytes) {
            if (bytesDownloaded < 0 || totalBytes == 0) {
                // Avoid division by zero and other weird values
                return;
            }

            if (totalBytes == -1) {
                setProgressVisible(true);
                progressBar.setIndeterminate(true);
                progressSize.setText(Utils.getFriendlySize(bytesDownloaded));
                progressPercent.setText("");
            } else {
                long percent = bytesDownloaded * 100 / totalBytes;
                setProgressVisible(true);
                progressBar.setIndeterminate(false);
                progressBar.setProgress((int) percent);
                progressBar.setMax(100);
                progressSize.setText(String.format("%s / %s", Utils.getFriendlySize(bytesDownloaded), Utils.getFriendlySize(totalBytes)));
                progressPercent.setText(percent + " %");
            }
        }

        /**
         * Shows or hides progress bar and related views.
         */
        private void setProgressVisible(boolean visible) {
            int state = visible ? View.VISIBLE : View.GONE;
            progressBar.setVisibility(state);
            progressSize.setVisibility(state);
            progressPercent.setVisibility(state);
        }

        /**
         * Removes progress bar and related views, invokes {@link #updateViews()}.
         */
        void removeProgress() {
            setProgressVisible(false);
            cancelButton.setVisibility(View.GONE);
            updateViews();
        }

        /**
         * Cancels download and hides progress bar.
         */
        @Override
        public void onClick(View view) {
            AppDetails appDetails = (AppDetails) getActivity();
            if (appDetails == null || appDetails.activeDownloadUrlString == null) {
                return;
            }

            InstallManagerService.cancel(getContext(), appDetails.activeDownloadUrlString);
        }

        void updateViews() {
            updateViews(getView());
        }

        void updateViews(View view) {
            if (view == null) {
                Log.e(TAG, "AppDetailsHeaderFragment.updateViews(): view == null. Oops.");
                return;
            }
            App app = appDetails.getApp();
            TextView statusView = view.findViewById(R.id.status);
            btMain.setVisibility(View.VISIBLE);

            if (appDetails.activeDownloadUrlString != null) {
                btMain.setText(R.string.downloading);
                btMain.setEnabled(false);
                btMain.setVisibility(View.GONE);
            } else if (!app.isApk) {
                // Media file, just download it with a browser
                btMain.setText(R.string.button_download);
                btMain.setOnClickListener(v -> {
                    Apk suggestedMediaFile = ApkProvider.Helper.findSuggestedApk(getActivity(), app);
                    Intent downloadIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(suggestedMediaFile.getCanonicalUrl()));
                    startActivity(downloadIntent);
                });
                btMain.setEnabled(true);
                btMain.setVisibility(View.VISIBLE);
            } else if (!app.isInstalled() && app.suggestedVersionCode > 0 &&
                    appDetails.adapter.getCount() > 0) {
                // Check count > 0 due to incompatible apps resulting in an empty list.
                // If App isn't installed
                installed = false;
                statusView.setText(R.string.details_notinstalled);
                // Set Install button and hide second button
                btMain.setText(R.string.menu_install);
                btMain.setOnClickListener(mOnClickListener);
                btMain.setEnabled(true);
                btMain.setVisibility(View.VISIBLE);
            } else if (app.isInstalled()) {
                // If App is installed
                installed = true;
                statusView.setText(getString(R.string.details_installed, app.installedVersionName));
                btMain.setOnClickListener(mOnClickListener);
                btMain.setEnabled(true);
                btMain.setVisibility(View.VISIBLE);
                if (app.canAndWantToUpdate(appDetails)) {
                    updateWanted = true;
                    btMain.setText(R.string.menu_upgrade);
                } else {
                    updateWanted = false;
                    if (appDetails.packageManager.getLaunchIntentForPackage(app.packageName) != null) {
                        btMain.setText(R.string.menu_launch);
                    } else if (app.isUninstallable(getContext())) {
                        btMain.setText(R.string.menu_uninstall);
                    } else {
                        btMain.setVisibility(View.GONE);
                    }
                }
            }
            TextView author = view.findViewById(R.id.author);
            if (!TextUtils.isEmpty(app.authorName)) {
                author.setText(getString(R.string.by_author_format, app.authorName));
                author.setVisibility(View.VISIBLE);
            }
            TextView currentVersion = view.findViewById(R.id.current_version);
            if (!appDetails.getApks().isEmpty()) {
                currentVersion.setText(String.format("%s (%s)", appDetails.getApks().getItem(0).versionName, app.license));
            } else {
                currentVersion.setVisibility(View.GONE);
                btMain.setVisibility(View.GONE);
            }

        }

        private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
            public void onClick(View v) {
                App app = appDetails.getApp();
                AppDetails activity = (AppDetails) getActivity();
                if (updateWanted && app.suggestedVersionCode > 0) {
                    final Apk apkToInstall = ApkProvider.Helper.findSuggestedApk(activity, app);
                    if (apkToInstall == null) {
                        Log.w(TAG, "onClick: apkToInstall == null, this shouldn't happen");
                        return;
                    }
                    activity.install(apkToInstall);
                    return;
                }
                if (installed) {
                    // If installed
                    if (activity.packageManager.getLaunchIntentForPackage(app.packageName) != null) {
                        // If "launchable", launch
                        activity.launchApk(app.packageName);
                    } else if (app.isUninstallable(getContext())) {
                        activity.uninstallApk();
                    }
                } else if (app.suggestedVersionCode > 0) {
                    // If not installed, install
                    btMain.setEnabled(false);
                    btMain.setText(R.string.system_install_installing);
                    final Apk apkToInstall = ApkProvider.Helper.findSuggestedApk(activity, app);
                    if (apkToInstall == null) {
                        Log.w(TAG, "onClick: apkToInstall == null, this shouldn't happen");
                        return;
                    }
                    activity.install(apkToInstall);
                }
            }
        };
    }

    public static class AppDetailsListFragment extends ListFragment {

        private static final String SUMMARY_TAG = "summary";

        private AppDetails appDetails;
        private AppDetailsSummaryFragment summaryFragment;

        private FrameLayout headerView;

        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
            appDetails = (AppDetails) context;
        }

        @Override
        public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
            // A bit of a hack, but we can't add the header view in setupSummaryHeader(),
            // due to the fact it needs to happen before setListAdapter(). Also, seeing
            // as we may never add a summary header (i.e. in landscape), this is probably
            // the last opportunity to set the list adapter. As such, we use the headerView
            // as a mechanism to optionally allow adding a header in the future.
            if (headerView == null) {
                headerView = new FrameLayout(getActivity());
                headerView.setId(R.id.appDetailsSummaryHeader);
            } else {
                Fragment summaryFragment = getChildFragmentManager().findFragmentByTag(SUMMARY_TAG);
                if (summaryFragment != null) {
                    getChildFragmentManager().beginTransaction().remove(summaryFragment).commit();
                }
            }

            setListAdapter(null);
            getListView().addHeaderView(headerView);
            setListAdapter(appDetails.getApks());
        }

        @Override
        public void onListItemClick(ListView l, @NonNull View v, int position, long id) {
            App app = appDetails.getApp();
            final Apk apk = appDetails.getApks().getItem(position - l.getHeaderViewsCount());
            if (!app.isApk) {
                // media file - give it to the browser
                Intent downloadIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(apk.getCanonicalUrl()));
                startActivity(downloadIntent);
            } else if (app.installedVersionCode == apk.versionCode && app.isUninstallable(this.appDetails)) {
                appDetails.uninstallApk();
            } else if (app.installedVersionCode > apk.versionCode) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(R.string.installDowngrade);
                builder.setPositiveButton(R.string.yes,
                        (dialog, whichButton) -> appDetails.install(apk));
                builder.setNegativeButton(R.string.no,
                        (dialog, whichButton) -> {
                        });
                AlertDialog alert = builder.create();
                alert.show();
            } else {
                appDetails.install(apk);
            }
        }

        void removeSummaryHeader() {
            Fragment summary = getChildFragmentManager().findFragmentByTag(SUMMARY_TAG);
            if (summary != null) {
                getChildFragmentManager().beginTransaction().remove(summary).commit();
                headerView.removeAllViews();
                headerView.setVisibility(View.GONE);
                summaryFragment = null;
            }
        }

        void setupSummaryHeader() {
            Fragment fragment = getChildFragmentManager().findFragmentByTag(SUMMARY_TAG);
            if (fragment != null) {
                summaryFragment = (AppDetailsSummaryFragment) fragment;
            } else {
                summaryFragment = new AppDetailsSummaryFragment();
            }
            getChildFragmentManager().beginTransaction().replace(headerView.getId(), summaryFragment, SUMMARY_TAG).commit();
            headerView.setVisibility(View.VISIBLE);
        }
    }

}
