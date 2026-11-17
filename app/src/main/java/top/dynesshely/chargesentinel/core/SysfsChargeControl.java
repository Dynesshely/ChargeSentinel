package top.dynesshely.chargesentinel.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * 通过 root 操作 sysfs 节点控制充电。
 *
 * 已验证的节点（OnePlus 7 Pro / SM8150 / Android 14）：
 *   /sys/class/power_supply/battery/mmi_charging_enable  0 = 停充，1 = 允许充电（主控制，写入后能保持）
 *   /sys/class/power_supply/battery/input_suspend        1 = 切断输入，0 = 恢复（立即生效，但驱动会周期性重置）
 *
 * 每次调用只 fork 一次 su：脚本内部先读、按需写、再回读，返回最终的 "enable suspend"。
 */
public final class SysfsChargeControl implements ChargeControl {

    public static final String NODE_ENABLE = "/sys/class/power_supply/battery/mmi_charging_enable";
    public static final String NODE_SUSPEND = "/sys/class/power_supply/battery/input_suspend";

    private static final long TIMEOUT_SECONDS = 8;

    private static final class ShellResult {
        final boolean ok;
        final String output;

        ShellResult(boolean ok, String output) {
            this.ok = ok;
            this.output = output;
        }
    }

    @Override
    public ChargeState read() {
        return parse(run("echo \"$(cat " + NODE_ENABLE + " 2>/dev/null) $(cat " + NODE_SUSPEND + " 2>/dev/null)\""));
    }

    @Override
    public ChargeState apply(boolean suspend) {
        String m = NODE_ENABLE;
        String s = NODE_SUSPEND;
        String write = suspend
                ? "if [ \"$m\" != 0 ]; then echo 0 > $M 2>/dev/null; fi;"
                + " if [ \"$s\" != 1 ]; then echo 1 > $S 2>/dev/null; fi;"
                : "if [ \"$m\" != 1 ]; then echo 1 > $M 2>/dev/null; fi;"
                + " if [ \"$s\" != 0 ]; then echo 0 > $S 2>/dev/null; fi;";
        String cmd = "M=" + m + "; S=" + s + ";"
                + " m=$(cat $M 2>/dev/null); s=$(cat $S 2>/dev/null);"
                + " " + write
                + " echo \"$(cat $M 2>/dev/null) $(cat $S 2>/dev/null)\"";
        return parse(run(cmd));
    }

    @Override
    public boolean hasRoot() {
        ShellResult r = run("id");
        return r.ok && r.output.contains("uid=0");
    }

    private ChargeState parse(ShellResult r) {
        if (!r.ok) {
            return ChargeState.UNKNOWN;
        }
        String[] parts = r.output.trim().split("\\s+");
        if (parts.length < 2) {
            return ChargeState.UNKNOWN;
        }
        boolean e0 = "0".equals(parts[0]);
        boolean e1 = "1".equals(parts[0]);
        boolean s0 = "0".equals(parts[1]);
        boolean s1 = "1".equals(parts[1]);
        if (e0 && s1) {
            return ChargeState.SUSPENDED;
        }
        if (e1 && s0) {
            return ChargeState.ALLOWED;
        }
        if (e0 || e1 || s0 || s1) {
            return ChargeState.MIXED;
        }
        return ChargeState.UNKNOWN;
    }

    private ShellResult run(String cmd) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy();
                return new ShellResult(false, "root command timed out");
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            String out = sb.toString().trim();
            boolean ok = process.exitValue() == 0;
            return new ShellResult(ok, out.isEmpty() ? ("exit=" + process.exitValue()) : out);
        } catch (Exception e) {
            return new ShellResult(false, String.valueOf(e.getMessage()));
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
