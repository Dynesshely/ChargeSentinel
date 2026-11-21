package top.dynesshely.chargesentinel.history;

/**
 * 采样节流：每 5 分钟必记一条；电量变化时最多每 30 秒记一条，避免曲线抖动刷库。
 * 纯逻辑，便于单元测试。
 */
public final class SampleGate {

    public static final long INTERVAL_MS = 5L * 60 * 1000;
    public static final long MIN_GAP_MS = 30_000L;

    private SampleGate() {
    }

    public static boolean shouldRecord(long now, long lastAt, int level, int lastLevel) {
        if (lastLevel < 0) {
            return true;
        }
        if (now - lastAt >= INTERVAL_MS) {
            return true;
        }
        return level != lastLevel && now - lastAt >= MIN_GAP_MS;
    }
}
