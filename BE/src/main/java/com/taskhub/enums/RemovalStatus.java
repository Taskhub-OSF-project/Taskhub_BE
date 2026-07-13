package com.taskhub.enums;

public enum RemovalStatus {
    PENDING("Chờ duyệt"),
    APPROVED("Đã duyệt"),
    REJECTED("Từ chối");

    private final String label;

    RemovalStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
