# TaskHub admin setup

TaskHub không cho phép người dùng tự đăng ký vai trò `ADMIN`. Tài khoản quản
trị production chỉ được tạo bằng quy trình bootstrap một lần, sau đó bootstrap
phải được tắt và bí mật phải được gỡ khỏi Lambda.

## Trạng thái production (2026-07-22)

- [x] Đã tạo tài khoản admin riêng:
      `lehuynh.org.2310.gov+taskhubadmin@gmail.com`.
- [x] Tài khoản được đánh dấu email đã xác minh và có role `ADMIN`.
- [x] API đăng nhập đã xác nhận `ROLE=ADMIN`, `OTP_REQUIRED=true` và
      `OTP_PURPOSE=LOGIN`.
- [x] OTP được gửi qua Resend tới Gmail alias trên.
- [x] Bootstrap đã được tắt lại sau khi tạo tài khoản.
- [x] Lambda không còn giữ biến email hoặc mật khẩu admin; chỉ còn
      `APP_ADMIN_ENABLED=false`.
- [ ] Chủ tài khoản đăng nhập và đổi mật khẩu tạm ngay sau lần truy cập đầu.

Không ghi mật khẩu admin vào Git, tài liệu, ảnh chụp, frontend, biến `VITE_*`
hoặc log. Nếu mật khẩu tạm bị chia sẻ ngoài ý muốn, hãy đổi ngay trong phần cài
đặt tài khoản.

## Đăng nhập Admin Panel

1. Mở `https://taskhubvn.com/login`.
2. Nhập email admin và mật khẩu.
3. Nhập OTP gửi tới Gmail.
4. Tài khoản role `ADMIN` sẽ được chuyển tới `https://taskhubvn.com/admin`.
5. Vào phần cài đặt và đổi mật khẩu tạm.

Các trang quản trị chính:

- `/admin`: tổng quan.
- `/admin/users`: quản lý người dùng.
- `/admin/analytics`: thống kê hệ thống.
- `/admin/removal-requests`: duyệt tranh chấp/yêu cầu gỡ.

Mọi endpoint quản trị đều được backend kiểm tra role `ADMIN`; việc biết URL
không cấp quyền truy cập.

## Quy trình tạo admin mới trong tương lai

1. Dùng một email riêng có thể nhận OTP.
2. Sinh mật khẩu tạm ngẫu nhiên mạnh; không dùng mật khẩu mặc định.
3. Chỉ bật `APP_ADMIN_ENABLED=true` trong một lần khởi động có kiểm soát.
4. `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD` và `APP_ADMIN_FULL_NAME` phải được
   truyền bằng secret/`NoEcho`, không ghi cứng vào repository.
5. Xác nhận API đăng nhập trả role `ADMIN`.
6. Tắt lại bootstrap và gỡ toàn bộ email/mật khẩu bootstrap khỏi Lambda.
7. Yêu cầu chủ tài khoản đổi mật khẩu ngay lần đăng nhập đầu tiên.

`AdminDataSeeder` là idempotent: nếu email đã tồn tại, nó không ghi đè tài
khoản hoặc mật khẩu hiện có. Seeder cũng từ chối mật khẩu mặc định cũ
`Admin@TaskHub2026`.
