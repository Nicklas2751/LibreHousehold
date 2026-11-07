package eu.wiegandt.librehousehold.realtime.model;

/**
 * The fixed set of events on the household stream. The wire name is the message {@code name} in
 * {@code api/asyncapi.yml} and is sent as the SSE {@code event:} field; it never comes from user
 * input (RT6).
 */
public enum SignalType {
    TASK_CREATED("task.created"),
    TASK_UPDATED("task.updated"),
    TASK_DELETED("task.deleted"),
    TASK_DONE("task.done"),
    EXPENSE_CREATED("expense.created"),
    EXPENSE_UPDATED("expense.updated"),
    EXPENSE_DELETED("expense.deleted"),
    REIMBURSEMENT_CREATED("reimbursement.created"),
    REIMBURSEMENT_UPDATED("reimbursement.updated"),
    REIMBURSEMENT_DELETED("reimbursement.deleted"),
    CATEGORY_CREATED("category.created"),
    CATEGORY_UPDATED("category.updated"),
    CATEGORY_DELETED("category.deleted"),
    MEMBER_JOINED("member.joined"),
    MEMBER_UPDATED("member.updated"),
    MEMBER_REMOVED("member.removed"),
    HOUSEHOLD_UPDATED("household.updated"),
    HOUSEHOLD_DELETED("household.deleted"),
    SESSION_TERMINATED("session.terminated");

    private final String wireName;

    SignalType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
