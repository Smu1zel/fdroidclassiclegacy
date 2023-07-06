package org.fdroid.fdroid.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import androidx.cursoradapter.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.fdroid.fdroid.FDroidApp;
import org.fdroid.fdroid.LazyLoadingHelper;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.App;

public abstract class AppListAdapter extends CursorAdapter
        implements LazyLoadingHelper.Callbacks<App, AppListAdapter.LazyViewData> {

    private LayoutInflater mInflater;
    private String upgradeFromTo;
    private LazyLoadingHelper<App, LazyViewData> lazyLoadingHelper;

    @SuppressLint("RestrictedApi")
    @Override
    public boolean isEmpty() {
        return mDataValid && super.isEmpty();
    }

    AppListAdapter(Context context, Cursor c, int flags) {
        super(context, c, flags);
        init(context);
    }

    private void init(Context context) {
        mInflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        upgradeFromTo = context.getResources().getString(R.string.upgrade_from_to);
        lazyLoadingHelper = new LazyLoadingHelper<>(FDroidApp.getDatabaseExecutor(), app -> app.packageName);
    }

    protected abstract boolean showStatusUpdate();

    protected abstract boolean showStatusInstalled();

    private static class ViewHolder {
        TextView name;
        TextView summary;
        TextView status;
        TextView license;
        ImageView icon;
    }

    static class LazyViewData {
        String iconUrl;
        String version;
        String packageName;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = mInflater.inflate(R.layout.applistitem, parent, false);

        ViewHolder holder = new ViewHolder();
        holder.name = view.findViewById(R.id.name);
        holder.summary = view.findViewById(R.id.summary);
        holder.status = view.findViewById(R.id.status);
        holder.license = view.findViewById(R.id.license);
        holder.icon = view.findViewById(R.id.icon);
        view.setTag(holder);

        setupView(view, cursor, holder);

        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder holder = (ViewHolder) view.getTag();
        setupView(view, cursor, holder);
    }

    private void setupView(View view, Cursor cursor, ViewHolder holder) {
        final App app = new App(cursor);

        holder.name.setText(app.name);
        holder.summary.setText(app.summary);
        holder.license.setText(app.license);

        lazyLoadingHelper.startLoading(view, app, this);

        // Disable it all if it isn't compatible...
        final View[] views = {
            view,
            holder.status,
            holder.summary,
            holder.license,
            holder.name,
        };

        for (View v : views) {
            v.setEnabled(app.compatible);
        }
    }

    @Override
    public LazyViewData lazyLoadData(App app) {
        var context = FDroidApp.getInstance();
        var data = new LazyViewData();
        data.iconUrl = app.getIconUrl(context);
        data.version = getVersionInfo(context, app);
        data.packageName = app.packageName;
        return data;
    }

    @Override
    public void lazyBindViewPlaceholder(View view) {
        var holder = (ViewHolder) view.getTag();
        holder.status.setText(R.string.version_loading_placeholder);
        holder.icon.setImageDrawable(Utils.getDefaultIcon());
    }

    @Override
    public void lazyBindView(View view, LazyViewData data) {
        var holder = (ViewHolder) view.getTag();
        holder.status.setText(data.version);
        Utils.setIconFromRepoOrPM(holder.icon, data.iconUrl, data.packageName);
    }

    private String getVersionInfo(Context context, App app) {

        if (app.suggestedVersionCode <= 0) {
            return null;
        }

        if (!app.isInstalled()) {
            return app.getSuggestedVersionName();
        }

        final String installedVersionString = app.installedVersionName;

        if (app.canAndWantToUpdate(context) && showStatusUpdate()) {
            return String.format(upgradeFromTo,
                    installedVersionString, app.getSuggestedVersionName());
        }

        if (app.installedVersionCode > 0 && showStatusInstalled()) {
            return installedVersionString + " ✔";
        }

        return installedVersionString;
    }
}
