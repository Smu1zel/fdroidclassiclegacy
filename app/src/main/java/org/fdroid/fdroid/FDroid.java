/*
 * Copyright (C) 2010-12  Ciaran Gultnieks, ciaran@ciarang.com
 * Copyright (C) 2009  Roberto Jacinto, roberto.jacinto@caixamagica.pt
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

import static org.fdroid.fdroid.UpdateService.EXTRA_STATUS_CODE;
import static org.fdroid.fdroid.UpdateService.LOCAL_ACTION_STATUS;
import static org.fdroid.fdroid.UpdateService.STATUS_INFO;

import android.app.NotificationManager;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;

import org.fdroid.fdroid.compat.TabManager;
import org.fdroid.fdroid.data.AppProvider;
import org.fdroid.fdroid.data.NewRepoConfig;
import org.fdroid.fdroid.installer.PrivilegedInstaller;
import org.fdroid.fdroid.views.AppListFragmentPagerAdapter;
import org.fdroid.fdroid.views.ManageReposActivity;
import org.fdroid.fdroid.views.appdetails.AppDetails;
import org.ligi.tracedroid.sending.TraceDroidEmailSender;

public class FDroid extends AppCompatActivity implements SearchView.OnQueryTextListener {

    private static final String TAG = "FDroid";

    private static final int REQUEST_PREFS = 1;

    public static final String EXTRA_TAB_UPDATE = "extraTab";

    private static final String ACTION_ADD_REPO = "org.fdroid.fdroid.FDroid.ACTION_ADD_REPO";

    private static final String ADD_REPO_INTENT_HANDLED = "addRepoIntentHandled";

    private SearchView searchView;

    private ViewPager viewPager;

    @Nullable
    private TabManager tabManager;

    private AppListFragmentPagerAdapter adapter;

    @Nullable
    private MenuItem searchMenuItem;
    private MenuItem updateAllMenuItem;

    @Nullable
    private String pendingSearchQuery;
    private BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        TraceDroidEmailSender.sendStackTraces("fdroidclassic@bubu1.eu", this);
        FDroidApp fdroidApp = (FDroidApp) getApplication();
        fdroidApp.applyTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.fdroid);
        createViews();
        getTabManager().createTabs();

        // Start a search by just typing
        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        Intent intent = getIntent();
        handleSearchOrAppViewIntent(intent);

        if (intent.hasExtra(EXTRA_TAB_UPDATE)) {
            boolean showUpdateTab = intent.getBooleanExtra(EXTRA_TAB_UPDATE, false);
            if (showUpdateTab) {
                getTabManager().selectTab(2);
            }
        }
        Uri uri = AppProvider.getContentUri();
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (TextUtils.isEmpty(action)) {
                    return;
                }
                if (action.equals(LOCAL_ACTION_STATUS)) {
                    int resultCode = intent.getIntExtra(EXTRA_STATUS_CODE, -1);
                    if (resultCode < STATUS_INFO) {
                        refreshUpdateTabLabel();
                    }
                }
            }
        };
    }

    private void performSearch(String query) {
        if (searchMenuItem == null) {
            // Store this for later when we do actually have a search menu ready to use.
            pendingSearchQuery = query;
            return;
        }

        SearchView searchView = (SearchView) searchMenuItem.getActionView();
        searchMenuItem.expandActionView();
        searchView.setQuery(query, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        FDroidApp.checkStartTor(this);
        checkForAddRepoIntent(getIntent());
        refreshUpdateTabLabel();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSearchOrAppViewIntent(intent);

        // This is called here as well as onResume(), because onNewIntent() is not called the first
        // time the activity is created. An alternative option to make sure that the add repo intent
        // is always handled is to call setIntent(intent) here. However, after this good read:
        // http://stackoverflow.com/a/7749347 it seems that adding a repo is not really more
        // important than the original intent which caused the activity to start (even though it
        // could technically have been an add repo intent itself).
        // The end result is that this method will be called twice for one add repo intent. Once
        // here and once in onResume(). However, the method deals with this by ensuring it only
        // handles the same intent once.
        checkForAddRepoIntent(intent);
    }

    private void handleSearchOrAppViewIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            performSearch(query);
            return;
        }

        final Uri data = intent.getData();
        if (data == null) {
            return;
        }

        final String scheme = data.getScheme();
        final String path = data.getPath();
        String packageName = null;
        String query = null;
        if (data.isHierarchical()) {
            final String host = data.getHost();
            if (host == null) {
                return;
            }
            switch (host) {
                case "f-droid.org":
                case "www.f-droid.org":
                case "staging.f-droid.org":
                    if (path.startsWith("/app/") || path.startsWith("/packages/")
                            || path.matches("^/[a-z][a-z][a-zA-Z_-]*/packages/.*")) {
                        // http://f-droid.org/app/packageName
                        packageName = data.getLastPathSegment();
                    } else if (path.startsWith("/repository/browse")) {
                        // http://f-droid.org/repository/browse?fdfilter=search+query
                        query = data.getQueryParameter("fdfilter");

                        // http://f-droid.org/repository/browse?fdid=packageName
                        packageName = data.getQueryParameter("fdid");
                    }
                    break;
                case "details":
                    // market://details?id=app.id
                    packageName = data.getQueryParameter("id");
                    break;
                case "search":
                    // market://search?q=query
                    query = data.getQueryParameter("q");
                    break;
                case "play.google.com":
                    if (path.startsWith("/store/apps/details")) {
                        // http://play.google.com/store/apps/details?id=app.id
                        packageName = data.getQueryParameter("id");
                    } else if (path.startsWith("/store/search")) {
                        // http://play.google.com/store/search?q=foo
                        query = data.getQueryParameter("q");
                    }
                    break;
                case "apps":
                case "amazon.com":
                case "www.amazon.com":
                    // amzn://apps/android?p=app.id
                    // http://amazon.com/gp/mas/dl/android?s=app.id
                    packageName = data.getQueryParameter("p");
                    query = data.getQueryParameter("s");
                    break;
            }
        } else if ("fdroid.app".equals(scheme)) {
            // fdroid.app:app.id
            packageName = data.getSchemeSpecificPart();
        } else if ("fdroid.search".equals(scheme)) {
            // fdroid.search:query
            query = data.getSchemeSpecificPart();
        }

        if (!TextUtils.isEmpty(query)) {
            // an old format for querying via packageName
            if (query.startsWith("pname:")) {
                packageName = query.split(":")[1];
            }

            // sometimes, search URLs include pub: or other things before the query string
            if (query.contains(":")) {
                query = query.split(":")[1];
            }
        }

        if (!TextUtils.isEmpty(packageName)) {
            Utils.debugLog(TAG, "FDroid launched via app link for '" + packageName + "'");
            Intent intentToInvoke = new Intent(this, AppDetails.class);
            intentToInvoke.putExtra(AppDetails.EXTRA_APPID, packageName);
            startActivity(intentToInvoke);
            finish();
        } else if (!TextUtils.isEmpty(query)) {
            Utils.debugLog(TAG, "FDroid launched via search link for '" + query + "'");
            performSearch(query);
        }
    }

    private void checkForAddRepoIntent(Intent intent) {
        // Don't handle the intent after coming back to this view (e.g. after hitting the back button)
        // http://stackoverflow.com/a/14820849
        if (!intent.hasExtra(ADD_REPO_INTENT_HANDLED)) {
            intent.putExtra(ADD_REPO_INTENT_HANDLED, true);
            NewRepoConfig parser = new NewRepoConfig(intent.getData());
            if (parser.isValidRepo()) {
                startActivity(new Intent(ACTION_ADD_REPO, intent.getData(), this, ManageReposActivity.class));
            } else if (parser.getErrorMessage() != 0) {
                Toast.makeText(this, parser.getErrorMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiver,
                new IntentFilter(LOCAL_ACTION_STATUS));
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchMenuItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) MenuItemCompat.getActionView(searchMenuItem);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        // LayoutParams.MATCH_PARENT does not work, use a big value instead
        searchView.setMaxWidth(1000000);
        searchView.setOnQueryTextListener(this);

        // If we were asked to execute a search before getting around to building the options
        // menu, then we should deal with that now that the options menu is all sorted out.
        if (pendingSearchQuery != null) {
            performSearch(pendingSearchQuery);
            pendingSearchQuery = null;
        }

        updateAllMenuItem = menu.findItem(R.id.action_update_all);
        if (PrivilegedInstaller.isExtensionInstalledCorrectly(this) == PrivilegedInstaller.IS_EXTENSION_INSTALLED_YES
                && Preferences.get().isPrivilegedInstallerEnabled()) {
            updateAllMenuItem.setTitle(R.string.install_all_updates);
        }

        Preferences.get().registerPrivextChangeListener(() -> {
            if (Preferences.get().isPrivilegedInstallerEnabled()) {
                updateAllMenuItem.setTitle(R.string.install_all_updates);
            } else {
                updateAllMenuItem.setTitle(R.string.download_all_updates);
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int itemId = item.getItemId();
        if (itemId == R.id.action_update_repo) {
            UpdateService.updateNow(this);
            return true;
        } else if (itemId == R.id.action_update_all) {
            UpdateService.autoDownloadUpdates(this);
            return true;
        } else if (itemId == R.id.action_manage_repos) {
            startActivity(new Intent(this, ManageReposActivity.class));
            return true;
        } else if (itemId == R.id.action_settings) {
            Intent prefs = new Intent(getBaseContext(), PreferencesActivity.class);
            startActivityForResult(prefs, REQUEST_PREFS);
            return true;
        } else if (itemId == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PREFS) {// The automatic update settings may have changed, so reschedule (or
            // unschedule) the service accordingly. It's cheap, so no need to
            // check if the particular setting has actually been changed.
            UpdateService.schedule(getBaseContext());

            if ((resultCode & PreferencesActivity.RESULT_RESTART) != 0) {
                ((FDroidApp) getApplication()).reloadTheme();
                final Intent intent = getIntent();
                overridePendingTransition(0, 0);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                finish();
                overridePendingTransition(0, 0);
                startActivity(intent);
            }
        }
    }

    private void createViews() {
        viewPager = findViewById(R.id.main_pager);
        adapter = new AppListFragmentPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                getTabManager().selectTab(position);
            }
        });
    }

    @NonNull
    private TabManager getTabManager() {
        if (tabManager == null) {
            tabManager = new TabManager(this, viewPager);
        }
        return tabManager;
    }

    private void refreshUpdateTabLabel() {
        getTabManager().refreshTabLabel(TabManager.INDEX_CAN_UPDATE);
        getTabManager().refreshTabLabel(TabManager.INDEX_INSTALLED);
    }

    public void removeNotification(int id) {
        NotificationManager nMgr = (NotificationManager) getBaseContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        nMgr.cancel(id);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        searchView.clearFocus();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        adapter.updateSearchQuery(newText);
        return true;
    }

}
