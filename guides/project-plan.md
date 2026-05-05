Two quick notes before the plan: `Spring Boot 3.5.14` is a non-existent patch as of my knowledge (3.5.x just launched — start with `3.5.0`), and Java 26 is a non-LTS preview build; Spring's official support tops out at Java 21 LTS. I'd recommend `<java.version>21</java.version>` for team stability, but the plan below works either way — just update the property.

---

## The mental shift: template, not library

You're cloning this repo as a **git template** (`git clone → git remote set-url`), so the project is a fully runnable Spring Boot app your team removes modules they don't need, adds their own feature packages, and ships. Everything below is designed with that workflow in mind.

Here is the definitive package structure — every folder has a single, non-negotiable responsibility:---

## Complete file tree

```
auth-spring/                                     ← git clone this for every new project
├── pom.xml                                      ← spring-boot-maven-plugin present (runnable jar)
├── .gitignore
├── README.md
├── CHANGELOG.md
│
├── src/
│   ├── main/
│   │   ├── java/io/epsilon/auth/
│   │   │   ├── AuthSpringApplication.java
│   │   │   │
│   │   │   ├── common/
│   │   │   │   ├── config/
│   │   │   │   │   ├── SecurityConfig.java
│   │   │   │   │   ├── OpenApiConfig.java
│   │   │   │   │   ├── JacksonConfig.java
│   │   │   │   │   └── SchedulingConfig.java
│   │   │   │   ├── exception/
│   │   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   │   ├── AuthException.java
│   │   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   │   ├── EmailAlreadyExistsException.java
│   │   │   │   │   ├── RoleInUseException.java
│   │   │   │   │   └── InvalidPermissionNameException.java
│   │   │   │   ├── response/
│   │   │   │   │   ├── ApiResponse.java          ← generic wrapper record
│   │   │   │   │   └── ApiError.java
│   │   │   │   └── filter/
│   │   │   │       └── RequestIdFilter.java
│   │   │   │
│   │   │   ├── auth/
│   │   │   │   ├── controller/
│   │   │   │   │   └── AuthController.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── AuthService.java
│   │   │   │   │   ├── JwtService.java
│   │   │   │   │   └── RefreshTokenService.java
│   │   │   │   ├── security/
│   │   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   │   ├── CustomUserDetailsService.java
│   │   │   │   │   └── StarterPermissionEvaluator.java
│   │   │   │   ├── blacklist/
│   │   │   │   │   ├── TokenBlacklist.java          ← interface (SRP + OCP)
│   │   │   │   │   ├── RdbmsTokenBlacklist.java
│   │   │   │   │   └── RedisTokenBlacklist.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── request/
│   │   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   │   ├── RegisterRequest.java
│   │   │   │   │   │   └── RefreshTokenRequest.java
│   │   │   │   │   └── response/
│   │   │   │   │       ├── TokenResponse.java
│   │   │   │   │       └── UserProfileResponse.java
│   │   │   │   └── event/
│   │   │   │       ├── UserRegisteredEvent.java
│   │   │   │       ├── UserLoggedInEvent.java
│   │   │   │       └── UserLoggedOutEvent.java
│   │   │   │
│   │   │   ├── rbac/
│   │   │   │   ├── controller/
│   │   │   │   │   ├── RoleController.java
│   │   │   │   │   ├── PermissionController.java
│   │   │   │   │   └── UserRoleController.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── RoleService.java
│   │   │   │   │   ├── PermissionService.java
│   │   │   │   │   └── DataSeederService.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── request/
│   │   │   │   │   │   ├── CreateRoleRequest.java
│   │   │   │   │   │   ├── UpdateRoleRequest.java
│   │   │   │   │   │   ├── CreatePermissionRequest.java
│   │   │   │   │   │   └── AssignPermissionRequest.java
│   │   │   │   │   └── response/
│   │   │   │   │       ├── RoleResponse.java
│   │   │   │   │       └── PermissionResponse.java
│   │   │   │   └── event/
│   │   │   │       ├── RoleCreatedEvent.java
│   │   │   │       ├── RoleUpdatedEvent.java
│   │   │   │       └── PermissionAssignedEvent.java
│   │   │   │
│   │   │   ├── domain/
│   │   │   │   ├── entity/
│   │   │   │   │   ├── UserEntity.java
│   │   │   │   │   ├── RoleEntity.java
│   │   │   │   │   ├── PermissionEntity.java
│   │   │   │   │   ├── RefreshTokenEntity.java
│   │   │   │   │   ├── TokenBlacklistEntry.java
│   │   │   │   │   └── AuditLogEntity.java
│   │   │   │   └── repository/
│   │   │   │       ├── UserRepository.java
│   │   │   │       ├── RoleRepository.java
│   │   │   │       ├── PermissionRepository.java
│   │   │   │       ├── RefreshTokenRepository.java
│   │   │   │       ├── TokenBlacklistRepository.java
│   │   │   │       └── AuditLogRepository.java
│   │   │   │
│   │   │   └── audit/
│   │   │       ├── AuditableEvent.java           ← marker interface
│   │   │       └── AuditEventListener.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── db/changelog/
│   │           ├── db.changelog-master.yaml
│   │           └── changes/
│   │               ├── 0001_create_users.yaml
│   │               ├── 0002_create_roles.yaml
│   │               ├── 0003_create_permissions.yaml
│   │               ├── 0004_create_user_roles.yaml
│   │               ├── 0005_create_role_permissions.yaml
│   │               ├── 0006_create_refresh_tokens.yaml
│   │               ├── 0007_create_token_blacklist.yaml
│   │               └── 0008_create_audit_log.yaml
│   │
│   └── test/
│       └── java/io/epsilon/auth/
│           ├── auth/
│           │   ├── controller/AuthControllerTest.java
│           │   ├── service/
│           │   │   ├── AuthServiceTest.java
│           │   │   ├── JwtServiceTest.java
│           │   │   └── RefreshTokenServiceTest.java
│           │   ├── blacklist/RdbmsTokenBlacklistTest.java
│           │   └── security/StarterPermissionEvaluatorTest.java
│           ├── rbac/
│           │   ├── controller/
│           │   │   ├── RoleControllerTest.java
│           │   │   └── PermissionControllerTest.java
│           │   └── service/
│           │       ├── RoleServiceTest.java
│           │       ├── PermissionServiceTest.java
│           │       └── DataSeederServiceTest.java
│           ├── domain/repository/
│           │   ├── UserRepositoryTest.java
│           │   └── RefreshTokenRepositoryTest.java
│           └── integration/
│               ├── AuthFlowIntegrationTest.java
│               ├── RbacFlowIntegrationTest.java
│               └── TokenBlacklistIntegrationTest.java
```

---

## Phase 1 — Project bootstrap & configuration contract

**Goal:** The project boots, connects to a database, and exposes every configuration knob through a single, documented YAML contract — before a single line of business logic is written.

---

**Task 1.1 — Fix the `pom.xml`**

Update `<java.version>21</java.version>` (or 26 if your team accepts the risk). Add these dependencies:

```xml
<!-- Security + JWT -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<!-- Data -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<!-- Optional Redis blacklist -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
    <optional>true</optional>
</dependency>
<!-- API docs -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.8</version>
</dependency>
<!-- Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<!-- Testing -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

**Task 1.2 — Write `application.yml` (the master configuration contract)**

This is the single source of truth for every tunable behavior. Every property is documented inline so a new team member understands it at a glance:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/auth_db}
    username: ${DB_USER:postgres}
    password: ${DB_PASS:password}
  jpa:
    hibernate:
      ddl-auto: validate           # Liquibase owns the schema — never let Hibernate touch it
    open-in-view: false            # prevent lazy-load anti-pattern through the web layer
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml

app:
  jwt:
    secret: ${JWT_SECRET}          # min 32-char random string, externalize via env var
    access-token-ttl-seconds: 900  # 15 minutes
    refresh-token-ttl-seconds: 604800 # 7 days
  cookie:
    enabled: true
    name: accessToken
    http-only: true
    secure: true                   # set false for local HTTP dev in application-dev.yml
    same-site: Strict
    path: /
  blacklist:
    strategy: rdbms                # options: rdbms | redis
    cleanup-cron: "0 0 * * * *"   # prune expired entries every hour
  seeding:
    enabled: true
    developer-email: ${DEV_EMAIL:dev@example.com}
    developer-initial-password: ${DEV_PASSWORD:ChangeMe123!}
  cors:
    allowed-origins: ${CORS_ORIGINS:http://localhost:3000}
    allowed-methods: GET,POST,PUT,DELETE,OPTIONS
    max-age-seconds: 3600
  openapi:
    title: "Auth Spring API"
    description: "Authentication and RBAC layer — remove or extend for your project"
    version: "1.0.0"
```

**Task 1.3 — Create `application-dev.yml`**

```yaml
app:
  cookie:
    secure: false                 # allow HTTP cookies in local dev
  seeding:
    enabled: true
logging:
  level:
    io.epsilon.auth: DEBUG
    org.springframework.security: DEBUG
```

**Task 1.4 — Bind configuration to a typed `AppProperties` class**

Create `common/config/AppProperties.java` annotated `@ConfigurationProperties(prefix = "app")` `@Validated`. Nest static inner classes: `Jwt`, `Cookie`, `Blacklist`, `Seeding`, `Cors`, `OpenApi`. Annotate fields with `@NotBlank`, `@Min`, `@NotNull` where appropriate. Add `@EnableConfigurationProperties(AppProperties.class)` to `AuthSpringApplication.java`. This gives IDE autocomplete, fails fast on startup if required env vars are missing, and keeps every service class free of raw `@Value` annotations.

**Task 1.5 — Enable JPA auditing globally**

In `common/config/JacksonConfig.java` or a dedicated `JpaConfig.java`, add `@EnableJpaAuditing`. This activates `@CreatedDate` and `@LastModifiedDate` on all entities without any per-entity boilerplate.

**Task 1.6 — Enable scheduling**

In `common/config/SchedulingConfig.java`, add `@EnableScheduling`. The blacklist cleanup job defined later will pick this up.

---

## Phase 2 — Domain model & Liquibase schema

**Goal:** Every JPA entity is defined, the schema is version-controlled, and the data layer is independently testable before the business layer touches it.

---

**Task 2.1 — `UserEntity`**

```java
@Entity
@Table(name = "auth_users")
@EntityListeners(AuditingEntityListener.class)
public class UserEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private boolean enabled = true;
    private boolean accountNonLocked = true;
    private boolean accountNonExpired = true;
    private boolean credentialsNonExpired = true;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "auth_user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<RoleEntity> roles = new HashSet<>();

    // UserDetails methods delegate to fields above
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
            .flatMap(r -> r.getPermissions().stream())
            .map(p -> (GrantedAuthority) () -> p.getName())
            .collect(Collectors.toSet());
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
}
```

Implementing `UserDetails` directly on the entity eliminates the `UserDetailsAdapter` indirection layer that most projects add unnecessarily, keeping the class count lower.

**Task 2.2 — `RoleEntity`**

Table `auth_roles`. Fields: `id` (UUID), `name` (e.g. `ROLE_DEVELOPER`, unique, not null), `description`. `@ManyToMany(fetch = LAZY)` to `PermissionEntity` via `auth_role_permissions`. The `ROLE_` prefix is required by Spring Security's `hasRole()` expression — seed data must include it.

**Task 2.3 — `PermissionEntity`**

Table `auth_permissions`. Fields: `id` (UUID), `name` (e.g. `role:create`), `description`. Add a DB-level check constraint in the Liquibase changeset enforcing the `resource:action` format using a regex check: `name ~ '^[a-z_]+:[a-z_*]+$'` (PostgreSQL syntax). This is a data integrity guarantee, not just application-level validation.

**Task 2.4 — `RefreshTokenEntity`**

Table `auth_refresh_tokens`. Fields: `id` (UUID), `tokenHash` (VARCHAR 64, SHA-256 hex, indexed, unique), `userId` (FK → `auth_users.id`), `expiresAt` (Instant), `revokedAt` (Instant, nullable), `issuedAt`, `deviceFingerprint` (VARCHAR 255, nullable — reserved for future multi-device isolation).

**Task 2.5 — `TokenBlacklistEntry`**

Table `auth_token_blacklist`. Fields: `id` (UUID), `jti` (VARCHAR 36, indexed), `expiresAt` (Instant, indexed — used by cleanup query). No FK to users intentionally: a blacklisted token must remain blacklisted even if the user is deleted. Composite index on `(jti, expires_at)` for the fast lookup path: `WHERE jti = ? AND expires_at > NOW()`.

**Task 2.6 — `AuditLogEntity`**

Table `auth_audit_log`. Fields: `id` (UUID), `eventType` (VARCHAR enum: `LOGIN`, `LOGOUT`, `REGISTER`, `TOKEN_REFRESH`, `TOKEN_BLACKLISTED`, `ROLE_CREATED`, `ROLE_UPDATED`, `PERMISSION_ASSIGNED`), `actorId` (UUID, nullable — system events have no actor), `targetId` (UUID, nullable), `ipAddress` (VARCHAR 45 — supports IPv6), `metadata` (TEXT, JSON string), `occurredAt` (Instant, not null). This table is append-only — no update or delete operations ever touch it.

**Task 2.7 — Write all 8 Liquibase changesets**

Each changeset in `db/changelog/changes/` must:
- Have a unique `id` and an `author` field (use your GitHub username)
- Include a `<rollback>` block — required for responsible schema management
- Use explicit column types, not Hibernate-inferred ones
- Add all indexes declared in the entity comments above

Example for `0001_create_users.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 0001_create_users
      author: epsilon-team
      changes:
        - createTable:
            tableName: auth_users
            columns:
              - column:
                  name: id
                  type: uuid
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: email
                  type: varchar(255)
                  constraints:
                    nullable: false
                    unique: true
              - column:
                  name: password_hash
                  type: varchar(255)
                  constraints:
                    nullable: false
              - column:
                  name: enabled
                  type: boolean
                  defaultValueBoolean: true
              - column:
                  name: account_non_locked
                  type: boolean
                  defaultValueBoolean: true
              - column:
                  name: account_non_expired
                  type: boolean
                  defaultValueBoolean: true
              - column:
                  name: credentials_non_expired
                  type: boolean
                  defaultValueBoolean: true
              - column:
                  name: created_at
                  type: timestamptz
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: timestamptz
                  constraints:
                    nullable: false
      rollback:
        - dropTable:
            tableName: auth_users
```

**Task 2.8 — All 6 JPA repositories**

Each extends `JpaRepository<Entity, UUID>`. Add custom query methods where needed:

```java
// UserRepository
Optional<UserEntity> findByEmail(String email);
boolean existsByEmail(String email);

// RefreshTokenRepository
Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
int revokeAllByUserId(UUID userId);  // @Modifying @Query

// TokenBlacklistRepository
boolean existsByJtiAndExpiresAtAfter(String jti, Instant now);
int deleteByExpiresAtBefore(Instant cutoff);  // @Modifying for cleanup job

// RoleRepository
Optional<RoleEntity> findByName(String name);
boolean existsByName(String name);

// PermissionRepository
Optional<PermissionEntity> findByName(String name);
List<PermissionEntity> findAllByNameIn(Set<String> names);
```

**Task 2.9 — Repository slice tests (`@DataJpaTest`)**

Use `@Testcontainers` with `@Container static PostgreSQLContainer`. Test assertions:
- Unique email constraint raises `DataIntegrityViolationException`
- `RefreshTokenRepository.findByTokenHash` returns correct entity
- `TokenBlacklistRepository.existsByJtiAndExpiresAtAfter` correctly rejects expired entries
- `deleteByExpiresAtBefore` deletes exactly the right rows
- Liquibase changelog applies without error on the fresh test container (this is the migration health check — run it in CI on every PR)

---

## Phase 3 — Token infrastructure

**Goal:** A fully tested `JwtService` and a swappable `TokenBlacklist` strategy, both with zero Spring context dependency in their unit tests.

---

**Task 3.1 — Define the `TokenBlacklist` interface (OCP + DIP)**

```java
/**
 * Strategy interface for JWT blacklist storage.
 *
 * <p>The default implementation uses an RDBMS table. To switch to Redis,
 * set {@code app.blacklist.strategy=redis} and ensure
 * {@code spring-boot-starter-data-redis} is on the classpath.
 * No business logic requires modification.
 *
 * @implSpec Implementations must be thread-safe.
 */
public interface TokenBlacklist {
    /** Adds a token ID to the blacklist until its natural expiry time. */
    void add(String jti, Instant expiresAt);

    /** Returns true if the given JTI is currently blacklisted. */
    boolean isBlacklisted(String jti);
}
```

**Task 3.2 — `RdbmsTokenBlacklist` (default strategy)**

```java
@Component
@ConditionalOnProperty(
    name = "app.blacklist.strategy",
    havingValue = "rdbms",
    matchIfMissing = true        // RDBMS is the safe default — no config needed
)
public class RdbmsTokenBlacklist implements TokenBlacklist {

    private final TokenBlacklistRepository repo;

    @Override
    public void add(String jti, Instant expiresAt) {
        repo.save(TokenBlacklistEntry.of(jti, expiresAt));
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return repo.existsByJtiAndExpiresAtAfter(jti, Instant.now());
    }

    @Scheduled(cron = "${app.blacklist.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void pruneExpired() {
        int deleted = repo.deleteByExpiresAtBefore(Instant.now());
        log.debug("Pruned {} expired blacklist entries", deleted);
    }
}
```

**Task 3.3 — `RedisTokenBlacklist` (swap-in strategy)**

```java
@Component
@ConditionalOnProperty(name = "app.blacklist.strategy", havingValue = "redis")
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
public class RedisTokenBlacklist implements TokenBlacklist {

    private final RedisTemplate<String, String> redis;
    private static final String PREFIX = "bl:";

    @Override
    public void add(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (!ttl.isNegative()) {
            redis.opsForValue().set(PREFIX + jti, "1", ttl);
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }
}
```

Setting `app.blacklist.strategy=redis` + adding `spring-boot-starter-data-redis` to the consuming project's `pom.xml` is the complete migration — literally two changes, zero code.

**Task 3.4 — `JwtService`**

```java
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final AppProperties props;

    public JwtService(AppProperties props) {
        this.props = props;
        byte[] keyBytes = Decoders.BASE64.decode(props.getJwt().getSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /** Issues a signed access token embedding the user's resolved permission set. */
    public String issueAccessToken(UserDetails user, Set<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(user.getUsername())
            .claim("jti", UUID.randomUUID().toString())
            .claim("permissions", permissions)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(props.getJwt().getAccessTokenTtlSeconds())))
            .signWith(signingKey)
            .compact();
    }

    /** Returns all claims from a valid, non-expired token. Throws JwtException otherwise. */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public String extractJti(String token)    { return parseAndValidate(token).get("jti", String.class); }
    public Instant extractExpiry(String token) { return parseAndValidate(token).getExpiration().toInstant(); }
}
```

Unit test every branch: valid token, expired token (advance clock), tampered signature, missing `jti` claim, token issued with wrong secret.

**Task 3.5 — `RefreshTokenService`**

All refresh token operations are `@Transactional`. Token rotation atomically revokes the old entry and saves a new one within the same transaction — if anything fails mid-rotation, neither the old nor the new token is left in an inconsistent state.

```java
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final AppProperties props;

    public String issue(UUID userId) {
        String raw = UUID.randomUUID().toString();
        RefreshTokenEntity entity = RefreshTokenEntity.builder()
            .tokenHash(sha256(raw))
            .userId(userId)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(props.getJwt().getRefreshTokenTtlSeconds()))
            .build();
        repo.save(entity);
        return raw;  // return raw once, never stored
    }

    @Transactional
    public RefreshTokenEntity validateAndRotate(String rawToken) {
        String hash = sha256(rawToken);
        RefreshTokenEntity entity = repo.findByTokenHash(hash)
            .orElseThrow(() -> new AuthException("Refresh token not found"));

        if (entity.getRevokedAt() != null)
            throw new AuthException("Refresh token already revoked — possible replay attack");
        if (entity.getExpiresAt().isBefore(Instant.now()))
            throw new AuthException("Refresh token expired");

        entity.setRevokedAt(Instant.now());
        repo.save(entity);
        return entity;  // caller then calls issue() with userId
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repo.revokeAllByUserId(userId);
    }

    private static String sha256(String input) {
        // MessageDigest SHA-256, return hex string
    }
}
```

---

## Phase 4 — Security filter chain & authentication infrastructure

**Goal:** A production-hardened filter chain that extracts tokens from both `Authorization: Bearer` and `HttpOnly` cookies, and a method security layer that understands wildcard permissions.

---

**Task 4.1 — `JwtAuthenticationFilter`**

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklist blacklist;
    private final CustomUserDetailsService userDetailsService;
    private final AppProperties props;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        extractToken(request).ifPresent(token -> {
            try {
                Claims claims = jwtService.parseAndValidate(token);
                String jti = claims.get("jti", String.class);

                if (!blacklist.isBlacklisted(jti)) {
                    UserDetails user = userDetailsService.loadUserByUsername(claims.getSubject());
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException e) {
                // Invalid token — do not set authentication, let security chain handle 401
                log.debug("JWT validation failed: {}", e.getMessage());
            }
        });

        chain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        // 1. Check Authorization header
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7));
        }
        // 2. Fall back to HttpOnly cookie
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                .filter(c -> props.getCookie().getName().equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
        }
        return Optional.empty();
    }
}
```

**Task 4.2 — `CustomUserDetailsService`**

```java
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
```

**Task 4.3 — `StarterPermissionEvaluator` (wildcard RBAC)**

```java
@Component
public class StarterPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        if (auth == null || !auth.isAuthenticated() || !(permission instanceof String required))
            return false;

        Set<String> held = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        // Rule 1: wildcard grant — developer role
        if (held.contains("all:all")) return true;

        // Rule 2: resource-level wildcard — e.g. "role:*" grants role:create, role:read, etc.
        String resource = required.split(":")[0];
        if (held.contains(resource + ":*")) return true;

        // Rule 3: exact match
        return held.contains(required);
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId, String targetType, Object permission) {
        return hasPermission(auth, targetId, permission);
    }
}
```

**Task 4.4 — `SecurityConfig`**

```java
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AppProperties props;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/refresh",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                .accessDeniedHandler((req, res, e) ->
                    res.sendError(HttpServletResponse.SC_FORBIDDEN))
            )
            .build();
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            StarterPermissionEvaluator evaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(evaluator);
        return handler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(props.getCors().getAllowedOrigins().split(",")));
        configuration.setAllowedMethods(List.of(props.getCors().getAllowedMethods().split(",")));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(props.getCors().getMaxAgeSeconds());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

---

## Phase 5 — Authentication endpoints

**Goal:** `/login`, `/register`, `/logout`, `/refresh`, and `/me` — fully tested, with full cookie lifecycle management.

---

**Task 5.1 — DTOs as Java records**

```java
public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password
) {}

public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank @Size(max = 100) String fullName
) {}

public record TokenResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn
) {
    public static TokenResponse of(String access, String refresh, long ttl) {
        return new TokenResponse(access, refresh, "Bearer", ttl);
    }
}

public record UserProfileResponse(
    UUID id,
    String email,
    String fullName,
    Set<String> roles,
    Set<String> permissions    // fully evaluated, including wildcard expansion
) {}
```

**Task 5.2 — `AuthService`**

```java
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklist blacklist;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final ApplicationEventPublisher eventPublisher;
    private final AppProperties props;

    public TokenResponse login(LoginRequest req) {
        // Delegate credential verification to Spring Security
        Authentication auth = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        UserEntity user = (UserEntity) auth.getPrincipal();

        Set<String> permissions = resolvePermissions(user);
        String access = jwtService.issueAccessToken(user, permissions);
        String refresh = refreshTokenService.issue(user.getId());

        eventPublisher.publishEvent(new UserLoggedInEvent(user.getId(), Instant.now()));
        return TokenResponse.of(access, refresh, props.getJwt().getAccessTokenTtlSeconds());
    }

    public UserEntity register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email()))
            throw new EmailAlreadyExistsException(req.email());

        RoleEntity userRole = roleRepo.findByName("ROLE_USER")
            .orElseThrow(() -> new IllegalStateException("ROLE_USER not seeded — check DataSeederService"));

        UserEntity user = new UserEntity();
        user.setEmail(req.email());
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRoles(Set.of(userRole));
        UserEntity saved = userRepo.save(user);

        eventPublisher.publishEvent(new UserRegisteredEvent(saved.getId(), req.email(), Instant.now()));
        return saved;
    }

    public void logout(String accessToken) {
        try {
            String jti = jwtService.extractJti(accessToken);
            Instant expiry = jwtService.extractExpiry(accessToken);
            blacklist.add(jti, expiry);
            eventPublisher.publishEvent(new UserLoggedOutEvent(Instant.now()));
        } catch (JwtException e) {
            // Already invalid token — logout is idempotent, do nothing
        }
    }

    public TokenResponse refreshTokens(String rawRefreshToken) {
        RefreshTokenEntity old = refreshTokenService.validateAndRotate(rawRefreshToken);
        UserEntity user = userRepo.findById(old.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String access = jwtService.issueAccessToken(user, resolvePermissions(user));
        String newRefresh = refreshTokenService.issue(user.getId());
        return TokenResponse.of(access, newRefresh, props.getJwt().getAccessTokenTtlSeconds());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me(Authentication authentication) {
        UserEntity user = (UserEntity) authentication.getPrincipal();
        Set<String> roles = user.getRoles().stream()
            .map(RoleEntity::getName).collect(Collectors.toSet());
        return new UserProfileResponse(
            user.getId(), user.getEmail(), roles, resolvePermissions(user));
    }

    private Set<String> resolvePermissions(UserEntity user) {
        return user.getRoles().stream()
            .flatMap(r -> r.getPermissions().stream())
            .map(PermissionEntity::getName)
            .collect(Collectors.toSet());
    }
}
```

**Task 5.3 — `AuthController`**

```java
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AppProperties props;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @RequestBody @Valid LoginRequest req,
            HttpServletResponse response) {
        TokenResponse tokens = authService.login(req);
        attachCookie(response, tokens.accessToken());
        return ResponseEntity.ok(ApiResponse.success(tokens));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> register(@RequestBody @Valid RegisterRequest req) {
        authService.register(req);
        return ApiResponse.success(null);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        extractRawToken(request).ifPresent(authService::logout);
        clearCookie(response);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @RequestBody @Valid RefreshTokenRequest req,
            HttpServletResponse response) {
        TokenResponse tokens = authService.refreshTokens(req.refreshToken());
        attachCookie(response, tokens.accessToken());
        return ResponseEntity.ok(ApiResponse.success(tokens));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserProfileResponse> me(Authentication auth) {
        return ApiResponse.success(authService.me(auth));
    }

    private void attachCookie(HttpServletResponse response, String token) {
        if (!props.getCookie().isEnabled()) return;
        AppProperties.Cookie c = props.getCookie();
        ResponseCookie cookie = ResponseCookie.from(c.getName(), token)
            .httpOnly(c.isHttpOnly())
            .secure(c.isSecure())
            .sameSite(c.getSameSite())
            .path(c.getPath())
            .maxAge(props.getJwt().getAccessTokenTtlSeconds())
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response) {
        if (!props.getCookie().isEnabled()) return;
        ResponseCookie cookie = ResponseCookie.from(props.getCookie().getName(), "")
            .httpOnly(true).maxAge(0).path("/").build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private Optional<String> extractRawToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer "))
            return Optional.of(header.substring(7));
        return Optional.empty();
    }
}
```

**Task 5.4 — Domain events**

```java
// All events are Java records — immutable, zero boilerplate
public record UserRegisteredEvent(UUID userId, String email, Instant occurredAt) {}
public record UserLoggedInEvent(UUID userId, Instant occurredAt) {}
public record UserLoggedOutEvent(Instant occurredAt) {}
```

`AuditEventListener` in the `audit/` package picks these up with `@EventListener` and writes to `AuditLogEntity`. The service never knows how events are consumed — adding a Kafka bridge later requires only adding a second listener, not touching `AuthService`.

---

## Phase 6 — RBAC module

**Goal:** Seeded roles + permissions on boot, fully protected CRUD endpoints, and wildcard evaluation in place.

---

**Task 6.1 — `DataSeederService`**

Implements `ApplicationRunner`. Runs once per startup. Every operation is idempotent — safe to run on a pre-populated database:

```java
@Service
@RequiredArgsConstructor
public class DataSeederService implements ApplicationRunner {

    private final RoleRepository roleRepo;
    private final PermissionRepository permRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.getSeeding().isEnabled()) {
            log.info("Data seeding disabled via app.seeding.enabled=false");
            return;
        }
        seedPermissions();
        seedRoles();
        seedDeveloperUser();
    }

    private void seedPermissions() {
        List<String> names = List.of(
            "all:all",
            "role:create", "role:read", "role:update", "role:delete",
            "permission:create", "permission:read", "permission:update", "permission:delete"
        );
        names.forEach(name -> {
            if (!permRepo.existsByName(name)) {
                permRepo.save(PermissionEntity.of(name));
                log.info("Seeded permission: {}", name);
            }
        });
    }

    private void seedRoles() {
        seedRole("ROLE_USER", Set.of());
        seedRole("ROLE_SYSTEM_ADMIN", Set.of(
            "role:create", "role:read", "role:update", "role:delete",
            "permission:create", "permission:read", "permission:update", "permission:delete"));
        seedRole("ROLE_DEVELOPER", Set.of("all:all"));
    }

    private void seedRole(String name, Set<String> permissionNames) {
        if (roleRepo.existsByName(name)) return;
        Set<PermissionEntity> permissions = permissionNames.stream()
            .map(pn -> permRepo.findByName(pn)
                .orElseThrow(() -> new IllegalStateException("Permission not seeded: " + pn)))
            .collect(Collectors.toSet());
        roleRepo.save(RoleEntity.of(name, permissions));
        log.info("Seeded role: {} with {} permissions", name, permissionNames.size());
    }

    private void seedDeveloperUser() {
        AppProperties.Seeding seeding = props.getSeeding();
        if (seeding.getDeveloperEmail() == null) return;
        if (userRepo.existsByEmail(seeding.getDeveloperEmail())) return;

        RoleEntity devRole = roleRepo.findByName("ROLE_DEVELOPER").orElseThrow();
        UserEntity dev = new UserEntity();
        dev.setEmail(seeding.getDeveloperEmail());
        dev.setPasswordHash(encoder.encode(seeding.getDeveloperInitialPassword()));
        dev.setRoles(Set.of(devRole));
        userRepo.save(dev);
        log.warn("SECURITY: Developer user seeded — change the password immediately in production!");
    }
}
```

**Task 6.2 — `RoleService`**

```java
@Service
@Transactional
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepo;
    private final PermissionRepository permRepo;
    private final UserRepository userRepo;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<RoleResponse> listRoles(Pageable pageable) {
        return roleRepo.findAll(pageable).map(RoleResponse::from);
    }

    @Transactional(readOnly = true)
    public RoleResponse getById(UUID id) {
        return RoleResponse.from(findOrThrow(id));
    }

    public RoleResponse createRole(CreateRoleRequest req) {
        if (roleRepo.existsByName(req.name()))
            throw new IllegalArgumentException("Role already exists: " + req.name());
        RoleEntity entity = RoleEntity.of(req.name(), req.description(), Set.of());
        RoleEntity saved = roleRepo.save(entity);
        eventPublisher.publishEvent(new RoleCreatedEvent(saved.getId(), saved.getName()));
        return RoleResponse.from(saved);
    }

    public RoleResponse updateRole(UUID id, UpdateRoleRequest req) {
        RoleEntity entity = findOrThrow(id);
        entity.setDescription(req.description());
        return RoleResponse.from(roleRepo.save(entity));
    }

    public void deleteRole(UUID id) {
        if (userRepo.existsByRolesId(id))
            throw new RoleInUseException("Role is assigned to one or more users — reassign before deleting");
        roleRepo.deleteById(id);
    }

    public RoleResponse assignPermission(UUID roleId, UUID permissionId) {
        RoleEntity role = findOrThrow(roleId);
        PermissionEntity perm = permRepo.findById(permissionId)
            .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permissionId));
        role.getPermissions().add(perm);
        RoleEntity saved = roleRepo.save(role);
        eventPublisher.publishEvent(new PermissionAssignedEvent(roleId, permissionId));
        return RoleResponse.from(saved);
    }

    public RoleResponse removePermission(UUID roleId, UUID permissionId) {
        RoleEntity role = findOrThrow(roleId);
        role.getPermissions().removeIf(p -> p.getId().equals(permissionId));
        return RoleResponse.from(roleRepo.save(role));
    }

    private RoleEntity findOrThrow(UUID id) {
        return roleRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
    }
}
```

**Task 6.3 — `PermissionService`**

Validates the `resource:action` name format at the service layer using a compiled `Pattern`:

```java
private static final Pattern PERMISSION_NAME_PATTERN =
    Pattern.compile("^[a-z_]+:[a-z_*]+$");

public PermissionResponse createPermission(CreatePermissionRequest req) {
    if (!PERMISSION_NAME_PATTERN.matcher(req.name()).matches())
        throw new InvalidPermissionNameException(
            "Permission name must follow 'resource:action' format. Got: " + req.name());
    if (permRepo.existsByName(req.name()))
        throw new IllegalArgumentException("Permission already exists: " + req.name());
    return PermissionResponse.from(permRepo.save(PermissionEntity.of(req.name(), req.description())));
}
```

**Task 6.4 — `RoleController`**

```java
@RestController
@RequestMapping("/api/roles")
@Tag(name = "RBAC — Roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'role:read')")
    public ApiResponse<Page<RoleResponse>> list(Pageable pageable) {
        return ApiResponse.success(roleService.listRoles(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'role:read')")
    public ApiResponse<RoleResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(roleService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission(null, 'role:create')")
    public ApiResponse<RoleResponse> create(@RequestBody @Valid CreateRoleRequest req) {
        return ApiResponse.success(roleService.createRole(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'role:update')")
    public ApiResponse<RoleResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateRoleRequest req) {
        return ApiResponse.success(roleService.updateRole(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission(null, 'role:delete')")
    public void delete(@PathVariable UUID id) {
        roleService.deleteRole(id);
    }

    @PostMapping("/{roleId}/permissions/{permId}")
    @PreAuthorize("hasPermission(null, 'role:update')")
    public ApiResponse<RoleResponse> assignPermission(
            @PathVariable UUID roleId, @PathVariable UUID permId) {
        return ApiResponse.success(roleService.assignPermission(roleId, permId));
    }

    @DeleteMapping("/{roleId}/permissions/{permId}")
    @PreAuthorize("hasPermission(null, 'role:update')")
    public ApiResponse<RoleResponse> removePermission(
            @PathVariable UUID roleId, @PathVariable UUID permId) {
        return ApiResponse.success(roleService.removePermission(roleId, permId));
    }
}
```

**Task 6.5 — `PermissionController`** — identical structure to `RoleController` with `permission:*` guards.

**Task 6.6 — `UserRoleController`**

```java
@RestController
@RequestMapping("/api/users/{userId}/roles")
@Tag(name = "RBAC — User Role Assignment")
public class UserRoleController {

    @PostMapping("/{roleId}")
    @PreAuthorize("hasPermission(null, 'role:update')")
    public ApiResponse<Void> assignRole(@PathVariable UUID userId, @PathVariable UUID roleId) { ... }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasPermission(null, 'role:update')")
    public ResponseEntity<Void> removeRole(@PathVariable UUID userId, @PathVariable UUID roleId) { ... }
}
```

---

## Phase 7 — Shared infrastructure

**Goal:** Consistent response envelopes, global error mapping, request tracing, and self-documenting API.

---

**Task 7.1 — `ApiResponse<T>` record**

```java
public record ApiResponse<T>(
    boolean success,
    T data,
    ApiError error,
    String requestId,
    Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null,
            MDC.get("requestId"), Instant.now());
    }
    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(false, null,
            new ApiError(code, message), MDC.get("requestId"), Instant.now());
    }
}

public record ApiError(String code, String message) {}
```

**Task 7.2 — `GlobalExceptionHandler`**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                e -> Objects.requireNonNullElse(e.getDefaultMessage(), "Invalid")));
        return ApiResponse.failure("VALIDATION_ERROR", "Request validation failed");
        // include errors map in the response body — adjust return type accordingly
    }

    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuth(AuthException ex) {
        return ApiResponse.failure("AUTH_ERROR", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbidden(AccessDeniedException ex) {
        return ApiResponse.failure("FORBIDDEN", "You do not have the required permission");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(ResourceNotFoundException ex) {
        return ApiResponse.failure("NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(RoleInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleRoleInUse(RoleInUseException ex) {
        return ApiResponse.failure("ROLE_IN_USE", ex.getMessage());
    }

    @ExceptionHandler(ExpiredJwtException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleExpiredJwt(ExpiredJwtException ex) {
        return ApiResponse.failure("TOKEN_EXPIRED", "Access token has expired");
    }

    @ExceptionHandler(JwtException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleInvalidJwt(JwtException ex) {
        return ApiResponse.failure("TOKEN_INVALID", "Access token is invalid");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleGeneral(Exception ex) {
        log.error("Unhandled exception [requestId={}]", MDC.get("requestId"), ex);
        return ApiResponse.failure("INTERNAL_ERROR", "An unexpected error occurred");
    }
}
```

**Task 7.3 — `RequestIdFilter`**

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Request-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {
        String id = Optional.ofNullable(req.getHeader(HEADER))
            .filter(StringUtils::hasText)
            .orElseGet(() -> UUID.randomUUID().toString());
        MDC.put("requestId", id);
        res.setHeader(HEADER, id);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.clear();  // critical: prevents MDC leakage between threads in a pool
        }
    }
}
```

**Task 7.4 — `OpenApiConfig`**

```java
@Configuration
@RequiredArgsConstructor
public class OpenApiConfig {

    private final AppProperties props;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title(props.getOpenApi().getTitle())
                .description(props.getOpenApi().getDescription())
                .version(props.getOpenApi().getVersion()))
            .addSecurityItem(new SecurityRequirement().addList("Bearer Auth"))
            .components(new Components().addSecuritySchemes("Bearer Auth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Paste your JWT access token here. Also accepted via the 'accessToken' HttpOnly cookie.")));
    }
}
```

---

## Phase 8 — Full test suite

**Goal:** Every behavior is verified at the right layer. Unit tests have zero Spring context and run in milliseconds. Integration tests use real Postgres via Testcontainers.

---

**Task 8.1 — Unit tests (JUnit 5 + Mockito, no Spring context)**

| Test class | Key assertions |
|---|---|
| `JwtServiceTest` | valid token, expired token (mock `Clock`), tampered signature, `extractJti`, `extractExpiry` |
| `StarterPermissionEvaluatorTest` | `all:all` grants everything, `role:*` grants `role:create`, exact match, wrong permission denied, unauthenticated denied |
| `RdbmsTokenBlacklistTest` | `add` calls `repo.save`, `isBlacklisted` delegates to repo, `pruneExpired` calls `deleteByExpiresAtBefore` |
| `AuthServiceTest` | login success, login bad password (AuthException), register duplicate email (EmailAlreadyExistsException), logout blacklists correct JTI, refresh rotates token |
| `RoleServiceTest` | create, update, delete success, delete throws when users hold role, assignPermission idempotent |
| `PermissionServiceTest` | valid name passes regex, `role:*` passes, `ROL:create` fails, `role:` fails, duplicate name rejected |
| `DataSeederServiceTest` | run twice → inserts happen only once (verify `save` called exactly once per entity) |
| `RefreshTokenServiceTest` | issue, validate success, validate revoked token throws, validate expired token throws |

**Task 8.2 — Repository slice tests (`@DataJpaTest` + Testcontainers)**

```java
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = NONE)  // use real Postgres, not H2
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // Assertions: duplicate email throws DataIntegrityViolationException,
    // findByEmail returns correct entity, Liquibase migrations apply cleanly
}
```

**Task 8.3 — Web layer tests (`@WebMvcTest`)**

Use `@MockBean` for all services. Use `@WithMockUser` for authenticated requests. Cover for each controller: 200 happy path, 400 validation failure, 401 no token, 403 wrong permission, 404 not found, 409 conflict.

```java
@WebMvcTest(RoleController.class)
@Import(SecurityConfig.class)
class RoleControllerTest {

    @MockBean RoleService roleService;
    @MockBean JwtService jwtService;
    @MockBean TokenBlacklist blacklist;
    @MockBean CustomUserDetailsService userDetailsService;
    @MockBean StarterPermissionEvaluator permissionEvaluator;

    @Test
    @WithMockUser(authorities = "role:create")
    void createRole_validRequest_returns201() throws Exception { ... }

    @Test
    @WithMockUser(authorities = "role:read")  // has read but not create
    void createRole_insufficientPermission_returns403() throws Exception { ... }

    @Test
    void createRole_noAuthentication_returns401() throws Exception { ... }
}
```

**Task 8.4 — Integration tests (`@SpringBootTest` + Testcontainers)**

Full HTTP flows via `TestRestTemplate` or `MockMvc` with the full Spring context. Test these exact scenarios:

1. Register → Login → `/me` with returned token → assert profile contains correct role and permissions
2. Register → Login → Logout → reuse same access token → assert 401 with `TOKEN_BLACKLISTED`
3. Developer login → `POST /api/roles` → `POST /api/roles/{id}/permissions/{id}` → verify role has permission in DB
4. Set `app.jwt.access-token-ttl-seconds=1` in test → sleep 2s → call `/me` → assert 401 `TOKEN_EXPIRED`
5. Login → get refresh token → call `/api/auth/refresh` → old refresh token rejected (replay attack test)
6. Call `POST /api/auth/refresh` with old refresh token after rotation → assert 401

**Task 8.5 — `DataSeederService` idempotency integration test**

Boot the full context, then manually call `dataSeederService.run(null)` twice. Assert using repository counts that no duplicate roles or permissions exist.

---

## Phase 9 — Open source readiness

**Goal:** Any developer who finds this repo on GitHub can understand, run, and contribute to it within 30 minutes.

---

**Task 9.1 — `README.md`**

Sections: one-command quick start with Docker Compose, environment variable table, API endpoint reference, the three default roles and what they can do, how to add a new permission to a role, the blacklist strategy swap guide (2 steps), how to add your own feature module alongside `auth/` and `rbac/`, contributing guide.

**Task 9.2 — `docker-compose.yml`**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: auth_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"

  redis:                          # optional — only needed if strategy=redis
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

Running `docker compose up -d && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` gives a running auth server.

**Task 9.3 — `.github/workflows/ci.yml`**

```yaml
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: ./mvnw verify   # runs unit + integration tests, Liquibase migration check
      - run: ./mvnw jacoco:report
      - uses: codecov/codecov-action@v4  # enforce coverage gate
```

**Task 9.4 — `CHANGELOG.md` + branch strategy**

Use `main` for stable, `develop` for active work. Tag releases as `v1.0.0`. The CHANGELOG follows [Keep a Changelog](https://keepachangelog.com) format. Add a `CONTRIBUTING.md` explaining: fork → feature branch → tests required → PR against `develop`.

**Task 9.5 — `SECURITY.md`**

Document: how to report a vulnerability privately, the JWT secret requirements, why `app.cookie.secure=false` must never go to production, the bcrypt cost factor and when to increase it, the CORS `allowedOrigins` requirement. This is important for an open-source project — it tells security researchers you've thought about this.

---

## SOLID principle mapping — where each one lives

| Principle | Where enforced |
|---|---|
| SRP | Each class has one reason to change: `JwtService` only knows about tokens, `AuthService` only orchestrates auth flows, `AuditEventListener` only writes audit rows |
| OCP | `TokenBlacklist` interface — add Redis without touching a single existing class |
| LSP | `RdbmsTokenBlacklist` and `RedisTokenBlacklist` are fully substitutable — the filter chain never behaves differently |
| ISP | `TokenBlacklist` has exactly 2 methods; no implementation is forced to implement unneeded behavior |
| DIP | `SecurityConfig`, `AuthService`, `JwtAuthenticationFilter` all depend on the `TokenBlacklist` interface, never on a concrete class |