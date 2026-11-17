package top.dynesshely.chargesentinel.data;

import android.content.Context;
import android.content.SharedPreferences;

import top.dynesshely.chargesentinel.core.ChargePolicy;

/** 阈值、开关与运行状态持久化。 */
public final class Settings {

    private static final String FILE = "charge_sentinel";

    private Settings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context) {
        return prefs(context).getBoolean("enabled", false);
    }

    public static void setEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean("enabled", value).apply();
    }

    public static int stop(Context context) {
        return ChargePolicy.clampStop(prefs(context).getInt("stop", ChargePolicy.DEFAULT_STOP));
    }

    public static void setStop(Context context, int value) {
        prefs(context).edit().putInt("stop", ChargePolicy.clampStop(value)).apply();
    }

    public static int resume(Context context) {
        return ChargePolicy.clampResume(
                prefs(context).getInt("resume", ChargePolicy.DEFAULT_RESUME), stop(context));
    }

    public static void setResume(Context context, int value) {
        prefs(context).edit().putInt("resume", ChargePolicy.clampResume(value, stop(context))).apply();
    }

    /** 是否由本应用主动停止了充电（迟滞状态机的记忆）。 */
    public static boolean suspended(Context context) {
        return prefs(context).getBoolean("suspended", false);
    }

    public static void setSuspended(Context context, boolean value) {
        prefs(context).edit().putBoolean("suspended", value).apply();
    }

    public static boolean simulationEnabled(Context context) {
        return prefs(context).getBoolean("sim_enabled", false);
    }

    public static void setSimulationEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean("sim_enabled", value).apply();
    }

    public static int simulationLevel(Context context) {
        return prefs(context).getInt("sim_level", 90);
    }

    public static void setSimulationLevel(Context context, int value) {
        prefs(context).edit().putInt("sim_level", Math.max(0, Math.min(100, value))).apply();
    }
}
