package dev.linjian.peek;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

public class DeskPetBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!AppPrefs.get(context).getBoolean(AppPrefs.KEY_DESK_PET_ENABLED, false)) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) return;
        Intent service = new Intent(context, DeskPetService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service); else context.startService(service);
    }
}
