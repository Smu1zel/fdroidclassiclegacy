package org.fdroid.fdroid.views.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.app.LoaderManager;

import org.fdroid.fdroid.Preferences;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.AppProvider;
import org.fdroid.fdroid.data.Category;
import org.fdroid.fdroid.data.CategoryProvider;
import org.fdroid.fdroid.data.RepoCategory;
import org.fdroid.fdroid.views.AppListAdapter;
import org.fdroid.fdroid.views.AvailableAppListAdapter;

import java.util.List;

public class AvailableAppsFragment extends AppListFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "AvailableAppsFragment";

    private static final String PREFERENCES_FILE = "CategorySpinnerPosition";
    private static final String CATEGORY_ID_KEY = "SelectionID";

    private static int defaultCategoryID;

    private List<Category> categories;

    @Nullable
    private View categoryWrapper;

    @Nullable
    private Spinner categorySpinner;
    private Category currentCategory;
    private AppListAdapter adapter;

    @Override
    protected String getFromTitle() {
        return getString(R.string.tab_available_apps);
    }

    @Override
    protected int getLayout() {
        return R.layout.available_app_list;
    }

    @Override
    protected AppListAdapter getAppListAdapter() {
        if (adapter == null) {
            final AppListAdapter a = AvailableAppListAdapter.create(getActivity(), null, 0);
            Preferences.get().registerUpdateHistoryListener(a::notifyDataSetChanged);
            adapter = a;
        }
        return adapter;
    }

    private class CategoryObserver extends ContentObserver {

        private final ArrayAdapter<Category> adapter;

        CategoryObserver(ArrayAdapter<Category> adapter) {
            // Using Looper.getMainLooper() ensures that the onChange method is run on the main thread.
            super(new Handler(Looper.getMainLooper()));
            this.adapter = adapter;
        }

        @SuppressLint("StaticFieldLeak")
        @Override
        public void onChange(boolean selfChange) {
            final Activity activity = getActivity();
            if (!isAdded() || adapter == null || activity == null) {
                return;
            }

            // Because onChange is always invoked on the main thread (see constructor), we want to
            // run the database query on a background thread. Afterwards, the UI is updated
            // on a foreground thread.
            new AsyncTask<Void, Void, List<Category>>() {
                @Override
                protected List<Category> doInBackground(Void... params) {
                    return CategoryProvider.Helper.categories(activity);
                }

                @Override
                protected void onPostExecute(List<Category> loadedCategories) {
                    adapter.clear();
                    categories = loadedCategories;
                    adapter.addAll(loadedCategories);
                }
            }.execute();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            onChange(selfChange);
        }
    }

    private void setupCategorySpinner(Spinner spinner) {

        categorySpinner = spinner;
        categorySpinner.setId(R.id.category_spinner);

        categories = CategoryProvider.Helper.categories(getActivity());

        ArrayAdapter<Category> adapter = new ArrayAdapter<Category>(
                getActivity(), android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);

        getActivity().getContentResolver().registerContentObserver(
                AppProvider.getContentUri(), false, new CategoryObserver(adapter));

        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                getListView().setSelection(0);
                setCurrentCategory(categories.get(pos));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                setCurrentCategory(null);
            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(getLayout(), container, false);

        categoryWrapper = view.findViewById(R.id.category_wrapper);
        setupCategorySpinner(view.findViewById(R.id.category_spinner));
        defaultCategoryID = CategoryProvider.Helper.getCategoryWhatsNew(getActivity()).getId();

        super.setUpPullToRefresh(view);
        return view;
    }

    @Override
    protected Uri getDataUri() {
        if (currentCategory == null || currentCategory.equals(CategoryProvider.Helper.getCategoryAll(getActivity()))) {
            return AppProvider.getContentUri();
        }
        if (currentCategory.equals(CategoryProvider.Helper.getCategoryRecentlyUpdated(getActivity()))) {
            return AppProvider.getRecentlyUpdatedUri();
        }
        if (currentCategory.equals(CategoryProvider.Helper.getCategoryWhatsNew(getActivity()))) {
            return AppProvider.getNewlyAddedUri();
        }
        if (currentCategory instanceof RepoCategory) {
            return AppProvider.getRepoCategoryUri((RepoCategory) currentCategory);
        }
        return AppProvider.getCategoryUri(currentCategory);
    }

    @Override
    protected Uri getDataUri(String query) {
        return AppProvider.getSearchUri(query);
    }

    @Override
    protected int getEmptyMessage() {
        return R.string.empty_available_app_list;
    }

    @Override
    protected int getNoSearchResultsMessage() {
        return R.string.empty_search_available_app_list;
    }

    private void setCurrentCategory(Category category) {
        currentCategory = category;
        Utils.debugLog(TAG, "Category '" + currentCategory + "' selected.");
        LoaderManager.getInstance(this).restartLoader(0, null, this);
    }

    @Override
    public void onResume() {
        /* restore the saved Category Spinner position */
        Activity activity = getActivity();
        SharedPreferences preferences = activity.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
        try {
            currentCategory = categories.get(preferences.getInt(CATEGORY_ID_KEY, defaultCategoryID));
        } catch (IndexOutOfBoundsException e) {
            currentCategory = CategoryProvider.Helper.getCategoryWhatsNew(getActivity());
        }

        if (categorySpinner != null) {
            for (int i = 0; i < categorySpinner.getCount(); i++) {
                if (currentCategory.equals(categorySpinner.getItemAtPosition(i))) {
                    categorySpinner.setSelection(i);
                    break;
                }
            }
        }

        setCurrentCategory(currentCategory);
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        /* store the Category Spinner position for when we come back */
        SharedPreferences preferences = getActivity().getSharedPreferences(PREFERENCES_FILE,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(CATEGORY_ID_KEY, currentCategory.getId());
        editor.apply();
    }

    @Override
    protected void onSearch() {
        if (categoryWrapper != null) {
            categoryWrapper.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onSearchStopped() {
        if (categoryWrapper != null) {
            categoryWrapper.setVisibility(View.VISIBLE);
        }
    }
}
