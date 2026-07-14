package org.fdroid.fdroid.views;

import android.content.Context;
import android.database.Cursor;
import android.os.Build;

public class CanUpdateAppListAdapter extends AppListAdapter {

    public static CanUpdateAppListAdapter create(Context context, Cursor cursor, int flags) {
        return new CanUpdateAppListAdapter(context, cursor, flags);
    }

    private CanUpdateAppListAdapter(Context context, Cursor c, int flags) {
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
