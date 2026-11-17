package top.dynesshely.chargesentinel.core;

/** 充电通断控制的抽象，便于替换实现（不同机型 / 测试替身）。 */
public interface ChargeControl {

    /** 只读当前状态。 */
    ChargeState read();

    /**
     * 幂等地把充电设成期望状态：已经到位就不写节点，否则重写。
     * 返回写入后的实际状态，供调用方判断是否成功。
     */
    ChargeState apply(boolean suspend);

    /** 当前进程是否拿到 root。 */
    boolean hasRoot();
}
