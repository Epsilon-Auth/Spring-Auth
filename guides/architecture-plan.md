# auth-spring — Architecture Plan (v2)

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

`auth-spring` is a production-ready authentication and RBAC template. All code lives under a `module/` parent package. Each feature module (`auth`, `rbac`, `audit`) owns its entities, repositories, services, use cases, and web controllers. Cross-cutting infrastructure lives in `shared/`, which depends on **nothing** — no feature module imports, no exceptions.

### 1.1 Core Design Decisions

| Decision | Rationale |
|---|---|
| Single Maven module | Template scope — multi-module adds Maven reactor complexity with no benefit here |
| `module/` parent package | Groups all features and shared concerns under one readable root |
| Feature-based top-level packages | `auth/`, `rbac/`, `audit/` own everything that changes together |
| **`shared/` is a true sink — zero feature imports** | `shared/security/` exposes `TokenValidator` and `TokenRevocationChecker` *interfaces*; `auth/` provides the implementations. `JwtAuthenticationFilter` depends on interfaces only (DIP). No `auth/` class is imported by `shared/`. |
| **`UserPrincipal.fromClaims()` only** | `fromEntity()` belongs in `UserDetailsServiceImpl` — the single login-path class that touches the DB. `UserPrincipal` in `shared/` never imports a JPA entity. |
| **Module-local exception hierarchies** | `auth/exception/` and `rbac/exception/` own their sealed subtypes. `GlobalExceptionHandler` catches each root separately. Adding a new feature module does NOT touch `shared/`. |
| **Cross-module communication through service boundaries** | `RegisterUseCase` calls `RbacQueryService.findRoleByName()` — not `RoleRepository` directly. `UserRoleService` calls `AuthUserService` — not `UserRepository` directly. Repositories are private to their module. |
| `service/` for infrastructure services | Technology-coupled classes (JwtService, TokenBlacklist strategies, RefreshTokenService) belong here. No business decisions. |
| `usecase/` for named business operations | One class per operation. Calls `service/` within the **same module only**. Calls cross-module services through their public service APIs. Publishes `ApplicationEvent` for side effects. Contains business validation and branching logic. |
| `entity/event/` events owned by publisher | Events live in the publishing module's `entity/event/` sub-package. `audit` subscribes — never the reverse. |
| Records for all non-entity types | Java 25 records eliminate boilerplate for DTOs, events, value objects, responses |
| Virtual thread executor for async audit | Project Loom provides lightweight threads; no pool tuning needed |
| Strategy pattern for `TokenBlacklist` | Internal interface in `auth/service/`. Swap RDBMS ↔ Redis by changing one property |
| `ddl-auto: validate` + Liquibase | Liquibase owns schema; Hibernate only validates at startup |
| **ArchUnit enforces all dependency rules** | Module boundaries are checked at build time, not just documented. Violations fail the build. |
| **Rate limiting on auth endpoints** | `LoginRateLimitFilter` (Bucket4j) limits `/login` and `/refresh` per IP. Brute-force protection. |
| **Account lockout** | `FailedLoginTracker` increments a counter per email on `BadCredentialsException`. After N failures the account is locked via `UserStatus.LOCKED`. |

### 1.2 Layer Dependency Rules

```
┌──────────────────────────────────────────────────────────────┐
│  web/  — HTTP Interface                                       │
│  Knows about: usecase/ and service/ within same module        │
│  Never imports: entity JPA types, Security internals          │
└──────────────────────────────────────────────────────────────┘
                            ↓ depends on
┌──────────────────────────────────────────────────────────────┐
│  usecase/  — Named Business Operations (one class per op)    │
│  Calls: service/ within same module only                      │
│  Calls: cross-module via the target module's service API      │
│  Publishes: ApplicationEvents for side effects               │
│  Never imports: web/, HttpServletRequest, @Entity             │
│  Never imports: service/ from another module                  │
└──────────────────────────────────────────────────────────────┘
                            ↓ depends on
┌──────────────────────────────────────────────────────────────┐
│  service/  — Infrastructure Services (technology-coupled)    │
│  Knows about: repository/, entity/ within same module        │
│  Contains: JwtService, TokenBlacklist strategies, pruners    │
│  Never contains: business decisions or branching logic        │
│  Never imports: usecase/ of any module                        │
└──────────────────────────────────────────────────────────────┘
                            ↓ depends on
┌──────────────────────────────────────────────────────────────┐
│  repository/  — Spring Data JPA Interfaces                   │
│  Private to their owning module                              │
│  Never accessed directly by another module's code            │
└──────────────────────────────────────────────────────────────┘
                            ↓ depends on
┌──────────────────────────────────────────────────────────────┐
│  entity/  — JPA Entities, Enums, Value Objects, Events       │
│  entity/event/ — domain event records (publisher-owned)      │
└──────────────────────────────────────────────────────────────┘
                            ↑ all layers depend on
┌──────────────────────────────────────────────────────────────┐
│  shared/  — Cross-Cutting Concerns                            │
│  Depends on: NOTHING — no feature module imports, ever        │
│  Contains: interfaces TokenValidator, TokenRevocationChecker  │
│            SecurityConfig, JwtAuthenticationFilter            │
│            UserPrincipal (fromClaims only), ApiResponse       │
│            abstract DomainException, ResourceNotFoundException│
└──────────────────────────────────────────────────────────────┘
```

**The `usecase/` vs `service/` distinction in plain language:**

| Layer | Rule | Example |
|---|---|---|
| `service/` | Does one technical thing, no business decisions | `JwtService.issueAccessToken()` — cryptography only |
| `usecase/` | Orchestrates the business operation end-to-end | `LoginUseCase.execute()` — authenticate, issue tokens, publish event |
| `service/` | Never branches on business state | `RefreshTokenService.issue()` — CRUD, no validation |
| `usecase/` | Owns all validation and guard clauses | `RegisterUseCase` checks duplicate email before persisting |

If you're unsure: "Could this class be tested without any business scenario?" → `service/`. "Is this class a named user action?" → `usecase/`.

### 1.3 Hard Dependency Rules (ArchUnit enforced)

| Rule | Description |
|---|---|
| `shared/` is a true sink | `shared/` MUST NOT import from any feature module (`auth/`, `rbac/`, `audit/`). No exceptions. |
| Cross-module via service API | One module may only call another module's `service/` classes — never its `repository/` or `usecase/`. |
| `usecase/` is technology-light | No `HttpServletRequest`, no `Cookie`. `@Transactional` permitted. |
| `web/` only calls use cases and services | Controllers never inject repositories directly. |
| Repositories are private | No class outside `auth/` may import `auth/repository/`. Same for `rbac/` and `audit/`. |
| Events flow from publisher to audit | `auth/entity/event/` and `rbac/entity/event/` are imported by `audit/`. Never the reverse. |
| Security config is in `shared/` | `SecurityConfig`, `JwtAuthenticationFilter`, `StarterPermissionEvaluator`, `UserPrincipal` live in `shared/security/`. |
| Exception hierarchies are module-local | `auth/exception/` and `rbac/exception/` own their sealed subtypes. Neither is imported by `shared/`. |

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
    │   │       │   │                                            zero feature-module imports
    │   │       │   ├── config/
    │   │       │   │   ├── AppProperties.java
    │   │       │   │   ├── JpaConfig.java
    │   │       │   │   ├── JacksonConfig.java
    │   │       │   │   ├── SchedulingConfig.java
    │   │       │   │   ├── AsyncConfig.java
    │   │       │   │   └── OpenApiConfig.java
    │   │       │   │
    │   │       │   ├── exception/                            ← Cross-module exceptions only
    │   │       │   │   ├── DomainException.java              ← abstract base (NOT sealed)
    │   │       │   │   └── ResourceNotFoundException.java    ← 404, used by all modules
    │   │       │   │
    │   │       │   ├── security/
    │   │       │   │   ├── TokenValidator.java               ← interface: parseAndValidate()
    │   │       │   │   ├── TokenRevocationChecker.java       ← interface: isRevoked()
    │   │       │   │   ├── SecurityConfig.java
    │   │       │   │   ├── JwtAuthenticationFilter.java      ← depends on interfaces only
    │   │       │   │   ├── LoginRateLimitFilter.java         ← Bucket4j rate limiter
    │   │       │   │   ├── StarterPermissionEvaluator.java
    │   │       │   │   └── UserPrincipal.java                ← fromClaims() ONLY — no entity import
    │   │       │   │
    │   │       │   └── web/
    │   │       │       ├── ApiResponse.java
    │   │       │       ├── ApiError.java
    │   │       │       ├── GlobalExceptionHandler.java
    │   │       │       └── RequestIdFilter.java
    │   │       │
    │   │       ├── auth/
    │   │       │   ├── entity/
    │   │       │   │   ├── UserEntity.java
    │   │       │   │   ├── RefreshTokenEntity.java
    │   │       │   │   ├── TokenBlacklistEntity.java
    │   │       │   │   ├── UserStatus.java
    │   │       │   │   └── event/
    │   │       │   │       ├── UserRegisteredEvent.java
    │   │       │   │       ├── UserLoggedInEvent.java
    │   │       │   │       ├── UserLoggedOutEvent.java
    │   │       │   │       └── PasswordResetRequestedEvent.java   ← placeholder for v2
    │   │       │   │
    │   │       │   ├── exception/                            ← auth's sealed hierarchy
    │   │       │   │   ├── AuthDomainException.java          ← sealed root
    │   │       │   │   ├── InvalidCredentialsException.java  ← 401
    │   │       │   │   ├── EmailAlreadyExistsException.java  ← 409
    │   │       │   │   ├── AccountLockedException.java       ← 403
    │   │       │   │   └── TokenException.java               ← 401 (refresh/blacklist errors)
    │   │       │   │
    │   │       │   ├── repository/                           ← private to auth module
    │   │       │   │   ├── UserRepository.java
    │   │       │   │   ├── RefreshTokenRepository.java
    │   │       │   │   └── TokenBlacklistRepository.java
    │   │       │   │
    │   │       │   ├── service/
    │   │       │   │   ├── AuthUserService.java              ← public cross-module API for user ops
    │   │       │   │   ├── JwtService.java                   ← implements TokenValidator
    │   │       │   │   ├── TokenBlacklist.java               ← internal strategy interface
    │   │       │   │   ├── RdbmsTokenBlacklistService.java   ← implements TokenBlacklist + TokenRevocationChecker
    │   │       │   │   ├── RedisTokenBlacklistService.java   ← implements TokenBlacklist + TokenRevocationChecker
    │   │       │   │   ├── TokenBlacklistPruner.java
    │   │       │   │   ├── RefreshTokenService.java
    │   │       │   │   ├── FailedLoginTracker.java           ← tracks consecutive failures, locks accounts
    │   │       │   │   └── UserDetailsServiceImpl.java       ← builds UserPrincipal from entity (login only)
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
    │   │       │           │   ├── LoginRequest.java
    │   │       │           │   ├── RegisterRequest.java
    │   │       │           │   └── RefreshTokenRequest.java
    │   │       │           └── response/
    │   │       │               ├── TokenResponse.java
    │   │       │               └── UserProfileResponse.java
    │   │       │
    │   │       ├── rbac/
    │   │       │   ├── entity/
    │   │       │   │   ├── RoleEntity.java
    │   │       │   │   ├── PermissionEntity.java
    │   │       │   │   ├── PermissionName.java
    │   │       │   │   └── event/
    │   │       │   │       ├── RoleCreatedEvent.java
    │   │       │   │       ├── RoleUpdatedEvent.java
    │   │       │   │       ├── RoleDeletedEvent.java
    │   │       │   │       └── PermissionAssignedEvent.java
    │   │       │   │
    │   │       │   ├── exception/                            ← rbac's sealed hierarchy
    │   │       │   │   ├── RbacDomainException.java          ← sealed root
    │   │       │   │   ├── RoleInUseException.java           ← 409
    │   │       │   │   └── InvalidPermissionNameException.java ← 400
    │   │       │   │
    │   │       │   ├── repository/                           ← private to rbac module
    │   │       │   │   ├── RoleRepository.java
    │   │       │   │   └── PermissionRepository.java
    │   │       │   │
    │   │       │   ├── service/
    │   │       │   │   ├── RbacQueryService.java             ← public cross-module API for role/perm queries
    │   │       │   │   ├── RoleService.java
    │   │       │   │   ├── PermissionService.java
    │   │       │   │   └── UserRoleService.java
    │   │       │   │
    │   │       │   ├── usecase/
    │   │       │   │   ├── AssignRoleUseCase.java
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
    │   │       │       └── RbacDataSeeder.java               ← calls AuthUserService, not UserRepository
    │   │       │
    │   │       └── audit/
    │   │           ├── entity/
    │   │           │   └── AuditLogEntity.java
    │   │           │
    │   │           ├── repository/
    │   │           │   └── AuditLogRepository.java
    │   │           │
    │   │           └── service/
    │   │               └── AuditEventListener.java           ← saves directly to AuditLogRepository
    │   │                                                        no LogAuditEventUseCase wrapper
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
            ├── arch/
            │   └── ArchitectureRulesTest.java        ← ArchUnit — all rules enforced at build time
            ├── auth/
            │   └── test/
            │       ├── integration/
            │       ├── api/
            │       └── unit/
            ├── rbac/
            │   └── test/
            │       ├── integration/
            │       ├── api/
            │       └── unit/
            └── audit/
                └── test/
                    ├── integration/
                    └── unit/
```

---

## 3. Layer Contract Definitions

### 3.1 `shared/` — Cross-Cutting Infrastructure

| Aspect | Contract |
|---|---|
| **Owns** | `AppProperties`, config beans, abstract `DomainException`, `ResourceNotFoundException`, `ApiResponse<T>`, `ApiError`, `GlobalExceptionHandler`, `RequestIdFilter`, `SecurityConfig`, `JwtAuthenticationFilter`, `LoginRateLimitFilter`, `StarterPermissionEvaluator`, `UserPrincipal`, `TokenValidator` (interface), `TokenRevocationChecker` (interface) |
| **Must NOT contain** | Business logic, `@Entity`, any import from `auth/`, `rbac/`, or `audit/` |
| **Exception rule** | `DomainException` is an abstract base only. `shared/exception/` contains only exceptions that are genuinely cross-module (e.g. `ResourceNotFoundException`). Module-specific exceptions live in `auth/exception/` and `rbac/exception/`. |
| **Security filter rule** | `JwtAuthenticationFilter` injects `TokenValidator` and `TokenRevocationChecker` — interfaces defined in `shared/security/`. `auth/` provides the implementations; Spring's DI wires them at runtime. `shared/` has no compile-time dependency on `auth/`. |
| **Test signal** | `grep -r "import io.epsilon.auth.module.auth"` in the `shared/` source tree must return zero results. Same for `rbac` and `audit`. |

### 3.2 `{module}/exception/` — Module-Local Sealed Hierarchy

| Aspect | Contract |
|---|---|
| `auth/exception/AuthDomainException` | sealed, permits `InvalidCredentialsException`, `EmailAlreadyExistsException`, `AccountLockedException`, `TokenException` |
| `rbac/exception/RbacDomainException` | sealed, permits `RoleInUseException`, `InvalidPermissionNameException` |
| **OCP compliance** | Adding a new feature module means adding a new `{module}/exception/` package and a new `@ExceptionHandler` in `GlobalExceptionHandler`. `shared/` is never touched. |
| **GlobalExceptionHandler** | Catches `AuthDomainException`, `RbacDomainException`, and `DomainException` (for `ResourceNotFoundException`) in separate handlers. Pattern-matching switch used within each handler for status mapping. |

### 3.3 `{module}/entity/` — JPA Entities, Value Objects, Events

| Aspect | Contract |
|---|---|
| **Owns** | JPA `@Entity` classes, enums, value objects (non-entity records), domain event records in `entity/event/` |
| **Must NOT contain** | `@Component`, `@Service`, business methods, Spring imports |
| **Cross-module entity references** | `UserEntity` references `RoleEntity` via `@ManyToMany`. This is the only permitted cross-module entity reference and is required by JPA. It does not make `auth/entity/` dependent on `rbac/service/` or `rbac/usecase/`. |

### 3.4 `{module}/repository/` — Data Access (Module-Private)

| Aspect | Contract |
|---|---|
| **Owns** | Spring Data JPA `interface` declarations |
| **Access rule** | Only classes within the **same module** may import a repository. No class in `rbac/` may import `auth/repository/UserRepository`. Cross-module data needs flow through the owning module's service API. |
| **Enforcement** | ArchUnit `noClasses().that().resideInAPackage("..rbac..").should().dependOnClassesThat().resideInAPackage("..auth.repository..")` |

### 3.5 `{module}/service/` — Infrastructure and Cross-Module APIs

| Aspect | Contract |
|---|---|
| **Infrastructure services** | `JwtService`, `TokenBlacklist` strategies, `RefreshTokenService`, `UserDetailsServiceImpl`, `FailedLoginTracker`, pruners — technology-coupled, no business logic |
| **Cross-module service APIs** | `AuthUserService` (exposed by `auth/`) and `RbacQueryService` (exposed by `rbac/`) are the only permitted cross-module entry points. They wrap their module's repositories and expose a controlled, intention-revealing API. |
| **Must NOT contain** | `HttpServletRequest`, `Cookie`, business branching logic |
| **Must NOT import** | `usecase/` from any module |

**`AuthUserService`** — `auth/service/AuthUserService.java`

The single public API through which other modules interact with user data:

```
findById(UUID)           → Optional<UserEntity>
findByIdOrThrow(UUID)    → UserEntity
isRoleAssignedToAnyUser(UUID roleId) → boolean   ← used by RoleService.delete()
save(UserEntity)         → UserEntity
existsByEmail(String)    → boolean
```

**`RbacQueryService`** — `rbac/service/RbacQueryService.java`

The single public API through which other modules query role/permission data:

```
findRoleByName(String)   → Optional<RoleEntity>   ← used by RegisterUseCase
```

### 3.6 `{module}/usecase/` — Named Business Operations

| Aspect | Contract |
|---|---|
| **Owns** | One class per named operation. Injects services from the **same module** and cross-module service APIs only. |
| **Publishes** | Spring `ApplicationEvent` for side effects — never calls the side-effector directly |
| **Must NOT import** | `repository/` from any module, `usecase/` from another module, `HttpServletRequest` |
| **Business logic rule** | All guard clauses, validation, and branching live here. Services are called for their technical capability, not their decisions. |
| **Test signal** | If you can test this class with zero mocks, it has no business logic. If mocking `HttpServletRequest` is required, it has a boundary violation. |

### 3.7 `{module}/web/` — HTTP Interface

| Aspect | Contract |
|---|---|
| **Owns** | `@RestController` classes, request/response `record` DTOs |
| **Calls** | Only `usecase/` and `service/` within the same module |
| **Must NOT contain** | Business logic, `try-catch` around domain exceptions, direct repository access |
| **Test signal** | Controller method over ~15 lines of meaningful logic → extract to a use case |

### 3.8 `audit/` — Event-Driven Cross-Cutting Concern

| Aspect | Contract |
|---|---|
| **Owns** | `AuditEventListener`, `AuditLogEntity`, `AuditLogRepository` |
| **No use-case wrapper** | `AuditEventListener` persists directly via `AuditLogRepository`. There is no `LogAuditEventUseCase` — that class was a function masquerading as a use case with no business logic, no branching, and no reason to exist as a separate class. Exception handling lives in the listener itself. |
| **Imports** | `auth/entity/event/*` and `rbac/entity/event/*` — one-directional |
| **Must NOT be imported by** | `auth/`, `rbac/` |

---

## 4. Dependency Rules Matrix

✅ = permitted · ❌ = forbidden · 🔗 = permitted cross-module via service API (not repository)

| From ↓ / To → | `shared/` | `auth/entity` | `auth/service` | `auth/repo` | `auth/web` | `rbac/entity` | `rbac/service` | `rbac/repo` | `rbac/web` | `audit/` |
|---|---|---|---|---|---|---|---|---|---|---|
| `shared/` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `auth/entity` | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ RoleEntity | ❌ | ❌ | ❌ | ❌ |
| `auth/service` | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `auth/usecase` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ entity only | 🔗 RbacQueryService | ❌ | ❌ | ❌ |
| `auth/web` | ✅ | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `rbac/entity` | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `rbac/service` | ✅ | ❌ | 🔗 AuthUserService | ❌ | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ |
| `rbac/usecase` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `rbac/web` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ | ❌ |
| `rbac/seeding` | ✅ | ✅ entity | 🔗 AuthUserService | ❌ | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ |
| `audit/service` | ✅ | ✅ events | ❌ | ❌ | ❌ | ✅ events | ❌ | ❌ | ❌ | ✅ |

> **Cross-module rules:**
> - `auth/entity/UserEntity` references `rbac/entity/RoleEntity` — required by JPA `@ManyToMany`. This is an entity-level structural coupling; no business dependency is implied.
> - `auth/usecase/RegisterUseCase` calls `rbac/service/RbacQueryService.findRoleByName()` to look up `ROLE_USER` — uses the rbac module's public service API, not its repository.
> - `rbac/service/RoleService` and `rbac/service/UserRoleService` call `auth/service/AuthUserService` — uses auth's public service API, not its repository.
> - `rbac/seeding/RbacDataSeeder` calls `auth/service/AuthUserService` to create the developer user.
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

Seeded on first boot by `RbacDataSeeder`. All seeding is idempotent.

### Seeded Permissions

| Permission Name | Granted To |
|---|---|
| `all:all` | `ROLE_DEVELOPER` |
| `role:create`, `role:read`, `role:update`, `role:delete` | `ROLE_SYSTEM_ADMIN` |
| `permission:create`, `permission:read`, `permission:update`, `permission:delete` | `ROLE_SYSTEM_ADMIN` |

### Seeded Roles

| Role Name | Default For | Permissions |
|---|---|---|
| `ROLE_USER` | Every registered user | None |
| `ROLE_SYSTEM_ADMIN` | Operators | Full CRUD on roles and permissions |
| `ROLE_DEVELOPER` | Dev/test environments | `all:all` |

### Wildcard Evaluation (`StarterPermissionEvaluator`)

```
Rule 1: held.contains("all:all")          → GRANTED
Rule 2: held.contains(resource + ":*")    → GRANTED
Rule 3: held.contains(required)           → GRANTED (exact match)
otherwise                                 → DENIED
```

---

## 7. Database Schema

`ddl-auto: validate` — Liquibase owns all schema evolution. Every changeset includes a `rollback` block.

| Changelog File | Table | Key Design Decisions |
|---|---|---|
| `0001_create_users` | `auth_users` | `id UUID PK`, `email VARCHAR UNIQUE`, `password_hash`, `enabled`, `account_non_locked`, `account_non_expired`, `credentials_non_expired`, `failed_login_attempts INT DEFAULT 0`, `locked_at TIMESTAMPTZ NULL`, `created_at`/`updated_at TIMESTAMPTZ` |
| `0002_create_roles` | `auth_roles` | `id UUID PK`, `name VARCHAR(100) UNIQUE`, `description VARCHAR(500)` |
| `0003_create_permissions` | `auth_permissions` | `id UUID PK`, `name VARCHAR(100) UNIQUE`. **DB-level CHECK**: `name ~ '^[a-z_]+:[a-z_*]+$'` |
| `0004_create_user_roles` | `auth_user_roles` | Composite PK `(user_id, role_id)`. FK → `auth_users ON DELETE CASCADE`. FK → `auth_roles ON DELETE RESTRICT` |
| `0005_create_role_permissions` | `auth_role_permissions` | Composite PK `(role_id, permission_id)`. FK → `auth_roles ON DELETE CASCADE`. FK → `auth_permissions ON DELETE RESTRICT` |
| `0006_create_refresh_tokens` | `auth_refresh_tokens` | `id UUID PK`, `token_hash VARCHAR(64) UNIQUE`, `user_id FK`, `issued_at`, `expires_at`, `revoked_at NULLABLE`, `device_fingerprint VARCHAR(255)` |
| `0007_create_token_blacklist` | `auth_token_blacklist` | `id UUID PK`, `jti VARCHAR(36)`, `expires_at TIMESTAMPTZ`. **Composite index** `idx_blacklist_jti_expires (jti, expires_at)` |
| `0008_create_audit_log` | `auth_audit_log` | `id UUID PK`, `event_type VARCHAR(50)`, `actor_id UUID`, `target_id UUID`, `ip_address VARCHAR(45)`, `metadata JSONB`, `occurred_at TIMESTAMPTZ`. Indexes on `(actor_id, occurred_at)` and `(event_type, occurred_at)` |

> `auth_users.failed_login_attempts` and `locked_at` support `FailedLoginTracker`. The existing `account_non_locked` boolean is set to `false` when `locked_at` is populated. This makes the lockout both query-friendly (JPA reads `account_non_locked`) and auditable (timestamp of when locking occurred).

---

## 8. Security Architecture

### 8.1 Authentication Flow

```
Client
  │
  ├─ POST /api/auth/login
  │     │
  │     ├─ [LoginRateLimitFilter] — check per-IP bucket (Bucket4j)
  │     ├─ [JwtAuthenticationFilter] — skips, no token present
  │     ├─ [LoginUseCase]
  │     │     ├─ authManager.authenticate() → [DaoAuthenticationProvider]
  │     │     │     └─ [UserDetailsServiceImpl] → DB query (ONCE per login)
  │     │     │         → builds UserPrincipal from entity fields
  │     │     ├─ On BadCredentialsException → FailedLoginTracker.recordFailure()
  │     │     ├─ On success → FailedLoginTracker.reset()
  │     │     ├─ JwtService.issueAccessToken()
  │     │     ├─ RefreshTokenService.issue()
  │     │     └─ publish UserLoggedInEvent
  │     │
  │     └─ Returns: {accessToken, refreshToken} + HttpOnly cookie
  │
  ├─ GET /api/auth/me
  │     │
  │     ├─ [RequestIdFilter] — MDC requestId
  │     ├─ [JwtAuthenticationFilter]
  │     │     ├─ Extract token (Bearer header or cookie)
  │     │     ├─ tokenValidator.parseAndValidate()      ← interface call
  │     │     ├─ tokenRevocationChecker.isRevoked(jti)  ← interface call
  │     │     ├─ UserPrincipal.fromClaims()  → NO DB QUERY
  │     │     └─ Set SecurityContextHolder
  │     │
  │     └─ [GetProfileUseCase] — reads from SecurityContext
  │
  └─ POST /api/auth/logout
        ├─ [LogoutUseCase]
        │     ├─ blacklist.add(jti, expiry)
        │     └─ publish UserLoggedOutEvent
        └─ Clear HttpOnly cookie
```

### 8.2 Per-Request DB Query Budget

| Path | DB queries |
|---|---|
| Public endpoint (no token) | 0 |
| Authenticated request (valid JWT, not blacklisted) | 1 (blacklist check, indexed) |
| Login | 1 (UserDetailsServiceImpl) + 1 (refresh token write) |
| Failed login | 1 (user lookup) + 1 (increment failure counter) |
| Refresh | 1 (refresh token read/revoke) + 1 (user read for permission re-resolution) |

### 8.3 Cookie Configuration

| Property | Value | Notes |
|---|---|---|
| `HttpOnly` | `true` | JS cannot read the cookie |
| `Secure` | `true` (prod) | HTTPS only |
| `SameSite` | `Strict` | Prevents CSRF on same-site requests |
| `Path` | `/` | All paths receive the cookie |

### 8.4 Rate Limiting

`LoginRateLimitFilter` intercepts `/api/auth/login` and `/api/auth/refresh` before any processing. Per-IP bucket via Bucket4j:

| Setting | Default |
|---|---|
| Max requests | 10 per minute per IP |
| Refill rate | Token bucket (smooth, not burst) |
| Response on exceeded | `429 Too Many Requests` with `Retry-After` header |
| Storage | In-memory (single instance). Redis-backed bucket for multi-instance deployments. |

### 8.5 Account Lockout

`FailedLoginTracker` is called by `LoginUseCase`:
- On `BadCredentialsException`: increment `auth_users.failed_login_attempts`, set `account_non_locked = false` and `locked_at = now()` after threshold (default: 5).
- On successful login: reset `failed_login_attempts = 0`.
- `UserDetailsServiceImpl` reads `account_non_locked`; Spring Security raises `LockedException` which maps to `AccountLockedException → 403`.
- Configurable via `app.security.max-failed-attempts` (default: 5) and `app.security.lockout-duration-minutes` (default: 30 for time-based auto-unlock, or manual admin unlock).

### 8.6 Production Checklist

| Item | Requirement |
|---|---|
| `JWT_SECRET` | Minimum 32 random bytes, base64-encoded. Store in Vault / AWS Secrets Manager. |
| `app.cookie.secure` | Must be `true` in production |
| `app.seeding.enabled` | Set to `false` after first boot |
| `app.cors.allowed-origins` | Set to actual frontend domain — never `*` |
| `spring.jpa.hibernate.ddl-auto` | Always `validate` in production |
| Rate limiting | `LoginRateLimitFilter` is active by default |
| Account lockout | `FailedLoginTracker` active by default; threshold configurable |
| TLS termination | At load balancer or reverse proxy |

### 8.7 Known Trade-offs

| Trade-off | Explanation |
|---|---|
| CSRF disabled | Stateless JWT makes CSRF inapplicable for API clients |
| BCrypt cost factor 12 | Appropriate for 2025 hardware. Increasing it does not auto-rehash existing passwords. |
| Refresh tokens as SHA-256 hash | Raw token returned once to client; DB compromise exposes no valid refresh tokens |
| Blacklist check on every request | Indexed RDBMS read or Redis key lookup. Measure under real load before optimizing. |
| In-memory rate limit buckets | Must switch to Redis-backed for multi-instance deployments |

---

## 9. ArchUnit Enforcement

All dependency rules in Section 1.3 and the matrix in Section 4 are expressed as ArchUnit tests in `ArchitectureRulesTest`. They run as part of the standard test suite and **fail the build** on violation.

Key rule groups:

```
// 1. shared/ imports nothing from feature modules
noClasses().that().resideInAPackage("..shared..")
    .should().dependOnClassesThat().resideInAPackage("..module.auth..")

// 2. Repositories are private to their module
noClasses().that().resideInAPackage("..rbac..")
    .should().dependOnClassesThat().resideInAPackage("..auth.repository..")

noClasses().that().resideInAPackage("..auth..")
    .should().dependOnClassesThat().resideInAPackage("..rbac.repository..")

// 3. usecase/ may not import repository/ from any module
noClasses().that().resideInAPackage("..usecase..")
    .should().dependOnClassesThat().resideInAPackage("..repository..")

// 4. service/ may not import usecase/ from any module
noClasses().that().resideInAPackage("..service..")
    .should().dependOnClassesThat().resideInAPackage("..usecase..")

// 5. web/ may not import repository/ from any module
noClasses().that().resideInAPackage("..web..")
    .should().dependOnClassesThat().resideInAPackage("..repository..")

// 6. audit/ may not be imported by auth/ or rbac/
noClasses().that().resideInAPackage("..auth..").or().resideInAPackage("..rbac..")
    .should().dependOnClassesThat().resideInAPackage("..audit..")
```

---

## 10. Java 25 + Spring Boot 4.0.6 Notes

### 10.1 Virtual Threads

```java
@Override
public Executor getAsyncExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

### 10.2 Module-Local Sealed Exception Hierarchies

```java
// auth/exception/AuthDomainException.java
public sealed class AuthDomainException extends DomainException
    permits InvalidCredentialsException, EmailAlreadyExistsException,
            AccountLockedException, TokenException {}

// rbac/exception/RbacDomainException.java
public sealed class RbacDomainException extends DomainException
    permits RoleInUseException, InvalidPermissionNameException {}
```

`GlobalExceptionHandler` handles each root type:

```java
@ExceptionHandler(AuthDomainException.class)
public ResponseEntity<ApiResponse<Void>> handleAuth(AuthDomainException ex) {
    HttpStatus status = switch (ex) {
        case InvalidCredentialsException e -> HttpStatus.UNAUTHORIZED;
        case EmailAlreadyExistsException e -> HttpStatus.CONFLICT;
        case AccountLockedException e      -> HttpStatus.FORBIDDEN;
        case TokenException e              -> HttpStatus.UNAUTHORIZED;
    };
    return ResponseEntity.status(status).body(ApiResponse.failure(status.name(), ex.getMessage()));
}

@ExceptionHandler(RbacDomainException.class)
public ResponseEntity<ApiResponse<Void>> handleRbac(RbacDomainException ex) {
    HttpStatus status = switch (ex) {
        case RoleInUseException e              -> HttpStatus.CONFLICT;
        case InvalidPermissionNameException e  -> HttpStatus.BAD_REQUEST;
    };
    return ResponseEntity.status(status).body(ApiResponse.failure(status.name(), ex.getMessage()));
}
```

### 10.3 Records and Pattern Matching

All DTOs, response objects, domain events, and value objects are Java `record` types. Lombok retained only for JPA entities.

---

## 11. SOLID Principles Map

| Principle | Location |
|---|---|
| **S** | `LoginUseCase` handles login only. `FailedLoginTracker` handles failure tracking only. `TokenBlacklistPruner` handles cleanup only. `AuditEventListener` handles event persistence only. |
| **O** | `TokenBlacklist` strategy: add `RedisTokenBlacklistService` by implementing the interface. `TokenRevocationChecker` and `TokenValidator` interfaces let shared/ be extended without modification. |
| **L** | `RdbmsTokenBlacklistService` and `RedisTokenBlacklistService` are fully substitutable. `TokenValidator` impls are substitutable. |
| **I** | `TokenValidator` has 1 method. `TokenRevocationChecker` has 1 method. `TokenBlacklist` has 2 methods. Each interface is exactly what its consumers need. |
| **D** | `JwtAuthenticationFilter` depends on `TokenValidator` and `TokenRevocationChecker` (interfaces). `LogoutUseCase` depends on `TokenBlacklist` (interface). Module exceptions extend abstract `DomainException` without `shared/` knowing about their subtypes. |

---

## 12. Extension Points

| Extension | How to Extend |
|---|---|
| New blacklist strategy | Implement `TokenBlacklist` + `TokenRevocationChecker`, add `@ConditionalOnProperty` |
| New audit event consumer | Add `@EventListener` method to `AuditEventListener` or a new component |
| New domain events | Add record to publishing module's `entity/event/`, publish from use case |
| New permissions | Add to `RbacDataSeeder.seedPermissions()` |
| Custom user fields | Add Liquibase changeset + update `UserEntity`. `AuthUserService` exposes the new field if needed by other modules. |
| Rate limiting on new endpoints | Register new path in `LoginRateLimitFilter` or create a new filter |
| OAuth2 / Social login | Add `spring-boot-starter-oauth2-client` + `auth/service/oauth/` package |
| Device fingerprinting | Populate `RefreshTokenEntity.deviceFingerprint` from `AuthController` before calling `RefreshTokenService` |
| MFA (TOTP) | Add `auth/service/TotpService`, `MfaChallengeUseCase`, `/api/auth/mfa/verify` endpoint |
| Password reset | `PasswordResetRequestedEvent` placeholder is in `auth/entity/event/`. Add the use case, email service call, and `/api/auth/reset-password` endpoint. |
| New feature module | Create `{module}/entity/`, `{module}/exception/`, `{module}/repository/`, `{module}/service/`, `{module}/usecase/`, `{module}/web/`. Add one `@ExceptionHandler` block to `GlobalExceptionHandler`. Add ArchUnit rules. Zero changes to `shared/`. |
