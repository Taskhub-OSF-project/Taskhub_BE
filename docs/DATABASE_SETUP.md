# Cấu hình database TaskHub

## Lỗi thường gặp

```text
Login failed for user 'sa'
```

→ Spring Boot **không đăng nhập được SQL Server** (sai mật khẩu, SQL Server chưa chạy, hoặc user `sa` bị tắt).

---

## Cách 1 — Chạy local nhanh (khuyến nghị): profile `dev` + H2

Mặc định project dùng **H2 in-memory**, không cần cài SQL Server.

1. **Maven reload** (sau khi thêm dependency H2)
2. Run `TaskHubApplication` — không cần set gì thêm
3. API: http://localhost:8080  
4. H2 Console (xem bảng): http://localhost:8080/h2-console  
   - JDBC URL: `jdbc:h2:mem:taskhub`  
   - User: `sa`  
   - Password: *(để trống)*

**Lưu ý ID:** Mọi bảng dùng `id` kiểu **BIGINT** tự tăng `1, 2, 3...`. H2 in-memory tạo lại schema mỗi lần restart app (profile `dev`).

**IntelliJ:** Run Configuration → Active profiles: `dev` (hoặc để trống — mặc định đã là `dev`).

---

## Cách 2 — SQL Server thật: profile `sqlserver`

1. Tạo database `TaskhubData` trên SQL Server  
2. Bật SQL Server Authentication, đặt mật khẩu cho `sa` (hoặc user khác)  
3. Run với:

**Environment variables:**

```text
SPRING_PROFILES_ACTIVE=sqlserver
SPRING_DATASOURCE_PASSWORD=<mat-khau-sa-cua-ban>
```

**IntelliJ:** Run Configuration → Active profiles: `sqlserver`  
→ Environment: `SPRING_DATASOURCE_PASSWORD=YourPassword123`

4. Tùy chọn URL:

```text
SPRING_DATASOURCE_URL=jdbc:sqlserver://localhost:1433;databaseName=TaskhubData;encrypt=true;trustServerCertificate=true
```

---

## Phase 2 schema update (manual SQL)

Run once if you are not using Flyway/Liquibase:

```sql
ALTER TABLE tasks ADD category NVARCHAR(100) NULL;
ALTER TABLE [users] ADD university NVARCHAR(100) NULL;
ALTER TABLE [users] ADD major NVARCHAR(100) NULL;
```

---

## Tóm tắt

| Profile      | Database   | Khi nào dùng        |
|-------------|------------|---------------------|
| `dev` (mặc định) | H2 memory  | Dev local, demo     |
| `sqlserver` | SQL Server | Production / team DB |
