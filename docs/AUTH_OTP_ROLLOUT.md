# TaskHub fast sign-up and email OTP rollout

This rollout keeps the existing TaskHub JWT and OTP flow. Google users are
verified immediately from a Google ID token. Email/password users receive the
existing six-digit OTP through Resend while AWS SES production access is
pending. Switching back to SES later does not require frontend or auth-flow
changes.

## Implementation checklist

- [x] Google registration creates an email-verified TaskHub account without OTP.
- [x] Amplify `VITE_GOOGLE_CLIENT_ID` and Lambda `APP_GOOGLE_CLIENT_ID` use the
      same production OAuth Web client ID.
- [x] Malformed Google credentials return HTTP 401 instead of an internal error.
- [x] Email/password registration creates a hashed six-digit OTP challenge.
- [x] Login OTP and registration OTP expire after 10 minutes.
- [x] OTP resend is rate-limited and invalidates the previous active challenge.
- [x] Resend implements the shared backend `MailService` contract.
- [x] SES remains selectable with `APP_MAIL_PROVIDER=ses`.
- [x] Resend configuration is represented in `application.yml`, `.env.example`,
      and the AWS SAM template.
- [x] Browser refresh-token cookies require the SPA CSRF header; API clients can
      still submit a refresh token in the request body.
- [x] Resend/OTP tests pass, Google identity tests pass, and the full backend
      suite passes (151 tests).
- [x] The Amplify production bundle builds locally and returns HTTP 200 for `/`
      and `/login`.
- [x] Confirmed that `taskhubvn.com` DNS is managed by Cloudflare
      (`art.ns.cloudflare.com` and `emely.ns.cloudflare.com`).
- [x] Created `mail.taskhubvn.com` in Resend in Tokyo
      (`ap-northeast-1`), domain ID `9a51bccf-1c99-48d1-b7da-842edc95bd11`.
- [x] Verified the sending subdomain `mail.taskhubvn.com` in Resend (SPF and DKIM).
- [x] Created a domain-scoped, send-only Resend API key and stored it in the
      Lambda environment through the CloudFormation `NoEcho` parameter.
- [x] Deployed the Lambda with Resend enabled; the CloudFormation stack is
      `UPDATE_COMPLETE` and the production health endpoint returns HTTP 200.
- [x] Production registration created an OTP challenge and Resend reported the
      OTP email as `delivered` to the test Gmail alias.
- [ ] Enter the delivered OTP in the web UI and confirm the test account becomes
      email verified.
- [ ] After SES production access is approved, switch the provider back to SES.

## Authentication and UI hardening checklist (2026-07-22)

- [x] A returning Google account signs in immediately without being asked for a
      role again.
- [x] A new Google identity is verified first, then receives an explicit
      Nhà tuyển dụng / Sinh viên role chooser before the account is created.
- [x] The backend returns the machine-readable `GOOGLE_ROLE_REQUIRED` code and
      never silently assigns a default role.
- [x] A successful login OTP creates a signed, HttpOnly, Secure trusted-device
      cookie for 30 days; logging out removes the session but keeps the device
      trust marker, so the next password login does not request OTP again.
- [x] The trusted-device marker is bound to one user, has an expiry, and rejects
      malformed or modified values.
- [x] The main authenticated address is the neutral `/dashboard` route instead
      of exposing `/hirer` on the overview page.
- [x] Visible `Task #<database-id>` labels were removed from the dashboard and
      AI assistant; internal IDs remain only in API calls and route parameters.
- [x] Typography uses one Vietnamese-capable family (`Be Vietnam Pro`) with a
      16px base, 13px minimum caption token, fixed heading/display scale,
      consistent line height, and readable metadata contrast.
- [x] All hard-coded UI font classes below 12px were removed; chart labels were
      raised to 13px.
- [x] Wallet balance/history now show an explicit API error state. Production
      nạp/rút actions are disabled and described honestly until a real payment
      provider is connected; the UI no longer pretends that VietQR/MoMo was
      completed while backend cash simulation is disabled.
- [x] Frontend production build passes.
- [x] Backend full test suite passes: 151 tests, 0 failures.
- [x] Deploy backend trusted-device/Google/wallet changes to Lambda; the
      CloudFormation stack reached `UPDATE_COMPLETE` and `/api/health` returned
      `UP`.
- [x] Deploy frontend role chooser, typography, wallet and `/dashboard` changes
      to Amplify; job `67` succeeded and `/`, `/login`, `/dashboard` each
      returned HTTP 200 on `taskhubvn.com`.
- [ ] Verify Google new-user role selection, trusted login, dashboard and wallet
      with a real browser session on `https://taskhubvn.com`.

Do not mark an external item complete until it has been verified in the live
environment.

## Live rollout evidence (2026-07-22)

- Resend domain ID: `9a51bccf-1c99-48d1-b7da-842edc95bd11`
- Resend status: `verified` (DKIM TXT, SPF MX, and SPF TXT all verified)
- Send-only API key ID: `c366754f-2b73-4039-bd4e-2bac4c52c2b1`
- AWS account: `201062409810`
- CloudFormation stack: `taskhub-backend` / `UPDATE_COMPLETE`
- Lambda mail configuration: provider `resend`, delivery enabled, sender
  `TaskHub <otp@mail.taskhubvn.com>`, API key present (value not read or logged)
- Google Identity configuration: Lambda client ID matches the Amplify app;
  production malformed-token smoke test returns HTTP 401 `UNAUTHORIZED`
- Production API: `https://6meekld3r6.execute-api.ap-southeast-1.amazonaws.com/Prod/`
- Production OTP test recipient:
  `huynhld.ai+taskhubotp20260722174107@gmail.com`
- OTP challenge purpose: `REGISTRATION`, expiry: 600 seconds
- Resend email ID: `f7424173-22f5-409d-b409-fdd3a8d31687`, final event:
  `delivered`

## Resend and Cloudflare setup

### 1. Add the sending domain

1. Sign in at <https://resend.com/domains>.
2. Select **Add Domain**.
3. Enter `mail.taskhubvn.com` (use the subdomain, not the root domain).
4. Select a Resend region and create the domain.
5. Keep the Resend DNS-record page open. The record names and values are
   account/domain-specific, so copy them exactly rather than using example
   values from this document.

### 2. Add the records in Cloudflare

The live Resend domain created on 2026-07-22 requires these records:

| Type | Cloudflare name | Content / mail server | Priority | TTL |
| --- | --- | --- | ---: | --- |
| TXT | `resend._domainkey.mail` | `p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIBMxh6E2Y+mPK4RcBHh6uscnN2pI5fM3pD9+8qRi2v+OfGF9PKSMZ65pTXcv7Nn6BzHUCh9rhXsj9cnY68qim1XSgxLBaijqe1UiaeFOTJwRoXC0EQFBo+2d8NibI5VogMQjvHX4lKRQQDfR6/Et6ZXUq+P6KSqEYfVzTJVzFmwIDAQAB` | - | Auto |
| MX | `send.mail` | `feedback-smtp.ap-northeast-1.amazonses.com` | 10 | Auto |
| TXT | `send.mail` | `v=spf1 include:amazonses.com ~all` | - | Auto |

These DNS values are public verification data, not API secrets.

1. Sign in to Cloudflare and open **Websites > taskhubvn.com > DNS > Records**.
2. For every record displayed by Resend, select **Add record** and copy its
   type, name, priority (when present), and value exactly.
3. Resend normally displays a DKIM TXT record plus SPF-related TXT and MX
   records. Add all records Resend marks as required; SPF delivery can require
   both its TXT and MX records.
4. For any Resend CNAME record, set **Proxy status** to **DNS only** (gray
   cloud). Email-authentication records must not be proxied.
5. Leave TTL on **Auto** unless Resend explicitly says otherwise.
6. Cloudflare automatically appends `taskhubvn.com` to relative names. After
   saving, confirm the resulting full hostname matches the hostname shown by
   Resend and does not end with `taskhubvn.com.taskhubvn.com`.
7. Return to Resend and select **Verify DNS Records**. Do not continue to the
   production deploy until the domain status is **Verified**.

Do not add a second SPF TXT record to the same hostname when one already
exists. Merge the required `include:` mechanisms into a single SPF value
instead, or use the separate Resend hostname exactly as its dashboard shows.

### 3. Create the API key

1. Open <https://resend.com/api-keys> and select **Create API Key**.
2. Name it `TaskHub Production OTP`.
3. Select **Sending access** instead of full access.
4. Restrict it to `mail.taskhubvn.com` if Resend offers the domain restriction.
5. Copy the key when it is shown once. Paste it directly into the AWS setup;
   never paste it into chat, a screenshot, Git, documentation, frontend
   `VITE_*` variables, or a committed `.env` file.
6. Use `TaskHub <otp@mail.taskhubvn.com>` as the sender.

If a key is accidentally disclosed, revoke it in Resend immediately and create
a replacement.

## Lambda configuration

The SAM template declares `AppMailResendApiKey` with `NoEcho: true` and maps it
only to the Lambda backend environment. AWS Lambda encrypts environment
variables at rest. Set these values through the SAM parameters:

```text
APP_MAIL_DELIVERY_ENABLED=true
APP_MAIL_PROVIDER=resend
APP_MAIL_FROM_EMAIL=TaskHub <otp@mail.taskhubvn.com>
APP_MAIL_RESEND_API_KEY=<secret>
```

Before deploying, authenticate the local AWS CLI without sharing credentials:

```powershell
aws login
aws sts get-caller-identity
```

The second command must return the intended AWS account and role. Then deploy
from `Taskhub_BE/BE`. Do not save the Resend key in `samconfig.toml` or
PowerShell history. The safe assisted workflow is to paste the key only when
the deployment prompt requests the `NoEcho` parameter:

```powershell
sam deploy --guided
```

Use these values at the SAM prompts:

```text
AppMailDeliveryEnabled: true
AppMailProvider: resend
AppMailFromEmail: TaskHub <otp@mail.taskhubvn.com>
AppMailResendApiKey: <paste the Resend key at the prompt>
```

For CI/CD, inject the same `NoEcho` parameter from the CI secret store. The
following non-interactive command is a reference only; do not paste a real key
into an interactive shell because it may be retained in shell history:

```powershell
sam deploy --parameter-overrides `
  SpringDatasourceUrl="<jdbc-url>" `
  SpringDatasourceUsername="<database-user>" `
  SpringDatasourcePassword="<database-password>" `
  AppJwtSecret="<at-least-32-character-secret>" `
  AppMailDeliveryEnabled="true" `
  AppMailProvider="resend" `
  AppMailFromEmail="TaskHub <otp@mail.taskhubvn.com>" `
  AppMailResendApiKey="<resend-api-key>"
```

After deployment, check **AWS Lambda > taskhub-backend > Configuration >
Environment variables** for the four variable names. Do not reveal or copy the
API key value during verification.

## Production verification

1. Confirm `GET /api/health` returns HTTP 200.
2. Register a new email/password account.
3. Confirm the API response contains `emailOtpRequired=true` and a challenge ID.
4. Confirm the OTP email arrives and does not land in spam.
5. Submit the code and confirm the account becomes email verified.
6. Sign in again and confirm the login OTP creates a session only after the
   correct code is submitted.
7. Confirm an expired, reused, or incorrect code is rejected.

## Switch to SES later

After SES production access is approved and the sender identity is verified,
redeploy with:

```text
AppMailDeliveryEnabled=true
AppMailProvider=ses
AppMailFromEmail=TaskHub <otp@taskhubvn.com>
AppMailResendApiKey=
```

The database, frontend routes, OTP challenge format, and JWT session flow do
not change.
