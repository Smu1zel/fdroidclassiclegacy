package org.fdroid.fdroid.views;

import android.content.Context;
import android.database.Cursor;

public class InstalledAppListAdapter extends AppListAdapter {

    public static InstalledAppListAdapter create(Context context, Cursor cursor, int flags) {
        return new InstalledAppListAdapter(context, cursor, flags);
    }

    private InstalledAppListAdapter(Context context, Cursor c, int flags) {
        super(context, c, flags);
    }

    @Override
    protected boolean showStatusUpdate() {
        return true;
    }

    @Override
    protected boolean showStatusInstalled() {
        return false;
    }
}
