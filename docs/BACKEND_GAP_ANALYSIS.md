# TaskHub Backend — Việc cần làm

> Checklist đối chiếu BE với MVP FE. Đánh dấu `[x]` khi xong.

---

## Phase 1 — Ví, escrow, state machine

- [x] Entity `WalletTransaction` (`top_up`, `escrow_deduction`, `refund`) + `balanceAfter`
- [x] `GET /api/wallet/transactions` — lịch sử giao dịch
- [x] Phí nền tảng **5%** khi fund escrow (trừ `budget + fee`, ghi ledger)
- [x] Tách fund và publish: fund → `ESCROW_FUNDED`, `POST /api/tasks/{id}/publish` → `ACTIVE`
- [x] `approveSubmission` tự gọi `releaseEscrow` (chuyển tiền sang ví student)
- [x] `POST /api/escrow/refund/{taskId}` — hoàn tiền khi dispute → revision / re-publish
- [x] Sửa state machine: thêm `SUBMITTED → IN_PROGRESS` (revision), thoát được `DISPUTED`
- [x] Sửa `approveSubmission` + `fundEscrow` + `requestRevision` dùng `validateTransition` nhất quán

---

## Phase 2 — Task & application

- [ ] Field `category` trên `Task` + DTO
- [ ] `PATCH /api/tasks/{id}` — sửa task khi `DRAFT`
- [ ] `DELETE /api/tasks/{id}`
- [ ] `GET /api/tasks/mine?status=` — lọc dashboard theo state
- [ ] Embed applicants trong `TaskResponse` hoặc document contract gọi kèm
- [ ] `GET /api/applications/my-applied-tasks` — task student đã apply (chưa được chọn)
- [ ] Profile student: `university`, `major` trên `User` + hiển thị khi apply/review

---

## Phase 3 — Submission & revision

- [ ] `POST /api/files/upload` — multipart, trả URL
- [ ] Nhiều file mỗi lần nộp (`submittedFiles[]`)
- [ ] Model `SubmissionAIResult` (JSON): `overallStatus`, `criteriaResults[]`, `canSubmit`, `evaluatedAt`
- [ ] Lưu `submissionAIResult` trên task — **ghi một lần**, không ghi đè
- [ ] `POST /api/submissions/task/{id}/precheck` — AI trước khi nộp
- [ ] `canSubmit`: chặn 0%; logic partial ≤ 50% failed vẫn được nộp
- [ ] Bắt buộc đã precheck trước khi `submit`
- [ ] `GET /api/submissions/task/{id}/latest` — kết quả AI cho hirer/student
- [ ] Entity `RevisionRequest` + `revisionHistory`, `latestRevision`, `revisionCount`
- [ ] Giới hạn **tối đa 3** lần revision
- [ ] `criteriaStatus[]` + `locked: true` khi criterion đã `met`
- [ ] Request revision: clear submission + `submissionAIResult`; generate AI suggestions
- [ ] Revision dựa trên `submissionAIResult`, không đánh `FAILED` thủ công theo ID

---

## Phase 4 — Dispute

- [ ] `POST /api/tasks/{id}/dispute` — body: `reason`, `description`
- [ ] Lưu `disputeReason`, `disputeDescription`, `aiReport` (structured) trên task
- [ ] `GET /api/tasks/{id}/dispute/report` — JSON `AIReport` (assessments, recommendation 3 chiều)
- [ ] `POST /api/tasks/{id}/dispute/resolve` — release payment / revision+refund / escalate
- [ ] Refund + task về `ACTIVE` khi recommendation = request revision

---

## Phase 5 — AI (khớp contract FE)

- [ ] Tích hợp LLM thật (thay heuristic trong `AiValidationService`)
- [ ] Lock fail: trả `CriteriaValidationDetail[]` đầy đủ trong response (❌ + suggestion từng dòng)
- [ ] `POST /api/tasks/{id}/criteria/auto-improve` — rewrite criteria fail một lần
- [ ] `POST /api/tasks/{id}/criteria/extract` — trích criteria từ PDF/Excel/image
- [ ] `POST /api/tasks/{id}/validate` — lint passive (optional, real-time FE gọi debounce)
- [ ] `GET` hoặc `POST` progress validation — live AI khi student làm bài
- [ ] Dispute AI: recommendation `Release payment` | `Request revision` | `Escalate`
- [ ] Bỏ validate trùng lúc lock (controller + service)
- [ ] Bỏ `Thread.sleep(1800)` trong `TaskController`

---

## Phase 6 — Auth, bảo mật, hạ tầng

- [ ] Refresh token, logout
- [ ] Đổi mật khẩu, quên mật khẩu, verify email (nếu cần production)
- [ ] Dual role / chọn role (nếu giữ hành vi FE prototype)
- [ ] `PATCH /api/users/me` — cập nhật profile
- [ ] JWT secret + CORS production
- [ ] `@PreAuthorize` theo role trên controller
- [ ] Rate limit, audit log (tùy mức production)
- [ ] Unit / integration tests
- [ ] Flyway / Liquibase
- [ ] Chọn **một** DB (PostgreSQL hoặc SQL Server), đồng bộ README + `pom.xml` + `application.yml`
- [ ] Docker, CI/CD
- [ ] Xóa dead code `entity/ValidationResult.java`
- [ ] Tích hợp FE: gọi API thật, adapter `ApiResponse<T>`
