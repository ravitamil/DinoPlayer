package com.brouken.player;
import android.content.*;
public final class CaptionBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) { CaptionAutoJobs.scheduleWatch(c);CaptionAutoJobs.kick(c); }
}
