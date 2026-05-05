# auth-spring — Project Overview

> **A production-ready, open-source Spring Boot authentication and RBAC template.**
> Clone it. Configure it. Ship it. Never write auth from scratch again.

---

## Table of Contents

1. [Project Identity](#1-project-identity)
2. [Problem Statement](#2-problem-statement)
3. [Core Features](#3-core-features)
4. [Technology Stack](#4-technology-stack)
5. [Architecture](#5-architecture)
6. [Package Structure](#6-package-structure)
7. [Module Breakdown](#7-module-breakdown)
8. [Domain Model](#8-domain-model)
9. [Security Model](#9-security-model)
10. [Default RBAC Seed Data](#10-default-rbac-seed-data)
11. [API Reference](#11-api-reference)
12. [Configuration Reference](#12-configuration-reference)
13. [Token Blacklist — Strategy Pattern](#13-token-blacklist--strategy-pattern)
14. [Audit System](#14-audit-system)
15. [Testing Strategy](#15-testing-strategy)
16. [Database Migrations](#16-database-migrations)
17. [Environment Variables](#17-environment-variables)
18. [Quick Start](#18-quick-start)
19. [How to Use This as a Team Template](#19-how-to-use-this-as-a-team-template)
20. [Extension Points](#20-extension-points)
21. [SOLID Principles Map](#21-solid-principles-map)
22. [Security Hardening Notes](#22-security-hardening-notes)
23. [Roadmap](#23-roadmap)
24. [Contributing](#24-contributing)
25. [License](#25-license)

---

## 1. Project Identity

| Property       | Value                                              |
|----------------|----------------------------------------------------|
| **Group ID**   | `io.epsilon`                                       |
| **Artifact**   | `auth-spring`                                      |
| **Version**    | `0.0.1-SNAPSHOT`                                   |
| **Java**       | 21 LTS (recommended) / 26 preview                  |
| **Spring Boot**| 3.5.x                                              |
| **Database**   | PostgreSQL 15+                                     |
| **Packaging**  | Runnable JAR (not a starter/library)               |
| **License**    | MIT                                                |

---

## 2. Problem Statement

Every new backend project begins with the same ~3-week detour: stand up a user table,
integrate Spring Security, wire JWT issuance and validation, build refresh-token rotation,
design a roles-and-permissions model, write the CRUD endpoints for managing that model,
add audit logging, and cover it all with tests.

**auth-spring eliminates that detour.**

It is a fully working, fully tested Spring Boot application whose sole job is authentication
and role-based access control. Teams clone it, add their own feature packages alongside the
existing `auth/` and `rbac/` modules, and start building product logic on day one — not day
fifteen.

---

## 3. Core Features

### Authentication
- **JWT access tokens** — HS256-signed, short-lived (default 15 min), carry resolved permissions as claims
- **Opaque refresh tokens** — long-lived (default 7 days), stored as SHA-256 hash, support rotation
- **Dual token extraction** — `Authorization: Bearer <token>` header **and** `HttpOnly` cookie, checked in that order
- **Token invalidation** — blacklist-based logout with a pluggable storage strategy (RDBMS by default, Redis as a one-property swap)
- **Refresh token rotation** — every `/refresh` call atomically revokes the old token and issues a new one; a reused token is treated as a replay attack
- **Cookie lifecycle** — `accessToken` cookie is set on login/refresh and cleared (`maxAge=0`) on logout
- **BCrypt password hashing** — cost factor 12

### Role-Based Access Control
- **Three seeded roles** — `ROLE_USER`, `ROLE_SYSTEM_ADMIN`, `ROLE_DEVELOPER` — present on first boot
- **Nine seeded permissions** — covering full CRUD on roles and permissions, plus `all:all`
- **Wildcard permission evaluation** — `all:all` grants everything; `role:*` grants all `role:` actions; exact match as fallback
- **Method-level security** — every management endpoint guarded by `@PreAuthorize("hasPermission(null, 'role:create')")` — expressions that work for all three tier levels automatically
- **Protected CRUD** for roles, permissions, and user–role assignment
- **Idempotent data seeding** — safe to run on every boot against a pre-populated database

### Infrastructure
- **Global `ApiResponse<T>` envelope** — every endpoint returns `{ success, data, error, requestId, timestamp }`
- **Global exception mapping** — validation errors, auth failures, 404s, 409s all return structured JSON
- **Request tracing** — `X-Request-ID` header propagated through MDC, present in every response and log line
- **OpenAPI 3.0 / Swagger UI** — auto-generated, Bearer-auth-enabled, browseable at `/swagger-ui.html`
- **Liquibase schema management** — every table, index, and constraint is version-controlled with rollback blocks
- **Event-driven audit log** — every login, logout, register, and RBAC mutation is recorded via Spring application events, with no coupling between the business service and the persistence of audit records
- **Multi-profile configuration** — `dev` profile relaxes cookie security and enables SQL logging; `prod` profile enforces strict settings

---

## 4. Technology Stack

| Category            | Library / Version                        | Notes                                              |
|---------------------|------------------------------------------|----------------------------------------------------|
| Framework           | Spring Boot 3.5.x                        | `spring-boot-starter-parent`                       |
| Security            | Spring Security 6.x                      | Stateless JWT, method security, custom evaluator   |
| JWT                 | JJWT 0.12.6                              | `jjwt-api`, `jjwt-impl`, `jjwt-jackson`           |
| Persistence         | Spring Data JPA + Hibernate 6.x          | `ddl-auto: validate` — Liquibase owns schema       |
| Database            | PostgreSQL 16 (runtime)                  | H2 not used — Testcontainers for real DB tests     |
| Schema migrations   | Liquibase                                | YAML changelogs, per-table files, rollback blocks  |
| API documentation   | springdoc-openapi 2.8.x                  | `/swagger-ui.html`, `/v3/api-docs`                 |
| Validation          | Jakarta Bean Validation (Hibernate 8.x)  | Records + `@Valid` on all request DTOs             |
| Redis (optional)    | Spring Data Redis                        | Only activated when `app.blacklist.strategy=redis` |
| Testing             | JUnit 5, Mockito, Testcontainers         | PostgreSQL container for slice + integration tests |
| Build               | Maven 3.9+ (Maven Wrapper included)      | `./mvnw verify` runs the full suite                |

---

## 5. Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         HTTP Request                            │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │  RequestIdFilter    │  sets X-Request-ID, MDC
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │  JwtAuthFilter      │  header → cookie → skip
                    │                     │  validates JWT
                    │                     │  checks blacklist
                    │                     │  sets SecurityContext
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                                 │
   ┌──────────▼──────────┐          ┌──────────▼──────────┐
   │   AuthController    │          │   RoleController    │
   │   /api/auth/*       │          │   /api/roles/*      │
   └──────────┬──────────┘          └──────────┬──────────┘
              │                                 │
   ┌──────────▼──────────┐          ┌──────────▼──────────┐
   │    AuthService      │          │    RoleService      │
   │  (business logic)   │          │  (business logic)   │
   └──────────┬──────────┘          └──────────┬──────────┘
              │                                 │
              └────────────┬────────────────────┘
                           │
              ┌────────────▼────────────┐
              │      domain/            │
              │  JPA entities + repos   │
              └────────────┬────────────┘
                           │
              ┌────────────▼────────────┐
              │      PostgreSQL         │
              └─────────────────────────┘

Side channel:
  AuthService ──publishes──▶ ApplicationEvent
                                    │
                          ┌─────────▼──────────┐
                          │  AuditEventListener │
                          └─────────┬──────────┘
                                    │
                          ┌─────────▼──────────┐
                          │   AuditLogEntity   │
                          └────────────────────┘
```

**Key design decisions:**

- The filter chain is **stateless** — no `HttpSession` is ever created
- `UserEntity` implements `UserDetails` directly — no adapter class needed
- `TokenBlacklist` is an interface — the filter and service never reference a concrete implementation
- Domain events decouple the audit system from all business services completely
- `GlobalExceptionHandler` is the single exit point for all error responses — no try/catch in controllers

---

## 6. Package Structure

```
src/main/java/io/epsilon/auth/
│
├── AuthSpringApplication.java
│
├── common/                          ← zero domain logic; consumed by all modules
│   ├── config/
│   │   ├── SecurityConfig.java      ← filter chain, CORS, method security, password encoder
│   │   ├── OpenApiConfig.java       ← Swagger Bearer auth scheme
│   │   ├── JacksonConfig.java       ← ISO-8601 Instant serialization
│   │   └── SchedulingConfig.java    ← @EnableScheduling
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── AuthException.java
│   │   ├── ResourceNotFoundException.java
│   │   ├── EmailAlreadyExistsException.java
│   │   ├── RoleInUseException.java
│   │   └── InvalidPermissionNameException.java
│   ├── response/
│   │   ├── ApiResponse.java         ← generic wrapper record
│   │   └── ApiError.java
│   └── filter/
│       └── RequestIdFilter.java     ← X-Request-ID + MDC
│
├── auth/                            ← authentication module
│   ├── controller/
│   │   └── AuthController.java      ← /login /register /logout /refresh /me
│   ├── service/
│   │   ├── AuthService.java         ← core auth business logic
│   │   ├── JwtService.java          ← issue, validate, parse, extract
│   │   └── RefreshTokenService.java ← issue, rotate, revoke
│   ├── security/
│   │   ├── JwtAuthenticationFilter.java   ← OncePerRequestFilter
│   │   ├── CustomUserDetailsService.java  ← loads UserEntity by email
│   │   └── StarterPermissionEvaluator.java ← wildcard RBAC resolution
│   ├── blacklist/
│   │   ├── TokenBlacklist.java            ← Strategy interface
│   │   ├── RdbmsTokenBlacklist.java       ← default; @ConditionalOnProperty matchIfMissing=true
│   │   └── RedisTokenBlacklist.java       ← opt-in; requires redis on classpath
│   ├── dto/
│   │   ├── request/
│   │   │   ├── LoginRequest.java
│   │   │   ├── RegisterRequest.java
│   │   │   └── RefreshTokenRequest.java
│   │   └── response/
│   │       ├── TokenResponse.java
│   │       └── UserProfileResponse.java
│   └── event/
│       ├── UserRegisteredEvent.java
│       ├── UserLoggedInEvent.java
│       └── UserLoggedOutEvent.java
│
├── rbac/                            ← role-based access control module
│   ├── controller/
│   │   ├── RoleController.java       ← /api/roles
│   │   ├── PermissionController.java ← /api/permissions
│   │   └── UserRoleController.java   ← /api/users/{id}/roles
│   ├── service/
│   │   ├── RoleService.java
│   │   ├── PermissionService.java
│   │   └── DataSeederService.java    ← ApplicationRunner; idempotent seeding
│   ├── dto/
│   │   ├── request/
│   │   │   ├── CreateRoleRequest.java
│   │   │   ├── UpdateRoleRequest.java
│   │   │   ├── CreatePermissionRequest.java
│   │   │   └── AssignPermissionRequest.java
│   │   └── response/
│   │       ├── RoleResponse.java
│   │       └── PermissionResponse.java
│   └── event/
│       ├── RoleCreatedEvent.java
│       ├── RoleUpdatedEvent.java
│       └── PermissionAssignedEvent.java
│
├── domain/                          ← JPA entities + repositories; shared by auth + rbac
│   ├── entity/
│   │   ├── UserEntity.java          ← implements UserDetails
│   │   ├── RoleEntity.java
│   │   ├── PermissionEntity.java
│   │   ├── RefreshTokenEntity.java
│   │   ├── TokenBlacklistEntry.java
│   │   └── AuditLogEntity.java
│   └── repository/
│       ├── UserRepository.java
│       ├── RoleRepository.java
│       ├── PermissionRepository.java
│       ├── RefreshTokenRepository.java
│       ├── TokenBlacklistRepository.java
│       └── AuditLogRepository.java
│
└── audit/                           ← event-driven audit; no direct service coupling
    ├── AuditableEvent.java          ← marker interface
    └── AuditEventListener.java      ← @EventListener; writes AuditLogEntity
```

---

## 7. Module Breakdown

### `common/`
Cross-cutting concerns only. Has no knowledge of `auth/` or `rbac/` domain objects.
Any class placed here must be usable by both modules without introducing a circular dependency.
This module owns the global error contract, the response envelope, and filter infrastructure.

### `auth/`
Everything related to proving who a caller is. This module is responsible for:
- Issuing, validating, and invalidating tokens
- Exposing the five authentication endpoints
- Extracting and verifying tokens on every inbound request
- Evaluating permissions during method-level security checks

This module has a **read-only dependency on `domain/`** — it never defines its own JPA entities.

### `rbac/`
Everything related to what a caller is allowed to do. Responsible for:
- Defining and managing the roles and permissions model
- Seeding the default data on startup
- Exposing the protected CRUD endpoints for roles, permissions, and assignments

This module also has a **read-only dependency on `domain/`**.

### `domain/`
The persistence layer. Contains JPA entities and Spring Data repositories.
No business logic lives here — entities are data containers, repositories are query interfaces.
Both `auth/` and `rbac/` depend on this module. It depends on nothing inside the project.

### `audit/`
Purely reactive. Listens to Spring `ApplicationEvent`s published by `auth/` and `rbac/`,
and persists them to `auth_audit_log`. Adding a new audit consumer (e.g., streaming to Kafka)
requires only adding a second `@EventListener` — no modifications to any publisher.

---

## 8. Domain Model

```
auth_users
  id            UUID PK
  email         VARCHAR(255) UNIQUE NOT NULL
  password_hash VARCHAR(255) NOT NULL
  enabled       BOOLEAN DEFAULT true
  account_non_locked       BOOLEAN DEFAULT true
  account_non_expired      BOOLEAN DEFAULT true
  credentials_non_expired  BOOLEAN DEFAULT true
  created_at    TIMESTAMPTZ NOT NULL
  updated_at    TIMESTAMPTZ NOT NULL

auth_roles
  id            UUID PK
  name          VARCHAR(100) UNIQUE NOT NULL   -- e.g. ROLE_DEVELOPER
  description   TEXT

auth_permissions
  id            UUID PK
  name          VARCHAR(100) UNIQUE NOT NULL   -- format: resource:action
  description   TEXT
  -- DB-level CHECK: name ~ '^[a-z_]+:[a-z_*]+$'

auth_user_roles             (join table)
  user_id       UUID FK → auth_users.id
  role_id       UUID FK → auth_roles.id

auth_role_permissions       (join table)
  role_id       UUID FK → auth_roles.id
  permission_id UUID FK → auth_permissions.id

auth_refresh_tokens
  id                UUID PK
  token_hash        VARCHAR(64) UNIQUE INDEX  -- SHA-256 hex; raw token never stored
  user_id           UUID FK → auth_users.id
  issued_at         TIMESTAMPTZ NOT NULL
  expires_at        TIMESTAMPTZ NOT NULL
  revoked_at        TIMESTAMPTZ              -- null = active
  device_fingerprint VARCHAR(255)            -- reserved for multi-device session isolation

auth_token_blacklist
  id            UUID PK
  jti           VARCHAR(36) INDEX            -- JWT ID claim
  expires_at    TIMESTAMPTZ INDEX            -- used by hourly cleanup job

auth_audit_log              (append-only; never updated or deleted)
  id            UUID PK
  event_type    VARCHAR(50)                  -- LOGIN | LOGOUT | REGISTER | TOKEN_REFRESH |
                                             -- TOKEN_BLACKLISTED | ROLE_CREATED | ...
  actor_id      UUID                         -- nullable (system events have no actor)
  target_id     UUID                         -- nullable
  ip_address    VARCHAR(45)                  -- supports IPv6
  metadata      TEXT                         -- JSON string for context-specific data
  occurred_at   TIMESTAMPTZ NOT NULL
```

**Relationship summary:**
- A `User` holds many `Role`s (M:N)
- A `Role` holds many `Permission`s (M:N)
- A `User`'s effective permissions = union of all permissions across all their roles
- Refresh tokens are 1:N per user (one per session; revoked on logout or rotation)
- Blacklist entries have no FK to users — a blacklisted JTI stays blacklisted even if the user is deleted

---

## 9. Security Model

### Token flow

```
Client                              Server
  │                                    │
  │──── POST /api/auth/login ─────────▶│
  │                                    │  1. AuthenticationManager validates credentials
  │                                    │  2. JwtService issues access token (JWT, 15 min)
  │                                    │  3. RefreshTokenService issues refresh token (opaque, 7 days)
  │◀─── 200 { accessToken, refreshToken } + Set-Cookie: accessToken=<jwt>; HttpOnly ──│
  │                                    │
  │──── GET /api/roles (Bearer: jwt) ─▶│
  │       OR Cookie: accessToken=jwt   │  1. JwtAuthFilter extracts token (header first, then cookie)
  │                                    │  2. JwtService.parseAndValidate(token)
  │                                    │  3. TokenBlacklist.isBlacklisted(jti)
  │                                    │  4. Sets SecurityContext
  │                                    │  5. @PreAuthorize("hasPermission(null, 'role:read')")
  │                                    │     → StarterPermissionEvaluator resolves
  │◀─── 200 [roles list] ─────────────│
  │                                    │
  │──── POST /api/auth/refresh ───────▶│
  │       { refreshToken: "..." }      │  1. RefreshTokenService.validateAndRotate(raw)
  │                                    │  2. Old token revoked atomically
  │                                    │  3. New access + refresh token issued
  │◀─── 200 { new tokens } ───────────│
  │                                    │
  │──── POST /api/auth/logout ────────▶│
  │                                    │  1. Extract JTI from token
  │                                    │  2. TokenBlacklist.add(jti, expiry)
  │                                    │  3. RefreshTokenService.revokeAllForUser(userId)
  │                                    │  4. Clear cookie (maxAge=0)
  │◀─── 200 + Set-Cookie: accessToken=; maxAge=0 ─│
```

### Permission evaluation order (StarterPermissionEvaluator)

```
Required permission: "role:create"

Step 1: Does caller hold "all:all"?
        YES → GRANT (developer role)
        NO  → continue

Step 2: Does caller hold "role:*"?
        YES → GRANT (resource-level wildcard)
        NO  → continue

Step 3: Does caller hold "role:create" exactly?
        YES → GRANT
        NO  → DENY (403 Forbidden)
```

### Publicly accessible endpoints

| Method | Path                    | Notes                        |
|--------|-------------------------|------------------------------|
| POST   | `/api/auth/login`       | No auth required             |
| POST   | `/api/auth/register`    | No auth required             |
| POST   | `/api/auth/refresh`     | Refresh token in request body|
| GET    | `/swagger-ui.html`      | API documentation            |
| GET    | `/v3/api-docs/**`       | OpenAPI spec                 |

All other endpoints require a valid, non-blacklisted JWT.

---

## 10. Default RBAC Seed Data

Seeding runs on application startup via `DataSeederService implements ApplicationRunner`.
Every insert is idempotent — running against a pre-populated database is always safe.

### Seeded permissions

| Permission Name       | Description                               |
|-----------------------|-------------------------------------------|
| `all:all`             | Wildcard — grants access to everything    |
| `role:create`         | Create a new role                         |
| `role:read`           | Read roles and their permission lists     |
| `role:update`         | Update a role's metadata or permissions   |
| `role:delete`         | Delete a role (blocked if users hold it)  |
| `permission:create`   | Create a new permission                   |
| `permission:read`     | List and read permissions                 |
| `permission:update`   | Update a permission's metadata            |
| `permission:delete`   | Delete a permission                       |

### Seeded roles

| Role Name           | Assigned Permissions                                                           | Typical Use                           |
|---------------------|--------------------------------------------------------------------------------|---------------------------------------|
| `ROLE_USER`         | *(none)*                                                                       | Every registered user starts here     |
| `ROLE_SYSTEM_ADMIN` | `role:create/read/update/delete` + `permission:create/read/update/delete`      | DevOps and platform administrators    |
| `ROLE_DEVELOPER`    | `all:all`                                                                      | Internal developers; full access      |

> **Security note:** `ROLE_DEVELOPER` should never exist in a production database with real end users.
> Set `app.seeding.enabled=false` in production and manage users out-of-band, or delete the
> developer user immediately after seeding with a password change.

---

## 11. API Reference

All responses follow the `ApiResponse<T>` envelope:

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "requestId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

### Authentication endpoints (`/api/auth`)

#### `POST /api/auth/register`
Register a new user. Assigns `ROLE_USER` automatically.

**Request:**
```json
{
  "email": "alice@example.com",
  "password": "SecurePass123!",
  "fullName": "Alice Smith"
}
```
**Response:** `201 Created` — `ApiResponse<Void>`

---

#### `POST /api/auth/login`
Authenticate and receive tokens. Sets `accessToken` HttpOnly cookie if `app.cookie.enabled=true`.

**Request:**
```json
{ "email": "alice@example.com", "password": "SecurePass123!" }
```
**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "550e8400-e29b-...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

---

#### `POST /api/auth/refresh`
Rotate tokens. The provided refresh token is immediately revoked.

**Request:**
```json
{ "refreshToken": "550e8400-e29b-..." }
```
**Response:** `200 OK` — same shape as `/login`

---

#### `POST /api/auth/logout`
Blacklist the current access token and revoke all refresh tokens for the user.
Also clears the `accessToken` cookie.

**Auth:** Bearer token or cookie required.
**Response:** `200 OK` — `ApiResponse<Void>`

---

#### `GET /api/auth/me`
Return the authenticated user's profile with their active role names and resolved permission set.

**Auth:** Required.
**Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "550e8400-...",
    "email": "alice@example.com",
    "roles": ["ROLE_SYSTEM_ADMIN"],
    "permissions": ["role:create", "role:read", "role:update", "role:delete",
                    "permission:create", "permission:read", "permission:update", "permission:delete"]
  }
}
```

---

### Role management endpoints (`/api/roles`)

| Method   | Path                              | Permission Required | Description                      |
|----------|-----------------------------------|---------------------|----------------------------------|
| `GET`    | `/api/roles`                      | `role:read`         | List roles (paginated)           |
| `GET`    | `/api/roles/{id}`                 | `role:read`         | Get role by ID                   |
| `POST`   | `/api/roles`                      | `role:create`       | Create a new role                |
| `PUT`    | `/api/roles/{id}`                 | `role:update`       | Update role description          |
| `DELETE` | `/api/roles/{id}`                 | `role:delete`       | Delete role (fails if in use)    |
| `POST`   | `/api/roles/{id}/permissions/{p}` | `role:update`       | Assign permission to role        |
| `DELETE` | `/api/roles/{id}/permissions/{p}` | `role:update`       | Remove permission from role      |

---

### Permission management endpoints (`/api/permissions`)

| Method   | Path                        | Permission Required    | Description              |
|----------|-----------------------------|------------------------|--------------------------|
| `GET`    | `/api/permissions`          | `permission:read`      | List permissions         |
| `GET`    | `/api/permissions/{id}`     | `permission:read`      | Get permission by ID     |
| `POST`   | `/api/permissions`          | `permission:create`    | Create permission        |
| `PUT`    | `/api/permissions/{id}`     | `permission:update`    | Update description       |
| `DELETE` | `/api/permissions/{id}`     | `permission:delete`    | Delete permission        |

**Permission name format:** Must match `^[a-z_]+:[a-z_*]+$`
Examples: `invoice:create`, `report:read`, `product:*`

---

### User role assignment (`/api/users/{userId}/roles`)

| Method   | Path                                  | Permission Required | Description           |
|----------|---------------------------------------|---------------------|-----------------------|
| `POST`   | `/api/users/{userId}/roles/{roleId}`  | `role:update`       | Assign role to user   |
| `DELETE` | `/api/users/{userId}/roles/{roleId}`  | `role:update`       | Remove role from user |

---

### Error response codes

| HTTP Status | `error.code`          | Cause                                                 |
|-------------|------------------------|-------------------------------------------------------|
| 400         | `VALIDATION_ERROR`     | Bean Validation failed on request body                |
| 401         | `AUTH_ERROR`           | Bad credentials                                       |
| 401         | `TOKEN_EXPIRED`        | JWT access token past its expiry                      |
| 401         | `TOKEN_INVALID`        | Malformed or tampered JWT                             |
| 403         | `FORBIDDEN`            | Valid token but insufficient permission               |
| 404         | `NOT_FOUND`            | Requested resource does not exist                     |
| 409         | `ROLE_IN_USE`          | Attempted to delete a role assigned to active users   |
| 500         | `INTERNAL_ERROR`       | Unexpected server error (details logged, not exposed) |

---

## 12. Configuration Reference

All properties live under the `app.*` namespace. Full reference:

```yaml
app:

  jwt:
    secret: ""                          # REQUIRED. Base64-encoded HS256 key, min 32 chars.
                                        # Generate: openssl rand -base64 32
    access-token-ttl-seconds: 900       # 15 minutes. Min recommended: 300.
    refresh-token-ttl-seconds: 604800   # 7 days.

  cookie:
    enabled: true                       # Set false for pure SPA/mobile API (header-only mode)
    name: accessToken
    http-only: true                     # Never set to false in production
    secure: true                        # Set false only in dev profile (HTTP)
    same-site: Strict                   # Options: Strict | Lax | None
    path: /

  blacklist:
    strategy: rdbms                     # Options: rdbms | redis
    cleanup-cron: "0 0 * * * *"         # Spring cron. Default: every hour on the hour.

  seeding:
    enabled: true                       # Set false to skip all seeding in production
    developer-email: ""                 # Optional. If set, creates a ROLE_DEVELOPER user on first boot.
    developer-initial-password: ""      # Required if developer-email is set.

  cors:
    allowed-origins: "http://localhost:3000"  # Comma-separated list
    allowed-methods: "GET,POST,PUT,DELETE,OPTIONS"
    max-age-seconds: 3600

  openapi:
    title: "Auth Spring API"
    description: "Authentication and RBAC layer"
    version: "1.0.0"
```

---

## 13. Token Blacklist — Strategy Pattern

The blacklist is the mechanism that makes logout meaningful for stateless JWTs.

### Interface contract

```java
public interface TokenBlacklist {
    void add(String jti, Instant expiresAt);
    boolean isBlacklisted(String jti);
}
```

### RDBMS strategy (default)

- Activated when `app.blacklist.strategy=rdbms` or when the property is absent
- Stores `(jti, expires_at)` rows in `auth_token_blacklist`
- Composite index on `(jti, expires_at)` for O(log n) lookups
- Hourly cleanup job prunes expired rows via `@Scheduled` — the table stays bounded

### Redis strategy (opt-in)

To switch:
1. Add to `pom.xml`: `spring-boot-starter-data-redis`
2. Set `app.blacklist.strategy=redis` in `application.yml`
3. Configure `spring.data.redis.*` connection properties

That is the complete migration. No application source code changes.

Redis stores `"bl:<jti>" → "1"` with a TTL equal to the token's remaining lifetime.
Entries evict automatically — no cleanup job needed.

### Adding a new strategy (e.g. Hazelcast)

```java
@Component
@ConditionalOnProperty(name = "app.blacklist.strategy", havingValue = "hazelcast")
public class HazelcastTokenBlacklist implements TokenBlacklist {
    // implement add() and isBlacklisted()
}
```

Set `app.blacklist.strategy=hazelcast`. No other changes required anywhere in the codebase.

---

## 14. Audit System

The audit system is completely decoupled from the business modules via Spring's `ApplicationEvent` mechanism.

### Published events

| Event Class              | Published by        | Trigger                                    |
|--------------------------|---------------------|--------------------------------------------|
| `UserRegisteredEvent`    | `AuthService`       | Successful `/register`                     |
| `UserLoggedInEvent`      | `AuthService`       | Successful `/login`                        |
| `UserLoggedOutEvent`     | `AuthService`       | `/logout` call                             |
| `RoleCreatedEvent`       | `RoleService`       | `POST /api/roles`                          |
| `RoleUpdatedEvent`       | `RoleService`       | `PUT /api/roles/{id}`                      |
| `PermissionAssignedEvent`| `RoleService`       | `POST /api/roles/{id}/permissions/{p}`     |

### Consumer

`AuditEventListener` in `audit/` package listens via `@EventListener` and writes `AuditLogEntity`.

### Adding your own audit consumer

```java
@Component
public class SlackAlertListener {

    @EventListener
    public void onLogin(UserLoggedInEvent event) {
        // send Slack notification for suspicious logins
    }
}
```

No modifications to `AuthService`. The publisher doesn't know you exist.

### Extending to async / distributed events

Replace `ApplicationEventPublisher` with a custom implementation backed by Kafka or SQS.
Because all publishers depend only on the `ApplicationEventPublisher` interface (Spring's own),
no business service changes are required.

---

## 15. Testing Strategy

The test suite operates at three layers. Each layer tests what it uniquely owns.

### Layer 1 — Unit tests (no Spring context)

Fast. Milliseconds per test. Zero I/O.
Every class with business logic has a dedicated unit test that mocks all dependencies.

| Test class                      | What it covers                                                   |
|---------------------------------|------------------------------------------------------------------|
| `JwtServiceTest`                | Issue, validate, parse, expired token, tampered signature, clock |
| `StarterPermissionEvaluatorTest`| `all:all`, `role:*`, exact match, no match, unauthenticated      |
| `RdbmsTokenBlacklistTest`       | `add` delegates to repo, `isBlacklisted` uses expiry check       |
| `AuthServiceTest`               | Login, register, logout, refresh — all happy + failure paths     |
| `RoleServiceTest`               | CRUD + `RoleInUseException` on delete                            |
| `PermissionServiceTest`         | Valid name format, invalid format, duplicate name                |
| `DataSeederServiceTest`         | Idempotency — second run produces no additional DB writes        |
| `RefreshTokenServiceTest`       | Issue, rotate, revoke, replay attack detection                   |

### Layer 2 — Repository slice tests (`@DataJpaTest` + Testcontainers)

Real PostgreSQL. Tests Liquibase migrations, constraints, and custom query methods.
`@AutoConfigureTestDatabase(replace = NONE)` ensures H2 is never used.

Key assertions:
- All 8 Liquibase changelogs apply cleanly to a fresh container
- All changelogs roll back cleanly
- Unique email constraint raises `DataIntegrityViolationException`
- `TokenBlacklistRepository.existsByJtiAndExpiresAtAfter` correctly handles boundary times
- `deleteByExpiresAtBefore` deletes exactly the right rows

### Layer 3 — Integration tests (`@SpringBootTest` + Testcontainers)

Full application context. Real HTTP via `MockMvc`. Real PostgreSQL.

Key scenarios:

```
Scenario 1: Full auth flow
  Register → Login → GET /me → assert permissions match seeded role

Scenario 2: Logout invalidates token
  Login → Logout → reuse access token → expect 401 TOKEN_EXPIRED or TOKEN_INVALID

Scenario 3: Token rotation rejects replay
  Login → Refresh → attempt second Refresh with same old refresh token → expect 401

Scenario 4: Token expiry
  Set access-token-ttl-seconds=1 in test properties → sleep 2s → GET /me → expect 401 TOKEN_EXPIRED

Scenario 5: RBAC enforcement
  Login as ROLE_USER → POST /api/roles → expect 403 FORBIDDEN
  Login as ROLE_SYSTEM_ADMIN → POST /api/roles → expect 201 Created

Scenario 6: Developer wildcard
  Login as ROLE_DEVELOPER → POST /api/roles → expect 201 Created (via all:all)

Scenario 7: Seeder idempotency
  Boot context → call run() manually twice → assert role + permission counts unchanged
```

### Running tests

```bash
# Unit tests only (fast, no Docker required)
./mvnw test -Dgroups="unit"

# All tests including integration (Docker required for Testcontainers)
./mvnw verify

# With coverage report
./mvnw verify jacoco:report
# Report at: target/site/jacoco/index.html
```

---

## 16. Database Migrations

All schema changes live in `src/main/resources/db/changelog/`.

```
db/changelog/
├── db.changelog-master.yaml          ← root; includes all change files in order
└── changes/
    ├── 0001_create_users.yaml
    ├── 0002_create_roles.yaml
    ├── 0003_create_permissions.yaml
    ├── 0004_create_user_roles.yaml
    ├── 0005_create_role_permissions.yaml
    ├── 0006_create_refresh_tokens.yaml
    ├── 0007_create_token_blacklist.yaml
    └── 0008_create_audit_log.yaml
```

**Rules:**
- One file per table
- Every changeset has a `rollback:` block
- Never modify a changeset that has been applied to any shared environment — always add a new one
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate validates against the Liquibase-managed schema but never modifies it

**Adding a new table for your feature:**
```yaml
# db/changelog/changes/0009_create_your_table.yaml
databaseChangeLog:
  - changeSet:
      id: 0009_create_your_table
      author: your-github-username
      changes:
        - createTable:
            tableName: your_table
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true } }
              # ...
      rollback:
        - dropTable: { tableName: your_table }
```

Add the include to `db.changelog-master.yaml` and you're done.

---

## 17. Environment Variables

| Variable            | Required | Default              | Description                                      |
|---------------------|----------|----------------------|--------------------------------------------------|
| `JWT_SECRET`        | **Yes**  | —                    | Base64-encoded HS256 signing key, min 32 chars   |
| `DB_URL`            | No       | `jdbc:postgresql://localhost:5432/auth_db` | JDBC connection URL     |
| `DB_USER`           | No       | `postgres`           | Database username                                |
| `DB_PASS`           | No       | `password`           | Database password                                |
| `DEV_EMAIL`         | No       | `dev@example.com`    | Email for the seeded developer user              |
| `DEV_PASSWORD`      | No       | `ChangeMe123!`       | Initial password for the seeded developer user   |
| `CORS_ORIGINS`      | No       | `http://localhost:3000` | Comma-separated allowed CORS origins          |
| `REDIS_HOST`        | No       | `localhost`          | Redis host (only needed if strategy=redis)       |
| `REDIS_PORT`        | No       | `6379`               | Redis port                                       |

Generate a secure JWT secret:
```bash
openssl rand -base64 32
```

---

## 18. Quick Start

### Prerequisites

- Java 21+
- Docker and Docker Compose
- Maven 3.9+ (or use the included `./mvnw` wrapper)

### 1. Clone

```bash
git clone https://github.com/your-org/auth-spring.git
cd auth-spring
```

### 2. Start PostgreSQL

```bash
docker compose up -d
```

### 3. Configure secrets

```bash
cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml
# Edit application-dev.yml and set JWT_SECRET (or export as env var)
export JWT_SECRET=$(openssl rand -base64 32)
```

### 4. Run

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 5. Explore the API

Open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

The seeded developer account is ready:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@example.com","password":"ChangeMe123!"}'
```

---

## 19. How to Use This as a Team Template

This project is designed to be cloned, not forked as a library.
The `auth/` and `rbac/` modules are stable infrastructure — your team's business logic
goes alongside them in new sibling modules.

### Workflow for each new project

```bash
# 1. Clone as a new project
git clone https://github.com/your-org/auth-spring.git my-new-project
cd my-new-project

# 2. Point to your own repository
git remote set-url origin https://github.com/your-org/my-new-project.git
git push -u origin main

# 3. Update pom.xml coordinates
#    Change artifactId, name, and description

# 4. Add your feature modules alongside auth/ and rbac/
mkdir -p src/main/java/io/epsilon/auth/invoicing

# 5. Start building
```

### What to keep

- All of `auth/`, `rbac/`, `domain/`, `audit/`, `common/`
- All Liquibase changelogs
- `docker-compose.yml`
- CI workflow

### What to customize per project

- `application.yml` — update `app.openapi.title` and `app.cors.allowed-origins`
- Add new feature packages under `io.epsilon.auth.*`
- Add new Liquibase changelogs numbered from `0009_*`
- Add new permissions to the seed data if your features require access control
- Update `README.md` to describe the specific project

### Adding a new feature with access control

```java
// 1. Add your permission to DataSeederService.seedPermissions()
"invoice:create", "invoice:read", "invoice:update", "invoice:delete"

// 2. Assign to the appropriate role in DataSeederService.seedRole()
seedRole("ROLE_SYSTEM_ADMIN", Set.of(
    // ... existing permissions ...
    "invoice:create", "invoice:read", "invoice:update", "invoice:delete"
));

// 3. Use in your controller
@PostMapping
@PreAuthorize("hasPermission(null, 'invoice:create')")
public ApiResponse<InvoiceResponse> create(@RequestBody @Valid CreateInvoiceRequest req) { ... }
```

The wildcard evaluation in `StarterPermissionEvaluator` means `ROLE_DEVELOPER` users
automatically have access to your new permissions via `all:all` — no changes needed.

---

## 20. Extension Points

The project is designed with explicit extension points. None of them require modifying existing source files.

| Extension point                | How to extend                                                                  |
|--------------------------------|--------------------------------------------------------------------------------|
| Token blacklist strategy       | Implement `TokenBlacklist`, add `@ConditionalOnProperty`                       |
| Audit event consumers          | Add `@EventListener` methods anywhere in the context                           |
| Domain events                  | Add more `ApplicationEvent` subclasses and publish from services               |
| Permission name validation     | Override `PermissionService` bean with `@Primary`                              |
| Token extraction logic         | Extend or replace `JwtAuthenticationFilter`                                    |
| Custom `UserDetails` fields    | Add columns to `auth_users` via a new Liquibase changeset + update `UserEntity`|
| Rate limiting                  | Add a `HandlerInterceptor` bean — the filter chain has an injection point      |
| Device fingerprinting          | Populate `RefreshTokenEntity.deviceFingerprint` from your `HttpServletRequest` |
| Multi-tenancy                  | Enable the Hibernate `tenantId` filter on `UserEntity` (column already present)|
| Security filter chain override | Define your own `SecurityFilterChain` bean — the existing one backs off       |

---

## 21. SOLID Principles Map

| Principle | Concrete location                                              |
|-----------|----------------------------------------------------------------|
| **S** — Single Responsibility | `JwtService` only handles token cryptography. `AuthService` only orchestrates auth flows. `AuditEventListener` only writes audit rows. No class has more than one reason to change. |
| **O** — Open / Closed | `TokenBlacklist` interface: add new storage strategies without touching `JwtAuthFilter`, `AuthService`, or `SecurityConfig`. `AuditEventListener`: add consumers without touching publishers. |
| **L** — Liskov Substitution | `RdbmsTokenBlacklist` and `RedisTokenBlacklist` are fully substitutable. The application behaves identically regardless of which concrete implementation is active. |
| **I** — Interface Segregation | `TokenBlacklist` has exactly two methods. Implementations are not forced to implement unneeded behaviour. `AuditableEvent` is a pure marker interface. |
| **D** — Dependency Inversion | `SecurityConfig`, `AuthService`, and `JwtAuthFilter` depend on `TokenBlacklist` (the abstraction), never on `RdbmsTokenBlacklist` or `RedisTokenBlacklist` (the details). |

---

## 22. Security Hardening Notes

### Production checklist

- [ ] `JWT_SECRET` is at least 32 random bytes, stored in a secrets manager (not in `application.yml`)
- [ ] `app.cookie.secure=true` (enforced by default; only `application-dev.yml` relaxes this)
- [ ] `app.seeding.developer-email` is not set, or the developer user's password has been changed
- [ ] `app.cors.allowed-origins` is set to your actual frontend domain, not `*`
- [ ] PostgreSQL is not accessible from the public internet
- [ ] HTTPS is terminated at the load balancer or reverse proxy (not at Spring — let the infrastructure handle TLS)
- [ ] `spring.jpa.hibernate.ddl-auto=validate` (default) — never `update` or `create` in production
- [ ] Liquibase changelog history is committed to version control

### Known trade-offs

**CSRF disabled:** The filter chain disables CSRF protection because stateless JWT auth is used.
If you enable cookie-based sessions in addition to JWTs, re-enable CSRF.

**BCrypt cost factor 12:** Appropriate for 2024 hardware. Re-evaluate annually.
If you increase it, existing passwords are not automatically re-hashed — only on next login.

**Refresh token storage:** Refresh tokens are stored as SHA-256 hashes.
The raw token is returned once to the client and never persisted.
A database compromise does not expose valid refresh tokens.

**Blacklist lookup on every request:** Every authenticated request hits the blacklist store.
With RDBMS, this is an indexed read — fast. With Redis, it is a single key lookup — faster.
If you have millions of concurrent requests and no CDN, measure this under load.

---

## 23. Roadmap

| Version | Planned features                                                                        |
|---------|-----------------------------------------------------------------------------------------|
| 0.1.0   | All Phase 1–9 deliverables — stable auth + RBAC foundation                              |
| 0.2.0   | OAuth2 / social login (Google, GitHub) via Spring OAuth2 Client                         |
| 0.3.0   | Two-factor authentication (TOTP via Google Authenticator)                               |
| 0.4.0   | Account lockout after N failed login attempts + unlock endpoint                         |
| 0.5.0   | Password reset flow (email-based, time-limited token)                                   |
| 0.6.0   | Device fingerprinting + per-device session management in refresh token rotation         |
| 0.7.0   | Multi-tenancy activation (the schema column is already present — enable the HQL filter) |
| 1.0.0   | Stable API, full Javadoc, published to GitHub Packages                                  |

---

## 24. Contributing

Contributions are welcome. The full contributing guide is in `CONTRIBUTING.md`.

**Quick rules:**

1. Every PR must include tests — unit tests for business logic, integration test for any new endpoint
2. No business logic in controllers — they are thin HTTP adapters only
3. No `@Value` in business services — use `AppProperties` injection
4. New database columns go in new Liquibase changelogs — never modify an existing changeset
5. New permissions must be added to `DataSeederService` and documented in this file
6. All public methods on services require Javadoc
7. Run `./mvnw verify` locally before opening a PR — the CI will reject failures

**Reporting vulnerabilities:**
Please do not open a public issue for security vulnerabilities.
Email the details to `security@epsilon.io` (or the address in `SECURITY.md`).

---

## 25. License

```
MIT License

Copyright (c) 2025 Epsilon Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

---

*Last updated: 2025 — auth-spring v0.0.1-SNAPSHOT*
*Maintained by the Epsilon Team*