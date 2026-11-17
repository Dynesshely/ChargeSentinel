package top.dynesshely.chargesentinel.core;

/**
 * 充电迟滞策略（纯逻辑，无 Android 依赖，便于单元测试）。
 *
 *          stopAt
 *   ────────┬────────  ≥ stopAt  → 停充
 *           │
 *           │  保持原状
 *           │
 *   ────────┴────────  ≤ resumeAt → 恢复充电
 *        resumeAt
 */
public final class ChargePolicy {

    public static final int STOP_MIN = 50;
    public static final int STOP_MAX = 100;
    public static final int RESUME_MIN = 20;
    public static final int RESUME_MAX = 95;
    /** 停止与恢复之间至少留出的电量差，避免来回抖动。 */
    public static final int MIN_GAP = 5;

    public static final int DEFAULT_STOP = 80;
    public static final int DEFAULT_RESUME = 50;

    public enum Decision {
        SUSPEND,
        RESUME,
        HOLD
    }

    private ChargePolicy() {
    }

    public static Decision decide(int level, int stopAt, int resumeAt, boolean currentlySuspended) {
        if (level >= stopAt) {
            return Decision.SUSPEND;
        }
        if (level <= resumeAt) {
            return Decision.RESUME;
        }
        return Decision.HOLD;
    }

    /** 把决策落成“是否应该停充”。HOLD 时沿用当前状态。 */
    public static boolean wantSuspended(Decision decision, boolean currentlySuspended) {
        switch (decision) {
            case SUSPEND:
                return true;
            case RESUME:
                return false;
            default:
                return currentlySuspended;
        }
    }

    public static int clampStop(int stop) {
        return Math.max(STOP_MIN, Math.min(STOP_MAX, stop));
    }

    /** 恢复阈值必须严格低于停止阈值，且至少相差 MIN_GAP。 */
    public static int clampResume(int resume, int stop) {
        int upper = Math.min(RESUME_MAX, clampStop(stop) - MIN_GAP);
        return Math.max(RESUME_MIN, Math.min(upper, resume));
    }
}
