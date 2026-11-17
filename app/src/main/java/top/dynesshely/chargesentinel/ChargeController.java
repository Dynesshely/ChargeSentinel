package top.dynesshely.chargesentinel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * 通过 root 写入 sysfs 控制充电通断。
 *
 * OnePlus 7 Pro（SM8150，Android 14）上实测可用的节点：
 *   /sys/class/power_supply/battery/input_suspend       1=切断输入电源，0=恢复
 *   /sys/class/power_supply/battery/mmi_charging_enable 0=停充，1=允许充电（存在则一并写入）
 * 注意 charge_control_limit 在该内核上是只读的（-r--r--r--），不能作为控制手段。
 */
public final class ChargeController {

    public static final String NODE_SUSPEND = "/sys/class/power_supply/battery/input_suspend";
    public static final String NODE_CHARGE_ENABLE = "/sys/class/power_supply/battery/mmi_charging_enable";

    public static final int STATE_UNKNOWN = -1;
    public static final int STATE_CHARGING_ALLOWED = 0;
    public static final int STATE_SUSPENDED = 1;

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

    /** 停止充电：切断输入电源。返回值带上写入后的实际节点值。 */
    public static Result suspend() {
        return run("echo 1 > " + NODE_SUSPEND
                + "; echo 0 > " + NODE_CHARGE_ENABLE + " 2>/dev/null"
                + "; cat " + NODE_SUSPEND);
    }

    /** 恢复充电。 */
    public static Result resume() {
        return run("echo 0 > " + NODE_SUSPEND
                + "; echo 1 > " + NODE_CHARGE_ENABLE + " 2>/dev/null"
                + "; cat " + NODE_SUSPEND);
    }

    /** 读取当前是否处于“被本应用切断”的状态。 */
    public static int state() {
        Result r = run("cat " + NODE_SUSPEND + " 2>/dev/null");
        if (!r.ok) {
            return STATE_UNKNOWN;
        }
        String v = r.output.trim();
        if (v.startsWith("1")) {
            return STATE_SUSPENDED;
        }
        if (v.startsWith("0")) {
            return STATE_CHARGING_ALLOWED;
        }
        return STATE_UNKNOWN;
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
