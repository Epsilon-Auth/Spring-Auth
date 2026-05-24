# auth-spring — Architecture Plan

*Spring Boot 4.0.6 · Java 25 · Module-Layered Architecture · Production-Ready*

| Property | Value |
|---|---|
| Group ID | `io.epsilon` |
| Artifact | `auth-spring` |
| Spring Boot | `4.0.6` |
| Spring Framework | `7.x` (managed by Boot BOM) |
| Spring Security | `7.x` (managed by Boot BOM) |
| Java | `25` (LTS) |
| Database | PostgreSQL 16+ |
| JWT Library | JJWT `0.12.x` |
| Architecture | Module-Layered — entity · repository · service · usecase · web |
| License | MIT |

---

## 1. Architecture Overview

`auth-spring` is a production-ready authentication and RBAC template. The architecture groups all code under a `module/` parent package. Each feature module (`auth`, `rbac`, `audit`) owns its entities, repositories, services, use cases, and web controllers. Cross-cutting infrastructure lives in `shared/`.

### 1.1 Core Design Decisions

| Decision | Rationale |
|---|---|
| Single Maven module | Template scope — multi-module adds Maven reactor complexity with no benefit here |
| `module/` parent package | Groups all features and shared concerns under one readable root |
| Feature-based top-level packages | `auth/`, `rbac/`, `audit/` own everything that changes together |
| `shared/security/` for cross-cutting security | `SecurityConfig` governs all modules — it cannot live inside one |
| `service/` for orchestration + infrastructure services | Technology-coupled services (JwtService, TokenBlacklistService) live alongside application orchestration |
| `usecase/` for pure business logic | One class per use case. Minimal dependencies, targeted testing, clear intent |
| Direct repository injection in use cases | No port/adapter indirection. Use cases inject Spring Data repositories directly |
| `entity/event/` events owned by publisher | Events live in the publishing module's `entity/event/` sub-package. `audit` subscribes — never the reverse |
| Records for all non-entity types | Java 25 records eliminate boilerplate for DTOs, events, value objects, responses |
| Virtual thread executor for async audit | Project Loom provides lightweight threads; no pool tuning needed |
| Strategy pattern for `TokenBlacklist` | Internal interface in `auth/service/`. Swap RDBMS ↔ Redis by changing one property |
| `UserPrincipal` in `shared/security/` | Decouples JPA model from Spring Security; zero DB hit per authenticated request |
| `ddl-auto: validate` + Liquibase | Liquibase owns schema; Hibernate only validates at startup |

### 1.2 Layer Dependency Rules

```
┌──────────────────────────────────────────────────────────────┐
│  web/  — HTTP Interface                                       │
│  Knows about: usecase/ and service/ classes                   │
│  Never imports: entity JPA types, Security internals          │
└──────────────────────────────────────────────────────────────┘
                            ↓ depends on
┌──────────────────────────────────────────────────────────────┐
│  usecase/  — Business Logic (one class per use case)         │
│  Knows about: repository/, service/, shared/                  │
│  Publishes: ApplicationEvents for side effects               │
│  Never imports: web/, HttpServletRequest, @Entity             │
└──────────────────────────────────────────────────────────────┘
                            ↓ depends on
┌──────────────────────────────────────────────────────────────┐
│  service/  — Orchestration + Infrastructure Services         │
│  Knows about: repository/, entity/, shared/                   │
│  Contains: JwtService, TokenBlacklistService, orchestrators  │
└──────────────────────────────────────────────────────────────┘
                            ↓ depends on
┌──────────────────────────────────────────────────────────────┐
│  repository/  — Spring Data JPA Interfaces                   │
│  Knows about: entity/ JPA types only                         │
│  Never contains: business logic                              │
└──────────────────────────────────────────────────────────────┘
                            ↓ depends on
┌──────────────────────────────────────────────────────────────┐
│  entity/  — JPA Entities, Enums, Value Objects, Events       │
│  Knows about: Spring JPA annotations only                    │
│  entity/event/ — domain event records (publisher-owned)      │
└──────────────────────────────────────────────────────────────┘
                            ↑ all layers depend on
┌──────────────────────────────────────────────────────────────┐
│  shared/  — Cross-Cutting Concerns                            │
│  Knows about: nothing — no feature module imports            │
│  Contains: config, exceptions, ApiResponse, security filters  │
│  EXCEPTION: shared/security/ imports auth/service/JwtService │
│  (filter needs JWT cryptography — documented permitted dep)  │
└──────────────────────────────────────────────────────────────┘
```

### 1.3 Hard Dependency Rules

| Rule | Description |
|---|---|
| `shared/` is a sink | `shared/` must NEVER import from `auth/`, `rbac/`, or `audit/` — except: `shared/security/` may import `auth/service/JwtService` and `auth/service/TokenBlacklist` (filter chain requires these) |
| `entity/` is persistence-only | No business logic in entities. JPA annotations only. Events are pure records |
| `usecase/` is technology-light | No `HttpServletRequest`, no `Cookie`. Spring's `@Transactional` is permitted |
| `web/` only calls use cases and services | Controllers never inject repositories directly |
| Cross-module repository access is documented | `rbac/service/UserRoleService` and `rbac/seeding/RbacDataSeeder` import `auth/repository/UserRepository`. This is the only permitted cross-module repository dependency |
| Events flow from publisher to audit | `auth/entity/event/` and `rbac/entity/event/` are imported by `audit/`. Never the reverse |
| Security config is in `shared/` | `SecurityConfig`, `JwtAuthenticationFilter`, `StarterPermissionEvaluator`, `UserPrincipal` live in `shared/security/` |

---

## 2. Complete Folder Structure

```
auth-spring/
├── pom.xml
├── README.md
│
└── src/
    ├── main/
    │   ├── java/io/epsilon/auth/
    │   │   ├── AuthSpringApplication.java
    │   │   │
    │   │   └── module/
    │   │       │
    │   │       ├── shared/                                   ← CROSS-CUTTING ONLY
    │   │       │   │                                            zero business logic
    │   │       │   ├── config/
    │   │       │   │   ├── AppProperties.java                ← @ConfigurationProperties(prefix=app)
    │   │       │   │   ├── JpaConfig.java                    ← @EnableJpaAuditing
    │   │       │   │   ├── JacksonConfig.java                ← ObjectMapper bean
    │   │       │   │   ├── SchedulingConfig.java             ← @EnableScheduling
    │   │       │   │   ├── AsyncConfig.java                  ← Virtual thread executor
    │   │       │   │   └── OpenApiConfig.java                ← API documentation config
    │   │       │   │
    │   │       │   ├── exception/                            ← Sealed exception hierarchy
    │   │       │   │   ├── DomainException.java              ← sealed base
    │   │       │   │   ├── ResourceNotFoundException.java    ← 404
    │   │       │   │   ├── EmailAlreadyExistsException.java  ← 409
    │   │       │   │   ├── AuthException.java                ← 401
    │   │       │   │   ├── RoleInUseException.java           ← 409
    │   │       │   │   └── InvalidPermissionNameException.java ← 400
    │   │       │   │
    │   │       │   ├── security/                             ← Cross-cutting security
    │   │       │   │   ├── SecurityConfig.java               ← filter chain, CORS, method security
    │   │       │   │   ├── JwtAuthenticationFilter.java      ← per-request JWT validation
    │   │       │   │   ├── StarterPermissionEvaluator.java   ← @PreAuthorize hasPermission()
    │   │       │   │   └── UserPrincipal.java                ← UserDetails adapter (no DB)
    │   │       │   │
    │   │       │   └── web/
    │   │       │       ├── ApiResponse.java                  ← generic envelope record
    │   │       │       ├── ApiError.java                     ← error detail record
    │   │       │       ├── GlobalExceptionHandler.java       ← @RestControllerAdvice
    │   │       │       └── RequestIdFilter.java              ← MDC + X-Request-ID
    │   │       │
    │   │       ├── auth/                                     ← Feature: Authentication
    │   │       │   │
    │   │       │   ├── entity/
    │   │       │   │   ├── UserEntity.java                   ← @Entity, NO UserDetails
    │   │       │   │   ├── RefreshTokenEntity.java
    │   │       │   │   ├── TokenBlacklistEntity.java         ← JTI blacklist row
    │   │       │   │   ├── UserStatus.java                   ← enum: ACTIVE, LOCKED, EXPIRED
    │   │       │   │   └── event/                            ← auth PUBLISHES these
    │   │       │   │       ├── UserRegisteredEvent.java      ← record
    │   │       │   │       ├── UserLoggedInEvent.java        ← record
    │   │       │   │       └── UserLoggedOutEvent.java       ← record
    │   │       │   │
    │   │       │   ├── repository/
    │   │       │   │   ├── UserRepository.java               ← Spring Data JPA
    │   │       │   │   ├── RefreshTokenRepository.java
    │   │       │   │   └── TokenBlacklistRepository.java     ← hot path, indexed
    │   │       │   │
    │   │       │   ├── service/
    │   │       │   │   ├── JwtService.java                   ← token issue + parse only
    │   │       │   │   ├── TokenBlacklist.java               ← strategy interface (internal)
    │   │       │   │   ├── RdbmsTokenBlacklistService.java   ← @ConditionalOnProperty(rdbms)
    │   │       │   │   ├── RedisTokenBlacklistService.java   ← @ConditionalOnProperty(redis)
    │   │       │   │   ├── TokenBlacklistPruner.java         ← @Scheduled, SRP
    │   │       │   │   ├── RefreshTokenService.java          ← issue/revoke/hash helpers
    │   │       │   │   └── UserDetailsServiceImpl.java       ← login path DB lookup
    │   │       │   │
    │   │       │   ├── usecase/
    │   │       │   │   ├── LoginUseCase.java
    │   │       │   │   ├── RegisterUseCase.java
    │   │       │   │   ├── LogoutUseCase.java
    │   │       │   │   ├── RefreshTokensUseCase.java
    │   │       │   │   └── GetProfileUseCase.java
    │   │       │   │
    │   │       │   └── web/
    │   │       │       ├── AuthController.java
    │   │       │       └── dto/
    │   │       │           ├── request/
    │   │       │           │   ├── LoginRequest.java         ← record
    │   │       │           │   ├── RegisterRequest.java      ← record
    │   │       │           │   └── RefreshTokenRequest.java  ← record
    │   │       │           └── response/
    │   │       │               ├── TokenResponse.java        ← record
    │   │       │               └── UserProfileResponse.java  ← record
    │   │       │
    │   │       ├── rbac/                                     ← Feature: Roles & Permissions
    │   │       │   │
    │   │       │   ├── entity/
    │   │       │   │   ├── RoleEntity.java
    │   │       │   │   ├── PermissionEntity.java
    │   │       │   │   ├── PermissionName.java               ← value object, self-validating
    │   │       │   │   └── event/                            ← rbac PUBLISHES these
    │   │       │   │       ├── RoleCreatedEvent.java         ← record
    │   │       │   │       ├── RoleUpdatedEvent.java         ← record
    │   │       │   │       ├── RoleDeletedEvent.java         ← record
    │   │       │   │       └── PermissionAssignedEvent.java  ← record
    │   │       │   │
    │   │       │   ├── repository/
    │   │       │   │   ├── RoleRepository.java
    │   │       │   │   └── PermissionRepository.java
    │   │       │   │
    │   │       │   ├── service/
    │   │       │   │   ├── RoleService.java                  ← CRUD + permission assignment
    │   │       │   │   ├── PermissionService.java            ← CRUD with PermissionName validation
    │   │       │   │   └── UserRoleService.java              ← assign/remove roles from users
    │   │       │   │
    │   │       │   ├── usecase/
    │   │       │   │   ├── AssignRoleUseCase.java            ← thin delegate if needed
    │   │       │   │   └── ManagePermissionsUseCase.java
    │   │       │   │
    │   │       │   ├── web/
    │   │       │   │   ├── RoleController.java
    │   │       │   │   ├── PermissionController.java
    │   │       │   │   ├── UserRoleController.java
    │   │       │   │   └── dto/
    │   │       │   │       ├── request/
    │   │       │   │       │   ├── CreateRoleRequest.java
    │   │       │   │       │   ├── UpdateRoleRequest.java
    │   │       │   │       │   └── CreatePermissionRequest.java
    │   │       │   │       └── response/
    │   │       │   │           ├── RoleResponse.java
    │   │       │   │           └── PermissionResponse.java
    │   │       │   │
    │   │       │   └── seeding/
    │   │       │       └── RbacDataSeeder.java               ← @Component, ApplicationRunner
    │   │       │
    │   │       └── audit/                                    ← Feature: Audit Logging
    │   │           │                                            CONSUMES auth + rbac events
    │   │           ├── entity/
    │   │           │   └── AuditLogEntity.java
    │   │           │
    │   │           ├── repository/
    │   │           │   └── AuditLogRepository.java
    │   │           │
    │   │           ├── service/
    │   │           │   └── AuditEventListener.java           ← @EventListener, @Async
    │   │           │
    │   │           └── usecase/
    │   │               └── LogAuditEventUseCase.java         ← delegates save to repository
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       └── db/changelog/
    │           ├── db.changelog-master.yaml
    │           └── changes/
    │               ├── 0001_create_users.yaml
    │               ├── 0002_create_roles.yaml
    │               ├── 0003_create_permissions.yaml
    │               ├── 0004_create_user_roles.yaml
    │               ├── 0005_create_role_permissions.yaml
    │               ├── 0006_create_refresh_tokens.yaml
    │               ├── 0007_create_token_blacklist.yaml
    │               └── 0008_create_audit_log.yaml
    │
    └── test/java/io/epsilon/auth/
        └── module/
            ├── auth/
            │   └── test/
            │       ├── integration/         ← DB, full context tests
            │       ├── api/                 ← Controller / endpoint tests
            │       └── unit/                ← Service, usecase, entity tests
            ├── rbac/
            │   └── test/
            │       ├── integration/
            │       ├── api/
            │       └── unit/
            └── audit/
                └── test/
                    ├── integration/
                    ├── api/
                    └── unit/
```

---

## 3. Layer Contract Definitions

### 3.1 `shared/` — Cross-Cutting Infrastructure

| Aspect | Contract |
|---|---|
| **Owns** | `AppProperties`, config beans, sealed exception hierarchy, `ApiResponse<T>`, `ApiError`, `GlobalExceptionHandler`, `RequestIdFilter`, `SecurityConfig`, `JwtAuthenticationFilter`, `StarterPermissionEvaluator`, `UserPrincipal` |
| **Must NOT contain** | Business logic, `@Entity`, feature-specific service calls |
| **Permitted imports** | `shared/security/` may import `auth.module.auth.service.JwtService` and `auth.module.auth.service.TokenBlacklist` (required by `JwtAuthenticationFilter`); also `auth.module.auth.entity.UserEntity` (required by `UserPrincipal.fromEntity()` at login) |
| **Test signal** | If a class in `shared/` imports from `rbac/` or `audit/`, it is in the wrong place |

### 3.2 `{module}/entity/` — JPA Entities, Value Objects, Events

| Aspect | Contract |
|---|---|
| **Owns** | JPA `@Entity` classes, enums, value objects (non-entity records), domain event records in `entity/event/` |
| **Must NOT contain** | `@Component`, `@Service`, business methods, Spring imports |
| **Test signal** | If an entity has a method that calls a repository or service, it has absorbed business logic |
| **Examples** | `UserEntity`, `UserStatus`, `PermissionName` (value object), `UserRegisteredEvent` (record) |

### 3.3 `{module}/repository/` — Data Access

| Aspect | Contract |
|---|---|
| **Owns** | Spring Data JPA `interface` declarations extending `JpaRepository<T, ID>` |
| **Must NOT contain** | Business logic, event publishing, service calls |
| **Change rule** | A change in query logic is the only valid reason to modify a repository |

### 3.4 `{module}/service/` — Orchestration and Infrastructure Services

| Aspect | Contract |
|---|---|
| **Owns** | Application services that coordinate multiple repositories or infrastructure concerns. Auth-specific infrastructure services: `JwtService`, `TokenBlacklistService`, `RefreshTokenService`, `UserDetailsServiceImpl` |
| **Calls** | Repositories and other services within the same module, or `shared/` |
| **Must NOT contain** | `HttpServletRequest`, `Cookie`, `@Scheduled` scheduling logic beyond simple delegation |
| **Transaction rule** | `@Transactional` is permitted at the service level |

### 3.5 `{module}/usecase/` — Business Logic

| Aspect | Contract |
|---|---|
| **Owns** | One class per named use case. Injects only the dependencies it actually needs |
| **Publishes** | Spring `ApplicationEvent` for side effects — never calls the side-effector directly |
| **Must NOT contain** | `HttpServletRequest`, `HttpServletResponse`, `Cookie`, Spring Security internals |
| **Test signal** | If you must mock `HttpServletRequest` to test a use case, it has a boundary violation |

### 3.6 `{module}/web/` — HTTP Interface

| Aspect | Contract |
|---|---|
| **Owns** | `@RestController` classes, request/response `record` DTOs |
| **Calls** | Only `usecase/` and `service/` classes |
| **Must NOT contain** | Business logic, `try-catch` around domain exceptions, direct repository access |
| **Test signal** | If a controller method exceeds ~15 lines of meaningful logic, extract to a use case |

### 3.7 `audit/` — Event-Driven Cross-Cutting Concern

| Aspect | Contract |
|---|---|
| **Owns** | `AuditEventListener`, `LogAuditEventUseCase`, `AuditLogEntity`, `AuditLogRepository` |
| **Imports** | `auth/entity/event/*` and `rbac/entity/event/*` — one-directional |
| **Must NOT be imported by** | `auth/`, `rbac/` — direction is strictly: publishers → events ← audit |
| **Extension** | Add a Kafka bridge by creating a second `@EventListener` without touching any publisher |

---

## 4. Dependency Rules Matrix

✅ = permitted · ❌ = forbidden · ⚠️ = permitted (documented cross-module dependency)

| From ↓ / To → | `shared/` | `auth/entity` | `auth/service` | `auth/repo` | `auth/web` | `rbac/entity` | `rbac/service` | `rbac/repo` | `rbac/web` | `audit/` |
|---|---|---|---|---|---|---|---|---|---|---|
| `shared/` | ✅ | ⚠️ UserEntity only | ⚠️ JwtService only | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `auth/entity` | ❌ | ✅ | ❌ | ❌ | ❌ | ⚠️ RoleEntity | ❌ | ❌ | ❌ | ❌ |
| `auth/service` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `auth/usecase` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ⚠️ RoleRepo | ❌ | ❌ |
| `auth/web` | ✅ | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `rbac/entity` | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `rbac/service` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `rbac/service UserRoleService` | ✅ | ✅ | ❌ | ⚠️ UserRepo | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `rbac/usecase` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `rbac/web` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ | ❌ |
| `rbac/seeding` | ✅ | ⚠️ UserEntity | ❌ | ⚠️ UserRepo | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ |
| `audit/service` | ✅ | ✅ events | ❌ | ❌ | ❌ | ✅ events | ❌ | ❌ | ❌ | ✅ |

> **Note on ⚠️ cross-module dependencies:**
> - `shared/security/UserPrincipal` imports `auth/entity/UserEntity` — required for `fromEntity()` at login. `shared/security/JwtAuthenticationFilter` imports `auth/service/JwtService` and `auth/service/TokenBlacklist` — required by the filter chain. These are the only permitted `shared/` → feature-module imports.
> - `auth/usecase/RegisterUseCase` imports `rbac/repository/RoleRepository` to look up `ROLE_USER` during registration. This is the only `auth/usecase` → `rbac/repo` dependency.
> - `rbac/service/UserRoleService` and `rbac/seeding/RbacDataSeeder` import `auth/repository/UserRepository` and `auth/entity/UserEntity` — required for user-role assignment and developer user seeding.
> - `audit/service/AuditEventListener` imports event records from `auth/entity/event/` and `rbac/entity/event/` — correct event subscription direction.

---

## 5. API Reference

### Authentication Endpoints (`/api/auth`)

| Method | Path | Auth Required | Description | Response |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register new user. Assigns `ROLE_USER` automatically | 201 Created |
| `POST` | `/api/auth/login` | Public | Authenticate. Returns JWT + refresh token. Sets HttpOnly cookie | 200 + cookie |
| `POST` | `/api/auth/logout` | Authenticated | Blacklists current JWT JTI, clears cookie | 200 |
| `POST` | `/api/auth/refresh` | Public | Rotate refresh token. Issues new access + refresh token pair | 200 + cookie |
| `GET` | `/api/auth/me` | Authenticated | Returns userId, email, roles, permissions from JWT claims | 200 |

### RBAC — Roles (`/api/roles`)

| Method | Path | Permission Required | Description |
|---|---|---|---|
| `GET` | `/api/roles` | `role:read` | Paginated list of all roles |
| `GET` | `/api/roles/{id}` | `role:read` | Get role by UUID |
| `POST` | `/api/roles` | `role:create` | Create new role |
| `PUT` | `/api/roles/{id}` | `role:update` | Update role description (name is immutable after creation) |
| `DELETE` | `/api/roles/{id}` | `role:delete` | Delete role (fails if role is assigned to any user) |
| `POST` | `/api/roles/{roleId}/permissions/{permId}` | `role:update` | Assign permission to role |
| `DELETE` | `/api/roles/{roleId}/permissions/{permId}` | `role:update` | Remove permission from role |

### RBAC — Permissions (`/api/permissions`)

| Method | Path | Permission Required | Description |
|---|---|---|---|
| `GET` | `/api/permissions` | `permission:read` | Paginated list |
| `GET` | `/api/permissions/{id}` | `permission:read` | Get by UUID |
| `POST` | `/api/permissions` | `permission:create` | Create (name must match `resource:action` format) |
| `DELETE` | `/api/permissions/{id}` | `permission:delete` | Delete permission |

### RBAC — User Role Assignment (`/api/users/{userId}/roles`)

| Method | Path | Permission Required | Description |
|---|---|---|---|
| `POST` | `/api/users/{userId}/roles/{roleId}` | `role:update` | Assign role to user |
| `DELETE` | `/api/users/{userId}/roles/{roleId}` | `role:update` | Remove role from user |

### Standard Response Envelope

```json
{
  "success":   true,
  "data":      "<T> or null",
  "error":     null,
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2025-09-01T10:30:00Z"
}
```

Error response:
```json
{
  "success":   false,
  "data":      null,
  "error":     { "code": "ROLE_IN_USE", "message": "Role assigned to users — reassign before deleting" },
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2025-09-01T10:30:00Z"
}
```

---

## 6. Default RBAC Seed Data

Seeded on first boot by `RbacDataSeeder`. All seeding is idempotent — safe to run on every startup.

### Seeded Permissions

| Permission Name | Format | Granted To |
|---|---|---|
| `all:all` | Wildcard — grants everything | `ROLE_DEVELOPER` |
| `role:create` | Exact | `ROLE_SYSTEM_ADMIN` |
| `role:read` | Exact | `ROLE_SYSTEM_ADMIN` |
| `role:update` | Exact | `ROLE_SYSTEM_ADMIN` |
| `role:delete` | Exact | `ROLE_SYSTEM_ADMIN` |
| `permission:create` | Exact | `ROLE_SYSTEM_ADMIN` |
| `permission:read` | Exact | `ROLE_SYSTEM_ADMIN` |
| `permission:update` | Exact | `ROLE_SYSTEM_ADMIN` |
| `permission:delete` | Exact | `ROLE_SYSTEM_ADMIN` |

### Seeded Roles

| Role Name | Default For | Permissions |
|---|---|---|
| `ROLE_USER` | Every registered user | None — add domain-specific permissions per feature |
| `ROLE_SYSTEM_ADMIN` | Operators | Full CRUD on roles and permissions |
| `ROLE_DEVELOPER` | Dev/test environments | `all:all` — grants everything including future permissions |

### Wildcard Evaluation Order (`StarterPermissionEvaluator`)

```
Rule 1: held.contains("all:all")          → GRANTED (ROLE_DEVELOPER)
Rule 2: held.contains(resource + ":*")    → GRANTED (e.g. "role:*" grants role:read, role:create…)
Rule 3: held.contains(required)           → GRANTED (exact match)
otherwise                                 → DENIED
```

---

## 7. Database Schema

`ddl-auto: validate` — Hibernate validates only. Liquibase owns all schema evolution. Every changeset includes a `rollback` block.

| Changelog File | Table | Key Design Decisions |
|---|---|---|
| `0001_create_users` | `auth_users` | `id UUID PK`, `email VARCHAR UNIQUE NOT NULL`, `password_hash`, `enabled BOOL DEFAULT true`, `account_non_locked`, `account_non_expired`, `credentials_non_expired`, `created_at`/`updated_at TIMESTAMPTZ` |
| `0002_create_roles` | `auth_roles` | `id UUID PK`, `name VARCHAR(100) UNIQUE NOT NULL`, `description VARCHAR(500)` |
| `0003_create_permissions` | `auth_permissions` | `id UUID PK`, `name VARCHAR(100) UNIQUE NOT NULL`. **DB-level CHECK**: `name ~ '^[a-z_]+:[a-z_*]+$'` (second line of defence after `PermissionName` validation) |
| `0004_create_user_roles` | `auth_user_roles` | Composite PK `(user_id, role_id)`. FK → `auth_users ON DELETE CASCADE`. FK → `auth_roles ON DELETE RESTRICT` |
| `0005_create_role_permissions` | `auth_role_permissions` | Composite PK `(role_id, permission_id)`. FK → `auth_roles ON DELETE CASCADE`. FK → `auth_permissions ON DELETE RESTRICT` |
| `0006_create_refresh_tokens` | `auth_refresh_tokens` | `id UUID PK`, `token_hash VARCHAR(64) UNIQUE`, `user_id FK`, `issued_at`, `expires_at`, `revoked_at NULLABLE`, `device_fingerprint VARCHAR(255)` |
| `0007_create_token_blacklist` | `auth_token_blacklist` | `id UUID PK`, `jti VARCHAR(36)`, `expires_at TIMESTAMPTZ`. **Composite index** `idx_blacklist_jti_expires (jti, expires_at)` — hot path for every authenticated request |
| `0008_create_audit_log` | `auth_audit_log` | `id UUID PK`, `event_type VARCHAR(50) NOT NULL`, `actor_id UUID`, `target_id UUID`, `ip_address VARCHAR(45)`, `metadata JSONB`, `occurred_at TIMESTAMPTZ NOT NULL`. **Indexes**: `idx_audit_actor (actor_id, occurred_at)`, `idx_audit_type (event_type, occurred_at)` |

> `auth_audit_log.metadata` is `JSONB` — enables server-side JSON queries and eliminates string-concatenation injection. Requires `ObjectMapper` usage in `AuditEventListener`.

---

## 8. Security Architecture

### 8.1 Authentication Flow

```
Client
  │
  ├─ POST /api/auth/login (email + password)
  │     │
  │     ├─ [JwtAuthenticationFilter] — skips, no token present
  │     ├─ [SecurityConfig] — permits /api/auth/login
  │     ├─ [LoginUseCase] — calls AuthenticationManager
  │     │     │
  │     │     ├─ [DaoAuthenticationProvider]
  │     │     │     └─ [UserDetailsServiceImpl] → DB query (ONCE per login)
  │     │     │         → UserPrincipal.fromEntity()
  │     │     │
  │     │     ├─ JwtService.issueAccessToken()
  │     │     ├─ RefreshTokenService.issue()
  │     │     └─ publish UserLoggedInEvent
  │     │
  │     └─ Returns: {accessToken, refreshToken} + HttpOnly cookie
  │
  ├─ GET /api/auth/me (subsequent request)
  │     │
  │     ├─ [RequestIdFilter] — sets MDC requestId
  │     ├─ [JwtAuthenticationFilter]
  │     │     ├─ Extract token (Bearer header or cookie)
  │     │     ├─ JwtService.parseAndValidate()
  │     │     ├─ TokenBlacklist.isBlacklisted(jti) → indexed DB read
  │     │     ├─ UserPrincipal.fromClaims() → NO DB QUERY
  │     │     └─ Set SecurityContextHolder
  │     │
  │     └─ [GetProfileUseCase] — reads from UserPrincipal in SecurityContext
  │
  └─ POST /api/auth/logout
        ├─ [LogoutUseCase]
        │     ├─ TokenBlacklist.add(jti, expiry)
        │     └─ publish UserLoggedOutEvent
        └─ Clear HttpOnly cookie
```

### 8.2 Per-Request Performance Contract

| Path | DB queries |
|---|---|
| Public endpoint (no token) | 0 |
| Authenticated request (valid JWT, not blacklisted) | 1 (blacklist check, indexed) |
| Authenticated request (JWT blacklisted) | 1 (blacklist check returns true → 401) |
| Login | 1 (UserDetailsServiceImpl) + 1 (refresh token write) |
| Refresh | 1 (refresh token read/revoke) + 1 (user read for permission re-resolution) |

### 8.3 Cookie Configuration

| Property | Value | Notes |
|---|---|---|
| `HttpOnly` | `true` | JS cannot read the cookie |
| `Secure` | `true` (prod) | HTTPS only. `false` allowed in `application-dev.yml` |
| `SameSite` | `Strict` | Prevents CSRF on same-site requests |
| `Path` | `/` | All paths receive the cookie |

### 8.4 Production Checklist

| Item | Requirement |
|---|---|
| `JWT_SECRET` | Minimum 32 random bytes, base64-encoded. Store in secrets manager (Vault, AWS Secrets Manager), never in YAML committed to VCS |
| `app.cookie.secure` | Must be `true` in production |
| `app.seeding.enabled` | Set to `false` in production after first boot, or ensure `developer-email` is changed |
| `app.cors.allowed-origins` | Set to actual frontend domain — never `*` |
| `spring.jpa.hibernate.ddl-auto` | Always `validate` in production |
| TLS termination | At load balancer or reverse proxy — not at Spring |
| Database | Not exposed to the public internet |
| Refresh token rotation | Token is revoked on first use. Reuse of a revoked token triggers a replay attack detection log |

### 8.5 Known Trade-offs

| Trade-off | Explanation |
|---|---|
| CSRF disabled | Stateless JWT makes CSRF inapplicable for API clients. If browser sessions with cookies become the primary auth mechanism without JWT, re-enable CSRF |
| BCrypt cost factor 12 | Appropriate for 2025 hardware. Increasing it does not auto-rehash existing passwords — implement a rehash-on-login strategy if needed |
| Refresh tokens as SHA-256 hash | Raw token returned once to client, never persisted. A DB compromise exposes no valid refresh tokens |
| Blacklist check on every request | An indexed RDBMS read (`jti + expires_at` composite index) or a single Redis key lookup. Measure under real load before optimizing |

---

## 9. Java 25 + Spring Boot 4.0.6 Architectural Notes

### 9.1 Virtual Threads (Project Loom)

Spring Boot 4.0.x supports virtual threads natively. All Tomcat request threads and `@Async` tasks run on virtual threads by default when enabled. This eliminates the need to tune thread pool sizes for I/O-bound workloads.

Key change in `AsyncConfig`:
```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

### 9.2 Sealed Exception Hierarchy

Java 25 sealed classes enforce the exception type universe at compile time:

```java
public sealed class DomainException extends RuntimeException
    permits ResourceNotFoundException, EmailAlreadyExistsException,
            AuthException, RoleInUseException, InvalidPermissionNameException {}
```

`GlobalExceptionHandler` exhaustively handles all permitted subtypes. The compiler warns if a new subtype is added without a corresponding handler.

### 9.3 Records Replace Lombok for Non-Entity Types

All DTOs, response objects, domain events, and value objects are Java `record` types. Lombok is retained only for JPA entities (which require mutable state, no-arg constructors, and `equals`/`hashCode` based on `id` only).

### 9.4 Pattern Matching in GlobalExceptionHandler

```java
// Java 25 pattern matching switch in exception handler
HttpStatus status = switch (ex) {
    case AuthException e                  -> HttpStatus.UNAUTHORIZED;
    case ResourceNotFoundException e      -> HttpStatus.NOT_FOUND;
    case EmailAlreadyExistsException e    -> HttpStatus.CONFLICT;
    case RoleInUseException e             -> HttpStatus.CONFLICT;
    case InvalidPermissionNameException e -> HttpStatus.BAD_REQUEST;
};
```

---

## 10. SOLID Principles Map

| Principle | Concrete Location in Codebase |
|---|---|
| **S — Single Responsibility** | `LoginUseCase` handles only login. `LogoutUseCase` handles only logout. `JwtService` handles only token cryptography. `TokenBlacklistPruner` handles only expired-entry cleanup. `RbacDataSeeder` handles only bootstrap seeding |
| **O — Open/Closed** | `TokenBlacklist`: add `RedisTokenBlacklistService` by implementing the interface and adding `@ConditionalOnProperty`. No existing class changes. `AuditEventListener`: add a Kafka bridge by adding a second `@EventListener` method |
| **L — Liskov Substitution** | `RdbmsTokenBlacklistService` and `RedisTokenBlacklistService` are fully substitutable. Callers in `usecase/` behave identically regardless of which implementation is active |
| **I — Interface Segregation** | `TokenBlacklist` has exactly 2 methods. Each repository interface exposes only what its callers need. No interface carries unrelated methods |
| **D — Dependency Inversion** | `LoginUseCase` depends on `JwtService` and `RefreshTokenService` (Spring-managed beans abstracted by type). `LogoutUseCase` depends on `TokenBlacklist` (interface). `PermissionService` depends on `PermissionName` value object — never on raw strings |

---

## 11. Extension Points

None of these require modifying existing source files.

| Extension | How to Extend |
|---|---|
| New blacklist strategy | Implement `TokenBlacklist`, add `@ConditionalOnProperty`. Zero changes to filter or use cases |
| New audit event consumer | Add `@EventListener` method to `AuditEventListener`, or create a new `@EventListener` class for Kafka/Slack |
| New domain events | Add a record to the publishing module's `entity/event/` package, publish from use case, add handler to `AuditEventListener` |
| New permissions | Add to `RbacDataSeeder.seedPermissions()`. `ROLE_DEVELOPER` receives them via `all:all` automatically |
| Custom user fields | Add Liquibase changeset + update `UserEntity`. Use cases accessing the new field update accordingly |
| Rate limiting | Add a `HandlerInterceptor` bean — injection point in filter chain is ready in `SecurityConfig` |
| OAuth2 / Social login | Add `spring-boot-starter-oauth2-client` + `auth/service/oauth/` package |
| Device fingerprinting | Populate `RefreshTokenEntity.deviceFingerprint` from `HttpServletRequest` in `AuthController` before passing to `RefreshTokenService` |
| MFA (TOTP) | Add `auth/service/TotpService`, a new `MfaChallengeUseCase`, and a `/api/auth/mfa/verify` endpoint |
