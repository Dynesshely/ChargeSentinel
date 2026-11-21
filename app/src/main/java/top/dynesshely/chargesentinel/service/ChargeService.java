package top.dynesshely.chargesentinel.service;

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

import top.dynesshely.chargesentinel.R;
import top.dynesshely.chargesentinel.core.BatteryInfo;
import top.dynesshely.chargesentinel.core.ChargeControl;
import top.dynesshely.chargesentinel.core.ChargePolicy;
import top.dynesshely.chargesentinel.core.ChargeState;
import top.dynesshely.chargesentinel.core.SysfsChargeControl;
import top.dynesshely.chargesentinel.data.EventLog;
import top.dynesshely.chargesentinel.data.Settings;
import top.dynesshely.chargesentinel.history.HistoryEvent;
import top.dynesshely.chargesentinel.history.HistoryStore;
import top.dynesshely.chargesentinel.history.SampleGate;
import top.dynesshely.chargesentinel.ui.MainActivity;

/**
 * 前台服务：每 10 秒读一次电量，按迟滞策略把充电设成期望状态。
 *
 * 每轮都会调用一次幂等的 {@link ChargeControl#apply(boolean)}：已经到位就不写节点，
 * 被充电器驱动重置时会自动补写，因此在“驱动会周期性改回允许充电”的机型上也能保持停充。
 */
public class ChargeService extends Service {

    public static final String ACTION_TICK = "top.dynesshely.chargesentinel.action.TICK";
    public static final String ACTION_DISABLE_LIMIT = "top.dynesshely.chargesentinel.action.DISABLE_LIMIT";

    private static final String CHANNEL_ID = "charge_limit";
    private static final int NOTIFICATION_ID = 1001;
    private static final long INTERVAL_MS = 10_000L;
    private static final long PRUNE_INTERVAL_MS = 6L * 60 * 60 * 1000;

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    public static volatile boolean running = false;

    private final ChargeControl control = new SysfsChargeControl();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ScheduledExecutorService scheduler;
    private NotificationManager notificationManager;
    private String lastError = "";

    private long lastSampleAt = 0;
    private int lastSampleLevel = -1;
    private long lastPruneAt = 0;

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    public static void requestTick(Context context) {
        try {
            context.startService(new Intent(context, ChargeService.class).setAction(ACTION_TICK));
        } catch (Exception ignored) {
            // The service is not running (or background start is restricted); the next UI refresh retries.
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = getSystemService(NotificationManager.class);
        createChannel();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        running = true;
        startInForeground(buildNotification(BatteryInfo.level(this), BatteryInfo.plugged(this)));
        KeepAliveJobService.schedule(this);
        HistoryStore.get(this).pruneOldData();
        lastPruneAt = System.currentTimeMillis();
        scheduler.scheduleWithFixedDelay(this::safeTick, 0, INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_DISABLE_LIMIT.equals(action)) {
            EventLog.add(getString(R.string.log_disabled_from_notification));
            Settings.setEnabled(this, false);
            restoreCharging();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.enabled(this)) {
            restoreCharging();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_TICK.equals(action) && scheduler != null) {
            scheduler.execute(this::safeTick);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (!Settings.enabled(this)) {
            restoreCharging();
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (Settings.enabled(this)) {
            // 用户从最近任务划掉应用时，让 15 分钟的兜底任务尽快把服务拉回来。
            KeepAliveJobService.schedule(this);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void restoreCharging() {
        ChargeState state = control.apply(false);
        Settings.setSuspended(this, false);
        if (state == ChargeState.ALLOWED) {
            EventLog.add(getString(R.string.log_restore_ok));
        } else if (state != ChargeState.UNKNOWN) {
            EventLog.add(getString(R.string.log_restore_failed, state.name()));
        }
        notifyListeners();
    }

    private void reportError(String message) {
        if (!message.equals(lastError)) {
            lastError = message;
            EventLog.add(message);
        }
    }

    private void safeTick() {
        try {
            tick();
        } catch (Throwable t) {
            reportError(getString(R.string.log_tick_failed, String.valueOf(t)));
        }
    }

    private void tick() {
        if (!Settings.enabled(this)) {
            return;
        }

        int level = Settings.simulationEnabled(this)
                ? Settings.simulationLevel(this)
                : BatteryInfo.level(this);
        if (level < 0) {
            reportError(getString(R.string.log_level_unavailable));
            return;
        }

        int stopAt = Settings.stop(this);
        int resumeAt = Settings.resume(this);
        boolean wasSuspended = Settings.suspended(this);
        boolean plugged = BatteryInfo.plugged(this);

        ChargePolicy.Decision decision = ChargePolicy.decide(level, stopAt, resumeAt, wasSuspended);
        boolean wantSuspended = ChargePolicy.wantSuspended(decision, wasSuspended);

        ChargeState state = control.apply(wantSuspended);
        if (state == ChargeState.UNKNOWN) {
            reportError(getString(R.string.log_node_unavailable));
        } else if (state.isSuspended() != wantSuspended) {
            reportError(getString(R.string.log_apply_failed, state.name()));
        } else {
            lastError = "";
            if (wasSuspended != wantSuspended) {
                Settings.setSuspended(this, wantSuspended);
                EventLog.add(wantSuspended
                        ? getString(R.string.log_suspended, level, stopAt)
                        : getString(R.string.log_resumed, level, resumeAt));
                if (!Settings.simulationEnabled(this)) {
                    HistoryStore.get(this).recordEvent(System.currentTimeMillis(),
                            wantSuspended ? HistoryEvent.TYPE_SUSPEND : HistoryEvent.TYPE_RESUME, level);
                }
            }
        }

        recordSample(level, plugged, wantSuspended);
        updateNotification(level, plugged);
        notifyListeners();
    }

    /** 每 5 分钟或电量发生变化（且距上次至少 30 秒）时落一条采样，模拟电量不入库。 */
    private void recordSample(int level, boolean plugged, boolean suspended) {
        if (Settings.simulationEnabled(this)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (SampleGate.shouldRecord(now, lastSampleAt, level, lastSampleLevel)) {
            HistoryStore.get(this).recordSample(now, level, plugged, suspended);
            lastSampleAt = now;
            lastSampleLevel = level;
        }
        if (now - lastPruneAt >= PRUNE_INTERVAL_MS) {
            lastPruneAt = now;
            HistoryStore.get(this).pruneOldData();
        }
    }

    private void notifyListeners() {
        mainHandler.post(() -> {
            for (Runnable listener : LISTENERS) {
                try {
                    listener.run();
                } catch (Throwable ignored) {
                    // A broken listener must not stop the policy loop.
                }
            }
        });
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        channel.setDescription(getString(R.string.channel_description));
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification(int level, boolean plugged) {
        boolean suspended = Settings.suspended(this) && Settings.enabled(this);
        String title = suspended
                ? getString(R.string.notification_title_suspended, level)
                : getString(plugged ? R.string.notification_title_charging : R.string.notification_title_unplugged, level);
        String text = getString(R.string.notification_text, Settings.stop(this), Settings.resume(this));

        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent disableIntent = new Intent(this, ChargeService.class).setAction(ACTION_DISABLE_LIMIT);
        PendingIntent disablePending = PendingIntent.getService(
                this, 1, disableIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_battery)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE);
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        if (suspended) {
            builder.addAction(new Notification.Action.Builder(
                    null, getString(R.string.notification_action_disable), disablePending).build());
        }
        return builder.build();
    }

    private void updateNotification(int level, boolean plugged) {
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(level, plugged));
        } catch (Exception ignored) {
            // Notification updates are best effort.
        }
    }

    private void startInForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }
}
