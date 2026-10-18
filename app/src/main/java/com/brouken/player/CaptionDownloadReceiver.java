package com.brouken.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.DownloadManager;

public final class CaptionDownloadReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            // Reconciliation is cheap; file verification runs on the package manager's worker.
            CaptionPackages.get(context).reconcile();
        }
    }
}
