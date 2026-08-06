# Hướng Dẫn Tích Hợp Cổng Chuyển Khoản Tự Động SePay (VietQR) cho TaskHub

Tài liệu này hướng dẫn chi tiết từng bước (từ con số 0) cách thiết lập cổng thanh toán **SePay** để hệ thống TaskHub tự động ghi nhận giao dịch nạp tiền vào ví của người dùng chỉ trong 3 - 5 giây sau khi họ thanh toán chuyển khoản bằng VietQR.

---

## 1. SePay là gì và Tại sao nên dùng?

- **SePay** (https://sepay.vn) là dịch vụ gateway trung gian giúp theo dõi biến động số dư tài khoản ngân hàng (MBBank, VietinBank, Techcombank, ACB, VCB...) thông qua thông báo (Notification/SMS) và tự động gọi **Webhook** (gửi HTTP POST Request mang dữ liệu giao dịch) về Server Backend của TaskHub.
- **Ưu điểm**:
  - Không cần ký kết hợp đồng thương mại hay yêu cầu doanh nghiệp phức tạp như MoMo / VNPay.
  - Sử dụng trực tiếp tài khoản cá nhân hoặc công ty của bạn tại bất kỳ ngân hàng nào được hỗ trợ.
  - Phí dịch vụ rất rẻ (thậm chí có gói miễn phí cho số lượng giao dịch nhỏ).
  - Tương thích 100% với VietQR Quốc gia.

---

## 2. Bước 1: Đăng Ký và Liên Kết Ngân Hàng Trên SePay

1. Truy cập [https://my.sepay.vn/register](https://my.sepay.vn/register) để đăng ký một tài khoản SePay (hoặc đăng nhập nếu đã có).
2. Vào mục **Quản lý Ngân hàng** (hoặc **Tài khoản ngân hàng**) -> Nhấn **Thêm tài khoản Ngân hàng**.
3. Chọn ngân hàng bạn muốn dùng để nhận tiền nạp từ user (Ví dụ: **MBBank** hoặc **Vietcombank**), sau đó làm theo hướng dẫn của SePay để liên kết (thường là đăng nhập ứng dụng hoặc cài tool theo dõi thông báo biến động số dư theo hướng dẫn của hệ thống SePay).
4. Khi liên kết thành công, bạn sẽ thấy trạng thái tài khoản chuyển sang **Đang hoạt động** và SePay bắt đầu ghi nhận được các thông báo biến động tài khoản.

---

## 3. Bước 2: Tạo Cấu Hình Webhook Về TaskHub

Webhook chính là "nhịp cầu" để SePay báo cho Server TaskHub biết mỗi khi có ai đó vừa chuyển tiền vào tài khoản của bạn.

1. Tại giao diện quản trị SePay, chuyển sang menu **Webhooks** (hoặc **Tích hợp Webhook**).
2. Nhấn **Thêm Webhook** và điền các thông tin sau:
   - **URL Webhook**:
     - Khi chạy chính thức (Production): `https://your-domain.com/api/sepay/webhook`
     - Khi phát triển / test Local (Sử dụng Ngrok hoặc localtunnel để mở port 8080): `https://xyz-123.ngrok-free.app/api/sepay/webhook`
   - **Sự kiện kích hoạt**: Chọn **Khi có giao dịch Ngân hàng đến (Tiền vào / Inward)**.
   - **Phương thức (HTTP Method)**: `POST`
   - **Định dạng dữ liệu (Content-Type)**: `application/json` (Chuẩn mặc định của SePay).
3. **Cấu hình bảo mật (API Token / Secret Key)**:
   - Trong phần cài đặt nâng cao / Header xác thực của Webhook (hoặc phần Quản lý API Key trong SePay), sao chép chuỗi **API Token** bí mật của bạn.
   - (Bạn cũng có thể định cấu hình gửi header `Authorization: Bearer <API_KEY_CỦA_BẠN>`).
4. Nhấn **Lưu cấu hình**.

---

## 4. Bước 3: Cấu Hình Biến Môi Trường Trong TaskHub Backend

Mở tệp biến môi trường `.env` (hoặc đặt trực tiếp trong config deployment / AWS Lambda / Docker) với các giá trị từ tài khoản SePay của bạn:

```properties
# Token bảo mật để Backend từ chối các webhook không hợp lệ từ bên thứ ba (khớp với Token bạn cài trong SePay)
APP_SEPAY_API_TOKEN=chuoi-token-bi-mat-tu-sepay-cua-ban

# Thông tin tài khoản ngân hàng dùng để hiển thị lên QR Code cho khách hàng quét
APP_SEPAY_BANK_ACCOUNT=0123456789
APP_SEPAY_BANK_NAME=MBBank
APP_SEPAY_ACCOUNT_NAME=TASKHUB PLATFORM
APP_SEPAY_QR_TEMPLATE=compact
```

> **Lưu ý**: Ở môi trường phát triển (dev local), nếu để trống `APP_SEPAY_API_TOKEN`, `SepayService` sẽ tự động chuyển sang chế độ linh hoạt (bỏ qua kiểm tra chữ ký) để bạn dễ dàng gửi cURL giả lập test ngay lập tức mà không cần Token.

---

## 5. Bước 4: Nguyên Lý Nhận Diện & Tự Động Cộng Tiền

Để biết một khoản tiền gửi vào là của User nào, hệ thống TaskHub sử dụng **Quy tắc cú pháp Nội dung giao dịch**:

- Cú pháp quy ước: **`THTT <ID_NGƯỜI_DÙNG>`** hoặc **`TASKHUB <ID_NGƯỜI_DÙNG>`**
- **Ví dụ cụ thể**:
  - Nhà tuyển dụng Nguyễn Văn A có tài khoản trên TaskHub mang `User ID = 15`.
  - Khi anh A muốn nạp `500,000 VND`, hệ thống Frontend sẽ hiển thị hình ảnh VietQR. Khi dùng app Ngân hàng quét QR này, phần nội dung chuyển khoản sẽ được tự động điền sẵn là: `THTT 15` (hoặc `TASKHUB 15`).
  - Sau khi chuyển tiền thành công, SePay bắt được biến động, gửi JSON Webhook về `/api/sepay/webhook` với trường `content: "NGUYEN VAN A CK NAP TIEN THTT 15"`.
  - `SepayService` tại Backend sẽ dùng Regular Expression bóc tách ra cụm `THTT 15` -> Nhận biết được mục tiêu là `User ID = 15`.
  - Hệ thống thực hiện locking transaction DB, cộng thêm `500,000 VND` vào trường `wallet_balance` của User 15, đồng thời ghi 1 bản ghi sổ cái (`WalletTransaction`) với loại `top_up` thành công!

---

## 6. Bước 5: Cách Test Thử Nghiệm Ngay Trên Máy Local (Không Mất Tiền Thật)

Bạn có thể tự nghiệm thu trọn vẹn luồng thanh toán ngay bây giờ bằng 2 cách mà **không cần chuyển khoản tiền thật**:

### Cách 1: Sử Dụng Công Cụ Giả Lập Webhook Của SePay
In Giao diện SePay -> mục **Webhooks**, nhấn nút **Test Webhook / Gửi dữ liệu mẫu**. Điền phần nội dung là `THTT 1` (Giả sử nạp cho admin mang User ID = 1), nhập số tiền bất kỳ rồi nhấn Gửi.

### Cách 2: Sử Dụng Lệnh cURL / Postman gửi trực tiếp đến Backend Local
Khi Backend đang chạy trên cổng `8080`, mở Terminal / PowerShell và chạy thử một lệnh gửi dữ liệu webhook mẫu như sau:

```powershell
$body = @{
    id = 999999
    gateway = "MBBank"
    transactionDate = "2026-08-04 20:30:00"
    accountNumber = "0123456789"
    content = "TEST NAP TIEN THTT 1"
    transferType = "in"
    transferAmount = 500000
    accumulated = 5500000
    referenceCode = "REF_TEST_2026_001"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/sepay/webhook" `
    -Method Post `
    -Body $body `
    -ContentType "application/json"
```

Sau khi gửi request trên, hãy kiểm tra:
1. Log của Backend: Sẽ báo `"SePay deposit SUCCESS: Added 500000 VND to userId=1's wallet..."`.
2. Kiểm tra DB (bảng `sepay_webhook_logs`): Bản ghi được tạo ra với trạng thái `PROCESSED`.
3. Kiểm tra số dư giao diện Ví trên Frontend: Số dư tài khoản được tự động cộng thêm `500,000₫` cùng lịch sử giao dịch rõ ràng!
