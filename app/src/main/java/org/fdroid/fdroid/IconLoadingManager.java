package org.fdroid.fdroid;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;

import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Handles icon loading on background threads by delegating to
 * {@link ImageLoader} or {@link PackageManager} and allows loading
 * tasks to be cancelled in a unified way.
 */
public class IconLoadingManager {
    private final LazyLoadingHelper<String, Drawable> helper;
    private final DisplayImageOptions options = getRepoAppDisplayImageOptions();
    private final Context context;

    private static IconLoadingManager instance;

    public static IconLoadingManager getInstance() {
        if (instance == null) {
            instance = new IconLoadingManager(
                    FDroidApp.getImageCacheExecutor(),
                    FDroidApp.getInstance());
        }
        return instance;
    }

    /**
     * @param executor used for loading icons from {@link PackageManager}
     */
    private IconLoadingManager(Executor executor, Context context) {
        helper = new LazyLoadingHelper<>(executor, Function.identity());
        this.context = context.getApplicationContext();
    }

    /**
     * Load an icon from {@link ImageLoader}.
     *
     * @param view view to load the icon into
     * @param iconUrl URL to load the icon from
     */
    public void loadFromUrl(ImageView view, String iconUrl) {
        cancelLoading(view);
        displayIconFromUrl(view, iconUrl);
    }

    /**
     * Load an icon from {@link PackageManager}.
     *
     * @param view view to load the icon into
     * @param packageName application package name for which to load the icon
     */
    public void loadFromPackage(ImageView view, String packageName) {
        cancelLoading(view);
        helper.startLoading(view, packageName, new HelperCallbacks());
    }

    /**
     * Load an icon using {@link #loadFromUrl} if {@code iconUrl} is non-null.
     * If {@code iconUrl} is null, load the icon using {@link #loadFromPackage}.
     *
     * @param view view to load the icon into
     * @param iconUrl URL to load the icon from (or null)
     * @param packageName application package name for which to load the icon (or null)
     */
    public void loadFromUrlOrPackage(ImageView view,
                                     @Nullable String iconUrl,
                                     @Nullable String packageName) {
        if (iconUrl == null) {
            loadFromPackage(view, packageName);
        }
        else {
            loadFromUrl(view, iconUrl);
        }
    }

    /**
     * Cancel loading for the specified view.
     */
    public void cancelLoading(ImageView view) {
        helper.cancelLoading(view);
        ImageLoader.getInstance().cancelDisplayTask(view);
    }

    /**
     * Returns the default icon.
     */
    public Drawable getDefaultIcon() {
        return options.shouldShowImageForEmptyUri()
                ? options.getImageForEmptyUri(context.getResources())
                : null;
    }

    private Drawable getIconFromPackageManager(String packageName) {
        var pm = context.getPackageManager();
        try {
            return pm.getApplicationIcon(packageName);
        }
        catch (PackageManager.NameNotFoundException e) {
            return getDefaultIcon();
        }
    }

    private void displayIconFromUrl(ImageView view, String url) {
        ImageLoader.getInstance().displayImage(url, view, options);
    }

    private class HelperCallbacks implements LazyLoadingHelper.Callbacks<String, Drawable> {
        @Override
        public Drawable lazyLoadData(String packageName) {
            return getIconFromPackageManager(packageName);
        }

        @Override
        public void lazyBindViewPlaceholder(View view) {
            ((ImageView) view).setImageDrawable(getDefaultIcon());
        }
        @Override
        public void lazyBindView(View view, Drawable drawable) {
            ((ImageView) view).setImageDrawable(drawable);
        }
    }

    /**
     * Gets the {@link DisplayImageOptions} instance used to configure
     * {@link com.nostra13.universalimageloader.core.ImageLoader} instances
     * used to display app icons.
     */
    private static DisplayImageOptions getRepoAppDisplayImageOptions() {
        return Utils.getDefaultDisplayImageOptionsBuilder()
                .showImageOnLoading(R.drawable.ic_repo_app_default)
                .showImageForEmptyUri(R.drawable.ic_repo_app_default)
                .showImageOnFail(R.drawable.ic_repo_app_default)
                .displayer(new FadeInBitmapDisplayer(200, true, true, false))
                .build();
    }
}
