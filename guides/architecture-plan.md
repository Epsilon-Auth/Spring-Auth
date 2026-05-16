# auth-spring — Architecture Plan

*Version 3.0 · Clean Layered Architecture · Production-Ready*
*Spring Boot 4.0.6 · Java 25 · Critical Revision*

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
| Architecture | Clean Layered — Domain · Application · Infrastructure · Web |
| License | MIT |

---

## 0. Critical Architecture Analysis of Previous Version

This section documents every architectural defect found in v2.0, ranked by severity. Each problem is explained with its root cause and the correction applied in v3.0.

### 0.1 Problem Inventory (Ranked by Severity)

---

#### ❶ CRITICAL — Security Configuration Trapped Inside a Feature Module

**Problem:** `SecurityConfig.java`, `JwtAuthenticationFilter.java`, and `StarterPermissionEvaluator.java` are located at `auth/infrastructure/security/`. These three classes are **not auth-specific**. They govern the entire HTTP security filter chain for the whole application. `StarterPermissionEvaluator` evaluates permissions from the **rbac** module. `JwtAuthenticationFilter` applies to every controller, not just auth controllers.

**Consequence:** If you ever split `rbac` into a separate module, or add a new feature module, the security infrastructure sits inside a different module, creating an invisible coupling. Any developer reading `rbac/web/RoleController.java` has no obvious path to understand what secures it.

**Fix in v3.0:** Move `SecurityConfig`, `JwtAuthenticationFilter`, and `StarterPermissionEvaluator` to `shared/security/`. Security is cross-cutting by definition.

---

#### ❷ CRITICAL — `OpenApiConfig` in the Wrong Package

**Problem:** `OpenApiConfig.java` was placed inside `auth/infrastructure/security/`. OpenAPI/Swagger configuration has nothing to do with authentication infrastructure. It configures API documentation for the entire application.

**Consequence:** Misleads every developer who reads the folder. OpenAPI is not a security concern and it is not an auth concern.

**Fix in v3.0:** Move to `shared/config/OpenApiConfig.java`.

---

#### ❸ CRITICAL — Cross-Module Repository Access (Boundary Violation)

**Problem:** `RoleService.java` (in `rbac/application/`) directly injects `UserJpaRepository` (from `auth/infrastructure/persistence/`). This is a hard boundary violation: the **rbac** application layer reaches into the **auth** infrastructure layer of another module.

```
rbac/application/RoleService  →  auth/infrastructure/persistence/UserJpaRepository
                                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                                FORBIDDEN: crosses module + layer boundaries
```

**Consequence:** Any change to `UserJpaRepository` or `UserEntity` can silently break `RoleService`. The modules are not independent.

**Fix in v3.0:** Introduce `UserRoleAssignmentPort` — an interface defined in `rbac/application/port/`. The `auth` infrastructure layer provides the adapter that implements it. Direction of dependency is inverted: rbac defines what it needs, auth fulfils the contract.

---

#### ❹ HIGH — Domain Events Owned by the Consumer, Not the Publisher

**Problem:** `UserRegisteredEvent`, `UserLoggedInEvent`, `RoleCreatedEvent`, etc. all live in `audit/event/`. This means `auth/application/AuthService` must **import from `audit/`** to publish `UserRegisteredEvent`. The `rbac/application/RoleService` must **import from `audit/`** to publish `RoleCreatedEvent`.

```
auth/application/AuthService  →  audit/event/UserRegisteredEvent   ← WRONG direction
rbac/application/RoleService  →  audit/event/RoleCreatedEvent      ← WRONG direction
```

**Consequence:** Feature modules depend on the audit module. Adding an audit module to a greenfield feature requires touching that feature's source files. Removing audit becomes impossible without breaking auth and rbac.

**Fix in v3.0:** Events live in the **publishing** module, in an `event/` sub-package. `auth/event/` contains auth-related events. `rbac/event/` contains rbac-related events. The `audit` module depends on these packages — not the reverse.

```
audit/application/AuditEventListener  →  auth/event/UserRegisteredEvent   ← CORRECT
audit/application/AuditEventListener  →  rbac/event/RoleCreatedEvent       ← CORRECT
```

---

#### ❺ HIGH — `AuthService` Violates Single Responsibility (God Class)

**Problem:** `AuthService` contains five distinct use cases: `login`, `register`, `logout`, `refreshTokens`, and `me`. Each use case has different dependencies, different transaction scopes, and different failure modes.

**Consequence:** Every dependency needed by any one use case is visible to every other. Testing `register` requires mocking JWT infrastructure. A change to the refresh flow forces a recompile of the login flow.

**Fix in v3.0:** One class per use case. `LoginUseCase`, `RegisterUseCase`, `LogoutUseCase`, `RefreshTokensUseCase`, `GetProfileUseCase`. Each injects only the dependencies it actually needs.

---

#### ❻ HIGH — Missing `UserRoleService` (Incomplete Feature)

**Problem:** The architecture defined a `UserRoleController` with POST/DELETE endpoints for assigning roles to users. No corresponding application service was specified in the implementation plan. The controller has nothing to call.

**Fix in v3.0:** `UserRoleService` is fully specified.

---

#### ❼ MEDIUM — Permission Name Validation in Application Layer (Wrong Layer)

**Problem:** `PermissionService` contains `Pattern NAME_PATTERN = Pattern.compile("^[a-z_]+:[a-z_*]+$")` and validates it. Format validation of a domain concept (`resource:action` format) is a **domain rule**, not an application orchestration concern.

**Fix in v3.0:** Introduce `PermissionName` as a value object in `rbac/domain/`. The value object self-validates on construction. `PermissionService` never sees raw strings for permission names.

---

#### ❽ MEDIUM — Unsafe JSON Construction in AuditEventListener

**Problem:**
```java
"{\"email\":\"" + event.email() + "\"}"    // string concatenation → injection vector
"{\"name\":\"" + event.roleName() + "\"}"   // not escaped, breaks on special chars
```

**Fix in v3.0:** Inject `ObjectMapper` into `AuditEventListener`. Use `objectMapper.writeValueAsString(Map.of(...))` for all metadata fields.

---

#### ❾ MEDIUM — Java 21 Features Not Leveraged (Version Upgrade)

**Problem:** The codebase targets Java 21 LTS with Spring Boot 3.5.0. The user requires Java 25 with Spring Boot 4.0.6. Several Java 25 features improve correctness and clarity:
- Virtual threads (Project Loom — stable since Java 21) for the async audit listener executor
- Pattern matching `switch` (stable since Java 21) for exception handler dispatch
- Sealed interfaces for the exception hierarchy (stable since Java 17)
- `@Async` default executor should use virtual threads

**Fix in v3.0:** All changes documented in the implementation plan.

---

#### ❿ LOW — Lombok as a Dependency (Java 25 Makes It Redundant)

**Problem:** Lombok is listed as a dependency. On Java 25, `record` covers `@Value`, `@Data` (immutable), and parameter objects. Annotation processor compatibility with Java 25 must be verified per release.

**Fix in v3.0:** Lombok is retained **only** for JPA entities (which cannot be records — JPA requires mutable state and no-arg constructors). All DTOs, value objects, events, and response types use `record`. This reduces the Lombok surface to `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor` on entity classes only.

---

### 0.2 Boundary Violation Map

The diagram below shows every illegal dependency that existed in v2.0. All are resolved in v3.0.

```
v2.0 VIOLATIONS:
┌──────────────────────────────────────────────────────────────┐
│  auth/infrastructure/security/SecurityConfig                  │
│  → governs ALL modules (boundary violation: feature owns      │
│    cross-cutting concern)                                     │
├──────────────────────────────────────────────────────────────┤
│  auth/infrastructure/security/OpenApiConfig                   │
│  → belongs in shared/config (zero relation to auth security)  │
├──────────────────────────────────────────────────────────────┤
│  rbac/application/RoleService → UserJpaRepository             │
│  → cross-module + cross-layer import (rbac → auth infra)      │
├──────────────────────────────────────────────────────────────┤
│  auth/application/AuthService → audit/event/*                 │
│  rbac/application/RoleService → audit/event/*                 │
│  → publishers depend on consumer (inverted event ownership)   │
└──────────────────────────────────────────────────────────────┘
```

---

## 1. Architecture Overview

`auth-spring` is a production-ready authentication and RBAC template. The architecture follows Clean Layered principles with strict dependency inversion. Every class has a single reason to change. The folder structure is the documentation.

### 1.1 Core Design Decisions (v3.0)

| Decision | Rationale |
|---|---|
| Single Maven module | Template scope — multi-module adds Maven reactor complexity with no benefit here |
| Feature-based top-level packages | `auth/`, `rbac/`, `audit/` own everything that changes together |
| `shared/security/` for cross-cutting security | `SecurityConfig` governs all modules — it cannot live inside one |
| One class per use case in `application/` | Minimal dependencies, targeted testing, clear intent |
| Port interfaces for cross-module access only | YAGNI for single-module internals; required at module boundaries |
| Domain value objects for validated concepts | `PermissionName` encodes the format rule; services never see raw strings |
| Events owned by publisher, consumed by audit | Correct event flow: auth/rbac publish, audit subscribes — never reverse |
| Records for all non-entity types | Java 25 records eliminate boilerplate for DTOs, events, value objects, responses |
| Virtual thread executor for async audit | Project Loom provides lightweight threads; no pool tuning needed |
| Strategy pattern for `TokenBlacklist` | Swap RDBMS ↔ Redis by changing one property, zero code changes |
| `UserPrincipal` separate from `UserEntity` | Decouples JPA model from Spring Security; zero DB hit per request |
| `ddl-auto: validate` + Liquibase | Liquibase owns schema; Hibernate only validates at startup |

### 1.2 Layer Dependency Rules (v3.0)

```
┌──────────────────────────────────────────────────────────────┐
│  web/  — HTTP Interface                                       │
│  Knows about: application/ use cases only                     │
│  Never imports: infrastructure/, domain/ entities, Security   │
└──────────────────────────────────────────────────────────────┘
                            ↓ depends on
┌──────────────────────────────────────────────────────────────┐
│  application/  — Use Case Orchestration                       │
│  Knows about: application/port/ interfaces, domain/, shared/  │
│  Publishes: ApplicationEvents (one-way, no coupling to audit) │
│  Never imports: web/, infrastructure/, Spring Security, JJWT  │
└──────────────────────────────────────────────────────────────┘
                            ↓ depends on
┌──────────────────────────────────────────────────────────────┐
│  domain/  — Business Rules & Value Objects                    │
│  Knows about: nothing — no Spring, no JPA, no Security        │
│  Pure Java: validated value objects, domain enums             │
└──────────────────────────────────────────────────────────────┘
                            ↑ implemented by
┌──────────────────────────────────────────────────────────────┐
│  infrastructure/  — Technical Adapters                        │
│  Knows about: domain/, application/port/, shared/, Spring     │
│  Contains: JPA entities/repos, JWT, token blacklist, seeding  │
│  Never imports: web/, other modules' infrastructure/          │
└──────────────────────────────────────────────────────────────┘
                            ↑ both depend on
┌──────────────────────────────────────────────────────────────┐
│  shared/  — Cross-Cutting Concerns                            │
│  Knows about: nothing — no feature module imports             │
│  Contains: config, exceptions, ApiResponse, security filters  │
└──────────────────────────────────────────────────────────────┘
```

### 1.3 Hard Dependency Rules

| Rule | Description |
|---|---|
| `shared/` is a sink | `shared/` must NEVER import from `auth/`, `rbac/`, or `audit/` |
| `domain/` is pure | No Spring annotations (`@Component`, `@Service`, etc.), no JPA, no Security |
| `application/` is technology-free | No `HttpServletRequest`, no `@Entity`, no JJWT, no `@Scheduled` |
| `web/` only calls application | Controllers never inject repositories, never call `infrastructure/` directly |
| Cross-module via port only | `rbac` accesses `auth` data **only** through a port interface |
| Events flow from publisher to audit | `auth/event/` and `rbac/event/` are imported by `audit/`. Never the reverse |
| Security config is in `shared/` | `SecurityConfig`, `JwtAuthenticationFilter`, `StarterPermissionEvaluator` live in `shared/security/` |

---

## 2. Complete Folder Structure (v3.0)

Every path encodes intent. Reading the path alone tells you what the class does and why it exists.

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
    │   │   ├── shared/                                   ← CROSS-CUTTING ONLY
    │   │   │   │                                            zero business logic
    │   │   │   │                                            zero feature-specific code
    │   │   │   ├── config/
    │   │   │   │   ├── AppProperties.java                ← @ConfigurationProperties(prefix=app)
    │   │   │   │   ├── JpaConfig.java                    ← @EnableJpaAuditing
    │   │   │   │   ├── JacksonConfig.java                ← ObjectMapper bean
    │   │   │   │   ├── SchedulingConfig.java             ← @EnableScheduling
    │   │   │   │   ├── AsyncConfig.java                  ← Virtual thread executor (NEW)
    │   │   │   │   └── OpenApiConfig.java                ← MOVED from auth/infra/security
    │   │   │   │
    │   │   │   ├── exception/                            ← Sealed exception hierarchy
    │   │   │   │   ├── DomainException.java              ← sealed base
    │   │   │   │   ├── ResourceNotFoundException.java    ← 404
    │   │   │   │   ├── EmailAlreadyExistsException.java  ← 409
    │   │   │   │   ├── AuthException.java                ← 401
    │   │   │   │   ├── RoleInUseException.java           ← 409
    │   │   │   │   └── InvalidPermissionNameException.java ← 400
    │   │   │   │
    │   │   │   ├── security/                             ← MOVED from auth/infra/security
    │   │   │   │   │                                        Security is CROSS-CUTTING
    │   │   │   │   ├── SecurityConfig.java               ← filter chain, CORS, method security
    │   │   │   │   ├── JwtAuthenticationFilter.java      ← per-request JWT validation
    │   │   │   │   └── StarterPermissionEvaluator.java   ← @PreAuthorize hasPermission()
    │   │   │   │
    │   │   │   └── web/
    │   │   │       ├── ApiResponse.java                  ← generic envelope record
    │   │   │       ├── ApiError.java                     ← error detail record
    │   │   │       ├── GlobalExceptionHandler.java       ← @RestControllerAdvice
    │   │   │       └── RequestIdFilter.java              ← MDC + X-Request-ID
    │   │   │
    │   │   ├── auth/
    │   │   │   │
    │   │   │   ├── event/                                ← auth publishes these (NEW location)
    │   │   │   │   ├── UserRegisteredEvent.java          ← record
    │   │   │   │   ├── UserLoggedInEvent.java            ← record
    │   │   │   │   └── UserLoggedOutEvent.java           ← record
    │   │   │   │
    │   │   │   ├── application/
    │   │   │   │   ├── port/                             ← output port interfaces (DIP)
    │   │   │   │   │   ├── UserPort.java                 ← interface: find/exists/save user
    │   │   │   │   │   └── RefreshTokenPort.java         ← interface: issue/revoke/validate
    │   │   │   │   │
    │   │   │   │   ├── LoginUseCase.java                 ← SPLIT from AuthService
    │   │   │   │   ├── RegisterUseCase.java              ← SPLIT from AuthService
    │   │   │   │   ├── LogoutUseCase.java                ← SPLIT from AuthService
    │   │   │   │   ├── RefreshTokensUseCase.java         ← SPLIT from AuthService
    │   │   │   │   └── GetProfileUseCase.java            ← SPLIT from AuthService
    │   │   │   │
    │   │   │   ├── domain/
    │   │   │   │   └── UserStatus.java                   ← enum: ACTIVE, LOCKED, EXPIRED
    │   │   │   │
    │   │   │   ├── infrastructure/
    │   │   │   │   ├── persistence/
    │   │   │   │   │   ├── UserEntity.java               ← @Entity, NO UserDetails
    │   │   │   │   │   ├── RefreshTokenEntity.java
    │   │   │   │   │   ├── TokenBlacklistEntry.java
    │   │   │   │   │   ├── UserJpaRepository.java        ← Spring Data interface
    │   │   │   │   │   ├── RefreshTokenJpaRepository.java
    │   │   │   │   │   ├── TokenBlacklistJpaRepository.java
    │   │   │   │   │   ├── JpaUserAdapter.java           ← implements UserPort
    │   │   │   │   │   └── JpaRefreshTokenAdapter.java   ← implements RefreshTokenPort
    │   │   │   │   │
    │   │   │   │   ├── security/
    │   │   │   │   │   ├── JwtService.java               ← token issue + parse only
    │   │   │   │   │   ├── UserPrincipal.java            ← implements UserDetails (adapter)
    │   │   │   │   │   └── UserDetailsServiceImpl.java   ← login path DB lookup
    │   │   │   │   │
    │   │   │   │   └── token/
    │   │   │   │       ├── TokenBlacklist.java           ← strategy interface
    │   │   │   │       ├── RdbmsTokenBlacklist.java      ← @ConditionalOnProperty
    │   │   │   │       ├── RedisTokenBlacklist.java      ← @ConditionalOnProperty
    │   │   │   │       └── TokenBlacklistPruner.java     ← @Scheduled, SRP
    │   │   │   │
    │   │   │   └── web/
    │   │   │       ├── AuthController.java
    │   │   │       └── dto/
    │   │   │           ├── request/
    │   │   │           │   ├── LoginRequest.java         ← record
    │   │   │           │   ├── RegisterRequest.java      ← record
    │   │   │           │   └── RefreshTokenRequest.java  ← record
    │   │   │           └── response/
    │   │   │               ├── TokenResponse.java        ← record
    │   │   │               └── UserProfileResponse.java  ← record
    │   │   │
    │   │   ├── rbac/
    │   │   │   │
    │   │   │   ├── event/                                ← rbac publishes these (NEW location)
    │   │   │   │   ├── RoleCreatedEvent.java             ← record
    │   │   │   │   ├── RoleUpdatedEvent.java             ← record
    │   │   │   │   ├── RoleDeletedEvent.java             ← record
    │   │   │   │   └── PermissionAssignedEvent.java      ← record
    │   │   │   │
    │   │   │   ├── domain/
    │   │   │   │   └── PermissionName.java               ← value object, self-validating
    │   │   │   │
    │   │   │   ├── application/
    │   │   │   │   ├── port/
    │   │   │   │   │   ├── RolePort.java                 ← find/exists/save/delete role
    │   │   │   │   │   ├── PermissionPort.java           ← find/exists/save/delete permission
    │   │   │   │   │   └── UserRoleAssignmentPort.java   ← FIX for cross-module access
    │   │   │   │   │                                        (replaces UserJpaRepository import)
    │   │   │   │   ├── RoleService.java
    │   │   │   │   ├── PermissionService.java
    │   │   │   │   └── UserRoleService.java              ← NEW (was missing)
    │   │   │   │
    │   │   │   ├── infrastructure/
    │   │   │   │   ├── persistence/
    │   │   │   │   │   ├── RoleEntity.java
    │   │   │   │   │   ├── PermissionEntity.java
    │   │   │   │   │   ├── RoleJpaRepository.java
    │   │   │   │   │   ├── PermissionJpaRepository.java
    │   │   │   │   │   ├── JpaRoleAdapter.java           ← implements RolePort
    │   │   │   │   │   └── JpaPermissionAdapter.java     ← implements PermissionPort
    │   │   │   │   │
    │   │   │   │   ├── crossmodule/
    │   │   │   │   │   └── JpaUserRoleAssignmentAdapter.java ← implements UserRoleAssignmentPort
    │   │   │   │   │                                          uses UserJpaRepository injected here
    │   │   │   │   │                                          (infra layer → cross-module is OK)
    │   │   │   │   │
    │   │   │   │   └── seeding/
    │   │   │   │       └── RbacDataSeeder.java           ← @Component, ApplicationRunner
    │   │   │   │
    │   │   │   └── web/
    │   │   │       ├── RoleController.java
    │   │   │       ├── PermissionController.java
    │   │   │       ├── UserRoleController.java
    │   │   │       └── dto/
    │   │   │           ├── request/
    │   │   │           │   ├── CreateRoleRequest.java
    │   │   │           │   ├── UpdateRoleRequest.java
    │   │   │           │   └── CreatePermissionRequest.java
    │   │   │           └── response/
    │   │   │               ├── RoleResponse.java
    │   │   │               └── PermissionResponse.java
    │   │   │
    │   │   └── audit/
    │   │       │                                         ← audit CONSUMES auth + rbac events
    │   │       ├── application/
    │   │       │   ├── AuditableEvent.java               ← marker interface
    │   │       │   └── AuditEventListener.java           ← @EventListener (imports auth/rbac events)
    │   │       │
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
        ├── auth/
        │   ├── application/            ← LoginUseCaseTest, RegisterUseCaseTest, etc.
        │   ├── infrastructure/         ← JwtServiceTest, UserPrincipalTest, BlacklistTest
        │   └── web/                    ← AuthControllerTest
        ├── rbac/
        │   ├── application/            ← RoleServiceTest, PermissionServiceTest, UserRoleServiceTest
        │   ├── domain/                 ← PermissionNameTest (value object validation)
        │   └── web/                    ← RoleControllerTest, PermissionControllerTest
        ├── shared/
        │   └── security/              ← StarterPermissionEvaluatorTest
        └── integration/
            ├── AuthFlowIntegrationTest
            ├── RbacFlowIntegrationTest
            └── TokenBlacklistIntegrationTest
```

---

## 3. Layer Contract Definitions

For each layer the contract is: **what it owns**, **what it must never contain**, and **the test signal** (a failing rule that tells you the code is in the wrong place).

### 3.1 `shared/` — Cross-Cutting Infrastructure

| Aspect | Contract |
|---|---|
| **Owns** | `AppProperties`, config beans (`JpaConfig`, `JacksonConfig`, `SchedulingConfig`, `AsyncConfig`, `OpenApiConfig`), sealed exception hierarchy, `ApiResponse<T>`, `ApiError`, `GlobalExceptionHandler`, `RequestIdFilter`, `SecurityConfig`, `JwtAuthenticationFilter`, `StarterPermissionEvaluator` |
| **Must NOT contain** | Business logic of any kind, `@Entity`, feature-specific service calls, any import from `auth/`, `rbac/`, or `audit/` |
| **Test signal** | If a class in `shared/` has an import path containing `auth`, `rbac`, or `audit`, it is in the wrong place |

### 3.2 `{module}/domain/` — Business Rules

| Aspect | Contract |
|---|---|
| **Owns** | Value objects, domain enums, domain-specific validation rules |
| **Must NOT contain** | `@Component`, `@Service`, `@Entity`, `@Repository`, any Spring annotation, any Jakarta EE import |
| **Test signal** | If the class has a Spring import, it is not a domain class |
| **Examples** | `PermissionName` (validates `resource:action` format), `UserStatus` (enum of account states) |

### 3.3 `{module}/application/` — Use Case Orchestration

| Aspect | Contract |
|---|---|
| **Owns** | One class per named use case, port interfaces (`application/port/`), internal coordination services |
| **Calls** | Port interfaces only — never Spring Data repositories directly across module boundaries |
| **Publishes** | Spring `ApplicationEvent` subclasses for side effects — never calls the side-effector directly |
| **Must NOT contain** | `HttpServletRequest`, `HttpServletResponse`, `@Entity`, JJWT classes, `@Scheduled`, `Cookie`, any Spring Security class |
| **Test signal** | If you must mock a Spring Security class to test an application service, the service has a boundary violation |
| **Transaction rule** | `@Transactional` is permitted here — it is a coordination annotation, not infrastructure logic |

### 3.4 `{module}/infrastructure/` — Technical Adapters

| Aspect | Contract |
|---|---|
| **Owns** | JPA entities and repositories, port implementations (`JpaXxxAdapter`), JWT service, security adapters (`UserPrincipal`, `UserDetailsServiceImpl`), token blacklist implementations, data seeders |
| **Must NOT contain** | Business logic, use case orchestration, controller logic |
| **Change rule** | A change in any external library (JJWT, Spring Security API, Redis client) must be **entirely contained** inside this layer |
| **Cross-module infra** | `rbac/infrastructure/crossmodule/` may import `auth/infrastructure/persistence/UserJpaRepository`. This is the **only permitted** cross-module infrastructure dependency and it is one-directional |

### 3.5 `{module}/web/` — HTTP Interface

| Aspect | Contract |
|---|---|
| **Owns** | `@RestController` classes, Java `record` DTOs for request/response, `@Valid` binding |
| **Calls** | Only `application/` use case classes |
| **Must NOT contain** | Business logic, `try-catch` around domain exceptions (those go in `GlobalExceptionHandler`), direct repository access |
| **Test signal** | If a controller method is longer than 15 lines, it has absorbed logic that belongs in the use case |

### 3.6 `audit/` — Event-Driven Cross-Cutting Concern

| Aspect | Contract |
|---|---|
| **Owns** | `AuditableEvent` marker interface, `AuditEventListener`, `AuditLogEntity`, `AuditLogJpaRepository` |
| **Imports** | `auth/event/*` and `rbac/event/*` — one-directional, audit depends on publishers |
| **Must NOT be imported by** | `auth/`, `rbac/` (direction is strictly: publishers → events ← audit) |
| **Extension** | Add a second `@EventListener` method to `AuditEventListener` for Kafka, Slack, etc. without touching any publisher |

---

## 4. Dependency Rules Matrix

✅ = permitted · ❌ = forbidden · ⚠️ = permitted only via port interface

| From ↓ / To → | `shared/` | `auth/domain` | `auth/app` | `auth/infra` | `auth/web` | `rbac/domain` | `rbac/app` | `rbac/infra` | `rbac/web` | `audit/` |
|---|---|---|---|---|---|---|---|---|---|---|
| `shared/` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `auth/domain` | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `auth/app` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `auth/infra` | ✅ | ✅ | ✅ port | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `auth/web` | ✅ | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `rbac/domain` | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `rbac/app` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| `rbac/infra` | ✅ | ❌ | ❌ | ✅ repo only | ❌ | ✅ | ✅ port | ✅ | ❌ | ❌ |
| `rbac/web` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ | ❌ |
| `audit/app` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `audit/infra` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

> **Note on `rbac/infra → auth/infra`:** `JpaUserRoleAssignmentAdapter` in `rbac/infrastructure/crossmodule/` injects `UserJpaRepository` from `auth/infrastructure/persistence/`. This is the only permitted cross-module infrastructure dependency. It is isolated in a dedicated `crossmodule/` sub-package to make the coupling explicit and auditable.

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
| `PUT` | `/api/roles/{id}` | `role:update` | Update role name/description |
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
| `0003_create_permissions` | `auth_permissions` | `id UUID PK`, `name VARCHAR(100) UNIQUE NOT NULL`. **DB-level CHECK**: `name ~ '^[a-z_]+:[a-z_*]+$'` (second line of defence after domain validation) |
| `0004_create_user_roles` | `auth_user_roles` | Composite PK `(user_id, role_id)`. FK → `auth_users ON DELETE CASCADE`. FK → `auth_roles ON DELETE RESTRICT` |
| `0005_create_role_permissions` | `auth_role_permissions` | Composite PK `(role_id, permission_id)`. FK → `auth_roles ON DELETE CASCADE`. FK → `auth_permissions ON DELETE RESTRICT` |
| `0006_create_refresh_tokens` | `auth_refresh_tokens` | `id UUID PK`, `token_hash VARCHAR(64) UNIQUE`, `user_id FK`, `issued_at`, `expires_at`, `revoked_at NULLABLE`, `device_fingerprint VARCHAR(255)` |
| `0007_create_token_blacklist` | `auth_token_blacklist` | `id UUID PK`, `jti VARCHAR(36)`, `expires_at TIMESTAMPTZ`. **Composite index** `idx_blacklist_jti_expires (jti, expires_at)` — hot path for every authenticated request |
| `0008_create_audit_log` | `auth_audit_log` | `id UUID PK`, `event_type VARCHAR(50) NOT NULL`, `actor_id UUID`, `target_id UUID`, `ip_address VARCHAR(45)`, `metadata JSONB`, `occurred_at TIMESTAMPTZ NOT NULL`. **Indexes**: `idx_audit_actor (actor_id, occurred_at)`, `idx_audit_type (event_type, occurred_at)` |

> Note on `auth_audit_log.metadata`: Changed from `TEXT` to `JSONB` in v3.0. Enables server-side JSON queries and eliminates string-concatenation injection. Requires `ObjectMapper` usage in `AuditEventListener`.

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
  │     │     ├─ RefreshTokenPort.issue()
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

All DTOs, response objects, domain events, and value objects are Java `record` types. Lombok is retained only for JPA entities (which require mutable state, no-arg constructors, and `equals`/`hashCode` based on `id` only — patterns that records do not fit).

### 9.4 Pattern Matching in GlobalExceptionHandler

```java
// Java 25 pattern matching switch in exception handler
private int statusFor(DomainException ex) {
    return switch (ex) {
        case AuthException e           -> 401;
        case ResourceNotFoundException e -> 404;
        case EmailAlreadyExistsException e,
             RoleInUseException e      -> 409;
        case InvalidPermissionNameException e -> 400;
    };
}
```

---

## 10. SOLID Principles Map (v3.0)

| Principle | Concrete Location in Codebase |
|---|---|
| **S — Single Responsibility** | `LoginUseCase` handles only login. `LogoutUseCase` handles only logout. `JwtService` handles only token cryptography. `TokenBlacklistPruner` handles only expired-entry cleanup. `RbacDataSeeder` handles only bootstrap seeding. No class has more than one reason to change |
| **O — Open/Closed** | `TokenBlacklist`: add `MemcachedTokenBlacklist` by implementing the interface and adding `@ConditionalOnProperty`. No existing class changes. `AuditEventListener`: add a Kafka bridge by adding a second `@EventListener` method |
| **L — Liskov Substitution** | `RdbmsTokenBlacklist` and `RedisTokenBlacklist` are fully substitutable. `JpaUserAdapter` and any future `MongoUserAdapter` implement `UserPort` — callers in `application/` behave identically regardless of which adapter is active |
| **I — Interface Segregation** | `TokenBlacklist` has exactly 2 methods. `UserPort` exposes only what `application/` needs from users. `UserRoleAssignmentPort` exposes only the one method `rbac` needs. `AuditableEvent` is a pure marker |
| **D — Dependency Inversion** | `LoginUseCase` depends on `UserPort` and `RefreshTokenPort` (abstractions). `RoleService` depends on `UserRoleAssignmentPort` (not `UserJpaRepository`). All `application/` classes depend on port interfaces — never on JPA concrete classes |

---

## 11. Extension Points

None of these require modifying existing source files.

| Extension | How to Extend |
|---|---|
| New blacklist strategy | Implement `TokenBlacklist`, add `@ConditionalOnProperty`. Zero changes to filter or use cases |
| New audit event consumer | Add `@EventListener` method to `AuditEventListener`, or create a new `@EventListener` class for Kafka/Slack |
| New domain events | Add a record to the publishing module's `event/` package, publish from use case, add handler to `AuditEventListener` |
| New permissions | Add to `RbacDataSeeder.seedPermissions()`. `ROLE_DEVELOPER` receives them via `all:all` automatically |
| Custom user fields | Add Liquibase changeset + update `UserEntity` + update `UserPort` method return type if needed |
| Rate limiting | Add a `HandlerInterceptor` bean — injection point in filter chain is ready in `SecurityConfig` |
| OAuth2 / Social login | Add `spring-boot-starter-oauth2-client` + `auth/infrastructure/oauth/` package |
| Device fingerprinting | Populate `RefreshTokenEntity.deviceFingerprint` from `HttpServletRequest` in `RegisterUseCase` or `LoginUseCase` |
| MFA (TOTP) | Add `auth/infrastructure/mfa/TotpService`, a new `MfaChallengeUseCase`, and a `/api/auth/mfa/verify` endpoint |
