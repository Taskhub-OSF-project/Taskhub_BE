# 🔐 Admin Setup — TaskHub

Tài liệu này mô tả cách TaskHub tự động tạo tài khoản admin mặc định khi khởi động lần đầu, và cách truy cập Admin Panel.

## Tài khoản Admin mặc định

Khi backend khởi động lần đầu, `AdminDataSeeder` sẽ tự động tạo 1 tài khoản admin nếu email chưa tồn tại (idempotent — chạy lại nhiều lần vẫn an toàn).

| Field    | Default                       |
|----------|-------------------------------|
| Email    | `admin@taskhub.com`           |
| Password | `Admin@TaskHub2026`           |
| FullName | `TaskHub Admin`               |
| Role     | `ADMIN`                       |

Khi tạo xong, server in log cảnh báo:

```
[SEEDER] ============================================================
[SEEDER] Default admin account created:
[SEEDER]   Email:    admin@taskhub.com
[SEEDER]   Password: Admin@TaskHub2026
[SEEDER]   Login URL (FE): /login (use the admin credentials above)
[SEEDER]   Admin URL  (FE): /admin  (hidden route — no nav link)
[SEEDER] CHANGE THE PASSWORD IMMEDIATELY IN PRODUCTION.
[SEEDER] ============================================================
```

## Cấu hình qua Environment Variables

Có thể override toàn bộ thông tin admin qua env (xem `.env.example`):

```env
APP_ADMIN_ENABLED=true
APP_ADMIN_EMAIL=admin@taskhub.com
APP_ADMIN_PASSWORD=Admin@TaskHub2026
APP_ADMIN_FULL_NAME=TaskHub Admin
```

> ⚠️ **BẮT BUỘC đổi `APP_ADMIN_PASSWORD` trước khi deploy production!**
> Set `APP_ADMIN_ENABLED=false` để tắt hoàn toàn auto-seed (ví dụ khi DB đã có admin thật).

## Truy cập Admin Panel (FE)

| URL                              | Mô tả                                              |
|----------------------------------|-----------------------------------------------------|
| `http://localhost:5173/login`    | Đăng nhập với email/password admin                 |
| `http://localhost:5173/admin`    | Sau khi đăng nhập, tự động redirect tới đây        |
| `http://localhost:5173/admin/users`     | Quản lý người dùng                            |
| `http://localhost:5173/admin/analytics` | Thống kê hệ thống                            |

### Tại sao link `/admin` không lộ trên UI?

TaskHub cố ý **không hiển thị** link admin trên navbar, sidebar, footer hay bất kỳ đâu trong UI để giảm nguy cơ:

- Kẻ tấn công dò URL `/admin` rồi brute-force password
- Người dùng thường vô tình đăng nhập sai tài khoản admin

Cách duy nhất để vào `/admin`:
1. Đăng nhập với tài khoản có `role === "ADMIN"` → `login.tsx` tự navigate
2. Hoặc gõ URL trực tiếp `http://localhost:5173/admin`

### Guard bảo mật

`src/routes/admin.tsx` có `beforeLoad` chặn mọi user không phải ADMIN:

```ts
export const Route = createFileRoute("/admin")({
  beforeLoad: () => {
    if (typeof window === "undefined") return;
    const u = getCachedUser();
    if (!u) throw redirect({ to: "/login" });
    if (u.role !== "ADMIN") throw redirect({ to: "/hirer" });
  },
  component: AdminLayout,
});
```

→ User thường cố truy cập `/admin` sẽ bị redirect về `/hirer` (hoặc `/login` nếu chưa đăng nhập).

## Đổi mật khẩu admin sau khi deploy

Sau khi tạo admin lần đầu, **nên đổi mật khẩu** bằng 1 trong 2 cách:

1. **SQL** (cập nhật password hash trực tiếp — hash từ `BCryptPasswordEncoder`).
2. **API** (nếu đã có endpoint đổi password cho user) — gọi API đổi password với tài khoản admin vừa tạo.

Ví dụ SQL (PostgreSQL):

```sql
-- Password mới phải được hash bằng BCrypt trước khi lưu
UPDATE users
SET password = '$2a$10$<bcrypt-hash-cua-mat-khau-moi>'
WHERE email = 'admin@taskhub.com';
```

Hoặc đơn giản nhất: xoá admin cũ trong DB, set biến môi trường mới, restart backend → seeder tạo lại với password mới.

```sql
DELETE FROM users WHERE email = 'admin@taskhub.com';
```

```env
APP_ADMIN_PASSWORD=MatKhauMoiRatManh@2026
```

```bash
./mvnw spring-boot:run
```
