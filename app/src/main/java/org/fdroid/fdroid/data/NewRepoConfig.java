package org.fdroid.fdroid.data;

import android.net.Uri;
import android.text.TextUtils;

import org.fdroid.fdroid.R;
import org.fdroid.fdroid.Utils;

import java.util.Arrays;
import java.util.Locale;

public class NewRepoConfig {
    private static final String TAG = "NewRepoConfig";

    public static final String DEFAULT_NEW_REPO_TEXT = "https://";

    private int errorMessage = 0;
    private boolean isValidRepo;

    private String uriString;
    private String host;
    private int port = -1;
    private String username;
    private String password;
    private String fingerprint;

    public NewRepoConfig() {
        uriString = DEFAULT_NEW_REPO_TEXT;
    }

    public NewRepoConfig(Uri incomingUri) {
        init(incomingUri);
    }

    private void init(Uri incomingUri) {
        /* an URL from a click, NFC, QRCode scan, etc */
        Uri uri = incomingUri;
        if (uri == null) {
            isValidRepo = false;
            return;
        }

        Utils.debugLog(TAG, "Parsing incoming intent looking for repo: " + incomingUri);

        // scheme and host should only ever be pure ASCII aka Locale.ENGLISH
        String scheme = uri.getScheme();
        host = uri.getHost();
        port = uri.getPort();
        if (TextUtils.isEmpty(scheme) || TextUtils.isEmpty(host)) {
            errorMessage = R.string.repo_url_invalid;
            isValidRepo = false;
            return;
        }

        if (Arrays.asList("FDROIDREPO", "FDROIDREPOS").contains(scheme)) {
            /*
             * QRCodes are more efficient in all upper case, so QR URIs are
             * encoded in all upper case, then forced to lower case. Checking if
             * the special F-Droid scheme being all is upper case means it
             * should be downcased.
             */
            uri = Uri.parse(uri.toString().toLowerCase(Locale.ENGLISH));
        } else if (uri.getPath() != null && uri.getPath().endsWith("/FDROID/REPO")) {
            /*
             * some QR scanners chop off the fdroidrepo:// and just try http://,
             * then the incoming URI does not get downcased properly, and the
             * query string is stripped off. So just downcase the path, and
             * carry on to get something working.
             */
            uri = Uri.parse(uri.toString().toLowerCase(Locale.ENGLISH));
        }

        // make scheme and host lowercase so they're readable in dialogs
        assert scheme != null;
        scheme = scheme.toLowerCase(Locale.ENGLISH);
        host = host.toLowerCase(Locale.ENGLISH);

        if (uri.getPath() == null
                || !Arrays.asList("https", "http", "fdroidrepos", "fdroidrepo").contains(scheme)) {
            isValidRepo = false;
            errorMessage = R.string.repo_url_invalid;
            return;
        }

        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            String[] userInfoTokens = userInfo.split(":");
            if (userInfoTokens.length >= 2) {
                username = userInfoTokens[0];
                password = userInfoTokens[1];
                for (int i = 2; i < userInfoTokens.length; i++) {
                    //noinspection StringConcatenationInLoop
                    password += ":" + userInfoTokens[i];
                }
            }
        }

        fingerprint = uri.getQueryParameter("fingerprint");
        uriString = sanitizeRepoUri(uri);
        isValidRepo = true;
    }

    public int getPort() {
        if (port == -1) {
            if (uriString.startsWith("https://"))
                return 443;
            if (uriString.startsWith("http://"))
                return 80;
        }
        return port;
    }

    public String getRepoUriString() {
        return uriString;
    }

    public Uri getRepoUri() {
        if (uriString == null) {
            return null;
        }
        return Uri.parse(uriString);
    }

    public String getHost() {
        return host;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public boolean isValidRepo() {
        return isValidRepo;
    }

    public int getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sanitize and format an incoming repo URI for function and readability
     */
    public static String sanitizeRepoUri(Uri uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        assert host != null;
        assert scheme != null;
        String userInfo = uri.getUserInfo();
        return uri.toString()
                .replaceAll("#.*$", "") //remove fragment
                .replaceAll("\\?.*$", "") // remove the whole query
                .replaceAll("/*$", "") // remove all trailing slashes
                .replace(userInfo + "@", "") // remove user authentication
                .replace(host, host.toLowerCase(Locale.ENGLISH))
                .replace(scheme, scheme.toLowerCase(Locale.ENGLISH))
                .replace("fdroidrepo", "http") // proper repo address
                .replace("/FDROID/REPO", "/fdroid/repo"); // for QR FDroid path
    }
}