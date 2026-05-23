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
10. [Escrow](#10-escrow)
11. [Luồng gợi ý (Hirer / Student)](#11-luồng-gợi-ý-hirer--student)

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
  "role": "HIRER"
}
```

| Field | Bắt buộc | Ghi chú |
|-------|-----------|---------|
| `email` | ✓ | Email hợp lệ, unique |
| `password` | ✓ | Tối thiểu 6 ký tự |
| `fullName` | ✓ | |
| `role` | ✓ | `HIRER` hoặc `STUDENT` |

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
| `title`, `description` | string |
| `budget` | number |
| `deadline` | ISO datetime |
| `status` | TaskStatus enum |
| `hirerId`, `hirerName` | |
| `assignedToId`, `assignedToName` | nullable |
| `acceptanceCriteria` | `[{ id, description, status }]` |
| `createdAt` | ISO datetime |

`status` criterion: `PENDING` | `PASSED` | `FAILED`

---

### GET `/api/tasks/mine`

- **HIRER:** tất cả task do mình tạo
- **STUDENT:** task đã được assign (`assignedToId` = mình)

**Response `data`:** `TaskResponse[]`

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

Yêu cầu chỉnh sửa — **HIRER**, task `SUBMITTED` hoặc `DISPUTED`.

**Body (RevisionRequest):**

```json
{
  "failedCriteriaIds": [2],
  "feedback": "Can bo sung file PDF..."
}
```

→ `IN_PROGRESS`, đánh `FAILED` cho criteria trong list.

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

## 9. Submissions

Base path: `/api/submissions` — **JWT required**

### POST `/api/submissions/task/{taskId}`

Student nộp bài — task `IN_PROGRESS`, đúng assignee.

**Body:**

```json
{
  "fileUrl": "https://storage.example.com/work.zip",
  "notes": "Em da nop file PDF 5 trang..."
}
```

**Quy tắc AI (heuristic):**

- `aiScore == 0` → **400**, không cho nộp
- `aiScore < 70` → vẫn nộp được, `aiReport` cảnh báo

**Response `data` (SubmissionResponse):**

```json
{
  "id": 1,
  "taskId": 1,
  "studentId": 1,
  "studentName": "...",
  "fileUrl": "...",
  "notes": "...",
  "aiScore": 75,
  "aiReport": "Submission meets criteria.",
  "isRevision": false,
  "submittedAt": "..."
}
```

Task → `SUBMITTED`.

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

## 10. Escrow

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

## 11. Luồng gợi ý (Hirer / Student)

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
    HOẶC POST /api/tasks/{id}/revision
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
| POST | `/api/submissions/task/{taskId}` | ✓ | STUDENT |
| POST | `/api/submissions/task/{taskId}/approve` | ✓ | HIRER |
| GET | `/api/submissions/task/{taskId}` | ✓ | * |
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
