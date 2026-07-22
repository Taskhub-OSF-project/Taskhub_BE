# TaskHub fast sign-up and email OTP rollout

This rollout keeps the existing TaskHub JWT and OTP flow. Google users are
verified immediately from a Google ID token. Email/password users receive the
existing six-digit OTP through Resend while AWS SES production access is
pending. Switching back to SES later does not require frontend or auth-flow
changes.

## Implementation checklist

- [x] Google registration creates an email-verified TaskHub account without OTP.
- [x] Email/password registration creates a hashed six-digit OTP challenge.
- [x] Login OTP and registration OTP expire after 10 minutes.
- [x] OTP resend is rate-limited and invalidates the previous active challenge.
- [x] Resend implements the shared backend `MailService` contract.
- [x] SES remains selectable with `APP_MAIL_PROVIDER=ses`.
- [x] Resend configuration is represented in `application.yml`, `.env.example`,
      and the AWS SAM template.
- [x] Browser refresh-token cookies require the SPA CSRF header; API clients can
      still submit a refresh token in the request body.
- [x] Resend/OTP tests pass (8 tests) and the full backend suite passes
      (144 tests).
- [x] The Amplify production bundle builds locally and returns HTTP 200 for `/`
      and `/login`.
- [ ] Verify the sending subdomain `mail.taskhubvn.com` in Resend (SPF and DKIM).
- [ ] Create a send-only Resend API key and store it outside source control.
- [ ] Deploy the Lambda with the Resend parameter overrides below.
- [ ] Complete a production end-to-end test with a new email address.
- [ ] After SES production access is approved, switch the provider back to SES.

Do not mark an external item complete until it has been verified in the live
environment.

## Resend setup

1. In Resend, add the sending subdomain `mail.taskhubvn.com`.
2. Add the SPF and DKIM records shown by Resend to the DNS provider.
3. Wait until the Resend domain status is `verified`.
4. Create a send-only API key.
5. Use `TaskHub <otp@mail.taskhubvn.com>` as the sender.

Never put the API key in frontend `VITE_*` variables, Git, documentation, or a
committed `.env` file.

## Lambda configuration

Set these environment values through the SAM parameters:

```text
APP_MAIL_DELIVERY_ENABLED=true
APP_MAIL_PROVIDER=resend
APP_MAIL_FROM_EMAIL=TaskHub <otp@mail.taskhubvn.com>
APP_MAIL_RESEND_API_KEY=<secret>
```

Deploy from `Taskhub_BE/BE`:

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
