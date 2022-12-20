package org.fdroid.fdroid.views.fragments;

import android.net.Uri;

import org.fdroid.fdroid.R;
import org.fdroid.fdroid.data.AppProvider;
import org.fdroid.fdroid.views.AppListAdapter;
import org.fdroid.fdroid.views.CanUpdateAppListAdapter;

public class CanUpdateAppsFragment extends AppListFragment {

    @Override
    protected int getLayout() {
        return R.layout.can_update_app_list;
    }

    @Override
    protected AppListAdapter getAppListAdapter() {
        return CanUpdateAppListAdapter.create(getActivity(), null, 0);
    }

    @Override
    protected String getFromTitle() {
        return getString(R.string.tab_updates);
    }

    @Override
    protected Uri getDataUri() {
        return AppProvider.getCanUpdateUri();
    }

    @Override
    protected Uri getDataUri(String query) {
        return AppProvider.getSearchCanUpdateUri(query);
    }

    @Override
    protected int getEmptyMessage() {
        return R.string.empty_can_update_app_list;
    }

    @Override
    protected int getNoSearchResultsMessage() {
        return R.string.empty_search_can_update_app_list;
    }
}
