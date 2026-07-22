# Update Backend and Supabase Database Commands

Use these commands when you changed backend code locally and want to update AWS
Lambda or Supabase.

## 1. Update BE to AWS Lambda

Run from PowerShell:

```powershell
cd D:\TaskHub1.0\Taskhub_BE\BE
..\..\maven\apache-maven-3.9.6\bin\mvn.cmd clean package -DskipTests
& "C:\Program Files\Amazon\AWSSAMCLI\bin\sam.cmd" deploy
```

If `sam` works in your terminal, this shorter command is also OK:

```powershell
sam deploy
```

After deploy, test the backend:

```powershell
Invoke-WebRequest "https://6meekld3r6.execute-api.ap-southeast-1.amazonaws.com/Prod/api/health" -UseBasicParsing
```

Expected result:

```text
StatusCode: 200
```

The response body should contain:

```json
{"status":"UP","service":"taskhub-backend","timestamp":"..."}
```

## 2. Update Supabase Database Through AWS Lambda

The Lambda environment currently uses:

```text
SPRING_PROFILES_ACTIVE=supabase
SPRING_JPA_HIBERNATE_DDL_AUTO=update
```

Because of this, when the backend starts on AWS Lambda, Hibernate can update the
Supabase schema from your JPA entities.

Normal flow:

```powershell
cd D:\TaskHub1.0\Taskhub_BE\BE
..\..\maven\apache-maven-3.9.6\bin\mvn.cmd clean package -DskipTests
& "C:\Program Files\Amazon\AWSSAMCLI\bin\sam.cmd" deploy
Invoke-WebRequest "https://6meekld3r6.execute-api.ap-southeast-1.amazonaws.com/Prod/api/health" -UseBasicParsing
```

The last command wakes up Lambda. During startup, Spring Boot connects to
Supabase and Hibernate applies compatible schema updates.

## 3. Update Supabase Database From Local BE

Use this when you want your local backend to connect directly to Supabase and
apply Hibernate schema updates without deploying to AWS first.

```powershell
cd D:\TaskHub1.0\Taskhub_BE\BE
$env:SPRING_PROFILES_ACTIVE="supabase"
$env:SPRING_DATASOURCE_PASSWORD="<your-supabase-db-password>"
..\..\maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
```

Stop the server after it starts successfully:

```text
Ctrl + C
```

Do not write the real database password into git, docs, `application.yml`, or
`samconfig.toml`.

## 4. Deploy With Parameters Again

If SAM asks for parameters again, pass them like this:

```powershell
cd D:\TaskHub1.0\Taskhub_BE\BE
& "C:\Program Files\Amazon\AWSSAMCLI\bin\sam.cmd" deploy --parameter-overrides `
  SpringDatasourcePassword="<your-supabase-db-password>" `
  AppJwtSecret="<at-least-32-character-secret>"
```

Generate a JWT secret in PowerShell:

```powershell
$bytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

## 5. Useful Checks

Check AWS identity:

```powershell
aws sts get-caller-identity
```

Check SAM version:

```powershell
& "C:\Program Files\Amazon\AWSSAMCLI\bin\sam.cmd" --version
```

Check Lambda logs:

```powershell
$env:PYTHONIOENCODING="utf-8"
aws logs tail /aws/lambda/taskhub-backend --region ap-southeast-1 --since 10m --format short
```

Test auth route reaches backend:

```powershell
Invoke-WebRequest `
  "https://6meekld3r6.execute-api.ap-southeast-1.amazonaws.com/Prod/api/auth/login" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"email":"test@example.com","password":"wrong"}' `
  -UseBasicParsing
```

With fake data, `400 Bad Request` is acceptable. It means the request reached
the backend controller.

## 6. Production Note

`SPRING_JPA_HIBERNATE_DDL_AUTO=update` is convenient during development. Before
production, switch to a migration tool such as Flyway or Liquibase and change
Hibernate DDL mode to `validate`.
