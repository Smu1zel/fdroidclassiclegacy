/*
 * Copyright (C) 2016 Blue Jay Wireless
 * Copyright (C) 2015-2016 Daniel Martí <mvdan@mvdan.cc>
 * Copyright (C) 2015 Christian Morgner
 * Copyright (C) 2014-2016 Hans-Christoph Steiner <hans@eds.org>
 * Copyright (C) 2013-2016 Peter Serwylo <peter@serwylo.com>
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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */

package org.fdroid.fdroid.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.fdroid.fdroid.R;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.Schema.AntiFeatureTable;
import org.fdroid.fdroid.data.Schema.ApkAntiFeatureJoinTable;
import org.fdroid.fdroid.data.Schema.ApkTable;
import org.fdroid.fdroid.data.Schema.AppMetadataTable;
import org.fdroid.fdroid.data.Schema.AppPrefsTable;
import org.fdroid.fdroid.data.Schema.CatJoinTable;
import org.fdroid.fdroid.data.Schema.InstalledAppTable;
import org.fdroid.fdroid.data.Schema.PackageTable;
import org.fdroid.fdroid.data.Schema.RepoTable;

/**
 * This is basically a singleton used to represent the database at the core
 * of all of the {@link android.content.ContentProvider}s used at the core
 * of this app.  {@link DBHelper} is not {@code private} so that it can be easily
 * used in test subclasses.
 */
@SuppressWarnings("LineLength")
public class DBHelper extends SQLiteOpenHelper {

    private static final String TAG = "DBHelper";

    public static final int REPO_XML_ITEM_COUNT = 8;

    private static DBHelper instance;
    private static final String DATABASE_NAME = "fdroid";

    private static final String CREATE_TABLE_PACKAGE = "CREATE TABLE " + PackageTable.NAME
            + " ( "
            + PackageTable.Cols.PACKAGE_NAME + " text not null, "
            + PackageTable.Cols.PREFERRED_METADATA + " integer"
            + ");";

    private static final String CREATE_TABLE_REPO = "create table "
            + RepoTable.NAME + " ("
            + RepoTable.Cols._ID + " integer primary key, "
            + RepoTable.Cols.ADDRESS + " text not null, "
            + RepoTable.Cols.NAME + " text, "
            + RepoTable.Cols.DESCRIPTION + " text, "
            + RepoTable.Cols.IN_USE + " integer not null, "
            + RepoTable.Cols.PRIORITY + " integer not null, "
            + RepoTable.Cols.SIGNING_CERT + " text, "
            + RepoTable.Cols.FINGERPRINT + " text, "
            + RepoTable.Cols.MAX_AGE + " integer not null default 0, "
            + RepoTable.Cols.VERSION + " integer not null default 0, "
            + RepoTable.Cols.LAST_ETAG + " text, "
            + RepoTable.Cols.LAST_UPDATED + " string,"
            + RepoTable.Cols.IS_SWAP + " integer boolean default 0,"
            + RepoTable.Cols.USERNAME + " string, "
            + RepoTable.Cols.PASSWORD + " string,"
            + RepoTable.Cols.TIMESTAMP + " integer not null default 0, "
            + RepoTable.Cols.ICON + " string, "
            + RepoTable.Cols.MIRRORS + " string, "
            + RepoTable.Cols.USER_MIRRORS + " string, "
            + RepoTable.Cols.PUSH_REQUESTS + " integer not null default " + Repo.PUSH_REQUEST_IGNORE
            + ");";

    static final String CREATE_TABLE_APK =
            "CREATE TABLE " + ApkTable.NAME + " ( "
                    + ApkTable.Cols.APP_ID + " integer not null, "
                    + ApkTable.Cols.VERSION_NAME + " text, "
                    + ApkTable.Cols.REPO_ID + " integer not null, "
                    + ApkTable.Cols.HASH + " text not null, "
                    + ApkTable.Cols.VERSION_CODE + " int not null,"
                    + ApkTable.Cols.NAME + " text not null, "
                    + ApkTable.Cols.SIZE + " int not null, "
                    + ApkTable.Cols.SIGNATURE + " string, "
                    + ApkTable.Cols.SOURCE_NAME + " string, "
                    + ApkTable.Cols.MIN_SDK_VERSION + " integer, "
                    + ApkTable.Cols.TARGET_SDK_VERSION + " integer, "
                    + ApkTable.Cols.MAX_SDK_VERSION + " integer, "
                    + ApkTable.Cols.OBB_MAIN_FILE + " string, "
                    + ApkTable.Cols.OBB_MAIN_FILE_SHA256 + " string, "
                    + ApkTable.Cols.OBB_PATCH_FILE + " string, "
                    + ApkTable.Cols.OBB_PATCH_FILE_SHA256 + " string, "
                    + ApkTable.Cols.REQUESTED_PERMISSIONS + " string, "
                    + ApkTable.Cols.FEATURES + " string, "
                    + ApkTable.Cols.NATIVE_CODE + " string, "
                    + ApkTable.Cols.HASH_TYPE + " string, "
                    + ApkTable.Cols.ADDED_DATE + " string, "
                    + ApkTable.Cols.IS_COMPATIBLE + " int not null, "
                    + ApkTable.Cols.INCOMPATIBLE_REASONS + " text"
                    + ");";

    static final String CREATE_TABLE_APP_METADATA = "CREATE TABLE " + AppMetadataTable.NAME
            + " ( "
            + AppMetadataTable.Cols.PACKAGE_ID + " integer not null, "
            + AppMetadataTable.Cols.REPO_ID + " integer not null, "
            + AppMetadataTable.Cols.NAME + " text not null, "
            + AppMetadataTable.Cols.SUMMARY + " text not null, "
            + AppMetadataTable.Cols.ICON + " text, "
            + AppMetadataTable.Cols.DESCRIPTION + " text not null, "
            + AppMetadataTable.Cols.WHATSNEW + " text, "
            + AppMetadataTable.Cols.LICENSE + " text not null, "
            + AppMetadataTable.Cols.AUTHOR_NAME + " text, "
            + AppMetadataTable.Cols.AUTHOR_EMAIL + " text, "
            + AppMetadataTable.Cols.WEBSITE + " text, "
            + AppMetadataTable.Cols.ISSUE_TRACKER + " text, "
            + AppMetadataTable.Cols.SOURCE_CODE + " text, "
            + AppMetadataTable.Cols.TRANSLATION + " text, "
            + AppMetadataTable.Cols.VIDEO + " string, "
            + AppMetadataTable.Cols.CHANGELOG + " text, "
            + AppMetadataTable.Cols.PREFERRED_SIGNER + " text,"
            + AppMetadataTable.Cols.SUGGESTED_VERSION_CODE + " text,"
            + AppMetadataTable.Cols.UPSTREAM_VERSION_NAME + " text,"
            + AppMetadataTable.Cols.UPSTREAM_VERSION_CODE + " integer,"
            + AppMetadataTable.Cols.ANTI_FEATURES + " string,"
            + AppMetadataTable.Cols.DONATE + " string,"
            + AppMetadataTable.Cols.BITCOIN + " string,"
            + AppMetadataTable.Cols.LITECOIN + " string,"
            + AppMetadataTable.Cols.FLATTR_ID + " string,"
            + AppMetadataTable.Cols.LIBERAPAY + " string,"
            + AppMetadataTable.Cols.OPEN_COLLECTIVE + " string,"
            + AppMetadataTable.Cols.REQUIREMENTS + " string,"
            + AppMetadataTable.Cols.ADDED + " string,"
            + AppMetadataTable.Cols.LAST_UPDATED + " string,"
            + AppMetadataTable.Cols.IS_COMPATIBLE + " int not null,"
            + AppMetadataTable.Cols.ICON_URL + " text, "
            + AppMetadataTable.Cols.FEATURE_GRAPHIC + " string,"
            + AppMetadataTable.Cols.PROMO_GRAPHIC + " string,"
            + AppMetadataTable.Cols.TV_BANNER + " string,"
            + AppMetadataTable.Cols.PHONE_SCREENSHOTS + " string,"
            + AppMetadataTable.Cols.SEVEN_INCH_SCREENSHOTS + " string,"
            + AppMetadataTable.Cols.TEN_INCH_SCREENSHOTS + " string,"
            + AppMetadataTable.Cols.TV_SCREENSHOTS + " string,"
            + AppMetadataTable.Cols.WEAR_SCREENSHOTS + " string,"
            + AppMetadataTable.Cols.IS_APK + " boolean,"
            + "primary key(" + AppMetadataTable.Cols.PACKAGE_ID + ", " + AppMetadataTable.Cols.REPO_ID + "));";

    private static final String CREATE_TABLE_APP_PREFS = "CREATE TABLE " + AppPrefsTable.NAME
            + " ( "
            + AppPrefsTable.Cols.PACKAGE_NAME + " TEXT, "
            + AppPrefsTable.Cols.IGNORE_THIS_UPDATE + " INT NOT NULL, "
            + AppPrefsTable.Cols.IGNORE_ALL_UPDATES + " INT BOOLEAN NOT NULL, "
            + AppPrefsTable.Cols.IGNORE_VULNERABILITIES + " INT BOOLEAN NOT NULL "
            + " );";

    private static final String CREATE_TABLE_CATEGORY = "CREATE TABLE " + Schema.CategoryTable.NAME
            + " ( "
            + Schema.CategoryTable.Cols.NAME + " TEXT NOT NULL "
            + " );";

    /**
     * The order of the two columns in the primary key matters for this table. The index that is
     * built for sqlite to quickly search the primary key will be sorted by app metadata id first,
     * and category id second. This means that we don't need a separate individual index on the
     * app metadata id, because it can instead look through the primary key index. This can be
     * observed by flipping the order of the primary key columns, and noting the resulting sqlite
     * logs along the lines of:
     * E/SQLiteLog(14164): (284) automatic index on fdroid_categoryAppMetadataJoin(appMetadataId)
     */
    static final String CREATE_TABLE_CAT_JOIN = "CREATE TABLE " + CatJoinTable.NAME
            + " ( "
            + CatJoinTable.Cols.APP_METADATA_ID + " INT NOT NULL, "
            + CatJoinTable.Cols.CATEGORY_ID + " INT NOT NULL, "
            + "primary key(" + CatJoinTable.Cols.APP_METADATA_ID + ", " + CatJoinTable.Cols.CATEGORY_ID + ") "
            + " );";

    private static final String CREATE_TABLE_INSTALLED_APP = "CREATE TABLE " + InstalledAppTable.NAME
            + " ( "
            + InstalledAppTable.Cols.PACKAGE_ID + " INT NOT NULL UNIQUE, "
            + InstalledAppTable.Cols.VERSION_CODE + " INT NOT NULL, "
            + InstalledAppTable.Cols.VERSION_NAME + " TEXT NOT NULL, "
            + InstalledAppTable.Cols.APPLICATION_LABEL + " TEXT NOT NULL, "
            + InstalledAppTable.Cols.SIGNATURE + " TEXT NOT NULL, "
            + InstalledAppTable.Cols.LAST_UPDATE_TIME + " INTEGER NOT NULL DEFAULT 0, "
            + InstalledAppTable.Cols.HASH_TYPE + " TEXT NOT NULL, "
            + InstalledAppTable.Cols.HASH + " TEXT NOT NULL"
            + " );";

    private static final String CREATE_TABLE_ANTI_FEATURE = "CREATE TABLE " + AntiFeatureTable.NAME
            + " ( "
            + AntiFeatureTable.Cols.NAME + " TEXT NOT NULL "
            + " );";

    static final String CREATE_TABLE_APK_ANTI_FEATURE_JOIN = "CREATE TABLE " + ApkAntiFeatureJoinTable.NAME
            + " ( "
            + ApkAntiFeatureJoinTable.Cols.APK_ID + " INT NOT NULL, "
            + ApkAntiFeatureJoinTable.Cols.ANTI_FEATURE_ID + " INT NOT NULL, "
            + "primary key(" + ApkAntiFeatureJoinTable.Cols.APK_ID + ", " + ApkAntiFeatureJoinTable.Cols.ANTI_FEATURE_ID + ") "
            + " );";

    protected static final int DB_VERSION = 85;

    private final Context context;

    DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DB_VERSION);
        this.context = context.getApplicationContext();
    }

    static synchronized DBHelper getInstance(Context context) {
        if (instance == null) {
            Utils.debugLog(TAG, "First time accessing database, creating new helper");
            instance = new DBHelper(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(CREATE_TABLE_PACKAGE);
        db.execSQL(CREATE_TABLE_APP_METADATA);
        db.execSQL(CREATE_TABLE_APK);
        db.execSQL(CREATE_TABLE_CATEGORY);
        db.execSQL(CREATE_TABLE_CAT_JOIN);
        db.execSQL(CREATE_TABLE_INSTALLED_APP);
        db.execSQL(CREATE_TABLE_REPO);
        db.execSQL(CREATE_TABLE_APP_PREFS);
        db.execSQL(CREATE_TABLE_ANTI_FEATURE);
        db.execSQL(CREATE_TABLE_APK_ANTI_FEATURE_JOIN);
        ensureIndexes(db);

        String[] defaultRepos = context.getResources().getStringArray(R.array.default_repos);
        if (defaultRepos.length % REPO_XML_ITEM_COUNT != 0) {
            throw new IllegalArgumentException(
                    "default_repo.xml array does not have the right number of elements");
        }
        for (int i = 0; i < defaultRepos.length / REPO_XML_ITEM_COUNT; i++) {
            int offset = i * REPO_XML_ITEM_COUNT;
            insertRepo(
                    db,
                    defaultRepos[offset],     // name
                    defaultRepos[offset + 1], // address
                    defaultRepos[offset + 2], // description
                    defaultRepos[offset + 3], // version
                    defaultRepos[offset + 4], // enabled
                    defaultRepos[offset + 5], // priority
                    defaultRepos[offset + 6], // pushRequests
                    defaultRepos[offset + 7]  // pubkey
            );
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        resetTransient(context);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Utils.debugLog(TAG, "Upgrading database from v" + oldVersion + " v" + newVersion);
        addOpenCollective(db, oldVersion);
        addTranslation(db, oldVersion);
    }

    private void addOpenCollective(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 85) {
            return;
        }

        if (!columnExists(db, AppMetadataTable.NAME, AppMetadataTable.Cols.OPEN_COLLECTIVE)) {
            Utils.debugLog(TAG, "Adding " + AppMetadataTable.Cols.OPEN_COLLECTIVE + " field to "
                    + AppMetadataTable.NAME + " table in db.");
            db.execSQL("alter table " + AppMetadataTable.NAME + " add column "
                    + AppMetadataTable.Cols.OPEN_COLLECTIVE + " string;");
        }
    }

    private void addTranslation(SQLiteDatabase db, int oldVersion) {
        if (oldVersion >= 85) {
            return;
        }
        if (!columnExists(db, AppMetadataTable.NAME, AppMetadataTable.Cols.TRANSLATION)) {
            Utils.debugLog(TAG, "Adding " + AppMetadataTable.Cols.TRANSLATION + " field to "
                    + AppMetadataTable.NAME + " table in db.");
            db.execSQL("alter table " + AppMetadataTable.NAME + " add column "
                    + AppMetadataTable.Cols.TRANSLATION + " string;");
        }
    }

    /**
     * By clearing the etags stored in the repo table, it means that next time the user updates
     * their repos (either manually or on a scheduled task), they will update regardless of whether
     * they have changed since last update or not.
     */
    private static void clearRepoEtags(SQLiteDatabase db) {
        Utils.debugLog(TAG, "Clearing repo etags, so next update will not be skipped with \"Repos up to date\".");
        db.execSQL("update " + RepoTable.NAME + " set " + RepoTable.Cols.LAST_ETAG + " = NULL");
    }

    /**
     * Resets all database tables that are generated from the index files downloaded
     * from the active repositories.  This will trigger the index file(s) to be
     * downloaded processed on the next update.
     */
    public static void resetTransient(Context context) {
        resetTransient(getInstance(context).getWritableDatabase());
    }

    private static void resetTransient(SQLiteDatabase db) {
        Utils.debugLog(TAG, "Removing all index tables, they will be recreated next time F-Droid updates.");

        db.beginTransaction();
        try {
            if (tableExists(db, Schema.CategoryTable.NAME)) {
                db.execSQL("DROP TABLE " + Schema.CategoryTable.NAME);
            }

            if (tableExists(db, CatJoinTable.NAME)) {
                db.execSQL("DROP TABLE " + CatJoinTable.NAME);
            }

            if (tableExists(db, PackageTable.NAME)) {
                db.execSQL("DROP TABLE " + PackageTable.NAME);
            }

            if (tableExists(db, AntiFeatureTable.NAME)) {
                db.execSQL("DROP TABLE " + AntiFeatureTable.NAME);
            }

            if (tableExists(db, ApkAntiFeatureJoinTable.NAME)) {
                db.execSQL("DROP TABLE " + ApkAntiFeatureJoinTable.NAME);
            }

            db.execSQL("DROP TABLE " + AppMetadataTable.NAME);
            db.execSQL("DROP TABLE " + ApkTable.NAME);

            db.execSQL(CREATE_TABLE_PACKAGE);
            db.execSQL(CREATE_TABLE_APP_METADATA);
            db.execSQL(CREATE_TABLE_APK);
            db.execSQL(CREATE_TABLE_CATEGORY);
            db.execSQL(CREATE_TABLE_CAT_JOIN);
            db.execSQL(CREATE_TABLE_ANTI_FEATURE);
            db.execSQL(CREATE_TABLE_APK_ANTI_FEATURE_JOIN);
            clearRepoEtags(db);
            ensureIndexes(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void ensureIndexes(SQLiteDatabase db) {
        if (tableExists(db, PackageTable.NAME)) {
            Utils.debugLog(TAG, "Ensuring indexes exist for " + PackageTable.NAME);
            db.execSQL("CREATE INDEX IF NOT EXISTS package_packageName on " + PackageTable.NAME + " (" + PackageTable.Cols.PACKAGE_NAME + ");");
            db.execSQL("CREATE INDEX IF NOT EXISTS package_preferredMetadata on " + PackageTable.NAME + " (" + PackageTable.Cols.PREFERRED_METADATA + ");");
        }

        Utils.debugLog(TAG, "Ensuring indexes exist for " + AppMetadataTable.NAME);
        db.execSQL("CREATE INDEX IF NOT EXISTS name on " + AppMetadataTable.NAME + " (" + AppMetadataTable.Cols.NAME + ");"); // Used for sorting most lists
        db.execSQL("CREATE INDEX IF NOT EXISTS added on " + AppMetadataTable.NAME + " (" + AppMetadataTable.Cols.ADDED + ");"); // Used for sorting "newly added"

        if (columnExists(db, AppMetadataTable.NAME, AppMetadataTable.Cols.PACKAGE_ID)) {
            db.execSQL("CREATE INDEX IF NOT EXISTS metadata_packageId ON " + AppMetadataTable.NAME + " (" + AppMetadataTable.Cols.PACKAGE_ID + ");");
        }

        if (columnExists(db, AppMetadataTable.NAME, AppMetadataTable.Cols.REPO_ID)) {
            db.execSQL("CREATE INDEX IF NOT EXISTS metadata_repoId ON " + AppMetadataTable.NAME + " (" + AppMetadataTable.Cols.REPO_ID + ");");
        }

        Utils.debugLog(TAG, "Ensuring indexes exist for " + ApkTable.NAME);
        db.execSQL("CREATE INDEX IF NOT EXISTS apk_vercode on " + ApkTable.NAME + " (" + ApkTable.Cols.VERSION_CODE + ");");
        db.execSQL("CREATE INDEX IF NOT EXISTS apk_appId on " + ApkTable.NAME + " (" + ApkTable.Cols.APP_ID + ");");
        db.execSQL("CREATE INDEX IF NOT EXISTS repoId ON " + ApkTable.NAME + " (" + ApkTable.Cols.REPO_ID + ");");

        if (tableExists(db, AppPrefsTable.NAME)) {
            Utils.debugLog(TAG, "Ensuring indexes exist for " + AppPrefsTable.NAME);
            db.execSQL("CREATE INDEX IF NOT EXISTS appPrefs_packageName on " + AppPrefsTable.NAME + " (" + AppPrefsTable.Cols.PACKAGE_NAME + ");");
            db.execSQL("CREATE INDEX IF NOT EXISTS appPrefs_packageName_ignoreAll_ignoreThis on " + AppPrefsTable.NAME + " (" +
                    AppPrefsTable.Cols.PACKAGE_NAME + ", " +
                    AppPrefsTable.Cols.IGNORE_ALL_UPDATES + ", " +
                    AppPrefsTable.Cols.IGNORE_THIS_UPDATE + ");");
        }

        if (columnExists(db, InstalledAppTable.NAME, InstalledAppTable.Cols.PACKAGE_ID)) {
            Utils.debugLog(TAG, "Ensuring indexes exist for " + InstalledAppTable.NAME);
            db.execSQL("CREATE INDEX IF NOT EXISTS installedApp_packageId_vercode on " + InstalledAppTable.NAME + " (" +
                    InstalledAppTable.Cols.PACKAGE_ID + ", " + InstalledAppTable.Cols.VERSION_CODE + ");");
        }

        Utils.debugLog(TAG, "Ensuring indexes exist for " + RepoTable.NAME);
        db.execSQL("CREATE INDEX IF NOT EXISTS repo_id_isSwap on " + RepoTable.NAME + " (" +
                RepoTable.Cols._ID + ", " + RepoTable.Cols.IS_SWAP + ");");
    }

    private static boolean columnExists(SQLiteDatabase db, String table, String field) {
        boolean found = false;
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
            if (name.equalsIgnoreCase(field)) {
                found = true;
                break;
            }
            cursor.moveToNext();
        }
        cursor.close();
        return found;
    }

    private static boolean tableExists(SQLiteDatabase db, String table) {
        Cursor cursor = db.query("sqlite_master", new String[]{"name"},
                "type = 'table' AND name = ?", new String[]{table}, null, null, null);

        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    private void insertRepo(SQLiteDatabase db, String name, String address,
                            String description, String version, String enabled,
                            String priority, String pushRequests, String pubKey) {
        ContentValues values = new ContentValues();
        values.put(RepoTable.Cols.ADDRESS, address);
        values.put(RepoTable.Cols.NAME, name);
        values.put(RepoTable.Cols.DESCRIPTION, description);
        values.put(RepoTable.Cols.SIGNING_CERT, pubKey);
        values.put(RepoTable.Cols.FINGERPRINT, Utils.calcFingerprint(pubKey));
        values.put(RepoTable.Cols.MAX_AGE, 0);
        values.put(RepoTable.Cols.VERSION, Utils.parseInt(version, 0));
        values.put(RepoTable.Cols.IN_USE, Utils.parseInt(enabled, 0));
        values.put(RepoTable.Cols.PRIORITY, Utils.parseInt(priority, Integer.MAX_VALUE));
        values.put(RepoTable.Cols.LAST_ETAG, (String) null);
        values.put(RepoTable.Cols.TIMESTAMP, 0);

        switch (pushRequests) {
            case "ignore":
                values.put(RepoTable.Cols.PUSH_REQUESTS, Repo.PUSH_REQUEST_IGNORE);
                break;
            default:
                throw new IllegalArgumentException(pushRequests + " is not a supported option!");
        }

        Utils.debugLog(TAG, "Adding repository " + name + " with push requests as " + pushRequests);
        db.insert(RepoTable.NAME, null, values);
    }

}
