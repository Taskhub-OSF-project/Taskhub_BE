# TaskHub Backend — Spring Boot

Production-ready JWT authentication, RBAC, Flyway migrations, and PostgreSQL.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (optional, for PostgreSQL + backend)

## Quick Start

### Option A — Docker (recommended)

```bash
cd Taskhub_BE
docker compose up --build
```

API: http://localhost:8080  
Swagger: http://localhost:8080/swagger-ui.html

### Option B — Local dev (H2, no Docker)

```bash
cd BE
mvn spring-boot:run
# default profile: dev (H2 in-memory + Flyway)
```

H2 Console: http://localhost:8080/h2-console  
JDBC URL: `jdbc:h2:mem:taskhub`

### Option C — Local PostgreSQL

```bash
docker compose up postgres -d
cd BE
SPRING_PROFILES_ACTIVE=postgres mvn spring-boot:run
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | `dev`, `postgres`, or `prod` | `dev` |
| `APP_JWT_SECRET` | HMAC secret (≥ 32 bytes, required in prod) | dev default |
| `APP_JWT_ACCESS_EXPIRATION_MS` | Access token TTL | `900000` (15 min) |
| `APP_JWT_REFRESH_EXPIRATION_MS` | Refresh token TTL | `604800000` (7 days) |
| `APP_CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins | `http://localhost:5173,...` |
| `APP_FRONTEND_BASE_URL` | Base URL for email links | `http://localhost:5173` |
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/taskhub` |
| `DATABASE_USERNAME` | DB user | `taskhub` |
| `DATABASE_PASSWORD` | DB password | `taskhub` |

## Authentication Flow

```
1. POST /api/auth/register  → accessToken + refreshToken
2. POST /api/auth/login     → accessToken + refreshToken
3. API calls                → Authorization: Bearer <accessToken>
4. POST /api/auth/refresh   → new accessToken + refreshToken (rotation)
5. POST /api/auth/logout    → revoke refresh token(s) [requires Bearer]
```

### Refresh Token Rotation

- Refresh tokens are **opaque** (256-bit random), stored as SHA-256 hash in DB
- On refresh: old token revoked, new pair issued
- On password change/reset: all refresh tokens revoked
- Logout with `{ "refreshToken": "..." }` revokes only that device session

### Account Security

| Method | Endpoint | Auth |
|--------|----------|------|
| PATCH | `/api/users/change-password` | Bearer |
| POST | `/api/auth/forgot-password` | Public |
| POST | `/api/auth/reset-password` | Public |
| POST | `/api/auth/verify-email` | Public |
| GET | `/api/users/me` | Bearer |
| PATCH | `/api/users/me` | Bearer |

## RBAC Matrix

Domain roles: **HIRER** (employer) and **STUDENT** (candidate).  
Spring Security authorities: `ROLE_HIRER`, `ROLE_STUDENT`.

| Resource | HIRER | STUDENT |
|----------|-------|---------|
| Create/edit/delete tasks | ✅ | ❌ |
| Lock/publish/complete tasks | ✅ | ❌ |
| Fund/release escrow | ✅ | ❌ |
| Accept applications | ✅ | ❌ |
| Approve submissions | ✅ | ❌ |
| Open/resolve disputes | ✅ | ❌ |
| Browse available tasks | ✅ | ✅ |
| Apply to tasks | ❌ | ✅ |
| Submit work / precheck | ❌ | ✅ |
| Wallet (own) | ✅ | ✅ |
| Profile (`/api/users/me`) | ✅ | ✅ |

Enforced via `@PreAuthorize("hasRole('HIRER')")` / `hasRole('STUDENT')` on controllers.

## Security Architecture

- **JWT access tokens** (15 min) — HMAC-SHA256, claim `type=access`
- **Opaque refresh tokens** (7 days) — DB-backed, revocable, rotated
- **BCrypt** password hashing (strength 10)
- **Rate limiting** (Bucket4j): login, refresh, forgot-password
- **CORS**: configurable origins, credentials enabled
- **Secure headers**: HSTS, X-Content-Type-Options, Referrer-Policy
- **CSRF disabled** — stateless JWT API (see `SecurityConfig` javadoc)
- **Audit logging**: structured `AUDIT` logger + `security_events` table

## Flyway

Migrations: `src/main/resources/db/migration/`

Schema managed by Flyway; Hibernate `ddl-auto=validate`.

## Testing

```bash
cd BE
mvn test
```

Security tests: `JwtServiceTest`, `AuthServiceTest`, `AuthIntegrationTest`

## CI/CD

GitHub Actions: `.github/workflows/backend-ci.yml`

## State Machine

```
DRAFT → LOCKED → ESCROW_FUNDED → ACTIVE → IN_PROGRESS → SUBMITTED → COMPLETED/DISPUTED
```
