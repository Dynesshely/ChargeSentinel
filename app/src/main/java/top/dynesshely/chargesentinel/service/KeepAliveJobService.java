package top.dynesshely.chargesentinel.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import top.dynesshely.chargesentinel.R;
import top.dynesshely.chargesentinel.data.EventLog;
import top.dynesshely.chargesentinel.data.Settings;

/**
 * 15 分钟一次的兜底任务：如果限制仍开启但前台服务已被系统回收，就把它拉回来。
 *
 * JobScheduler 的周期任务在 Doze 下也会被系统调度（窗口可能被推迟），配合
 * START_STICKY 与开机自启，构成“后台持久化运行”的第三道保险。
 */
public class KeepAliveJobService extends JobService {

    private static final int JOB_ID = 0x4348534C; // "CHSL"

    public static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            return;
        }
        if (scheduler.getPendingJob(JOB_ID) != null) {
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, KeepAliveJobService.class))
                .setPeriodic(15L * 60 * 1000)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build();
        scheduler.schedule(job);
    }

    public static void cancel(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) {
            scheduler.cancel(JOB_ID);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!Settings.enabled(this)) {
            return false;
        }
        if (!ChargeService.running) {
            try {
                startForegroundService(new Intent(this, ChargeService.class));
            } catch (Exception e) {
                EventLog.add(getString(R.string.log_service_start_failed, String.valueOf(e.getMessage())));
            }
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
