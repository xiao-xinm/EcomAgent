package com.smartcs.agent.common.enums;

/**
 * Risk levels are ordered from low to high.
 */
public enum RiskLevel {
    L0(0),
    L1(1),
    L2(2),
    L3(3);

    private final int order;

    RiskLevel(int order) {
        this.order = order;
    }

    public int order() {
        return order;
    }

    public boolean higherThan(RiskLevel other) {
        return other == null || order > other.order;
    }
}
