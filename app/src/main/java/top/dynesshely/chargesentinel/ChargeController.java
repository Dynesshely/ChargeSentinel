package top.dynesshely.chargesentinel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * 通过 root 写入 sysfs 控制充电通断。
 *
 * OnePlus 7 Pro（SM8150，Android 14 / LineageOS 21）实测：
 *   /sys/class/power_supply/battery/mmi_charging_enable  0=停充，1=允许充电（主控制，写入后能保持）
 *   /sys/class/power_supply/battery/input_suspend        1=切断输入电源，0=恢复（立即生效，但会被充电器
 *                                                        驱动的 AICL 周期重置，需要周期性复核重写）
 * 无效手段（实测）：
 *   charge_control_limit —— 本内核只读（-r--r--r--）
 *   /sys/class/qcom-battery/restrict_chg —— 写入后电压无变化，不影响充电
 */
public final class ChargeController {

    public static final String NODE_SUSPEND = "/sys/class/power_supply/battery/input_suspend";
    public static final String NODE_ENABLE = "/sys/class/power_supply/battery/mmi_charging_enable";

    public static final int STATE_UNKNOWN = -1;
    public static final int STATE_CHARGING_ALLOWED = 0;
    public static final int STATE_SUSPENDED = 1;
    /** 两个节点状态互相矛盾（例如驱动只重置了其中一个），需要重新写入。 */
    public static final int STATE_MIXED = 2;

    public static final class Result {
        public final boolean ok;
        public final String output;

        Result(boolean ok, String output) {
            this.ok = ok;
            this.output = output;
        }
    }

    private ChargeController() {
    }

    public static Result checkRoot() {
        Result r = run("id");
        boolean root = r.ok && r.output.contains("uid=0");
        return new Result(root, r.output);
    }

    /** 停止充电：关充电使能 + 切断输入电源。 */
    public static Result suspend() {
        return run("echo 0 > " + NODE_ENABLE + " 2>/dev/null"
                + "; echo 1 > " + NODE_SUSPEND
                + "; " + readScript());
    }

    /** 恢复充电。 */
    public static Result resume() {
        return run("echo 1 > " + NODE_ENABLE + " 2>/dev/null"
                + "; echo 0 > " + NODE_SUSPEND
                + "; " + readScript());
    }

    /** 读取当前实际状态：两个节点任一处于“停充”即视为停充。 */
    public static int state() {
        Result r = run(readScript());
        if (!r.ok) {
            return STATE_UNKNOWN;
        }
        String[] parts = r.output.trim().split("\\s+");
        if (parts.length < 2) {
            return STATE_UNKNOWN;
        }
        String enable = parts[0];
        String suspend = parts[1];
        boolean e0 = "0".equals(enable);
        boolean e1 = "1".equals(enable);
        boolean s0 = "0".equals(suspend);
        boolean s1 = "1".equals(suspend);
        if (e0 && s1) {
            return STATE_SUSPENDED;
        }
        if (e1 && s0) {
            return STATE_CHARGING_ALLOWED;
        }
        if (e0 || e1 || s0 || s1) {
            return STATE_MIXED;
        }
        return STATE_UNKNOWN;
    }

    /** 一次 su 同时读两个节点，输出 "enable suspend"。 */
    private static String readScript() {
        return "e=$(cat " + NODE_ENABLE + " 2>/dev/null || echo x); s=$(cat " + NODE_SUSPEND
                + " 2>/dev/null || echo x); echo \"$e $s\"";
    }

    private static Result run(String cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            if (!p.waitFor(8, TimeUnit.SECONDS)) {
                p.destroy();
                return new Result(false, "root 命令超时（可能尚未授权 root）");
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            String out = sb.toString().trim();
            boolean ok = p.exitValue() == 0;
            return new Result(ok, out.isEmpty() ? ("exit=" + p.exitValue()) : out);
        } catch (Exception e) {
            return new Result(false, "执行 su 失败: " + e.getMessage());
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }
}
