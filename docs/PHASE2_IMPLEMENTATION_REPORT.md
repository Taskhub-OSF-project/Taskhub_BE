# Báo cáo tổng kết Phase 2 — Task & Application

> Thời điểm: 2026-05-31

Phase 2 bổ sung các năng lực chính cho Task & Application:
- Task category.
- Update/Delete task khi `DRAFT`.
- Filter dashboard theo `status`.
- Hiển thị applicants trong `TaskResponse` cho hirer.
- API “my applied tasks” cho student.
- Profile student: `university`, `major`.

- Mục tiêu: bổ sung category cho Task, cho phép sửa/xóa task khi DRAFT, bổ sung filter dashboard và danh sách task đã apply, đồng thời hiển thị profile sinh viên khi review ứng tuyển.
- Module bị ảnh hưởng: Task, Application, User/Profile, DTO, Docs, Tests.

## 2. Checklist Phase 2  hoàn thành

| Hạng mục | Trạng thái | File liên quan | Ghi chú |
|---|---|---|---|
| Field `category` trên Task + DTO | Done | `BE/src/main/java/com/taskhub/entity/Task.java`, `BE/src/main/java/com/taskhub/dto/request/CreateTaskRequest.java`, `BE/src/main/java/com/taskhub/dto/request/PatchTaskRequest.java`, `BE/src/main/java/com/taskhub/dto/response/TaskResponse.java` | String nullable, trim input, max 100 |
| PATCH `/api/tasks/{id}` | Done | `BE/src/main/java/com/taskhub/controller/TaskController.java`, `BE/src/main/java/com/taskhub/service/TaskService.java`, `BE/src/main/java/com/taskhub/dto/request/PatchTaskRequest.java` | Chỉ DRAFT, chỉ HIRER owner, không đổi status |
| DELETE `/api/tasks/{id}` | Done | `BE/src/main/java/com/taskhub/controller/TaskController.java`, `BE/src/main/java/com/taskhub/service/TaskService.java`, `BE/src/main/java/com/taskhub/repository/TaskApplicationRepository.java` | Chỉ DRAFT, chặn nếu có application |
| GET `/api/tasks/mine?status=` | Done | `BE/src/main/java/com/taskhub/controller/TaskController.java`, `BE/src/main/java/com/taskhub/service/TaskService.java`, `BE/src/main/java/com/taskhub/repository/TaskRepository.java` | Status optional, parse TaskStatus |
| Embed applicants trong `TaskResponse` | Done | `BE/src/main/java/com/taskhub/dto/response/TaskResponse.java`, `BE/src/main/java/com/taskhub/service/TaskService.java`, `BE/src/main/java/com/taskhub/dto/response/ApplicationResponse.java` | Chỉ populate cho hirer owner |
| GET `/api/applications/my-applied-tasks` | Done | `BE/src/main/java/com/taskhub/controller/ApplicationController.java`, `BE/src/main/java/com/taskhub/service/ApplicationService.java`, `BE/src/main/java/com/taskhub/repository/TaskApplicationRepository.java` | Chỉ PENDING |
| Profile student: `university`, `major` | Done | `BE/src/main/java/com/taskhub/entity/User.java`, `BE/src/main/java/com/taskhub/dto/request/RegisterRequest.java`, `BE/src/main/java/com/taskhub/dto/response/ApplicationResponse.java`, `BE/src/main/java/com/taskhub/service/AuthService.java` | Optional, max 100 |

## 3. Thay đổi theo từng nhóm file

### A. Entity / Database

- Entity được thêm field:
  - `Task`: thêm `category` (String, nullable, max 100).
  - `User`: thêm `university`, `major` (String, nullable, max 100).
- SQL migration/manual SQL cần chạy:
  - SQL Server (đã ghi trong `docs/DATABASE_SETUP.md`):
    ```sql
    ALTER TABLE tasks ADD category NVARCHAR(100) NULL;
    ALTER TABLE [users] ADD university NVARCHAR(100) NULL;
    ALTER TABLE [users] ADD major NVARCHAR(100) NULL;
    ```
  - PostgreSQL/H2: các schema sẽ do JPA tự tạo ở profile dev; nếu prod Postgres thì cần tự tạo SQL tương đương (Cần xác nhận).

### B. DTO / Response contract

- Request DTO:
  - `CreateTaskRequest`: thêm `category` (max 100).
  - `PatchTaskRequest`: mới, cho phép patch `title`, `description`, `budget`, `deadline`, `category`, `acceptanceCriteria`.
  - `RegisterRequest`: thêm `university`, `major` (optional, max 100).
- Response DTO:
  - `TaskResponse`: thêm `category`, `applicants`.
  - `ApplicationResponse`: thêm `studentUniversity`, `studentMajor`.
- Breaking API: không có breaking thay đổi; các field mới là optional/additive.

### C. Controller / API endpoint

- `PATCH /api/tasks/{id}`
  - Ai được gọi: HIRER owner (JWT).
  - Body: `PatchTaskRequest`.
  - Response: `TaskResponse`.
  - Rules: chỉ DRAFT, không đổi status, chỉ cập nhật field có trong body.
- `DELETE /api/tasks/{id}`
  - Ai được gọi: HIRER owner (JWT).
  - Body: none.
  - Response: `ApiResponse<Void>`.
  - Rules: chỉ DRAFT, chặn nếu đã có application.
- `GET /api/tasks/mine?status=`
  - Ai được gọi: JWT.
  - Query: `status` optional (TaskStatus).
  - Response: `TaskResponse[]`.
  - Rules: HIRER -> task do mình tạo; STUDENT -> task assigned cho mình; status invalid -> lỗi rõ ràng.
- `GET /api/applications/my-applied-tasks`
  - Ai được gọi: STUDENT (JWT).
  - Query: none.
  - Response: `TaskResponse[]`.
  - Rules: chỉ trả task từ application PENDING.

### D. Service / Business logic

### API cập nhật field mới
- `POST /api/tasks`:
  - Bổ sung `category` trong `CreateTaskRequest`.
- `POST /api/auth/register`:
  - Bổ sung `university`, `major` trong `RegisterRequest`.

### E. Repository

- `TaskRepository`:
  - `findByHirerId`, `findByHirerIdAndStatus`, `findByAssignedToId`, `findByAssignedToIdAndStatus`, `findByStatusIn` (phục vụ /mine, /available).
- `TaskApplicationRepository`:
  - `findByStudentIdAndStatus` (my-applied-tasks),
  - `existsByTaskId` (chặn delete),
  - `existsByTaskIdAndStudentId`, `findByTaskIdAndStudentId` (apply rule).

### F. Docs

- `docs/API_REFERENCE.md`: bổ sung field category, PATCH/DELETE task, mine?status, applicants, my-applied-tasks, university/major trong register, mô tả response.
- `docs/BACKEND_GAP_ANALYSIS.md`: đánh dấu xong Phase 2.
- `docs/DATABASE_SETUP.md`: thêm manual SQL update cho Phase 2 (SQL Server).

### G. Tests

- File test mới: `BE/src/test/java/com/taskhub/Phase2TaskApplicationTests.java`.
- Test case cover:
  - PATCH DRAFT success
  - PATCH non-DRAFT fail
  - DELETE DRAFT success (no application)
  - DELETE DRAFT fail (có application)
  - mine?status filter
  - my-applied-tasks chỉ PENDING
- Kết quả test:  Phase2TaskApplicationTests pass 6/6 trong IntelliJ.

## 4. Những bổ sung ngoài yêu cầu ban đầu

- Test file `Phase2TaskApplicationTests`:
  - Lý do: đảm bảo rule Phase 2 hoạt động đúng.
  - Nên giữ: Có.
  - Rủi ro: thấp, chỉ cần DB H2 hoặc config test đúng.
- Update `docs/DATABASE_SETUP.md` (manual SQL):
  - Lý do: cần hướng dẫn update schema khi không dùng migration.
  - Nên giữ: Có.
  - Rủi ro: nếu dùng DB khác (Postgres) cần bổ sung SQL tương đương.
- Thêm `university/major` vào register và trim input:
  - Lý do: profile student, tránh lưu giá trị rỗng.
  - Nên giữ: Có.
  - Rủi ro: nếu FE chưa gửi 2 field này thì vẫn ok vì optional.
- Có sự xuất hiện file build/IDE trong working tree (`BE/target/**`, `.idea/**`):
  - Lý do: artifact build/IDE, không liên quan feature.
  - Nên giữ: Không cần commit.
  - Rủi ro: làm nhiễu git diff.

## 5. Rủi ro / điểm cần review thủ công

- Wallet/escrow/state machine Phase 1: không thay đổi trong source (Cần xác nhận).
- TaskStatus/ApplicationStatus enum: không thay đổi trong source (Cần xác nhận).
- Expose password/User entity: không thay; response DTO không có password.
- JSON recursion: đã map DTO; không trả entity trực tiếp.
- Lazy loading: `TaskService.toResponse` truy cập `hirer`, `assignedTo`, `acceptanceCriteria`, `applicants` có thể bị LazyInitialization nếu Open-Session-In-View bị tắt (Cần xác nhận cấu hình).
- Breaking API: thêm field mới là additive; chưa thay đổi field bắt buộc.
- SQL khác nhau Postgres/SQL Server: tài liệu chỉ có SQL Server; nếu chạy Postgres cần bổ sung SQL tương đương (Cần xác nhận).

## 6. Hướng dẫn test thủ công bằng API

- Create task có `category` (POST `/api/tasks`).
- PATCH DRAFT thành công (PATCH `/api/tasks/{id}`).
- PATCH non-DRAFT bị lỗi (PATCH `/api/tasks/{id}`).
- DELETE DRAFT chưa có application thành công (DELETE `/api/tasks/{id}`).
- DELETE DRAFT có application bị chặn (DELETE `/api/tasks/{id}`).
- GET `/api/tasks/mine?status=DRAFT`.
- GET `/api/applications/my-applied-tasks` (chỉ PENDING).
- Hirer xem task có `applicants` + `studentUniversity`, `studentMajor` (GET `/api/tasks/{id}` hoặc `/api/tasks/mine`).

## 7. Kết luận

- Tiến độ Phase 2: 100% theo checklist (kiểm chứng cần xác nhận qua test).
- Chuyển sang Phase 3: có thể bắt đầu sau khi chạy test và xác nhận DB migration.
- Điều kiện trước khi merge/commit:
  - Chạy `mvn test` và đảm bảo pass.
  - Xác nhận SQL migration (SQL Server/Postgres nếu có).
  - Không commit build artifact (`BE/target/**`) và file IDE (`.idea/**`).

SQL Server:
```sql
ALTER TABLE tasks ADD category NVARCHAR(100) NULL;
ALTER TABLE [users] ADD university NVARCHAR(100) NULL;
ALTER TABLE [users] ADD major NVARCHAR(100) NULL;
```

PostgreSQL:
```sql
ALTER TABLE tasks ADD COLUMN category VARCHAR(100) NULL;
ALTER TABLE users ADD COLUMN university VARCHAR(100) NULL;
ALTER TABLE users ADD COLUMN major VARCHAR(100) NULL;
```