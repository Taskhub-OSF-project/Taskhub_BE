package com.taskhub.enums;

public enum RemovalReason {
    DUPLICATE("Trùng lặp công việc"),
    NO_LONGER_NEEDED("Không còn cần thiết"),
    BUDGET_ISSUES("Vấn đề về ngân sách"),
    FOUND_BETTER_FREELANCER("Đã tìm được freelancer khác"),
    PROJECT_CANCELLED("Dự án bị hủy"),
    MISPOSTED("Đăng sai thông tin"),
    OTHER("Lý do khác");

    private final String label;

    RemovalReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
