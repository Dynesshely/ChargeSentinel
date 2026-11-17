package top.dynesshely.chargesentinel.core;

/** 充电控制节点的实际状态。 */
public enum ChargeState {
    /** 允许充电：enable=1 且 suspend=0。 */
    ALLOWED,
    /** 已停止充电：enable=0 且 suspend=1。 */
    SUSPENDED,
    /** 两个节点互相矛盾（例如驱动只重置了其中一个），需要重新写入。 */
    MIXED,
    /** 节点不存在或读取失败。 */
    UNKNOWN;

    public boolean isSuspended() {
        return this == SUSPENDED;
    }

    public boolean isKnown() {
        return this == ALLOWED || this == SUSPENDED;
    }
}
