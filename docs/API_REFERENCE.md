# TaskHub Backend — API Reference

> Base URL: `http://localhost:8080`  
> Swagger UI: http://localhost:8080/swagger-ui/index.html  
> OpenAPI JSON: http://localhost:8080/v3/api-docs  

---

## Mục lục

1. [Tổng quan](#1-tổng-quan)
2. [Xác thực (JWT)](#2-xác-thực-jwt)
3. [Định dạng response & lỗi](#3-định-dạng-response--lỗi)
4. [State machine task](#4-state-machine-task)
5. [Auth](#5-auth)
6. [Wallet](#6-wallet)
7. [Tasks](#7-tasks)
8. [Applications](#8-applications)
9. [Submissions](#9-submissions)
10. [Files](#10-files)
11. [Escrow](#11-escrow)
12. [Luồng gợi ý (Hirer / Student)](#12-luồng-gợi-ý-hirer--student)

---

## 1. Tổng quan

| Mục | Giá trị |
|-----|---------|
| Framework | Spring Boot 3.3, Java 21 |
| Auth | JWT Bearer, stateless |
| DB local (mặc định) | H2 in-memory, profile `dev` |
| DB production (tùy chọn) | SQL Server, profile `sqlserver` |
| Phí nền tảng escrow | **5%** trên `budget` |
| Tiền tệ | `BigDecimal` (VND trên FE) |
| ID | **Số tự tăng** `1, 2, 3, ...` (`Long`, `GenerationType.IDENTITY`) |

**Role:** `HIRER` | `STUDENT` — mỗi tài khoản một role.

> API vẫn yêu cầu **JWT** — ID số chỉ để dễ test trong Swagger/Postman, không thay thế xác thực.

---

## 2. Xác thực (JWT)

### API public (không cần token)

- `POST /api/auth/register`
- `POST /api/auth/login`
- Swagger, H2 console, `/error`

### API protected

Gửi header:

```http
Authorization: Bearer <token>
```

Lấy token từ `POST /api/auth/login` hoặc `/register` → field **`data.token`**.

### Swagger UI

1. Gọi **login/register**
2. Copy `data.token`
3. Bấm **Authorize** (góc phải trên) → dán token → Authorize
4. Gọi các API còn lại

---

## 3. Định dạng response & lỗi

### Wrapper chuẩn

```json
{
  "success": true,
  "message": "Optional message",
  "errorCode": null,
  "data": { }
}
```

| Field | Mô tả |
|-------|--------|
| `success` | `true` / `false` |
| `message` | Thông báo (khi có) |
| `errorCode` | Mã lỗi nghiệp vụ (khi có) |
| `data` | Payload hoặc `null` |

### HTTP status thường gặp

| Code | Ý nghĩa |
|------|---------|
| 200 | Thành công |
| 400 | Bad request / validation |
| 401 | Thiếu hoặc token không hợp lệ (Spring Security) |
| 403 | Không đủ quyền role / không phải owner |
| 402 | `INSUFFICIENT_WALLET` — ví không đủ |
| 404 | Không tìm thấy |
| 500 | Lỗi server |

### Mã lỗi nghiệp vụ (`errorCode`)

| errorCode | HTTP | Khi nào |
|-----------|------|---------|
| `INSUFFICIENT_WALLET` | 402 | Tạo task / fund escrow nhưng số dư < budget + 5% |
| `INVALID_CRITERIA` | 400 | Tiêu chí mơ hồ, quá ngắn, không đo được |

Body lỗi ví dụ:

```json
{
  "success": false,
  "message": "So du vi khong du...",
  "errorCode": "INSUFFICIENT_WALLET",
  "data": {
    "sufficient": false,
    "budget": 1000000,
    "platformFee": 50000,
    "requiredTotal": 1050000,
    "currentBalance": 200000,
    "shortfall": 850000,
    "action": "TOP_UP",
    "resumeFlow": "CREATE_TASK"
  }
}
```

---

## 4. State machine task

```
DRAFT → LOCKED → ESCROW_FUNDED → ACTIVE → IN_PROGRESS → SUBMITTED → COMPLETED
                              ↑                              ↓    ↘ DISPUTED
                              └──────── refund (dispute) ────┘         ↓
                                                                    IN_PROGRESS
```

| Status | Mô tả ngắn |
|--------|------------|
| `DRAFT` | Mới tạo, criteria có thể sửa (trước lock) |
| `LOCKED` | Đã khóa criteria, chờ nạp escrow |
| `ESCROW_FUNDED` | Đã trừ ví (budget + 5%), chờ publish |
| `ACTIVE` | Đã publish, student apply được |
| `IN_PROGRESS` | Đã chọn student, đang làm |
| `SUBMITTED` | Student đã nộp bài |
| `COMPLETED` | Hirer duyệt, escrow release cho student |
| `DISPUTED` | Tranh chấp |

Không được nhảy bước — BE validate `canTransitionTo`.

---

## 5. Auth

Base path: `/api/auth` — **Public**

### POST `/api/auth/register`

Đăng ký tài khoản mới.

**Body:**

```json
{
  "email": "hirer@example.com",
  "password": "password123",
  "fullName": "Nguyen Van A",
  "role": "HIRER",
  "university": "HCMUT",
  "major": "Computer Science"
}
```

| Field | Bắt buộc | Ghi chú |
|-------|-----------|---------|
| `email` | ✓ | Email hợp lệ, unique |
| `password` | ✓ | Tối thiểu 6 ký tự |
| `fullName` | ✓ | |
| `role` | ✓ | `HIRER` hoặc `STUDENT` |
| `university` | | Optional, max 100 |
| `major` | | Optional, max 100 |

**Response `data` (AuthResponse):**

```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "userId": 1,
  "email": "hirer@example.com",
  "fullName": "Nguyen Van A",
  "role": "HIRER"
}
```

---

### POST `/api/auth/login`

**Body:**

```json
{
  "email": "hirer@example.com",
  "password": "password123"
}
```

**Response:** giống register (`AuthResponse`).

---

## 6. Wallet

Base path: `/api/wallet` — **JWT required** — Role: thường **HIRER** (nạp tiền, kiểm tra trước tạo task)

### GET `/api/wallet/balance`

Số dư ví user hiện tại.

**Response `data`:**

```json
{
  "balance": 5000000
}
```

---

### GET `/api/wallet/readiness/create-task`

Kiểm tra **trước khi tạo task**: ví có đủ `budget + 5%` không.

**Query:**

| Param | Kiểu | Bắt buộc |
|-------|------|----------|
| `budget` | number | ✓ |

**Response `data` (WalletReadinessResponse):**

```json
{
  "sufficient": true,
  "budget": 1000000,
  "platformFee": 50000,
  "requiredTotal": 1050000,
  "currentBalance": 5000000,
  "shortfall": 0,
  "action": null,
  "resumeFlow": null
}
```

Khi `sufficient: false` → FE redirect nạp tiền (`action: "TOP_UP"`, `resumeFlow: "CREATE_TASK"`).

---

### POST `/api/wallet/deposit`

Nạp tiền mock vào ví (dev/demo).

**Query:**

| Param | Kiểu | Bắt buộc |
|-------|------|----------|
| `amount` | number | ✓ (> 0) |

**Ví dụ:** `POST /api/wallet/deposit?amount=5000000`

**Response `data`:** `{ "balance": <sau nạp> }`

Ghi ledger `top_up`.

---

### GET `/api/wallet/transactions`

Lịch sử giao dịch ví (mới nhất trước).

**Response `data`:** mảng `WalletTransactionResponse`

```json
[
  {
    "id": 1,
    "type": "top_up",
    "amount": 5000000,
    "balanceAfter": 5000000,
    "taskId": null,
    "createdAt": "2026-05-23T10:00:00"
  }
]
```

**`type`:** `top_up` | `escrow_deduction` | `refund` | `escrow_release`

---

## 7. Tasks

Base path: `/api/tasks` — **JWT required**

### POST `/api/tasks`

Tạo task — **HIRER only**.

**Điều kiện:**

- Ví đủ `budget + 5%` — nếu không → `402 INSUFFICIENT_WALLET`
- Mọi `acceptanceCriteria` pass AI validation — nếu không → `400 INVALID_CRITERIA`

**Body (CreateTaskRequest):**

```json
{
  "title": "Thiết kế poster sự kiện",
  "description": "Cần poster A3 cho buổi workshop...",
  "category": "Design",
  "budget": 1000000,
  "deadline": "2026-06-30T23:59:00",
  "acceptanceCriteria": [
    "Giao 1 file PNG 1920x1080 px, sRGB, dung luong toi da 5 MB",
    "Logo truong doc ro o 100% zoom, khong bi cat"
  ]
}
```

**Response `data` (TaskResponse):** task `status: DRAFT`

---

### GET `/api/tasks/{id}`

Chi tiết một task.

**Response `data` (TaskResponse):**

| Field | Kiểu |
|-------|------|
| `id` | number (1, 2, 3…) |
| `title`, `description`, `category` | string |
| `budget` | number |
| `deadline` | ISO datetime |
| `status` | TaskStatus enum |
| `hirerId`, `hirerName` | |
| `assignedToId`, `assignedToName` | nullable |
| `acceptanceCriteria` | `[{ id, description, status }]` |
| `applicants` | `ApplicationResponse[]` (hirer own tasks only) |
| `createdAt` | ISO datetime |

`status` criterion: `PENDING` | `PASSED` | `FAILED`

---

### GET `/api/tasks/mine`

- **HIRER:** tất cả task do mình tạo
- **STUDENT:** task đã được assign (`assignedToId` = mình)

**Query (optional):**

| Param | Kiểu | Ghi chú |
|-------|------|--------|
| `status` | TaskStatus | Lọc theo trạng thái; bỏ trống = tất cả |

**Response `data`:** `TaskResponse[]`

Ghi chu: chi HIRER owner moi co field `applicants`.

---

### PATCH `/api/tasks/{id}`

Cập nhật task khi **`DRAFT`** — **HIRER owner only**.

**Body (PatchTaskRequest):**

```json
{
  "title": "Cap nhat tieu de",
  "description": "Cap nhat mo ta",
  "budget": 1200000,
  "deadline": "2026-07-15T23:59:00",
  "category": "Design",
  "acceptanceCriteria": [
    "Giao file PNG 1920x1080",
    "Logo ro 100% zoom"
  ]
}
```

Chỉ update các field có mặt trong body. Không đổi `status`.

---

### DELETE `/api/tasks/{id}`

Xóa task khi **`DRAFT`** — **HIRER owner only**.

Nếu task có application → trả lỗi rõ ràng, không cascade.

---

### GET `/api/tasks/available`

Task ở trạng thái **`ACTIVE`** — cho student browse/apply.

**Response `data`:** `TaskResponse[]`

---

### POST `/api/tasks/validate-criteria`

Lint tiêu chí **khi đang soạn** (chưa cần task id).

**Body:**

```json
{
  "acceptanceCriteria": [
    "Poster dep",
    "Giao file PNG 1920x1080"
  ]
}
```

**Response `data`:**

```json
{
  "valid": false,
  "message": "1 criterion(s) are too vague...",
  "details": [
    {
      "index": 0,
      "criteria": "Poster dep",
      "valid": false,
      "issue": "SUBJECTIVE: Uses vague words...",
      "suggestion": "..."
    }
  ]
}
```

---

### POST `/api/tasks/{id}/validate`

Giống `validate-criteria` nhưng lấy criteria từ task đã lưu — **owner hirer**.

---

### POST `/api/tasks/criteria/extract`

AI gợi ý tiêu chí từ file brief — **multipart**.

**Content-Type:** `multipart/form-data`

| Part | Kiểu |
|------|------|
| `file` | PDF, XLSX, PNG, JPG, DOCX… (max 15 MB) |

**Response `data` (CriteriaExtractResponse):**

```json
{
  "fileName": "brief.pdf",
  "detectedType": "PDF",
  "suggestions": [
    {
      "text": "Giao 1 file PDF...",
      "rationale": "Trich tu tai lieu PDF..."
    }
  ]
}
```

---

### POST `/api/tasks/{id}/lock`

Khóa criteria + chuyển `DRAFT` → `LOCKED` — **HIRER owner**.

Chạy AI validation; nếu fail **không** lock.

**Response `data` (ValidationPhaseResponse):**

Khi **fail:**

```json
{
  "validationPhase": "failed",
  "blockReason": "...",
  "canProceed": false,
  "details": [ /* CriteriaValidationDetail[] */ ],
  "suggestions": [ { "issueReason": "...", "suggestion": "..." } ]
}
```

Khi **pass:**

```json
{
  "validationPhase": "passed",
  "canProceed": true,
  "message": "All criteria meet standards",
  "taskResponse": { /* TaskResponse LOCKED */ }
}
```

---

### POST `/api/tasks/{id}/publish`

`ESCROW_FUNDED` → `ACTIVE` — **HIRER owner**.

(Gọi sau `POST /api/escrow/fund/{taskId}`.)

---

### POST `/api/tasks/{id}/complete`

Chuyển thẳng sang `COMPLETED` (ít dùng; thường dùng approve submission).

---

### POST `/api/tasks/{id}/revision`

Compatibility endpoint for requesting revision. Preferred endpoint is
`POST /api/submissions/task/{taskId}/revision`.

**Body (RevisionRequest):**

```json
{
  "reason": "Bai nop con thieu section CTA",
  "description": "Vui long bo sung phan call-to-action va kiem tra lai mau chu dao."
}
```

Rules and response are identical to the preferred submissions endpoint. Hirer cannot pass failed
criteria IDs manually; backend derives revision suggestions from latest `SubmissionAIResult`.

---

### POST `/api/tasks/{id}/dispute`

Mở tranh chấp → `DISPUTED`.

---

## 8. Applications

Base path: `/api/applications` — **JWT required**

### POST `/api/applications/task/{taskId}`

Student apply — task phải **`ACTIVE`**.

**Body (optional):**

```json
{
  "coverLetter": "Em co kinh nghiem thiet ke..."
}
```

**Response `data` (ApplicationResponse):**

```json
{
  "id": 1,
  "taskId": 1,
  "studentId": 1,
  "studentName": "...",
  "studentUniversity": "...",
  "studentMajor": "...",
  "coverLetter": "...",
  "status": "PENDING",
  "appliedAt": "..."
}
```

`status`: `PENDING` | `ACCEPTED` | `REJECTED`

---

### POST `/api/applications/{id}/accept`

Hirer chọn student → task `IN_PROGRESS`, assign student, reject các đơn khác.

---

### GET `/api/applications/task/{taskId}`

Danh sách đơn ứng tuyển của một task.

---

### GET `/api/applications/mine`

Đơn apply của student đang đăng nhập.

---

### GET `/api/applications/my-applied-tasks`

Danh sách task student đã apply nhưng chưa được chọn (`PENDING`).

**Response `data`:** `TaskResponse[]`

---

## 9. Submissions

Base path: `/api/submissions` — **JWT required**

### POST `/api/submissions/task/{taskId}/precheck`

Student chay precheck truoc khi submit that. API danh gia `submittedFiles[]` + `notes` voi acceptance criteria bang heuristic co cau truc, khong goi LLM that.

**Rules:**

- Chi `STUDENT`
- Task phai ton tai va dang `IN_PROGRESS`
- Student phai la `assignedTo`
- `submittedFiles[]` bat buoc va validate nhu API submit Phase 3.2
- Precheck khong tao `Submission` va khong doi task status
- Latest result duoc luu tren Task lam current submit gate
- Sau khi hirer request revision, backend clear current precheck; student phai precheck lai truoc submit tiep theo

**Request:**

```json
{
  "submittedFiles": [
    {
      "fileName": "report.pdf",
      "path": "submissions/task-12/user-5/report.pdf",
      "url": null,
      "contentType": "application/pdf",
      "size": 123456,
      "uploadedAt": "2026-06-04T20:44:31"
    }
  ],
  "notes": "Em da hoan thanh theo yeu cau."
}
```

**Response `data` (SubmissionAIResult):**

```json
{
  "overallStatus": "PARTIAL",
  "criteriaResults": [
    {
      "index": 0,
      "criteria": "Nop 1 file PNG landing page 1920x1080",
      "status": "MET",
      "locked": true,
      "evidence": "Matched keywords: png, landing, page, 1920, 1080",
      "suggestion": null
    },
    {
      "index": 1,
      "criteria": "Co section hero va CTA",
      "status": "PARTIAL",
      "locked": false,
      "evidence": "Matched keywords: hero",
      "suggestion": "Bo sung bang chung hoac notes lien quan den: section, cta"
    }
  ],
  "canSubmit": true,
  "evaluatedAt": "2026-06-04T21:00:00"
}
```

**Heuristic status:**

- `MET`: keyword match ratio `>= 0.6`
- `PARTIAL`: keyword match ratio `> 0` va `< 0.6`
- `FAILED`: khong co keyword/evidence match
- `locked = true` chi khi status la `MET`

**Rule `canSubmit`:**

- `false` neu `0` criterion `MET`
- `false` neu `FAILED > 50%` tong criteria
- `true` neu co it nhat `1` criterion `MET` va `FAILED <= 50%`
- `PARTIAL` khong tinh la `FAILED`

---
### POST `/api/submissions/task/{taskId}`
**Phase 3.4 precheck requirement:**

- Student phai goi `POST /api/submissions/task/{taskId}/precheck` truoc khi submit.
- Latest precheck phai thuoc chinh assigned student cua task.
- Latest precheck phai co `canSubmit = true`.
- `submittedFiles[].path` khi submit phai trung voi path list da precheck. Backend sort path list de so sanh.
- Chua precheck -> `400`: `Precheck is required before submission`.
- Precheck `canSubmit=false` -> `400`: `Latest precheck does not allow submission`.
- Files doi sau precheck -> `400`: `Submitted files changed after precheck. Please run precheck again.`.

Student nộp bài — task `IN_PROGRESS`, đúng assignee.

**Body mới (Phase 3.2, ưu tiên `submittedFiles`):**

```json
{
  "submittedFiles": [
    {
      "fileName": "work.zip",
      "path": "submissions/task-1/user-2/1780580671131-work.zip",
      "url": null,
      "contentType": "application/zip",
      "size": 123456,
      "uploadedAt": "2026-06-04T20:44:31"
    }
  ],
  "notes": "Em đã nộp file theo yêu cầu."
}
```

`fileUrl` là legacy/optional để không phá API cũ:

```json
{
  "fileUrl": "https://storage.example.com/work.zip",
  "notes": "Em da nop file PDF 5 trang..."
}
```

Nếu có `submittedFiles[]`, backend ưu tiên `submittedFiles[]`. Nếu không có cả `submittedFiles[]` và `fileUrl`, API trả `400`.

Phase 3.4: submit nen gui submittedFiles[] da precheck. ileUrl chi con la legacy field; neu khong co path list trung voi precheck thi backend se yeu cau precheck lai.

**Validate `submittedFiles[]`:**

- `fileName` không blank
- `path` không blank, không chứa `../`
- `path` bắt đầu bằng `submissions/task-{taskId}/`
- `path` thuộc user hiện tại (`/user-{currentUserId}/`)
- `contentType`: `application/pdf`, `image/png`, `image/jpeg`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `application/zip`
- `size > 0` và `<= 20MB`

**Quy tắc AI (heuristic):**

- `aiScore == 0` → **400**, không cho nộp
- `aiScore < 70` → vẫn nộp được, `aiReport` cảnh báo

**Response `data` (SubmissionResponse):**

```json
{
  "id": 1,
  "taskId": 1,
  "studentId": 2,
  "studentName": "Nguyen Van A",
  "fileUrl": null,
  "submittedFiles": [
    {
      "fileName": "work.zip",
      "path": "submissions/task-1/user-2/1780580671131-work.zip",
      "url": null,
      "contentType": "application/zip",
      "size": 123456,
      "uploadedAt": "2026-06-04T20:44:31"
    }
  ],
  "notes": "Em đã nộp file theo yêu cầu.",
  "aiScore": 75,
  "aiReport": "Submission meets criteria.",
  "isRevision": false,
  "submittedAt": "2026-06-04T20:50:00"
}
```

Task → `SUBMITTED`.

---

### POST `/api/submissions/task/{taskId}/revision`

Hirer owner yeu cau student sua bai dua tren latest `SubmissionAIResult`.

**Rules:**

- Chi `HIRER`
- Hirer phai la owner cua task
- Task phai dang `SUBMITTED`
- Task phai co latest `Submission`
- Task phai co current `submissionAIResult`
- Backend tu generate `aiSuggestions` tu criteria status `PARTIAL`/`FAILED`
- `MET` criteria co `locked = true` va khong nam trong revision suggestions
- Toi da 3 lan revision; lan thu 4 tra `400`
- Neu tat ca criteria `MET`, tra `400`: `All criteria are met. Revision is not recommended.`

**Request:**

```json
{
  "reason": "Bai nop con thieu section CTA",
  "description": "Vui long bo sung phan call-to-action va kiem tra lai mau chu dao."
}
```

**Success effects:**

- Tao record `RevisionRequest`
- `revisionNumber = revisionCount + 1`
- Tang `Task.revisionCount`
- Task `SUBMITTED` -> `IN_PROGRESS`
- Khong xoa `Submission` cu hay file Supabase
- Luu `aiSuggestionsJson` vao revision request truoc khi clear current `submissionAIResult`/precheck gate tren task
- Student phai precheck lai truoc submit tiep theo

**Response `data` (RevisionRequestResponse):**

```json
{
  "id": 1,
  "taskId": 1,
  "submissionId": 10,
  "requestedById": 1,
  "studentId": 2,
  "revisionNumber": 1,
  "reason": "Bai nop con thieu section CTA",
  "description": "Vui long bo sung phan call-to-action va kiem tra lai mau chu dao.",
  "aiSuggestions": [
    {
      "index": 2,
      "criteria": "Mau chu dao xanh duong xuat hien tren button, heading, background",
      "status": "PARTIAL",
      "suggestion": "Bo sung bang chung ve button, heading, background mau xanh duong."
    }
  ],
  "createdAt": "2026-06-05T10:30:00"
}
```

**Max revision error:**

```json
{
  "success": false,
  "message": "Maximum revision requests reached. Please dispute or resolve the task."
}
```

---

### GET `/api/submissions/task/{taskId}/revisions`

Revision history cua task.

**Quyen xem:**

- Hirer owner cua task duoc xem
- Assigned student cua task duoc xem
- User khac -> `403`

**Response `data`:** `RevisionRequestResponse[]`

---

### GET `/api/submissions/task/{taskId}/latest`

Tra latest submission, current precheck AI result, revision count va latest/history revision cua task.

**Quyen xem:**

- Hirer owner cua task duoc xem.
- Assigned student cua task duoc xem.
- User khac -> `403`.

**Response `data` (LatestSubmissionResultResponse):**

```json
{
  "taskId": 1,
  "taskStatus": "SUBMITTED",
  "latestSubmission": {
    "id": 10,
    "taskId": 1,
    "studentId": 2,
    "studentName": "Nguyen Van A",
    "fileUrl": null,
    "submittedFiles": [
      {
        "fileName": "work.zip",
        "path": "submissions/task-1/user-2/1780580671131-work.zip",
        "url": null,
        "contentType": "application/zip",
        "size": 123456,
        "uploadedAt": "2026-06-04T20:44:31"
      }
    ],
    "notes": "work file application deliverable",
    "aiScore": 75,
    "aiReport": "Submission meets criteria.",
    "isRevision": false,
    "submittedAt": "2026-06-04T20:50:00"
  },
  "submissionAIResult": {
    "overallStatus": "PARTIAL",
    "criteriaResults": [],
    "canSubmit": true,
    "evaluatedAt": "2026-06-04T21:00:00"
  },
  "revisionCount": 0,
  "latestRevision": null,
  "revisionHistory": []
}
```

Sau revision, `submissionAIResult` co the la `null` vi backend da clear current precheck de bat student precheck lai. `latestRevision` va `revisionHistory` van tra revision da luu.

---
### POST `/api/submissions/task/{taskId}/approve`

Hirer chấp nhận — **HIRER owner**.

- Task → `COMPLETED`
- Criteria → `PASSED`
- **Tự động** `releaseEscrow` → tiền vào ví student

---

### GET `/api/submissions/task/{taskId}`

Lịch sử các lần nộp của task.

---

### GET `/api/submissions/task/{taskId}/dispute-report`

Báo cáo tranh chấp dạng **text** (AI heuristic theo notes vs criteria).

**Response `data`:** string (plain text)

---

## 10. Files

Base path: `/api/files` - **JWT required**

### POST `/api/files/upload`

Upload file to Supabase Storage private bucket `taskhub-submissions`.

**Request:** `multipart/form-data`

| Part | Type | Required |
|------|------|----------|
| `file` | file | yes |
| `taskId` | number | yes |

**Allowed content types:**

- `application/pdf`
- `image/png`
- `image/jpeg`
- `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- `application/zip`

**Max size:** `20MB`

**Response `data` (FileUploadResponse):**

```json
{
  "fileName": "report.pdf",
  "path": "submissions/task-12/user-5/1780308000000-report.pdf",
  "url": null,
  "contentType": "application/pdf",
  "size": 123456,
  "uploadedAt": "2026-06-04T19:30:00"
}
```

`url` is `null` in Phase 3.1 because the bucket is private. Use `path` for later submission metadata; add signed URL API in a later phase.

Required env/config:

```yaml
supabase:
  url: ${SUPABASE_URL:}
  service-role-key: ${SUPABASE_SERVICE_ROLE_KEY:}
  storage:
    bucket: ${SUPABASE_STORAGE_BUCKET:taskhub-submissions}
```

---

## 11. Escrow

Base path: `/api/escrow` — **JWT required**

### POST `/api/escrow/fund/{taskId}`

Nạp escrow — **HIRER owner**, task `LOCKED`.

- Trừ ví: `budget + 5% platform fee`
- Ghi `escrow_deduction`
- Task → `ESCROW_FUNDED`

Thiếu tiền → `402 INSUFFICIENT_WALLET`.

**Response:** `data: null`, message success.

---

### POST `/api/escrow/release/{taskId}`

Giải phóng escrow cho student — task **`COMPLETED`**, escrow `FUNDED`.

> Thường **không** gọi thủ công — `approveSubmission` đã gọi release.

---

### POST `/api/escrow/refund/{taskId}`

Hoàn escrow về ví hirer — **HIRER**, dispute path.

- Escrow → `REFUNDED`
- Task → `LOCKED`, clear assignee, reset criteria `PENDING`

---

## 12. Luồng gợi ý (Hirer / Student)

### Hirer — tạo task đến publish

```
1. POST /api/auth/register (HIRER)
2. POST /api/wallet/deposit?amount=...
3. GET  /api/wallet/readiness/create-task?budget=...
4. POST /api/tasks/validate-criteria        (optional, lint)
5. POST /api/tasks/criteria/extract         (optional, file)
6. POST /api/tasks
7. POST /api/tasks/{id}/lock
8. POST /api/escrow/fund/{taskId}
9. POST /api/tasks/{id}/publish
```

### Hirer — chọn student & duyệt bài

```
10. GET  /api/applications/task/{taskId}
11. POST /api/applications/{id}/accept
12. GET  /api/submissions/task/{taskId}     (sau khi student nộp)
13. POST /api/submissions/task/{taskId}/approve
    HOAC POST /api/submissions/task/{taskId}/revision
    HOẶC POST /api/tasks/{id}/dispute
```

### Student

```
1. POST /api/auth/register (STUDENT)
2. GET  /api/tasks/available
3. POST /api/applications/task/{taskId}
4. (sau khi được accept) POST /api/submissions/task/{taskId}
5. GET  /api/tasks/mine
```

---

## Phụ lục — Bảng endpoint nhanh

| Method | Path | Auth | Role chính |
|--------|------|------|------------|
| POST | `/api/auth/register` | ✗ | — |
| POST | `/api/auth/login` | ✗ | — |
| GET | `/api/wallet/balance` | ✓ | HIRER |
| GET | `/api/wallet/readiness/create-task` | ✓ | HIRER |
| POST | `/api/wallet/deposit` | ✓ | HIRER |
| GET | `/api/wallet/transactions` | ✓ | HIRER |
| POST | `/api/tasks` | ✓ | HIRER |
| GET | `/api/tasks/{id}` | ✓ | * |
| GET | `/api/tasks/mine` | ✓ | * |
| GET | `/api/tasks/available` | ✓ | STUDENT |
| POST | `/api/tasks/validate-criteria` | ✓ | HIRER |
| POST | `/api/tasks/{id}/validate` | ✓ | HIRER |
| POST | `/api/tasks/criteria/extract` | ✓ | HIRER |
| POST | `/api/tasks/{id}/lock` | ✓ | HIRER |
| POST | `/api/tasks/{id}/publish` | ✓ | HIRER |
| POST | `/api/tasks/{id}/complete` | ✓ | HIRER |
| POST | `/api/tasks/{id}/revision` | ✓ | HIRER |
| POST | `/api/tasks/{id}/dispute` | ✓ | HIRER |
| POST | `/api/applications/task/{taskId}` | ✓ | STUDENT |
| POST | `/api/applications/{id}/accept` | ✓ | HIRER |
| GET | `/api/applications/task/{taskId}` | ✓ | HIRER |
| GET | `/api/applications/mine` | ✓ | STUDENT |
| POST | `/api/files/upload` | ✓ | * |
| POST | `/api/submissions/task/{taskId}/precheck` | ? | STUDENT |
| POST | `/api/submissions/task/{taskId}` | ✓ | STUDENT |
| POST | `/api/submissions/task/{taskId}/revision` | ? | HIRER |
| GET | `/api/submissions/task/{taskId}/revisions` | ? | HIRER/STUDENT |
| POST | `/api/submissions/task/{taskId}/approve` | ✓ | HIRER |
| GET | `/api/submissions/task/{taskId}` | ✓ | * |
| GET | `/api/submissions/task/{taskId}/latest` | ? | HIRER/STUDENT |
| GET | `/api/submissions/task/{taskId}/dispute-report` | ✓ | * |
| POST | `/api/escrow/fund/{taskId}` | ✓ | HIRER |
| POST | `/api/escrow/release/{taskId}` | ✓ | HIRER |
| POST | `/api/escrow/refund/{taskId}` | ✓ | HIRER |

---

## Tài liệu liên quan

- [DATABASE_SETUP.md](./DATABASE_SETUP.md) — H2 / SQL Server
- [FE_WALLET_AND_CRITERIA_FLOW.md](./FE_WALLET_AND_CRITERIA_FLOW.md) — tích hợp FE ví & criteria
- [BACKEND_GAP_ANALYSIS.md](./BACKEND_GAP_ANALYSIS.md) — tính năng còn thiếu
- [LOVABLE_FE_PROMPT.md](./LOVABLE_FE_PROMPT.md) — prompt build FE

---

*Cập nhật theo codebase BE hiện tại. Khi thêm endpoint mới, cập nhật file này và Swagger.*
