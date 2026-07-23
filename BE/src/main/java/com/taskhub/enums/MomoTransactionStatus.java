package com.taskhub.enums;

/** Trạng thái của một lệnh MoMo */
public enum MomoTransactionStatus {
    PENDING,   // Đã tạo lệnh, chờ user thanh toán
    SUCCESS,   // MoMo callback thành công, ví đã được cập nhật
    FAILED,    // Thanh toán thất bại hoặc bị hủy
    CANCELLED  // User hủy thanh toán
}
