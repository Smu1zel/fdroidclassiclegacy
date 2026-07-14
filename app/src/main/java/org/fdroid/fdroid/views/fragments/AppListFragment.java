package org.fdroid.fdroid.views.fragments;

import static org.fdroid.fdroid.UpdateService.LOCAL_ACTION_STATUS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.fdroid.fdroid.views.appdetails.AppDetails;
import org.fdroid.fdroid.FDroidApp;
import org.fdroid.fdroid.Preferences;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.UpdateService;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.App;
import org.fdroid.fdroid.data.Schema.AppMetadataTable;
import org.fdroid.fdroid.receiver.UpdatingReceiver;
import org.fdroid.fdroid.views.AppListAdapter;

public abstract class AppListFragment extends ListFragment implements
        AdapterView.OnItemClickListener,
        Preferences.ChangeListener,
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "AppListFragment";

    private static final int REQUEST_APPDETAILS = 0;

    private static final String[] APP_PROJECTION = {
            AppMetadataTable.Cols._ID, // Required for cursor loader to work.
            AppMetadataTable.Cols.Package.PACKAGE_NAME,
            AppMetadataTable.Cols.NAME,
            AppMetadataTable.Cols.SUMMARY,
            AppMetadataTable.Cols.IS_COMPATIBLE,
            AppMetadataTable.Cols.LICENSE,
            AppMetadataTable.Cols.ICON,
            AppMetadataTable.Cols.ICON_URL,
            AppMetadataTable.Cols.InstalledApp.VERSION_CODE,
            AppMetadataTable.Cols.InstalledApp.VERSION_NAME,
            AppMetadataTable.Cols.SuggestedApk.VERSION_NAME,
            AppMetadataTable.Cols.SUGGESTED_VERSION_CODE,
            AppMetadataTable.Cols.REQUIREMENTS, // Needed for filtering apps that require root.
            AppMetadataTable.Cols.ANTI_FEATURES, // Needed for filtering apps that require anti-features.
            AppMetadataTable.Cols.IS_APK, // If we don't have this checking if an app is installed is super expensive.
            AppMetadataTable.Cols.REPO_ID,
    };

    private static final String APP_SORT = AppMetadataTable.Cols.NAME;

    protected abstract int getLayout();

    private AppListAdapter appAdapter;

    @Nullable private String searchQuery;

    private BroadcastReceiver receiver;
    private SwipeRefreshLayout pullToRefresh;

    protected abstract AppListAdapter getAppListAdapter();

    protected abstract String getFromTitle();

    protected abstract Uri getDataUri();

    protected abstract Uri getDataUri(String query);

    protected abstract int getEmptyMessage();

    protected abstract int getNoSearchResultsMessage();

    /**
     * Subclasses can choose to do different things based on when a user begins searching.
     * For example, the "Available" tab chooses to hide its category spinner to make it clear
     * that it is searching all apps, not the current category.
     * NOTE: This will get called <em>multiple</em> times, every time the user changes the
     * search query.
     */
    void onSearch() {
        // Do nothing by default.
    }

    /**
     * Alerts the child class that the user is no longer performing a search.
     * This is triggered every time the search query is blank.
     *
     * @see AppListFragment#onSearch()
     */
    protected void onSearchStopped() {
        // Do nothing by default.
    }

    /**
     * Utility function to set empty view text which should be different
     * depending on whether search is active or not.
     */
    private void setEmptyText(int resId) {
        ((TextView) getListView().getEmptyView()).setText(resId);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(getLayout(), container, false);
        setUpPullToRefresh(view);
        return view;
    }

    protected void setUpPullToRefresh(View view) {
        pullToRefresh = view.findViewById(R.id.pullToRefresh);
        pullToRefresh.setColorSchemeResources(R.color.fdroid_green);
        pullToRefresh.setOnRefreshListener(() -> {
            UpdateService.updateNow(FDroidApp.getInstance());
        });
        receiver = new UpdatingReceiver(pullToRefresh);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Can't do this in the onCreate view, because "onCreateView" which
        // returns the list view is "called between onCreate and
        // onActivityCreated" according to the docs.
        getListView().setOnItemClickListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(FDroidApp.getInstance()).unregisterReceiver(receiver);
    }

    @Override
    public void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(FDroidApp.getInstance()).registerReceiver(
                receiver,
                new IntentFilter(LOCAL_ACTION_STATUS)
        );
    }

    @Override
    public void onResume() {
        super.onResume();

        // We don't know if we are still updating, but we will only receive updates if we are,
        // so we need to disable this and potentially will restart the spinner a second later *shrug*
        pullToRefresh.setRefreshing(false);
        pullToRefresh.setEnabled(Preferences.get().pullToRefreshEnabled());

        //Starts a new or restarts an existing Loader in this manager
        LoaderManager.getInstance(this).initLoader(0, null, this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        appAdapter = getAppListAdapter();

        if (appAdapter.getCount() == 0) {
            updateEmptyRepos();
        }

        setListAdapter(appAdapter);
    }

    /**
     * The first time the app is run, we will have an empty app list.
     * If this is the case, we will attempt to update with the default repo.
     * However, if we have tried this at least once, then don't try to do
     * it automatically again, because the repos or internet connection may
     * be bad.
     */
    private void updateEmptyRepos() {
        final String triedEmptyUpdate = "triedEmptyUpdate";
        SharedPreferences prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
        boolean hasTriedEmptyUpdate = prefs.getBoolean(triedEmptyUpdate, false);
        if (!hasTriedEmptyUpdate) {
            Utils.debugLog(TAG, "Empty app list, and we haven't done an update yet. Forcing repo update.");
            prefs.edit().putBoolean(triedEmptyUpdate, true).apply();
            UpdateService.updateNow(getActivity());
            return;
        }
        Utils.debugLog(TAG, "Empty app list, but it looks like we've had an update previously. Will not force repo update.");
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Cursor is null in the swap list when touching the first item.
        Cursor cursor = (Cursor) getListView().getItemAtPosition(position);
        if (cursor != null) {
            final App app = new App(cursor);
            Intent intent = getAppDetailsIntent();
            intent.putExtra(AppDetails.EXTRA_APPID, app.packageName);
            intent.putExtra(AppDetails.EXTRA_FROM, getFromTitle());
            startActivityForResult(intent, REQUEST_APPDETAILS);
        }
    }

    private Intent getAppDetailsIntent() {
        return new Intent(getActivity(), AppDetails.class);
    }

    @Override
    public void onPreferenceChange() {
        getAppListAdapter().notifyDataSetChanged();
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        appAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        appAdapter.swapCursor(null);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri uri = updateSearchStatus() ? getDataUri(searchQuery) : getDataUri();
        return new CursorLoader(
                getActivity(), uri, APP_PROJECTION, null, null, APP_SORT);
    }

    /**
     * Notifies the subclass via {@link AppListFragment#onSearch()} and {@link AppListFragment#onSearchStopped()}
     * about whether or not a search is taking place and changes empty message
     * appropriately.
     *
     * @return True if a user is searching.
     */
    private boolean updateSearchStatus() {
        if (TextUtils.isEmpty(searchQuery)) {
            onSearchStopped();
            setEmptyText(getEmptyMessage());
            return false;
        }
        onSearch();
        setEmptyText(getNoSearchResultsMessage());
        return true;
    }

    public void updateSearchQuery(@Nullable String query) {
        if (!TextUtils.equals(query, searchQuery)) {
            searchQuery = query;
            if (isAdded()) {
                LoaderManager.getInstance(this).restartLoader(0, null, this);
            }
        }
    }
}
