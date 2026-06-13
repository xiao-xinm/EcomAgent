package com.smartcs.agent.common.enums;

/**
 * Shared dialog state names used by gateway, agent core, and workbench.
 */
public enum DialogState {
    INIT,
    INTENT_RECOGNIZED,
    SLOT_FILLING,
    READY_TO_EXECUTE,
    WAITING_CONFIRM,
    EXECUTING,
    EXECUTED,
    COMPLETED,
    HUMAN_REVIEW,
    HUMAN_TAKEOVER,
    TIMEOUT,
    CLOSED
}
