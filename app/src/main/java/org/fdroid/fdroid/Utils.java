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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.StatFs;
import android.text.Editable;
import android.text.Html;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.hash.Hashing;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;
import com.nostra13.universalimageloader.utils.StorageUtils;

import org.fdroid.fdroid.data.App;
import org.xml.sax.XMLReader;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

public final class Utils {

    public static void showKeyboard(Context context){
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    public static void closeKeyboard(Context context){
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
    }


    private static final String TAG = "Utils";

    private static final int BUFFER_SIZE = 4096;

    // The date format used for storing dates (e.g. lastupdated, added) in the
    // database.
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.ENGLISH);

    private static final TimeZone UTC = TimeZone.getTimeZone("Etc/GMT");

    private static final String[] FRIENDLY_SIZE_FORMAT = {
            "%.0f B", "%.0f KiB", "%.1f MiB", "%.2f GiB",
    };

    private static DisplayImageOptions.Builder defaultDisplayImageOptionsBuilder;
    private static DisplayImageOptions repoAppDisplayImageOptions;

    private static Pattern safePackageNamePattern;

    public static final String FALLBACK_ICONS_DIR = "/icons/";

    /*
     * @param dpiMultiplier Lets you grab icons for densities larger or
     * smaller than that of your device by some fraction. Useful, for example,
     * if you want to display a 48dp image at twice the size, 96dp, in which
     * case you'd use a dpiMultiplier of 2.0 to get an image twice as big.
     */
    public static String getIconsDir(final Context context, final double dpiMultiplier) {
        final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        final double dpi = metrics.densityDpi * dpiMultiplier;
        if (dpi >= 640) {
            return "/icons-640/";
        }
        if (dpi >= 480) {
            return "/icons-480/";
        }
        if (dpi >= 320) {
            return "/icons-320/";
        }
        if (dpi >= 240) {
            return "/icons-240/";
        }
        if (dpi >= 160) {
            return "/icons-160/";
        }

        return "/icons-120/";
    }

    /**
     * @return the directory where cached icons/feature graphics/screenshots are stored
     */
    public static File getImageCacheDir(Context context) {
        File cacheDir = StorageUtils.getCacheDirectory(context.getApplicationContext(), true);
        return new File(cacheDir, "icons");
    }

    public static long getImageCacheDirAvailableMemory(Context context) {
        File statDir = getImageCacheDir(context);
        while (statDir != null && !statDir.exists()) {
            statDir = statDir.getParentFile();
        }
        if (statDir == null) {
            return 50 * 1024 * 1024; // just return a minimal amount
        }
        StatFs stat = new StatFs(statDir.getPath());
		        if (Build.VERSION.SDK_INT < 18) {
            return (long) stat.getBlockCount() * (long) stat.getBlockSize();
        } else {
            return stat.getBlockCountLong() * stat.getBlockSizeLong();
        }
    }

    public static long getImageCacheDirTotalMemory(Context context) {
        File statDir = getImageCacheDir(context);
        while (statDir != null && !statDir.exists()) {
            statDir = statDir.getParentFile();
        }
        if (statDir == null) {
            return 100 * 1024 * 1024; // just return a minimal amount
        }
        StatFs stat = new StatFs(statDir.getPath());
		        if (Build.VERSION.SDK_INT < 18) {
            return (long) stat.getBlockCount() * (long) stat.getBlockSize();
        } else {
            return stat.getBlockCountLong() * stat.getBlockSizeLong();
        }
    }

    public static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (true) {
            int count = input.read(buffer);
            if (count == -1) {
                break;
            }
            output.write(buffer, 0, count);
        }
        output.flush();
    }

    public static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ioe) {
            // ignore
        }
    }

    public static String getFriendlySize(long size) {
        double s = size;
        int i = 0;
        while (i < FRIENDLY_SIZE_FORMAT.length - 1 && s >= 1024) {
            s = (100 * s / 1024) / 100.0;
            i++;
        }
        return String.format(FRIENDLY_SIZE_FORMAT[i], s);
    }

    private static final String[] ANDROID_VERSION_NAMES = {
            "?",     // 0, undefined
            "1.0",   // 1
            "1.1",   // 2
            "1.5",   // 3
            "1.6",   // 4
            "2.0",   // 5
            "2.0.1", // 6
            "2.1",   // 7
            "2.2",   // 8
            "2.3",   // 9
            "2.3.3", // 10
            "3.0",   // 11
            "3.1",   // 12
            "3.2",   // 13
            "4.0",   // 14
            "4.0.3", // 15
            "4.1",   // 16
            "4.2",   // 17
            "4.3",   // 18
            "4.4",   // 19
            "4.4W",  // 20
            "5.0",   // 21
            "5.1",   // 22
            "6.0",   // 23
            "7.0",   // 24
            "7.1",   // 25
            "8.0",   // 26
            "8.1",   // 27
            "9.0",   // 28
            "10.0",  // 29
            "11.0",  // 30
    };

    public static String getAndroidVersionName(int sdkLevel) {
        if (sdkLevel < 0) {
            return ANDROID_VERSION_NAMES[0];
        }
        if (sdkLevel >= ANDROID_VERSION_NAMES.length) {
            return String.format(Locale.ENGLISH, "v%d", sdkLevel);
        }
        return ANDROID_VERSION_NAMES[sdkLevel];
    }

    // return a fingerprint formatted for display
    public static String formatFingerprint(Context context, String fingerprint) {
        if (TextUtils.isEmpty(fingerprint)
                || fingerprint.length() != 64 // SHA-256 is 64 hex chars
                || fingerprint.matches(".*[^0-9a-fA-F].*")) { // its a hex string
            return context.getString(R.string.bad_fingerprint);
        }
        StringBuilder displayFP = new StringBuilder(fingerprint.substring(0, 2));
        for (int i = 2; i < fingerprint.length(); i = i + 2) {
            displayFP.append(" ").append(fingerprint.substring(i, i + 2));
        }
        return displayFP.toString();
    }

    /**
     * Create a standard {@link PackageManager} {@link Uri} for pointing to an app.
     */
    public static Uri getPackageUri(String packageName) {
        return Uri.parse("package:" + packageName);
    }

    public static String calcFingerprint(String keyHexString) {
        if (TextUtils.isEmpty(keyHexString)
                || keyHexString.matches(".*[^a-fA-F0-9].*")) {
            Log.e(TAG, "Signing key certificate was blank or contained a non-hex-digit!");
            return null;
        }
        return calcFingerprint(HashingUtils.unhex(keyHexString));
    }

    public static String calcFingerprint(Certificate cert) {
        if (cert == null) {
            return null;
        }
        try {
            return calcFingerprint(cert.getEncoded());
        } catch (CertificateEncodingException e) {
            return null;
        }
    }

    private static String calcFingerprint(byte[] key) {
        if (key == null) {
            return null;
        }
        if (key.length < 256) {
            Log.e(TAG, "key was shorter than 256 bytes (" + key.length + "), cannot be valid!");
            return null;
        }
        String ret = null;
        try {
            // keytool -list -v gives you the SHA-256 fingerprint
            MessageDigest digest = MessageDigest.getInstance("sha256");
            digest.update(key);
            byte[] fingerprint = digest.digest();
            Formatter formatter = new Formatter(new StringBuilder());
            for (byte aFingerprint : fingerprint) {
                formatter.format("%02X", aFingerprint);
            }
            ret = formatter.toString();
            formatter.close();
        } catch (Throwable e) {
            Log.w(TAG, "Unable to get certificate fingerprint", e);
        }
        return ret;
    }

    /**
     * Get the fingerprint used to represent an APK signing key in F-Droid.
     * This is a custom fingerprint algorithm that was kind of accidentally
     * created, but is still in use.
     */
    public static String getPackageSig(PackageInfo info) {
        if (info == null || info.signatures == null || info.signatures.length < 1) {
            return "";
        }
        Signature sig = info.signatures[0];
        return Hashing.md5().hashBytes(sig.toCharsString().getBytes()).toString();
    }

    /**
     * Since there have been vulnerabilities in EXIF processing in Android, this
     * disables all use of EXIF.
     *
     * @see <a href="https://securityaffairs.co/wordpress/51043/mobile-2/android-cve-2016-3862-flaw.html">CVE-2016-3862</a>
     */
    public static DisplayImageOptions.Builder getDefaultDisplayImageOptionsBuilder() {
        if (defaultDisplayImageOptionsBuilder == null) {
            defaultDisplayImageOptionsBuilder = new DisplayImageOptions.Builder()
                    .cacheInMemory(true)
                    .cacheOnDisk(true)
                    .considerExifParams(false);
        }
        return defaultDisplayImageOptionsBuilder;
    }

    /**
     * Gets the {@link DisplayImageOptions} instance used to configure
     * {@link com.nostra13.universalimageloader.core.ImageLoader} instances
     * used to display app icons.  It lazy loads a reusable static instance.
     */
    public static DisplayImageOptions getRepoAppDisplayImageOptions() {
        if (repoAppDisplayImageOptions == null) {
            repoAppDisplayImageOptions = getDefaultDisplayImageOptionsBuilder()
                    .showImageOnLoading(R.drawable.ic_repo_app_default)
                    .showImageForEmptyUri(R.drawable.ic_repo_app_default)
                    .showImageOnFail(R.drawable.ic_repo_app_default)
                    .displayer(new FadeInBitmapDisplayer(200, true, true, false))
                    .build();
        }
        return repoAppDisplayImageOptions;
    }

    /**
     * If app has an iconUrl we feed that to UIL, otherwise we ask the PackageManager which will
     * return the app's icon directly when the app is installed.
     * We fall back to the placeholder icon otherwise.
     */
    public static void setIconFromRepoOrPM(@NonNull App app, ImageView iv, Context context) {
        if (app.getIconUrl(iv.getContext()) == null) {
            try {
                iv.setImageDrawable(context.getPackageManager().getApplicationIcon(app.packageName));
            } catch (PackageManager.NameNotFoundException e) {
                DisplayImageOptions options = Utils.getRepoAppDisplayImageOptions();
                iv.setImageDrawable(options.shouldShowImageForEmptyUri()
                        ? options.getImageForEmptyUri(FDroidApp.getInstance().getResources())
                        : null);
            }
        } else {
            ImageLoader.getInstance().displayImage(app.getIconUrl(iv.getContext()), iv, Utils.getRepoAppDisplayImageOptions());
        }
    }

    /**
     * Get the checksum hash of the file {@code apk} using the algorithm in {@code algo}.
     * {@code apk} must exist on the filesystem and {@code algo} must be supported
     * by this device, otherwise an {@link IllegalArgumentException} is thrown.  This
     * method must be very defensive about checking whether the file exists, since APKs
     * can be uninstalled/deleted in background at any time, even if this is in the
     * middle of running.
     * <p>
     * This also will run into filesystem corruption if the device is having trouble.
     * So hide those so F-Droid does not pop up crash reports about that. As such this
     * exception-message-parsing-and-throwing-a-new-ignorable-exception-hackery is
     * probably warranted. See https://www.gitlab.com/fdroid/fdroidclient/issues/855
     * for more detail.
     */
    //TODO: replace with guava hashing
    @Nullable
    public static String getBinaryHash(File apk, String algo) {
        FileInputStream fis = null;
        try {
            MessageDigest md = MessageDigest.getInstance(algo);
            fis = new FileInputStream(apk);
            BufferedInputStream bis = new BufferedInputStream(fis);

            byte[] dataBytes = new byte[8192];
            int nread;
            while ((nread = bis.read(dataBytes)) != -1) {
                md.update(dataBytes, 0, nread);
            }

            byte[] mdbytes = md.digest();
            return HashingUtils.hex(mdbytes);
        } catch (IOException e) {
            String message = e.getMessage();
            if (message.contains("read failed: EIO (I/O error)")) {
                Utils.debugLog(TAG, "potential filesystem corruption while accessing " + apk + ": " + message);
            } else if (message.contains(" ENOENT ")) {
                Utils.debugLog(TAG, apk + " vanished: " + message);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        } finally {
            closeQuietly(fis);
        }
        return null;
    }

    public static int parseInt(String str, int fallback) {
        if (str == null || str.length() == 0) {
            return fallback;
        }
        int result;
        try {
            result = Integer.parseInt(str);
        } catch (NumberFormatException e) {
            result = fallback;
        }
        return result;
    }

    @Nullable
    public static String[] parseCommaSeparatedString(String values) {
        return values == null || values.length() == 0 ? null : values.split(",");
    }

    @Nullable
    public static String serializeCommaSeparatedString(@Nullable String[] values) {
        return values == null || values.length == 0 ? null : TextUtils.join(",", values);
    }

    private static Date parseDateFormat(DateFormat format, String str, Date fallback) {
        if (str == null || str.length() == 0) {
            return fallback;
        }
        Date result;
        try {
            format.setTimeZone(UTC);
            result = format.parse(str);
        } catch (ArrayIndexOutOfBoundsException | NumberFormatException | ParseException e) {
            e.printStackTrace();
            result = fallback;
        }
        return result;
    }

    private static String formatDateFormat(DateFormat format, Date date, String fallback) {
        if (date == null) {
            return fallback;
        }
        format.setTimeZone(UTC);
        return format.format(date);
    }

    /**
     * Parses a date string into UTC time
     */
    public static Date parseDate(String str, Date fallback) {
        return parseDateFormat(DATE_FORMAT, str, fallback);
    }

    /**
     * Formats UTC time into a date string
     */
    public static String formatDate(Date date, String fallback) {
        return formatDateFormat(DATE_FORMAT, date, fallback);
    }

    /**
     * Parses a date/time string into UTC time
     */
    public static Date parseTime(String str, Date fallback) {
        return parseDateFormat(TIME_FORMAT, str, fallback);
    }

    /**
     * Formats UTC time into a date/time string
     */
    public static String formatTime(Date date, String fallback) {
        return formatDateFormat(TIME_FORMAT, date, fallback);
    }

    /**
     * This is not strict validation of the package name, this is just to make
     * sure that the package name is not used as an attack vector, e.g. SQL
     * Injection.
     */
    public static boolean isSafePackageName(@Nullable String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        if (safePackageNamePattern == null) {
            safePackageNamePattern = Pattern.compile("[a-zA-Z0-9._]+");
        }
        return safePackageNamePattern.matcher(packageName).matches();
    }


    /**
     * Need this to add the unimplemented support for ordered and unordered
     * lists to Html.fromHtml().
     */
    public static class HtmlTagHandler implements Html.TagHandler {
        int listNum;

        @Override
        public void handleTag(boolean opening, String tag, Editable output,
                              XMLReader reader) {
            switch (tag) {
                case "ul":
                    if (opening) {
                        listNum = -1;
                    } else {
                        output.append('\n');
                    }
                    break;
                case "ol":
                    if (opening) {
                        listNum = 1;
                    } else {
                        output.append('\n');
                    }
                    break;
                case "li":
                    if (opening) {
                        if (listNum == -1) {
                            output.append("\t• ");
                        } else {
                            output.append("\t").append(Integer.toString(listNum)).append(". ");
                            listNum++;
                        }
                    } else {
                        output.append('\n');
                    }
                    break;
            }
        }
    }

    public static void debugLog(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg);
        }
    }

    public static void debugLog(String tag, String msg, Throwable tr) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg, tr);
        }
    }

    /**
     * Try to get the {@link PackageInfo#versionName} of the
     * client.
     *
     * @return null on failure
     */
    public static String getVersionName(Context context) {
        String versionName = null;
        try {
            versionName = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not get client version name", e);
        }
        return versionName;
    }

    /**
     * Try to get the {@link PackageInfo} for the {@code packageName} provided.
     *
     * @return null on failure
     */
    public static PackageInfo getPackageInfo(Context context, String packageName) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            debugLog(TAG, "Could not get PackageInfo: ", e);
        }
        return null;
    }

    /**
     * Useful for debugging during development, so that arbitrary queries can be made, and their
     * results inspected in the debugger.
     */
    @SuppressWarnings("unused")
    public static List<Map<String, String>> dumpCursor(Cursor cursor) {
        List<Map<String, String>> data = new ArrayList<>();

        if (cursor == null) {
            return data;
        }

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Map<String, String> row = new HashMap<>(cursor.getColumnCount());
            for (String col : cursor.getColumnNames()) {
                int i = cursor.getColumnIndex(col);
                switch (cursor.getType(i)) {
                    case Cursor.FIELD_TYPE_NULL:
                        row.put(col, null);
                        break;

                    case Cursor.FIELD_TYPE_INTEGER:
                        row.put(col, Integer.toString(cursor.getInt(i)));
                        break;

                    case Cursor.FIELD_TYPE_FLOAT:
                        row.put(col, Double.toString(cursor.getFloat(i)));
                        break;

                    case Cursor.FIELD_TYPE_STRING:
                        row.put(col, cursor.getString(i));
                        break;

                    case Cursor.FIELD_TYPE_BLOB:
                        row.put(col, new String(cursor.getBlob(i), Charset.defaultCharset()));
                        break;
                }
            }
            data.add(row);
            cursor.moveToNext();
        }

        cursor.close();
        return data;
    }

    /**
     * Converts two {@code long} bytes values, like from {@link File#length()}, to
     * an {@code int} value that is a percentage, suitable for things like
     * {@link android.widget.ProgressBar#setMax(int)} or
     * {@link androidx.core.app.NotificationCompat.Builder#setProgress(int, int, boolean)}.
     * {@code total} must never be zero!
     */
    public static int getPercent(long current, long total) {
        return (int) ((100L * current + total / 2) / total);
    }

    @SuppressWarnings("unused")
    public static class Profiler {
        public final long startTime = System.currentTimeMillis();
        public final String logTag;

        public Profiler(String logTag) {
            this.logTag = logTag;
        }

        public void log(String message) {
            long duration = System.currentTimeMillis() - startTime;
            Utils.debugLog(logTag, "[" + duration + "ms] " + message);
        }
    }
}
