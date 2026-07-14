package org.fdroid.fdroid.views;

import android.content.Context;
import android.database.Cursor;

public class AvailableAppListAdapter extends AppListAdapter {

    public static AvailableAppListAdapter create(Context context, Cursor cursor, int flags) {
        return new AvailableAppListAdapter(context, cursor, flags);
    }

    private AvailableAppListAdapter(Context context, Cursor c, int flags) {
        super(context, c, flags);
    }

    @Override
    protected boolean showStatusUpdate() {
        return true;
    }

    @Override
    protected boolean showStatusInstalled() {
        return true;
    }
}
