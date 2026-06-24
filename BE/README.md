# TaskHub Backend — Spring Boot

## Prerequisites
- Java 21
- PostgreSQL (create DB: `taskhub`)
- Maven

## Setup
1. Create PostgreSQL database: `CREATE DATABASE taskhub;`
2. Update `src/main/resources/application.yml` with your DB credentials and a secure JWT secret (min 256-bit)
3. Run: `./mvnw spring-boot:run`

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

## Admin & Configuration
- Tài khoản admin (auto-seed): [ADMIN_SETUP.md](docs/ADMIN_SETUP.md)
