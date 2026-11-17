package top.dynesshely.chargesentinel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 前台服务：每 10 秒读一次电量，按迟滞规则（>=stop 停止充电，<=resume 恢复充电）操作 sysfs。
 *
 * 每次 tick 都回读实际节点值，而不是只信 SharedPreferences：本机充电器驱动会周期性把
 * input_suspend 重置为 0（满电时大约十秒一次），只写一次是留不住“停止充电”的。
 */
public class ChargeService extends Service {

    public static final String ACTION_TICK = "top.dynesshely.chargesentinel.TICK";
    public static final String ACTION_STOP_LIMIT = "top.dynesshely.chargesentinel.STOP_LIMIT";

    private static final String CHANNEL_ID = "charge_limit";
    private static final int NOTIF_ID = 1001;
    private static final long INTERVAL_MS = 10_000L;

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    public static volatile boolean running = false;

    private ScheduledExecutorService exec;
    private final Handler main = new Handler(Looper.getMainLooper());
    private NotificationManager nm;
    private String lastError = "";

    public static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    public static void removeListener(Runnable r) {
        LISTENERS.remove(r);
    }

    public static void requestTick(Context c) {
        try {
            c.startService(new Intent(c, ChargeService.class).setAction(ACTION_TICK));
        } catch (Exception ignored) {
            // 服务未启动时会抛异常，忽略即可
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        nm = getSystemService(NotificationManager.class);
        createChannel();
        exec = Executors.newSingleThreadScheduledExecutor();
        running = true;
        startInForeground(buildNotification(Battery.level(this), Battery.plugged(this)));
        exec.scheduleWithFixedDelay(this::safeTick, 0, INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP_LIMIT.equals(action)) {
            Logs.add("用户从通知关闭限制");
            Prefs.setEnabled(this, false);
            restoreCharging();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Prefs.enabled(this)) {
            restoreCharging();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_TICK.equals(action) && exec != null) {
            exec.execute(this::safeTick);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (exec != null) {
            exec.shutdownNow();
        }
        if (!Prefs.enabled(this)) {
            restoreCharging();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void restoreCharging() {
        int actual = ChargeController.state();
        if (actual != ChargeController.STATE_CHARGING_ALLOWED) {
            ChargeController.Result r = ChargeController.resume();
            Prefs.setSuspended(this, false);
            Logs.add(r.ok ? "已恢复充电" : "恢复充电失败: " + r.output);
        } else {
            Prefs.setSuspended(this, false);
        }
        notifyListeners();
    }

    private void reportError(String msg) {
        if (!msg.equals(lastError)) {
            lastError = msg;
            Logs.add(msg);
        }
    }

    private void safeTick() {
        try {
            tick();
        } catch (Throwable t) {
            reportError("策略执行异常: " + t);
        }
    }

    private void tick() {
        boolean enabled = Prefs.enabled(this);
        if (!enabled) {
            if (ChargeController.state() != ChargeController.STATE_CHARGING_ALLOWED) {
                ChargeController.resume();
                Prefs.setSuspended(this, false);
                Logs.add("限制已关闭，恢复充电");
            }
            return;
        }

        int level = Battery.level(this);
        if (Prefs.simEnabled(this)) {
            level = Prefs.simLevel(this);
        }
        if (level < 0) {
            reportError("读取电量失败，跳过本轮");
            return;
        }

        boolean plugged = Battery.plugged(this);
        int stop = Prefs.stop(this);
        int resume = Prefs.resume(this);
        boolean wantSuspended = Prefs.suspended(this);

        if (level >= stop) {
            wantSuspended = true;
        } else if (level <= resume) {
            wantSuspended = false;
        }

        int actual = ChargeController.state();
        if (actual == ChargeController.STATE_UNKNOWN) {
            reportError("读取充电控制节点失败");
            return;
        }
        boolean isSuspended = actual == ChargeController.STATE_SUSPENDED;

        if (isSuspended != wantSuspended) {
            ChargeController.Result r = wantSuspended ? ChargeController.suspend() : ChargeController.resume();
            int now = ChargeController.state();
            boolean ok = now == (wantSuspended
                    ? ChargeController.STATE_SUSPENDED : ChargeController.STATE_CHARGING_ALLOWED);
            if (ok) {
                Prefs.setSuspended(this, wantSuspended);
                lastError = "";
                if (wantSuspended) {
                    Logs.add("电量 " + level + "% ≥ " + stop + "%，已停止充电");
                } else {
                    Logs.add("电量 " + level + "% ≤ " + resume + "%，已恢复充电");
                }
            } else {
                reportError((wantSuspended ? "停止" : "恢复") + "充电失败: " + r.output);
            }
        } else if (Prefs.suspended(this) != isSuspended) {
            Prefs.setSuspended(this, isSuspended);
        }

        updateNotification(level, plugged);
        notifyListeners();
    }

    private void notifyListeners() {
        main.post(() -> {
            for (Runnable r : LISTENERS) {
                try {
                    r.run();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setDescription("电量到达上限时停止充电，回落到下限时恢复");
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(int level, boolean plugged) {
        boolean suspended = Prefs.suspended(this) && Prefs.enabled(this);
        String title = suspended ? "充电已暂停 · " + level + "%"
                : (plugged ? "正在充电 · " + level + "%" : "未插电 · " + level + "%");
        String text = "上限 " + Prefs.stop(this) + "% 停止 / " + Prefs.resume(this) + "% 恢复";

        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopLimit = new Intent(this, ChargeService.class).setAction(ACTION_STOP_LIMIT);
        PendingIntent stopPi = PendingIntent.getService(
                this, 1, stopLimit, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_battery)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE);
        if (suspended) {
            b.addAction(new Notification.Action.Builder(null, "关闭限制并恢复充电", stopPi).build());
        }
        return b.build();
    }

    private void updateNotification(int level, boolean plugged) {
        try {
            nm.notify(NOTIF_ID, buildNotification(level, plugged));
        } catch (Exception ignored) {
        }
    }

    private void startInForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }
}
