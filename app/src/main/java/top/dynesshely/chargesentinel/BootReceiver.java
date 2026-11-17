package top.dynesshely.chargesentinel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机后如果限制处于开启状态，自动恢复前台服务。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Prefs.enabled(context)) {
            return;
        }
        try {
            context.startForegroundService(new Intent(context, ChargeService.class));
            Logs.add("开机自启：已拉起充电监控服务");
        } catch (Exception e) {
            Logs.add("开机自启失败: " + e.getMessage());
        }
    }
}
