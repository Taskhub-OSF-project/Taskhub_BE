# FE — Ví trước khi tạo task & tiêu chí rõ ràng

## 1. Kiểm tra ví (trước / khi tạo task)

**Pre-check (khi user nhập budget):**

```
GET /api/wallet/readiness/create-task?budget=1000000
Authorization: Bearer <token>
```

Response `data`:

```json
{
  "sufficient": false,
  "budget": 1000000,
  "platformFee": 50000,
  "requiredTotal": 1050000,
  "currentBalance": 200000,
  "shortfall": 850000,
  "action": "TOP_UP",
  "resumeFlow": "CREATE_TASK"
}
```

**UI:** Nếu `!sufficient` → disable nút "Tạo task" hoặc intercept → điều hướng **Ví → Nạp tiền** với `returnTo=create-task` (lưu form draft vào `sessionStorage`).

**Khi submit create:**

```
POST /api/tasks
```

Nếu thiếu tiền → HTTP **402**, body:

```json
{
  "success": false,
  "errorCode": "INSUFFICIENT_WALLET",
  "message": "So du vi khong du...",
  "data": { /* WalletReadinessResponse */ }
}
```

→ Toast + redirect nạp tiền → sau khi nạp xong quay lại form (restore draft).

---

## 2. Tiêu chí — không mơ hồ, không viết bừa

**Lint khi đang soạn (debounce 500ms):**

```
POST /api/tasks/validate-criteria
{ "acceptanceCriteria": ["...", "..."] }
```

Response `data.valid` + `data.details[]` (mỗi dòng: `isValid`, `issue`, `suggestion`).

**Khi tạo task:** BE từ chối nếu criteria fail → `errorCode: "INVALID_CRITERIA"`, `data` = cùng structure validation.

**Khi khóa task:**

```
POST /api/tasks/{id}/lock
```

`validationPhase: "failed"` → hiển thị ❌ từng dòng trong `details`, gợi ý `suggestion`; chặn lock.

### Quy tắc BE (gợi ý copy UI)

- Tối thiểu **20 ký tự**, **5 từ**
- Cấm placeholder: "làm cho đẹp", "tùy ý", "ok", …
- Cấm từ chủ quan không đo được: đẹp, tốt, chất lượng, … **trừ khi** có số liệu / file / định dạng cụ thể
- Phải có metric: số lượng, %, px, định dạng file (.pdf, .png), trang, dung lượng, …

---

## 3. AI trích xuất tiêu chí từ file

```
POST /api/tasks/criteria/extract
Content-Type: multipart/form-data
file: <PDF | XLSX | PNG | JPG | DOCX>  (max 15MB)
```

Response `data.suggestions[]`: `{ text, rationale }` → user chọn từng dòng thêm vào form (vẫn có thể sửa trước khi validate).

---

## Luồng màn Create Task (đề xuất)

1. Nhập budget → gọi readiness → cảnh báo thiếu tiền sớm  
2. Nhập criteria / upload file → **Trích xuất bằng AI**  
3. Debounce validate-criteria → hiện ⚠/❌ từng dòng  
4. Submit create → nếu 402 → nạp tiền → resume  
5. Khóa định nghĩa → lock API → escrow  
