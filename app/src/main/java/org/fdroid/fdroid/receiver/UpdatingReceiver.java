package org.fdroid.fdroid.receiver;

import static org.fdroid.fdroid.UpdateService.EXTRA_STATUS_CODE;
import static org.fdroid.fdroid.UpdateService.LOCAL_ACTION_STATUS;
import static org.fdroid.fdroid.UpdateService.STATUS_INFO;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class UpdatingReceiver extends BroadcastReceiver {
    SwipeRefreshLayout pullToRefresh;

    public UpdatingReceiver(SwipeRefreshLayout pullToRefresh) {
        this.pullToRefresh = pullToRefresh;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String s = intent.getAction();
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        if (TextUtils.isEmpty(action) || !action.equals(LOCAL_ACTION_STATUS)) {
            return;
        }

        int resultCode = intent.getIntExtra(EXTRA_STATUS_CODE, -1);
        if (resultCode == STATUS_INFO) {
            pullToRefresh.setRefreshing(true);
        }
        // all other status codes are success/error, so we are finished then.
        if (resultCode < STATUS_INFO)
            pullToRefresh.setRefreshing(false);
    }
}
