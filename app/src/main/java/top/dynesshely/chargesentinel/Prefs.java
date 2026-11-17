package top.dynesshely.chargesentinel;

import android.content.Context;

/** 轻量配置存储。所有开关与阈值都落在这里，服务重启后状态可恢复。 */
public final class Prefs {

    public static final int DEFAULT_STOP = 80;
    public static final int DEFAULT_RESUME = 50;

    private Prefs() {
    }

    private static android.content.SharedPreferences sp(Context c) {
        return c.getSharedPreferences("charge_sentinel", Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) {
        return sp(c).getBoolean("enabled", false);
    }

    public static void setEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean("enabled", v).apply();
    }

    public static int stop(Context c) {
        return sp(c).getInt("stop", DEFAULT_STOP);
    }

    public static void setStop(Context c, int v) {
        sp(c).edit().putInt("stop", v).apply();
    }

    public static int resume(Context c) {
        return sp(c).getInt("resume", DEFAULT_RESUME);
    }

    public static void setResume(Context c, int v) {
        sp(c).edit().putInt("resume", v).apply();
    }

    /** 记录“当前是否由本应用主动停止了充电”，用于迟滞状态机。 */
    public static boolean suspended(Context c) {
        return sp(c).getBoolean("suspended", false);
    }

    public static void setSuspended(Context c, boolean v) {
        sp(c).edit().putBoolean("suspended", v).apply();
    }

    public static boolean simEnabled(Context c) {
        return sp(c).getBoolean("sim_enabled", false);
    }

    public static void setSimEnabled(Context c, boolean v) {
        sp(c).edit().putBoolean("sim_enabled", v).apply();
    }

    public static int simLevel(Context c) {
        return sp(c).getInt("sim_level", 90);
    }

    public static void setSimLevel(Context c, int v) {
        sp(c).edit().putInt("sim_level", v).apply();
    }
}
