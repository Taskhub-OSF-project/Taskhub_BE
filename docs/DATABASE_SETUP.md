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

## Phase 3.5 schema update (manual SQL)

Run once if you are not using Flyway/Liquibase:

```sql
IF COL_LENGTH('tasks', 'revision_count') IS NULL
BEGIN
    ALTER TABLE tasks
    ADD revision_count INT NOT NULL
        CONSTRAINT DF_tasks_revision_count DEFAULT 0;
END;

IF OBJECT_ID('revision_requests', 'U') IS NULL
BEGIN
    CREATE TABLE revision_requests (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        task_id BIGINT NOT NULL,
        submission_id BIGINT NULL,
        requested_by_id BIGINT NOT NULL,
        student_id BIGINT NOT NULL,
        revision_number INT NOT NULL,
        reason NVARCHAR(MAX) NOT NULL,
        description NVARCHAR(MAX) NULL,
        ai_suggestions_json NVARCHAR(MAX) NOT NULL,
        created_at DATETIME2 NULL,

        CONSTRAINT FK_revision_requests_task
            FOREIGN KEY (task_id) REFERENCES tasks(id),
        CONSTRAINT FK_revision_requests_submission
            FOREIGN KEY (submission_id) REFERENCES submissions(id),
        CONSTRAINT FK_revision_requests_requested_by
            FOREIGN KEY (requested_by_id) REFERENCES [users](id),
        CONSTRAINT FK_revision_requests_student
            FOREIGN KEY (student_id) REFERENCES [users](id)
    );
END;
```

`revision_requests.ai_suggestions_json` stores the PARTIAL/FAILED criteria snapshot before
`tasks.submission_ai_result_json` is cleared for the next student precheck.

---

## Tóm tắt

| Profile      | Database   | Khi nào dùng        |
|-------------|------------|---------------------|
| `dev` (mặc định) | H2 memory  | Dev local, demo     |
| `sqlserver` | SQL Server | Production / team DB |
