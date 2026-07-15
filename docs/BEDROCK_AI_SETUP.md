# Claude Haiku trên AWS Bedrock

TaskHub dùng Amazon Bedrock Converse API và Claude Haiku 4.5 cho các API hiện có
trong `/api/ai/**`. Frontend không cần đổi request/response contract.

## Cấu hình mặc định

```text
APP_BEDROCK_ENABLED=true
APP_BEDROCK_REGION=us-east-1
APP_BEDROCK_MODEL_ID=global.anthropic.claude-haiku-4-5-20251001-v1:0
APP_BEDROCK_TEMPERATURE=0.3
APP_BEDROCK_MAX_TOKENS=4096
```

Model ID có thể đổi bằng biến môi trường mà không cần build lại ứng dụng. Cấu
hình `global.*` là cross-region inference profile. Nếu tài khoản chỉ cho phép gọi
model trong một region, thay bằng model ID hoặc inference profile được cấp quyền.

## Chuẩn bị tài khoản AWS

1. Mở Amazon Bedrock Console và xác nhận Claude Haiku 4.5 khả dụng cho tài khoản.
2. Hoàn tất yêu cầu subscription/model access của Anthropic nếu console yêu cầu.
3. Cấp quyền `bedrock:InvokeModel` cho IAM principal chạy backend.

Ví dụ policy tối thiểu để thử nghiệm:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "bedrock:InvokeModel",
      "Resource": "*"
    }
  ]
}
```

`BE/template.yaml` đã gắn quyền này vào execution role của Lambda. Khi hệ thống đã
ổn định, nên giới hạn `Resource` theo inference-profile và foundation-model ARN mà
tài khoản thực sự sử dụng.

## Chạy local

Backend dùng AWS default credential provider chain, không dùng API key riêng trong
source code. Cách đơn giản nhất:

```powershell
aws configure
aws sts get-caller-identity
```

Hoặc dùng AWS SSO/profile rồi đặt `AWS_PROFILE` cho terminal chạy Spring Boot.
Không commit `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` hoặc session token.

Từ thư mục `Taskhub_BE/BE`:

```powershell
..\..\maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
```

## Kiểm tra

Sau khi đăng nhập TaskHub để lấy JWT, gọi một API AI hiện có, ví dụ:

```http
POST /api/ai/criteria/from-job
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "jobTitle": "Thiết kế landing page",
  "jobDescription": "Thiết kế landing page responsive và bàn giao Figma",
  "jobCategory": "DESIGN"
}
```

Các lỗi thường gặp:

- `SERVICE_UNAVAILABLE`: thiếu AWS credentials, sai region, hoặc AI bị tắt.
- `AccessDeniedException`: IAM role chưa có `bedrock:InvokeModel` hoặc chưa có model access.
- `ValidationException`: model ID/inference profile không khả dụng trong region đang cấu hình.
