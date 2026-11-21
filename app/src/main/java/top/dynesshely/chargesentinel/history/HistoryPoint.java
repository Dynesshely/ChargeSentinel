package top.dynesshely.chargesentinel.history;

/** 一次电量采样。 */
public final class HistoryPoint {

    public final long timestamp;
    public final int level;
    public final boolean plugged;
    public final boolean suspended;

    public HistoryPoint(long timestamp, int level, boolean plugged, boolean suspended) {
        this.timestamp = timestamp;
        this.level = level;
        this.plugged = plugged;
        this.suspended = suspended;
    }
}
