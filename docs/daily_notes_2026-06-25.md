# 📋 Nhật ký thay đổi — Ngày 25/06/2026

---

## Tổng quan

| Hạng mục | Chi tiết |
|----------|----------|
| **Ngày** | 25/06/2026 (Thứ Tư) |
| **FE Commit** | `d7781ec` — "Refresh frontend task UI" |
| **BE Commit** | `1f26f39` — "Update backend notifications and deployment config" |
| **FE Repo** | [Taskhub_FE](https://github.com/Taskhub-OSF-project/Taskhub_FE) — branch `main` |
| **BE Repo** | [Taskhub_BE](https://github.com/Taskhub-OSF-project/Taskhub_BE) — branch `main` |
| **AWS Lambda** | Deploy thành công — `https://cyxlrtltrl.execute-api.ap-southeast-1.amazonaws.com/Prod/` |
| **Tổng file thay đổi** | FE: 24 files (+1537 / −947) · BE: 20 files (+178 / −38) |

---

## 🎨 Frontend (Taskhub_FE)

### 1. Redesign giao diện — Editorial Minimalism

Toàn bộ giao diện FE đã được thay đổi theo phong cách **Editorial Minimalism**:

- **Design System** ([design-system.ts](file:///d:/TaskHub1.0/Taskhub_FE/src/lib/design-system.ts)): Cập nhật 140 dòng token màu sắc, typography, spacing theo hướng tối giản, sạch sẽ.
- **Styles** ([styles.css](file:///d:/TaskHub1.0/Taskhub_FE/src/styles.css)): Viết lại phần lớn stylesheet (+405 dòng thay đổi), bổ sung hiệu ứng micro-animations, hover effects, glassmorphism nhẹ.

### 2. Bản địa hóa đa ngôn ngữ (VI / EN)

Tích hợp `useAppSettings` hook để toggle ngôn ngữ trên toàn bộ các trang chưa được dịch:

| Trang | File | Nội dung dịch |
|-------|------|---------------|
| Đăng ký | [register.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/register.tsx) | Form, labels, placeholders, kỹ năng, học vấn, toast, lỗi validation (+351 dòng) |
| Đăng nhập | [login.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/login.tsx) | Banner, vai trò, form inputs, thông báo |
| Khôi phục MK | [recover.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/recover.tsx) | Tab email/SMS, OTP, toast phản hồi |
| Giới thiệu | [about.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/about.tsx) | Nội dung giới thiệu, tính năng |
| Chính sách | [policy.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/policy.tsx) | Điều khoản, chính sách bảo mật |
| Cài đặt | [settings.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/settings.tsx) | Labels cài đặt, toggle ngôn ngữ |
| Hồ sơ xem | [profile.$userId.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/profile.$userId.tsx) | Thông tin hồ sơ, kỹ năng, đánh giá |
| Chỉnh sửa hồ sơ | [profile.edit.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/profile.edit.tsx) | Form chỉnh sửa, labels, validation |

### 3. Sửa lỗi logic hiển thị ứng tuyển

- **[tasks.ts](file:///d:/TaskHub1.0/Taskhub_FE/src/api/tasks.ts)**: Sử dụng hàm `pageContent()` để bọc API `/applications/mine` và `/applications/task/{taskId}`. Trước đó, cấu trúc phân trang `PageResponse` bị parse sai → sinh viên đã ứng tuyển vẫn hiển thị ở tab "Việc mở" thay vì "Đã ứng tuyển".
- **[format.ts](file:///d:/TaskHub1.0/Taskhub_FE/src/lib/format.ts)**: Bổ sung hàm format mới.

### 4. Sửa nhãn "Bị khiếu nại" cho sinh viên

Trước đó hiển thị "Đang khiếu nại" (góc nhìn người thuê), nay sửa thành "Bị khiếu nại" (góc nhìn sinh viên):

| File | Thay đổi |
|------|----------|
| [StatusBadge.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/components/StatusBadge.tsx) | Thêm prop `label` để override nhãn mặc định |
| [TaskCards.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/components/cards/TaskCards.tsx) | Đổi nhãn DISPUTED → "Bị khiếu nại" cho student view |
| [student.inprogress.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/student.inprogress.tsx) | Tab thống kê: "Đang khiếu nại" → "Bị khiếu nại" |
| [student.progress.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/student.progress.tsx) | Tương tự |
| [student.tasks.$taskId.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/student.tasks.$taskId.tsx) | Chi tiết task: truyền `label="Bị khiếu nại"` |
| [translations.ts](file:///d:/TaskHub1.0/Taskhub_FE/src/lib/translations.ts) | `workflowDisputed` (student) → "Bị khiếu nại" |

### 5. Tối ưu UI/UX Dashboard Hirer

File chính: [hirer.index.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/hirer.index.tsx) (+266 dòng thay đổi)

- **Thứ tự cột trái**: Urgent Actions → Job Progress → Recent Jobs (Flexbox order)
- **Job Progress**: Đổi từ grid 8 thẻ lớn → badge dạng pill nằm ngang (Active: xanh indigo, Inactive: nét đứt xám)
- **Sidebar**: New Applicants chuyển lên dưới Finance
- **Wallet Balance**: Font 24px bold, các dòng phụ opacity 0.7
- **Escrow History**: Giới hạn 3 giao dịch, thêm "Xem tất cả" → `/hirer/wallet`
- **Recent Jobs table**: Padding thu hẹp (py-2 px-3), gộp Budget vào dưới tiêu đề, bỏ cột Budget riêng

### 6. Cập nhật các trang Hirer khác

| File | Thay đổi |
|------|----------|
| [hirer.submissions.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/hirer.submissions.tsx) | Dịch thuật + redesign |
| [hirer.tasks.$taskId.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/hirer.tasks.$taskId.tsx) | Dịch thuật + redesign |
| [hirer.tasks.new.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/hirer.tasks.new.tsx) | Dịch thuật + redesign |
| [hirer.wallet.tsx](file:///d:/TaskHub1.0/Taskhub_FE/src/routes/hirer.wallet.tsx) | Dịch thuật + redesign |

---

## ⚙️ Backend (Taskhub_BE)

### 1. Hệ thống Thông báo (Notifications)

- **[NotificationService.java](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/java/com/taskhub/service/NotificationService.java)**: Mở rộng 22 dòng — thêm logic broadcast notification mới.
- **[BroadcastNotificationResponse.java](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/java/com/taskhub/dto/response/BroadcastNotificationResponse.java)**: DTO mới (12 dòng) cho response broadcast notification.

### 2. Messaging

- **[MessagingController.java](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/java/com/taskhub/controller/MessagingController.java)**: Thêm 7 dòng — endpoint mới.
- **[MessagingService.java](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/java/com/taskhub/service/MessagingService.java)**: Mở rộng 55 dòng — logic message mới.
- **[MessageRepository.java](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/java/com/taskhub/repository/MessageRepository.java)**: Thêm 8 dòng query mới.

### 3. Auth & User

- **[AuthService.java](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/java/com/taskhub/service/AuthService.java)**: Cập nhật 10 dòng logic đăng ký/đăng nhập.
- **[RegisterRequest.java](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/java/com/taskhub/dto/request/RegisterRequest.java)**: Thêm 6 dòng field mới.
- **[UserRepository.java](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/java/com/taskhub/repository/UserRepository.java)**: Thêm 1 query method.

### 4. Business Logic

- **[ReviewService.java](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/java/com/taskhub/service/ReviewService.java)**: Sửa 15 dòng — cải thiện logic review.
- **[SubmissionService.java](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/java/com/taskhub/service/SubmissionService.java)**: Thêm 7 dòng.
- **[TaskService.java](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/java/com/taskhub/service/TaskService.java)**: Sửa 2 dòng.
- **[WalletService.java](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/java/com/taskhub/service/WalletService.java)**: Sửa 4 dòng.

### 5. Cấu hình & Deployment

- **[application.yml](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/resources/application.yml)**: Cập nhật CORS config.
- **[application-supabase.yml](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/resources/application-supabase.yml)**: Điều chỉnh connection settings.
- **[V1__init.sql](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/main/resources/db/migration/V1__init.sql)**: Cập nhật migration script.
- **[template.yaml](file:///d:/TaskHub1.0/Taskhub_BE/BE/template.yaml)**: Cập nhật SAM template cho Lambda.
- **[pom.xml](file:///d:/TaskHub1.0/Taskhub_BE/BE/pom.xml)**: Thêm 5 dòng dependency.
- **[.env.example](file:///d:/TaskHub1.0/Taskhub_BE/BE/.env.example)**: Cập nhật mẫu biến môi trường.
- **[TaskServiceTest.java](file:///d:/TaskHub1.0/Taskhub_BE/BE/src/test/java/com/taskhub/service/TaskServiceTest.java)**: Cập nhật unit test.

---

## 🚀 Deployment

| Nền tảng | Trạng thái | Chi tiết |
|----------|-----------|----------|
| **GitHub FE** | ✅ Pushed | `main` branch → `Taskhub-OSF-project/Taskhub_FE` |
| **GitHub BE** | ✅ Pushed | `main` branch → `Taskhub-OSF-project/Taskhub_BE` |
| **AWS Lambda** | ✅ Deployed | Stack: `taskhub-backend` · Region: `ap-southeast-1` |
| **API URL** | 🌐 Live | `https://cyxlrtltrl.execute-api.ap-southeast-1.amazonaws.com/Prod/` |

---

## 📝 Ghi chú kỹ thuật

1. **FE chạy trên port 8081** (port 8080 bị BE chiếm khi chạy local).
2. **Backend sử dụng Supabase Session Pooler** (port 6543 cho local, port 5432 cho Lambda) do direct host không ổn định.
3. **Lambda memory: 3008 MB**, timeout: 30s, Java 21, TieredCompilation level 1 để giảm cold start.
4. **Hikari pool size: 2** (tối đa) để tránh `EMAXCONNSESSION` từ Supabase pooler.
5. **File `.env` đã được thêm vào `.gitignore`** ở cả FE và BE để không commit secrets.
