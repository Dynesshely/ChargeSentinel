package top.dynesshely.chargesentinel.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import top.dynesshely.chargesentinel.R;
import top.dynesshely.chargesentinel.data.EventLog;
import top.dynesshely.chargesentinel.data.Settings;

/** 开机（或应用被覆盖安装）后，如果限制仍处于开启状态，自动恢复监控服务。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Settings.enabled(context)) {
            return;
        }
        try {
            context.startForegroundService(new Intent(context, ChargeService.class));
            EventLog.add(context.getString(R.string.log_boot_started));
        } catch (Exception e) {
            EventLog.add(context.getString(R.string.log_boot_failed, e.getMessage()));
        }
    }
}
