# auth-spring — Architecture Plan

*Version 2.0 · Clean Architecture · Production-Ready*

| Property | Value |
|---|---|
| Group ID | io.epsilon |
| Artifact | auth-spring |
| Spring Boot | 3.5.0 |
| Java | 21 LTS |
| Database | PostgreSQL 16+ |
| JWT Library | JJWT 0.12.6 |
| Architecture | Clean Layered (Domain / Application / Infrastructure / Web) |
| License | MIT |

---

## 1. Architecture Overview

auth-spring is a production-ready authentication and RBAC template. Clone it, configure it, and ship. The architecture follows Clean Layered principles — every class has a single reason to change, dependencies point inward only, and the folder structure makes the intent of each class obvious without reading a line of code.

### 1.1 Core Design Decisions

| Decision | Rationale |
|---|---|
| Single Maven module | This is a template, not a distributed system. Multi-module adds Maven reactor complexity with no benefit at this scale. |
| Feature-based top-level packages | auth/, rbac/, audit/ each own everything that changes together. Easier to delete or extract a feature. |
| Spring Data JPA repositories (no port interfaces) | Correct abstraction for CRUD-heavy auth at this scale. Port interfaces add indirection with no return. |
| No separate domain POJOs from JPA entities | YAGNI. Mapping between domain objects and persistence entities adds code with no benefit here. |
| UserPrincipal separate from UserEntity | Critical. Decouples JPA persistence model from Spring Security. Eliminates DB hit on every authenticated request. |
| Strategy pattern for TokenBlacklist | Swap from RDBMS to Redis by changing one property. Zero code changes. |
| Event-driven audit log | AuditEventListener is the only consumer. Feature services publish without knowing who listens. |

### 1.2 Layer Dependency Rules

Dependencies point strictly inward. No layer may import from a layer above it.

```
┌──────────────────────────────────────────────┐
│           web/  (HTTP Controllers)            │
│       depends on → application/ only          │
└──────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────┐
│       application/  (Use Case Services)       │
│   depends on → infrastructure/persistence/    │
│   publishes → ApplicationEvents               │
└──────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────┐
│       infrastructure/  (Adapters)             │
│   persistence/ · security/ · token/ · seeding/│
│   depends on → shared/config                  │
└──────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────┐
│       shared/  (Cross-Cutting Utilities)      │
│       No outward module dependencies          │
└──────────────────────────────────────────────┘
```

### 1.3 Hard Dependency Rules

- `shared/` must not import from any feature module (auth/, rbac/, audit/)
- `application/` must not import from `web/` or from other modules' `infrastructure/`
- `web/` must not import directly from `infrastructure/persistence/`
- `audit/` depends on events in `audit/event/`. Feature modules publish but never import from `audit/`
- Cross-module service calls go through the other module's `application/` layer only

---

## 2. Complete Folder Structure

Every folder has a single, non-negotiable responsibility. Reading the path alone tells you what the class does and why it exists.

```
auth-spring/
├── pom.xml
├── README.md
├── CHANGELOG.md
│
└── src/
    ├── main/
    │   ├── java/io/epsilon/auth/
    │   │   ├── AuthSpringApplication.java
    │   │   │
    │   │   ├── shared/                              ← global, zero business logic
    │   │   │   ├── config/
    │   │   │   │   ├── AppProperties.java            ← @ConfigurationProperties(prefix=app)
    │   │   │   │   ├── JpaConfig.java                ← @EnableJpaAuditing
    │   │   │   │   ├── JacksonConfig.java
    │   │   │   │   └── SchedulingConfig.java
    │   │   │   ├── exception/
    │   │   │   │   ├── DomainException.java
    │   │   │   │   ├── ResourceNotFoundException.java
    │   │   │   │   ├── EmailAlreadyExistsException.java
    │   │   │   │   ├── AuthException.java
    │   │   │   │   ├── RoleInUseException.java
    │   │   │   │   └── InvalidPermissionNameException.java
    │   │   │   └── web/
    │   │   │       ├── ApiResponse.java
    │   │   │       ├── ApiError.java
    │   │   │       ├── GlobalExceptionHandler.java
    │   │   │       └── RequestIdFilter.java
    │   │   │
    │   │   ├── auth/
    │   │   │   ├── application/
    │   │   │   │   ├── AuthService.java
    │   │   │   │   └── RefreshTokenService.java
    │   │   │   ├── infrastructure/
    │   │   │   │   ├── persistence/
    │   │   │   │   │   ├── UserEntity.java            ← @Entity, NO UserDetails
    │   │   │   │   │   ├── RefreshTokenEntity.java
    │   │   │   │   │   ├── TokenBlacklistEntry.java
    │   │   │   │   │   ├── UserJpaRepository.java
    │   │   │   │   │   ├── RefreshTokenJpaRepository.java
    │   │   │   │   │   └── TokenBlacklistJpaRepository.java
    │   │   │   │   ├── security/
    │   │   │   │   │   ├── SecurityConfig.java
    │   │   │   │   │   ├── JwtAuthenticationFilter.java
    │   │   │   │   │   ├── UserPrincipal.java         ← implements UserDetails (adapter)
    │   │   │   │   │   ├── UserDetailsServiceImpl.java
    │   │   │   │   │   ├── JwtService.java
    │   │   │   │   │   ├── StarterPermissionEvaluator.java
    │   │   │   │   │   └── OpenApiConfig.java
    │   │   │   │   └── token/
    │   │   │   │       ├── TokenBlacklist.java        ← interface
    │   │   │   │       ├── RdbmsTokenBlacklist.java
    │   │   │   │       ├── RedisTokenBlacklist.java
    │   │   │   │       └── TokenBlacklistPruner.java  ← @Scheduled only (SRP)
    │   │   │   └── web/
    │   │   │       ├── AuthController.java
    │   │   │       └── dto/
    │   │   │           ├── request/
    │   │   │           │   ├── LoginRequest.java
    │   │   │           │   ├── RegisterRequest.java
    │   │   │           │   └── RefreshTokenRequest.java
    │   │   │           └── response/
    │   │   │               ├── TokenResponse.java
    │   │   │               └── UserProfileResponse.java
    │   │   │
    │   │   ├── rbac/
    │   │   │   ├── application/
    │   │   │   │   ├── RoleService.java
    │   │   │   │   └── PermissionService.java
    │   │   │   ├── infrastructure/
    │   │   │   │   ├── persistence/
    │   │   │   │   │   ├── RoleEntity.java
    │   │   │   │   │   ├── PermissionEntity.java
    │   │   │   │   │   ├── RoleJpaRepository.java
    │   │   │   │   │   └── PermissionJpaRepository.java
    │   │   │   │   └── seeding/
    │   │   │   │       └── RbacDataSeeder.java        ← @Component, not @Service
    │   │   │   └── web/
    │   │   │       ├── RoleController.java
    │   │   │       ├── PermissionController.java
    │   │   │       ├── UserRoleController.java
    │   │   │       └── dto/ ...
    │   │   │
    │   │   └── audit/
    │   │       ├── application/
    │   │       │   ├── AuditableEvent.java            ← marker interface
    │   │       │   └── AuditEventListener.java
    │   │       ├── event/                             ← ALL domain events
    │   │       │   ├── UserRegisteredEvent.java
    │   │       │   ├── UserLoggedInEvent.java
    │   │       │   ├── UserLoggedOutEvent.java
    │   │       │   ├── RoleCreatedEvent.java
    │   │       │   ├── RoleUpdatedEvent.java
    │   │       │   └── PermissionAssignedEvent.java
    │   │       └── infrastructure/
    │   │           ├── AuditLogEntity.java
    │   │           └── AuditLogJpaRepository.java
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       └── db/changelog/
    │           ├── db.changelog-master.yaml
    │           └── changes/  (0001 → 0008)
    │
    └── test/java/io/epsilon/auth/
        ├── auth/application/    ← AuthServiceTest, RefreshTokenServiceTest
        ├── auth/infrastructure/ ← JwtServiceTest, UserPrincipalTest, BlacklistTest
        ├── auth/web/            ← AuthControllerTest
        ├── rbac/application/    ← RoleServiceTest, PermissionServiceTest
        ├── rbac/web/            ← RoleControllerTest, PermissionControllerTest
        └── integration/         ← AuthFlowTest, RbacFlowTest, BlacklistTest
```

---

## 3. Layer Responsibilities

### 3.1 shared/ — Cross-Cutting Utilities

| Aspect | Rule |
|---|---|
| Owns | AppProperties, Jackson/JPA/Scheduling config, exception classes, ApiResponse, GlobalExceptionHandler, RequestIdFilter |
| Must NOT contain | Business logic, security config, JPA entities, any feature-specific code |
| Test | If something is only used by one module, it does not belong here |

### 3.2 {module}/application/ — Use Case Orchestration

| Aspect | Rule |
|---|---|
| Owns | What the system can do. Each method = one named use case (login, register, createRole) |
| Calls | JPA repositories directly. Publishes ApplicationEvents for side effects |
| Must NOT contain | HttpServletRequest, @Entity, JJWT calls, @Scheduled, Spring Boot lifecycle hooks |
| Test signal | Every method should be explainable to a business stakeholder without naming a technology |

### 3.3 {module}/infrastructure/ — Technical Adapters

| Aspect | Rule |
|---|---|
| Owns | How the system talks to external technology: databases, security framework, caching, scheduling |
| Sub-packages | persistence/ · security/ · token/ · seeding/ |
| Exclusive imports | JJWT, Spring Security, @Scheduled, @Entity, JpaRepository all belong exclusively here |
| Change rule | A change in any external library is contained entirely inside this layer |

### 3.4 {module}/web/ — HTTP Interface

| Aspect | Rule |
|---|---|
| Owns | @RestController classes, Java record DTOs for requests/responses, @Valid annotations |
| Calls | Only application/ service classes. Never calls infrastructure/ directly |
| Must NOT contain | Business logic, try-catch around domain exceptions (those go in GlobalExceptionHandler) |

### 3.5 audit/ — Cross-Cutting Audit Module

| Aspect | Rule |
|---|---|
| Owns | The audit event contract (audit/event/) and its persistence (audit/infrastructure/) |
| Consumer | AuditEventListener is the ONLY listener. No other class consumes audit events |
| Direction | audit/ listens to others. Others never listen to audit/ — no circular dependency possible |
| Extension | Add a Kafka bridge by adding a second @EventListener. Zero changes to publishers |

---

## 4. API Reference

### Authentication Endpoints

| Method | Path | Auth | Description | Response |
|---|---|---|---|---|
| POST | /api/auth/register | Public | Register new user. Assigns ROLE_USER. | 201 Created |
| POST | /api/auth/login | Public | Authenticate. Returns JWT + refresh token. | 200 + sets cookie |
| POST | /api/auth/logout | Authenticated | Blacklists JTI, clears cookie. | 200 |
| POST | /api/auth/refresh | Public | Rotate refresh token. Issues new access token. | 200 + sets cookie |
| GET | /api/auth/me | Authenticated | Returns userId, email, roles, permissions. | 200 |

### RBAC — Roles (/api/roles)

| Method | Path | Permission Required | Description |
|---|---|---|---|
| GET | /api/roles | role:read | Paginated list of all roles |
| GET | /api/roles/{id} | role:read | Get role by UUID |
| POST | /api/roles | role:create | Create new role |
| PUT | /api/roles/{id} | role:update | Update role description |
| DELETE | /api/roles/{id} | role:delete | Delete (fails if role is in use) |
| POST | /api/roles/{roleId}/permissions/{permId} | role:update | Assign permission to role |
| DELETE | /api/roles/{roleId}/permissions/{permId} | role:update | Remove permission from role |

### RBAC — Permissions (/api/permissions)

| Method | Path | Permission Required | Description |
|---|---|---|---|
| GET | /api/permissions | permission:read | Paginated list |
| GET | /api/permissions/{id} | permission:read | Get by UUID |
| POST | /api/permissions | permission:create | Create (must match resource:action format) |
| DELETE | /api/permissions/{id} | permission:delete | Delete permission |

### RBAC — User Role Assignment (/api/users/{userId}/roles)

| Method | Path | Permission Required | Description |
|---|---|---|---|
| POST | /api/users/{userId}/roles/{roleId} | role:update | Assign role to user |
| DELETE | /api/users/{userId}/roles/{roleId} | role:update | Remove role from user |

### Standard Response Envelope

```json
{
  "success":   true | false,
  "data":      "<T> | null",
  "error":     { "code": "ROLE_IN_USE", "message": "..." },
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## 5. Default RBAC Seed Data

Seeded on first boot by RbacDataSeeder. All seeding is idempotent — safe to run on every startup against a pre-populated database.

### Seeded Permissions

| Permission Name | Granted To |
|---|---|
| all:all | ROLE_DEVELOPER — grants every permission via wildcard evaluation |
| role:create | ROLE_SYSTEM_ADMIN |
| role:read | ROLE_SYSTEM_ADMIN |
| role:update | ROLE_SYSTEM_ADMIN |
| role:delete | ROLE_SYSTEM_ADMIN |
| permission:create | ROLE_SYSTEM_ADMIN |
| permission:read | ROLE_SYSTEM_ADMIN |
| permission:update | ROLE_SYSTEM_ADMIN |
| permission:delete | ROLE_SYSTEM_ADMIN |

### Seeded Roles

| Role Name | Default For | Permissions |
|---|---|---|
| ROLE_USER | Every newly registered user | None — add domain-specific permissions in your feature modules |
| ROLE_SYSTEM_ADMIN | Operators / admins | Full CRUD on roles and permissions |
| ROLE_DEVELOPER | Dev/test environments | all:all — wildcard grants everything including future permissions |

### Wildcard Permission Evaluation

```java
// Rule 1: all:all grants everything
hasPermission(null, 'role:create')  // DEVELOPER → true via all:all

// Rule 2: resource:* grants all actions for that resource
hasPermission(null, 'role:read')    // user with 'role:*' → true

// Rule 3: exact match
hasPermission(null, 'role:delete')  // SYSTEM_ADMIN → true (explicit)
```

---

## 6. Database Schema

Hibernate is set to `ddl-auto: validate` — it only checks the schema matches entities, never creates or modifies tables. Liquibase owns all schema changes. Every changeset has a rollback block.

| Changelog File | Table Created | Key Columns / Constraints |
|---|---|---|
| 0001_create_users | auth_users | id UUID PK, email VARCHAR UNIQUE NOT NULL, password_hash, enabled BOOL, created_at/updated_at TIMESTAMPTZ |
| 0002_create_roles | auth_roles | id UUID PK, name VARCHAR(100) UNIQUE NOT NULL, description VARCHAR(500) |
| 0003_create_permissions | auth_permissions | id UUID PK, name VARCHAR(100) UNIQUE NOT NULL + DB CHECK: name ~ '^[a-z_]+:[a-z_*]+$' |
| 0004_create_user_roles | auth_user_roles | Composite PK (user_id, role_id). FK to auth_users ON DELETE CASCADE, FK to auth_roles ON DELETE CASCADE |
| 0005_create_role_permissions | auth_role_permissions | Composite PK (role_id, permission_id). FK to auth_roles + auth_permissions ON DELETE CASCADE |
| 0006_create_refresh_tokens | auth_refresh_tokens | id, token_hash VARCHAR(64) UNIQUE, user_id FK, issued_at, expires_at, revoked_at NULLABLE, device_fingerprint |
| 0007_create_token_blacklist | auth_token_blacklist | id, jti VARCHAR(36), expires_at. Composite INDEX on (jti, expires_at) for hot lookup path |
| 0008_create_audit_log | auth_audit_log | id, event_type VARCHAR(50), actor_id UUID, target_id UUID, ip_address, metadata TEXT, occurred_at. INDEX on actor_id + occurred_at |

> The DB-level CHECK constraint on `auth_permissions.name` is a second line of defence. The first is `PermissionService.NAME_PATTERN` regex validation.

---

## 7. Testing Strategy

Test layout mirrors production layout exactly — finding the test for any production class is trivial.

| Test Class | Layer | What It Tests |
|---|---|---|
| JwtServiceTest | infra/security | Token issue, parse, expiry, tampered signature, key rotation |
| UserPrincipalTest | infra/security | fromClaims builds correct authorities, fromEntity maps all fields |
| RdbmsTokenBlacklistTest | infra/token | add persists entry, isBlacklisted queries correctly, expired entries ignored |
| TokenBlacklistPrunerTest | infra/token | pruneExpired calls deleteByExpiresAtBefore, conditional activation |
| AuthServiceTest | application | login, register, logout, refresh — mocked infra, asserts business rules |
| RefreshTokenServiceTest | application | issue returns raw, validateAndRotate handles replay/expiry/revoke |
| RoleServiceTest | application | CRUD, delete-in-use guard, permission assignment idempotency |
| PermissionServiceTest | application | NAME_PATTERN validation, duplicate rejection |
| RbacDataSeederTest | infra/seeding | Idempotent seed — entities created once even on double run |
| UserJpaRepositoryTest | infra/persistence | @DataJpaTest + Testcontainers — unique email, findByEmail, existsByRolesId |
| RefreshTokenJpaRepoTest | infra/persistence | findByTokenHash, revokeAllByUserId bulk update |
| AuthControllerTest | web | @WebMvcTest — 200/201/400/401/403/404/409 per endpoint |
| RoleControllerTest | web | Same HTTP status coverage as AuthControllerTest |
| AuthFlowIntegrationTest | integration | Register → Login → /me → Logout → token rejected (full flow) |
| RbacFlowIntegrationTest | integration | Login as admin → create role → assign permission → verify |
| TokenBlacklistIntTest | integration | Blacklist, replay attack, strategy swap (RDBMS ↔ Redis) |

---

## 8. Security Hardening Notes

### Production Checklist

| Item | Requirement | Default |
|---|---|---|
| JWT_SECRET | At least 32 random bytes, stored in secrets manager (not in YAML) | Example base64 — MUST change |
| app.cookie.secure | Must be true in production — only dev profile relaxes this | true |
| app.seeding.developer-email | Remove from production or change password immediately | dev@example.com |
| app.cors.allowed-origins | Set to your actual frontend domain. Never use * | http://localhost:3000 |
| spring.jpa.hibernate.ddl-auto | Always `validate` in production. Never `update` or `create` | validate |
| HTTPS | Terminate TLS at load balancer or reverse proxy — not at Spring | Not enforced by app |
| PostgreSQL access | Database must not be accessible from the public internet | N/A |

### Known Trade-offs

| Trade-off | Explanation |
|---|---|
| CSRF disabled | Stateless JWT makes CSRF inapplicable. If you enable cookie sessions alongside JWTs, re-enable CSRF. |
| BCrypt cost factor 12 | Appropriate for current hardware. Re-evaluate annually. Increasing it does not auto-rehash existing passwords. |
| Refresh tokens as SHA-256 hash | Raw token returned once to client, never persisted. A DB compromise does not expose valid refresh tokens. |
| Blacklist lookup per request | Every authenticated request hits the blacklist store. With RDBMS this is an indexed read. With Redis it is a single key lookup. Measure under your actual load. |

---

## 9. SOLID Principles Map

| Principle | Concrete Location in Codebase |
|---|---|
| S — Single Responsibility | JwtService handles only token cryptography. TokenBlacklistPruner handles only cleanup scheduling. RbacDataSeeder handles only bootstrap seeding. UserPrincipal handles only the Spring Security adapter contract. Each has exactly one reason to change. |
| O — Open/Closed | TokenBlacklist interface: add Redis, Memcached, or in-memory strategies without modifying JwtAuthenticationFilter, AuthService, or SecurityConfig. AuditEventListener: add Kafka consumer without touching publishers. |
| L — Liskov Substitution | RdbmsTokenBlacklist and RedisTokenBlacklist are fully substitutable. The filter and AuthService never behave differently regardless of which implementation is active. |
| I — Interface Segregation | TokenBlacklist has exactly 2 methods. AuditableEvent is a pure marker. UserDetailsService has exactly 1 method. No implementation is forced to stub unneeded behaviour. |
| D — Dependency Inversion | SecurityConfig, AuthService, JwtAuthenticationFilter all depend on TokenBlacklist (abstraction). AuditEventListener depends on AuditableEvent marker. Application services depend on JpaRepository interfaces, not concrete Hibernate implementations. |

---

## 10. Extension Points

None of these require modifying existing source files.

| Extension | How to Extend |
|---|---|
| Token blacklist strategy | Implement TokenBlacklist, add @ConditionalOnProperty. Zero changes elsewhere. |
| Audit event consumers | Add @EventListener methods anywhere — Kafka bridge, Slack notification, etc. |
| New domain events | Add a record to audit/event/, publish from application services. |
| New permissions | Add to RbacDataSeeder seedPermissions(). ROLE_DEVELOPER gets them via all:all automatically. |
| Custom user fields | Add columns via new Liquibase changeset + update UserEntity. Framework handles the rest. |
| Rate limiting | Add a HandlerInterceptor bean — filter chain injection point is ready. |
| Device fingerprinting | Populate RefreshTokenEntity.deviceFingerprint from HttpServletRequest in RefreshTokenService. |
| OAuth2 / Social Login | Add spring-boot-starter-oauth2-client dependency + new auth/infrastructure/oauth/ package. |
