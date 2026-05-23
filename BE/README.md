# TaskHub Backend — Spring Boot

## Prerequisites
- Java 21 (JDK 21 khuyến nghị; JDK 24 cần Lombok 1.18.38+)
- Maven

## Setup (local — không cần SQL Server)

1. Maven reload project
2. Run `TaskHubApplication` — profile mặc định **`dev`** dùng H2 in-memory
3. API: http://localhost:8080 — Swagger: http://localhost:8080/swagger-ui.html

Chi tiết DB: [docs/DATABASE_SETUP.md](../docs/DATABASE_SETUP.md)  
**API đầy đủ:** [docs/API_REFERENCE.md](../docs/API_REFERENCE.md)

## SQL Server (tùy chọn)

Set `SPRING_PROFILES_ACTIVE=sqlserver` và `SPRING_DATASOURCE_PASSWORD=<your-sa-password>`.

## API Endpoints

### Auth (`/api/auth`) — Public
| Method | Endpoint | Body |
|--------|----------|------|
| POST | `/register` | `{ email, password, fullName, role: "HIRER"/"STUDENT" }` |
| POST | `/login` | `{ email, password }` |

### Tasks (`/api/tasks`) — Authenticated
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/` | Create task (HIRER) |
| GET | `/{id}` | Get task by ID |
| GET | `/mine` | Get my tasks |
| GET | `/available` | Get active tasks (for students) |
| POST | `/{id}/lock` | Lock task (AI validates criteria) |
| POST | `/{id}/complete` | Mark completed |
| POST | `/{id}/revision` | Request revision |
| POST | `/{id}/dispute` | Dispute task |

### Applications (`/api/applications`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/task/{taskId}` | Apply to task (STUDENT) |
| POST | `/{id}/accept` | Accept application (HIRER) |
| GET | `/task/{taskId}` | List task applications |
| GET | `/mine` | My applications |

### Submissions (`/api/submissions`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/task/{taskId}` | Submit work (STUDENT) |
| POST | `/task/{taskId}/approve` | Approve submission (HIRER) |
| GET | `/task/{taskId}` | List submissions |
| GET | `/task/{taskId}/dispute-report` | AI dispute report |

### Escrow (`/api/escrow`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/fund/{taskId}` | Fund escrow (HIRER) |
| POST | `/release/{taskId}` | Release to student |

### Wallet (`/api/wallet`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/balance` | Get wallet balance |
| POST | `/deposit?amount=X` | Deposit funds |

## State Machine
```
DRAFT → LOCKED → ESCROW_FUNDED → ACTIVE → IN_PROGRESS → SUBMITTED → COMPLETED/DISPUTED
```
No state can be skipped. All transitions are validated in the service layer.

## AI Validation
- **Lock task**: Validates acceptance criteria for vague terms
- **Submission**: Scores against criteria (0% blocks, <70% warns)
- **Dispute**: Generates structured report

> **Note**: AI scoring uses keyword matching as placeholder. Replace `AiValidationService` methods with real LLM calls for production.
