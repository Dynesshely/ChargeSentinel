package top.dynesshely.chargesentinel.history;

/** 电量曲线上的一次状态切换（停止充电 / 恢复充电）。 */
public final class HistoryEvent {

    public static final String TYPE_SUSPEND = "suspend";
    public static final String TYPE_RESUME = "resume";

    public final long timestamp;
    public final String type;
    public final int level;

    public HistoryEvent(long timestamp, String type, int level) {
        this.timestamp = timestamp;
        this.type = type;
        this.level = level;
    }

    public boolean isSuspend() {
        return TYPE_SUSPEND.equals(type);
    }
}
