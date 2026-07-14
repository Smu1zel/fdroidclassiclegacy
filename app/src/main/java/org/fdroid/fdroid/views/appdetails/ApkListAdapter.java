package org.fdroid.fdroid.views.appdetails;


import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import org.fdroid.fdroid.Preferences;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.Apk;
import org.fdroid.fdroid.data.ApkProvider;
import org.fdroid.fdroid.data.App;
import org.fdroid.fdroid.data.InstalledAppProvider;
import org.fdroid.fdroid.data.Repo;
import org.fdroid.fdroid.data.RepoProvider;

import java.util.List;

class ApkListAdapter extends ArrayAdapter<Apk> {

    private static final String TAG = "ApkListAdapter";
    private final Context context;
    private final App app;

    ApkListAdapter(Context context, App app) {
        super(context, 0);
        this.context = context;
        this.app = app;
        final List<Apk> apks = ApkProvider.Helper.findByPackageName(context, app.packageName);
        for (final Apk apk : apks) {
            if (apk.compatible || Preferences.get().showIncompatibleVersions()) {
                add(apk);
            }
        }
    }

    private String getInstalledStatus(final Apk apk) {
        // Definitely not installed.
        if (apk.versionCode != app.installedVersionCode) {
            return "";
        }
        // Definitely installed this version.
        if (apk.sig != null && apk.sig.equals(app.installedSig)) {
            return context.getString(R.string.app_installed);
        }
        // Installed the same version, but from someplace else.
        final String installerPkgName;
        try {
            installerPkgName = context.getPackageManager().getInstallerPackageName(app.packageName);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Application " + app.packageName + " is not installed anymore");
            return "";
        }
        if (TextUtils.isEmpty(installerPkgName)) {
            return context.getString(R.string.app_inst_unknown_source);
        }
        final String installerLabel = InstalledAppProvider
                .getApplicationLabel(context, installerPkgName);
        return context.getString(R.string.app_inst_known_source, installerLabel);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {

        java.text.DateFormat df = DateFormat.getDateFormat(context);
        final Apk apk = getItem(position);
        AppDetails.ViewHolder holder;

        if (convertView == null) {
            convertView = ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                    .inflate(R.layout.apklistitem, parent, false);

            holder = new AppDetails.ViewHolder();
            holder.version = convertView.findViewById(R.id.version);
            holder.versionCode = convertView.findViewById(R.id.versionCode);
            holder.status = convertView.findViewById(R.id.status);
            holder.repository = convertView.findViewById(R.id.repository);
            holder.size = convertView.findViewById(R.id.size);
            holder.api = convertView.findViewById(R.id.api);
            holder.incompatibleReasons = convertView.findViewById(R.id.incompatible_reasons);
            holder.buildtype = convertView.findViewById(R.id.buildtype);
            holder.added = convertView.findViewById(R.id.added);
            holder.nativecode = convertView.findViewById(R.id.nativecode);

            convertView.setTag(holder);
        } else {
            holder = (AppDetails.ViewHolder) convertView.getTag();
        }

        holder.version.setText(context.getString(R.string.version)
                + " " + apk.versionName
                + (apk.versionCode == app.suggestedVersionCode ? "  ☆" : ""));
        if (!Preferences.get().expertMode()) {
            holder.versionCode.setVisibility(View.GONE);
        } else {
            holder.versionCode.setText(String.format("(%s)", apk.versionCode));
        }
        holder.status.setText(getInstalledStatus(apk));
        Repo repo = RepoProvider.Helper.findById(context, apk.repoId);
        if (repo != null) {
            holder.repository.setText(String.format(context.getString(R.string.repo_provider), repo.getName()));
        } else {
            holder.repository.setText(String.format(context.getString(R.string.repo_provider), "-"));
        }

        if (apk.size > 0) {
            holder.size.setText(Utils.getFriendlySize(apk.size));
            holder.size.setVisibility(View.VISIBLE);
        } else {
            holder.size.setVisibility(View.GONE);
        }

        if (!Preferences.get().expertMode()) {
            holder.api.setVisibility(View.GONE);
        } else if (apk.minSdkVersion > 0 && apk.maxSdkVersion < Apk.SDK_VERSION_MAX_VALUE) {
            holder.api.setText(context.getString(R.string.minsdk_up_to_maxsdk,
                    Utils.getAndroidVersionName(apk.minSdkVersion),
                    Utils.getAndroidVersionName(apk.maxSdkVersion)));
            holder.api.setVisibility(View.VISIBLE);
        } else if (apk.minSdkVersion > 0) {
            holder.api.setText(context.getString(R.string.minsdk_or_later,
                    Utils.getAndroidVersionName(apk.minSdkVersion)));
            holder.api.setVisibility(View.VISIBLE);
        } else if (apk.maxSdkVersion > 0) {
            holder.api.setText(context.getString(R.string.up_to_maxsdk,
                    Utils.getAndroidVersionName(apk.maxSdkVersion)));
            holder.api.setVisibility(View.VISIBLE);
        }

        if (apk.srcname != null) {
            holder.buildtype.setText(R.string.build_type_source);
        } else {
            holder.buildtype.setText(R.string.build_type_bin);
        }

        if (apk.added != null) {
            holder.added.setText(context.getString(R.string.added_on,
                    df.format(apk.added)));
            holder.added.setVisibility(View.VISIBLE);
        } else {
            holder.added.setVisibility(View.GONE);
        }

        if (Preferences.get().expertMode() && apk.nativecode != null) {
            holder.nativecode.setText(TextUtils.join(" ", apk.nativecode));
            holder.nativecode.setVisibility(View.VISIBLE);
        } else {
            holder.nativecode.setVisibility(View.GONE);
        }

        if (apk.incompatibleReasons != null) {
            holder.incompatibleReasons.setText(
                    context.getResources().getString(
                            R.string.requires_features,
                            TextUtils.join(", ", apk.incompatibleReasons)));
            holder.incompatibleReasons.setVisibility(View.VISIBLE);
        } else {
            holder.incompatibleReasons.setVisibility(View.GONE);
        }

        // Disable it all if it isn't compatible...
        final View[] views = {
                convertView,
                holder.version,
                holder.status,
                holder.repository,
                holder.size,
                holder.api,
                holder.buildtype,
                holder.added,
                holder.nativecode,
        };

        for (final View v : views) {
            v.setEnabled(apk.compatible);
        }

        return convertView;
    }
}