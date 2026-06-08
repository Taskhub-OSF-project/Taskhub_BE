# TaskHub Backend — Phase 3 Plan: Submission & Revision

## 0. Mục tiêu Phase 3

Phase 3 tập trung hoàn thiện luồng **student nộp bài**, **AI precheck trước khi nộp**, **lưu kết quả đánh giá**, và **hirer yêu cầu revision có kiểm soát**.

Nguyên tắc chính:

- Không phá state machine Phase 1.
- Không sửa Phase 2 trừ khi cần để compile.
- Không xóa lịch sử submission/AI result cũ.
- Không ghi đè dữ liệu quan trọng nếu dữ liệu đó cần dùng cho dispute sau này.
- Mỗi giai đoạn làm nhỏ, test được, rồi mới chuyển sang giai đoạn tiếp theo.

State machine liên quan Phase 3:

```text
ACTIVE -> IN_PROGRESS
IN_PROGRESS -> SUBMITTED
SUBMITTED -> IN_PROGRESS    // request revision
SUBMITTED -> COMPLETED      // approve submission
SUBMITTED -> DISPUTED       // dispute
```

---

## 1. Phase 3.1 — File Upload với Supabase Storage

### Mục tiêu

Bổ sung API upload file để student/hirer có thể upload file lên cloud storage, backend trả về metadata và URL/path để dùng cho submission sau này.

### Scope

- `POST /api/files/upload`
- Multipart upload.
- Lưu file lên Supabase Storage.
- Trả về `FileUploadResponse`.
- Chưa bắt buộc gắn file vào Submission ở giai đoạn này.

### Thiết kế storage

Provider chọn:

```text
Supabase Storage
```

Bucket:

```text
taskhub-submissions
```

Bucket nên để:

```text
private
```

Backend dùng secret/service role key để upload. Không expose key ra frontend.

### Config

Đọc config từ env/application config:

```yaml
supabase:
  url: ${SUPABASE_URL}
  service-role-key: ${SUPABASE_SERVICE_ROLE_KEY}
  storage:
    bucket: ${SUPABASE_STORAGE_BUCKET:taskhub-submissions}
```

### Endpoint

```http
POST /api/files/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

Form-data:

```text
file: MultipartFile
taskId: Long
```

### Path lưu file

```text
submissions/task-{taskId}/user-{currentUserId}/{timestamp}-{sanitizedOriginalFileName}
```

### Validate

- User phải login.
- File không được rỗng.
- Max size: 20MB.
- Allowed content types:
  - `application/pdf`
  - `image/png`
  - `image/jpeg`
  - `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
  - `application/zip`
- Sanitize file name.
- Không hard-code secret.

### Response mẫu

```json
{
  "fileName": "report.pdf",
  "path": "submissions/task-12/user-5/20260601-report.pdf",
  "url": "signed-url-or-null",
  "contentType": "application/pdf",
  "size": 123456,
  "uploadedAt": "2026-06-01T10:00:00"
}
```

### File/code dự kiến

- `FileController` hoặc `FileUploadController`
- `FileStorageService`
- `SupabaseStorageService`
- `FileUploadResponse`
- Supabase config properties nếu cần

### Test tối thiểu

- Upload PDF thành công.
- Upload PNG/JPG thành công.
- Upload file rỗng bị chặn.
- Upload file quá size bị chặn.
- Upload content type không hỗ trợ bị chặn.
- Kiểm tra file xuất hiện trong Supabase Storage bucket.

---

## 2. Phase 3.2 — Submitted Files cho Submission

### Mục tiêu

Cho phép mỗi lần student submit có nhiều file.

### Scope

- `submittedFiles[]`
- Submission lưu metadata file.
- Submit request nhận danh sách file đã upload.
- Không upload trực tiếp trong submit; submit chỉ nhận file metadata/path đã upload từ Phase 3.1.

### Thiết kế đề xuất

Nên lưu submitted files dạng structured metadata.

Option MVP:

```text
Lưu JSON trong Submission
```

Option production hơn:
```

Khuyến nghị:

```text
Nếu muốn nhanh cho MVP: JSON metadata.

```

### File metadata mẫu

```json
{
  "fileName": "report.pdf",
  "path": "submissions/task-12/user-5/report.pdf",
  "url": "signed-url-or-null",
  "contentType": "application/pdf",
  "size": 123456,
  "uploadedAt": "2026-06-01T10:00:00"
}
```

### Business rules

- Student chỉ submit task được assigned cho mình.
- Task phải ở `IN_PROGRESS`.
- Mỗi submission có thể có nhiều file.
- Không xóa file cũ khi submit/revision, vì cần lịch sử.

### Test tối thiểu

- Submit 1 file thành công.
- Submit nhiều file thành công.
- Submit không file bị chặn nếu business yêu cầu bắt buộc file.
- Student không assigned không được submit.

---

## 3. Phase 3.3 — SubmissionAIResult + Precheck

### Mục tiêu

Trước khi submit thật, student phải gọi AI precheck để backend đánh giá bài nộp có đạt acceptance criteria hay không.

### Endpoint

```http
POST /api/submissions/task/{id}/precheck
Authorization: Bearer <STUDENT_TOKEN>
Content-Type: application/json
```

Request đề xuất:

```json
{
  "submittedFiles": [
    {
      "fileName": "report.pdf",
      "path": "submissions/task-12/user-5/report.pdf",
      "contentType": "application/pdf",
      "size": 123456
    }
  ],
  "note": "Em đã hoàn thành theo yêu cầu."
}
```

### Model `SubmissionAIResult`

```json
{
  "overallStatus": "PASSED | PARTIAL | FAILED",
  "criteriaResults": [
    {
      "index": 0,
      "criteria": "Nộp 1 file PNG...",
      "status": "MET | PARTIAL | FAILED",
      "locked": true,
      "evidence": "Found PNG file with correct resolution",
      "suggestion": null
    }
  ],
  "canSubmit": true,
  "evaluatedAt": "2026-06-01T10:00:00"
}
```

### Rule `canSubmit`

Chốt rule:

```text
canSubmit = false nếu:
- 0 criterion MET
- hoặc FAILED > 50% tổng criteria

canSubmit = true nếu:
- có ít nhất 1 criterion MET
- và FAILED <= 50% tổng criteria

PARTIAL không tính là FAILED.
```

### Lưu AI result

Khuyến nghị:

```text
AI result nên gắn với submission/precheck attempt.
Task có thể giữ latestSubmissionAIResult để FE lấy nhanh.
```

Nếu vẫn giữ checklist “lưu submissionAIResult trên Task”:

```text
Lưu latest result trên Task, nhưng không xóa lịch sử cũ nếu đã có precheck/submission history.
Không ghi đè result cũ nếu result đó đã gắn với một submission đã submit.
```

### Test tối thiểu

- Precheck tạo AI result.
- 0% met -> `canSubmit=false`.
- Failed > 50% -> `canSubmit=false`.
- Failed <= 50% và có ít nhất 1 MET -> `canSubmit=true`.
- Response có `criteriaResults[]`, `locked`, `evaluatedAt`.

---

## 4. Phase 3.4 — Bắt buộc Precheck trước Submit + Latest Result

### Mục tiêu

Student không được submit trực tiếp nếu chưa có precheck hợp lệ.

### Endpoint cập nhật

```http
POST /api/submissions/task/{id}/submit
```

Rule mới:

- Task phải ở `IN_PROGRESS`.
- Student phải là assigned student.
- Phải có latest precheck của chính student cho task này.
- Latest precheck phải `canSubmit=true`.
- Nếu chưa precheck: chặn.
- Nếu precheck `canSubmit=false`: chặn.
- Submit thành công thì task chuyển `IN_PROGRESS -> SUBMITTED`.

### Endpoint latest

```http
GET /api/submissions/task/{id}/latest
Authorization: Bearer <token>
```

Quyền xem:

- Hirer owner được xem.
- Assigned student được xem.
- User khác không được xem.

Response trả:

- Latest submission.
- Latest/precheck AI result.
- Submitted files.
- Task status.
- Revision info nếu có.

### Test tối thiểu

- Submit không precheck -> fail.
- Submit có precheck nhưng `canSubmit=false` -> fail.
- Submit có precheck `canSubmit=true` -> success.
- Latest endpoint hirer xem được.
- Latest endpoint assigned student xem được.
- User lạ không xem được.

---

## 5. Phase 3.5 — RevisionRequest + Revision History

### Mục tiêu

Hirer có thể yêu cầu student sửa bài dựa trên kết quả AI, có lịch sử revision và giới hạn số lần revision.

### Entity `RevisionRequest`

Field đề xuất:

```text
id
task
submission
requestedBy
student
revisionNumber
reason
description
aiSuggestionsJson
createdAt
```

Task có thể có:

```text
revisionCount
latestRevision
```

Không nhất thiết lưu `revisionHistory` trực tiếp trong Task nếu có thể query từ `RevisionRequestRepository`.

### Rule revision

- Chỉ hirer owner được request revision.
- Task phải ở `SUBMITTED`.
- Revision dựa trên `submissionAIResult`.
- Không đánh FAILED thủ công theo ID.
- Max revision: 3.
- Nếu `revisionCount >= 3`: không cho request revision nữa, gợi ý dispute/escalate.
- Request revision chuyển task `SUBMITTED -> IN_PROGRESS`.
- Không hard delete submission cũ.
- Không xóa AI result cũ.
- Chỉ clear latest/current submission state nếu cần để student precheck/submit lại.
- Generate AI suggestions từ criteria result FAILED/PARTIAL.

### `criteriaStatus[] + locked`

Rule:

```text
MET -> locked=true
PARTIAL/FAILED -> locked=false
```

Revision chỉ tập trung vào criteria chưa đạt:

```text
FAILED/PARTIAL
```

### Test tối thiểu

- Request revision thành công khi task `SUBMITTED`.
- Task chuyển về `IN_PROGRESS`.
- `revisionCount` tăng.
- Max 3 revision.
- Revision thứ 4 bị chặn.
- Revision suggestions lấy từ AI result.
- Lịch sử submission/AI result cũ không bị xóa.

---

## 6. Phase 3.6 — Docs + Integration Test

### Mục tiêu

Cập nhật tài liệu và test end-to-end flow Phase 3.

### Docs cần cập nhật

- `docs/API_REFERENCE.md`
- `docs/BACKEND_GAP_ANALYSIS.md`
- `docs/DATABASE_SETUP.md`
- Tạo/cập nhật `docs/PHASE3_IMPLEMENTATION_REPORT.md`

### E2E flow cần test

```text
Hirer tạo task
-> lock
-> fund escrow
-> publish ACTIVE
-> hirer accept student application
-> task IN_PROGRESS
-> student upload files
-> student precheck
-> student submit
-> task SUBMITTED
-> hirer request revision
-> task IN_PROGRESS
-> student precheck lại
-> student submit lại
-> hirer approve
-> task COMPLETED
```

### Test tối thiểu

- File upload.
- Precheck.
- Submit requires precheck.
- Latest submission.
- Revision max 3.
- State machine không bị phá.
- Phase 1/2 tests vẫn pass.

---

## 7. Thứ tự triển khai đề xuất

```text
Phase 3.1: Supabase file upload
Phase 3.2: submittedFiles[] trong Submission
Phase 3.3: SubmissionAIResult + precheck
Phase 3.4: submit bắt buộc precheck + latest endpoint
Phase 3.5: RevisionRequest + revision history + max 3
Phase 3.6: docs + integration tests
```

Không nên cho AI agent làm full Phase 3 một lần vì dễ phá state machine và mất kiểm soát diff.
