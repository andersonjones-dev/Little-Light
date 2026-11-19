package dev.andersonjones.littlelight.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the faith-first helper after the phone restarts or the app updates. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            GuardService.sync(c);
        }
    }
}
