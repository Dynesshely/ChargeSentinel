package top.dynesshely.chargesentinel.core;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/** 从系统广播读取电量与供电状态（不需要 root）。 */
public final class BatteryInfo {

    private BatteryInfo() {
    }

    public static int level(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) {
            return -1;
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) {
            return -1;
        }
        return Math.round(level * 100f / scale);
    }

    public static boolean plugged(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return intent != null && intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }
}
