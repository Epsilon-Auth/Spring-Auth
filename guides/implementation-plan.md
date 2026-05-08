# auth-spring — Implementation Plan

*All phases must be completed in order. Each phase has defined inputs and outputs.*

---

## Phase 0 — Maven Setup

### Task 0.1 — pom.xml

Create the project with these exact coordinates and dependencies. The BOM from `spring-boot-starter-parent` manages all Spring versions automatically.

```xml
<groupId>io.epsilon</groupId>
<artifactId>auth-spring</artifactId>
<version>0.0.1-SNAPSHOT</version>

<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.0</version>
</parent>

<properties>
    <java.version>21</java.version>
    <jjwt.version>0.12.6</jjwt.version>
    <springdoc.version>2.8.8</springdoc.version>
    <testcontainers.version>1.20.4</testcontainers.version>
</properties>

<!-- Core -->
spring-boot-starter-web
spring-boot-starter-security
spring-boot-starter-data-jpa
spring-boot-starter-validation
spring-boot-configuration-processor  (optional)

<!-- JWT -->
jjwt-api:0.12.6
jjwt-impl:0.12.6  (runtime)
jjwt-jackson:0.12.6  (runtime)

<!-- Database -->
postgresql  (runtime)
liquibase-core

<!-- Optional Redis blacklist -->
spring-boot-starter-data-redis  (optional)

<!-- API Docs -->
springdoc-openapi-starter-webmvc-ui:2.8.8

<!-- Dev -->
lombok  (optional)

<!-- Test -->
spring-boot-starter-test
spring-security-test
testcontainers:junit-jupiter:1.20.4
testcontainers:postgresql:1.20.4
```

### Task 0.2 — AuthSpringApplication.java

```java
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class AuthSpringApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthSpringApplication.class, args);
    }
}
```

### Task 0.3 — application.yml

Single YAML source of truth. All values configurable via environment variables for 12-factor compliance.

```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/auth_db}
    username: ${DB_USER:postgres}
    password: ${DB_PASS:password}
  jpa:
    hibernate.ddl-auto: validate    # Liquibase owns the schema
    open-in-view: false
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml

app:
  jwt:
    secret: ${JWT_SECRET:base64-encoded-32-byte-minimum-secret}
    access-token-ttl-seconds: 900        # 15 minutes
    refresh-token-ttl-seconds: 604800    # 7 days
  cookie:
    enabled: true
    name: accessToken
    http-only: true
    secure: true
    same-site: Strict
    path: /
  blacklist:
    strategy: rdbms                 # swap to 'redis' with zero code changes
    cleanup-cron: '0 0 * * * *'    # hourly pruning for RDBMS strategy
  seeding:
    enabled: true
    developer-email: ${DEV_EMAIL:dev@example.com}
    developer-initial-password: ${DEV_PASSWORD:ChangeMe123!}
  cors:
    allowed-origins: ${CORS_ORIGINS:http://localhost:3000}
    allowed-methods: GET,POST,PUT,DELETE,OPTIONS
    max-age-seconds: 3600
```

---

## Phase 1 — Shared Layer

Build this first. Every subsequent phase depends on it. The shared layer must contain zero business logic and zero feature-specific code.

### Task 1.1 — AppProperties (Typed Configuration)

Binds the `app.*` YAML namespace. Validates required values at startup. If `JWT_SECRET` is missing or too short, the app refuses to start. All services inject `AppProperties` — never raw `@Value`.

```java
@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {
    @Valid @NotNull private Jwt jwt = new Jwt();
    @Valid @NotNull private Cookie cookie = new Cookie();
    @Valid @NotNull private Blacklist blacklist = new Blacklist();
    @Valid @NotNull private Seeding seeding = new Seeding();
    @Valid @NotNull private Cors cors = new Cors();
    // getters + setters for each

    public static class Jwt {
        @NotBlank private String secret;
        @Min(60)  private long accessTokenTtlSeconds = 900;
        @Min(3600) private long refreshTokenTtlSeconds = 604800;
    }
    public static class Cookie {
        private boolean enabled = true;
        @NotBlank private String name = "accessToken";
        private boolean httpOnly = true;  private boolean secure = true;
        @NotBlank private String sameSite = "Strict";
        @NotBlank private String path = "/";
    }
    public static class Blacklist {
        @NotBlank private String strategy = "rdbms";
        @NotBlank private String cleanupCron = "0 0 * * * *";
    }
    public static class Seeding {
        private boolean enabled = true;
        private String developerEmail;
        private String developerInitialPassword;
    }
    public static class Cors {
        @NotBlank private String allowedOrigins = "http://localhost:3000";
        @NotBlank private String allowedMethods = "GET,POST,PUT,DELETE,OPTIONS";
        @Min(0) private long maxAgeSeconds = 3600;
    }
}
```

### Task 1.2 — Support Configs

```java
@Configuration @EnableJpaAuditing
public class JpaConfig {}       // activates @CreatedDate / @LastModifiedDate

@Configuration @EnableScheduling
public class SchedulingConfig {}  // activates @Scheduled on TokenBlacklistPruner

@Configuration
public class JacksonConfig {
    @Bean public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Instants serialize as ISO-8601, not epoch millis
    }
}
```

### Task 1.3 — Exception Hierarchy

```
DomainException extends RuntimeException       // base
  └─ ResourceNotFoundException                 // → 404
  └─ EmailAlreadyExistsException               // → 409
  └─ AuthException                             // → 401
  └─ RoleInUseException                        // → 409
  └─ InvalidPermissionNameException            // → 400
```

### Task 1.4 — ApiResponse\<T\> and GlobalExceptionHandler

```java
public record ApiResponse<T>(
    boolean success, T data, ApiError error,
    String requestId, Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, MDC.get("requestId"), Instant.now());
    }
    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message),
            MDC.get("requestId"), Instant.now());
    }
}

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class) → 400 VALIDATION_ERROR
    @ExceptionHandler(AuthException.class)                   → 401 AUTH_ERROR
    @ExceptionHandler(AccessDeniedException.class)           → 403 FORBIDDEN
    @ExceptionHandler(ResourceNotFoundException.class)       → 404 NOT_FOUND
    @ExceptionHandler(EmailAlreadyExistsException.class)     → 409 EMAIL_EXISTS
    @ExceptionHandler(RoleInUseException.class)              → 409 ROLE_IN_USE
    @ExceptionHandler(InvalidPermissionNameException.class)  → 400 INVALID_PERMISSION_NAME
    @ExceptionHandler(ExpiredJwtException.class)             → 401 TOKEN_EXPIRED
    @ExceptionHandler(JwtException.class)                    → 401 TOKEN_INVALID
    @ExceptionHandler(Exception.class)                       → 500 INTERNAL_ERROR
}
```

### Task 1.5 — RequestIdFilter

Must run `HIGHEST_PRECEDENCE` so every log line — including those in security filters — carries the `requestId`. Clears MDC in `finally` to prevent thread pool leakage.

```java
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(req, res, chain) {
        String id = ofNullable(req.getHeader("X-Request-ID"))
            .filter(StringUtils::hasText)
            .orElseGet(() -> UUID.randomUUID().toString());
        MDC.put("requestId", id);
        res.setHeader("X-Request-ID", id);
        try { chain.doFilter(req, res); }
        finally { MDC.clear(); }  // CRITICAL: prevents thread pool MDC leakage
    }
}
```

---

## Phase 2 — Database Schema (Liquibase)

### Task 2.1 — db.changelog-master.yaml

```yaml
databaseChangeLog:
  - include: { file: db/changelog/changes/0001_create_users.yaml }
  - include: { file: db/changelog/changes/0002_create_roles.yaml }
  - include: { file: db/changelog/changes/0003_create_permissions.yaml }
  - include: { file: db/changelog/changes/0004_create_user_roles.yaml }
  - include: { file: db/changelog/changes/0005_create_role_permissions.yaml }
  - include: { file: db/changelog/changes/0006_create_refresh_tokens.yaml }
  - include: { file: db/changelog/changes/0007_create_token_blacklist.yaml }
  - include: { file: db/changelog/changes/0008_create_audit_log.yaml }
```

### Task 2.2 — Write all 8 changeset files

Each changeset must include a `rollback` block. Follow the schema defined in the architecture plan (Section 6).

---

## Phase 3 — JPA Entities

All entities live in their module's `infrastructure/persistence/` package. No entity implements any Spring Security interface.

### Task 3.1 — UserEntity (auth/infrastructure/persistence/)

> **Critical:** `UserEntity` does NOT implement `UserDetails`. It is a pure JPA model. `UserPrincipal` (Phase 6) is the Spring Security adapter.

```java
@Entity @Table(name = "auth_users")
@EntityListeners(AuditingEntityListener.class)
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(unique = true, nullable = false) private String email;
    @Column(nullable = false)               private String passwordHash;
    private boolean enabled = true;
    private boolean accountNonLocked = true;
    private boolean accountNonExpired = true;
    private boolean credentialsNonExpired = true;
    @CreatedDate  @Column(updatable = false) private Instant createdAt;
    @LastModifiedDate                        private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "auth_user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleEntity> roles = new HashSet<>();
    // Lombok @Getter @Setter @NoArgsConstructor
}
```

### Task 3.2 — RoleEntity (rbac/infrastructure/persistence/)

```java
@Entity @Table(name = "auth_roles")
public class RoleEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(unique = true, nullable = false, length = 100) private String name;
    @Column(length = 500) private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "auth_role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<PermissionEntity> permissions = new HashSet<>();

    public static RoleEntity of(String name, String desc, Set<PermissionEntity> perms) { ... }
}
```

### Task 3.3 — PermissionEntity (rbac/infrastructure/persistence/)

```java
@Entity @Table(name = "auth_permissions")
public class PermissionEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(unique = true, nullable = false, length = 100) private String name;
    @Column(length = 500) private String description;
    public static PermissionEntity of(String name) { ... }
    public static PermissionEntity of(String name, String desc) { ... }
}
```

### Task 3.4 — RefreshTokenEntity (auth/infrastructure/persistence/)

```java
@Entity @Table(name = "auth_refresh_tokens")
@Builder @NoArgsConstructor @AllArgsConstructor
public class RefreshTokenEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private Instant issuedAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant revokedAt;              // null = active
    @Column(length = 255) private String deviceFingerprint;
}
```

### Task 3.5 — TokenBlacklistEntry + AuditLogEntity

```java
// auth/infrastructure/persistence/TokenBlacklistEntry.java
@Entity @Table(name = "auth_token_blacklist")
public class TokenBlacklistEntry {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 36) private String jti;
    @Column(nullable = false)              private Instant expiresAt;
    public static TokenBlacklistEntry of(String jti, Instant expiresAt) { ... }
}

// audit/infrastructure/AuditLogEntity.java
@Entity @Table(name = "auth_audit_log")
public class AuditLogEntity {
    public enum EventType { LOGIN, LOGOUT, REGISTER, TOKEN_REFRESH,
        ROLE_CREATED, ROLE_UPDATED, ROLE_DELETED, PERMISSION_ASSIGNED }
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=50) private EventType eventType;
    private UUID actorId;
    private UUID targetId;
    @Column(length = 45) private String ipAddress;
    @Column(columnDefinition = "text") private String metadata;
    @Column(nullable = false) private Instant occurredAt;
    public static AuditLogEntity of(EventType type, UUID actorId, ...) { ... }
}
```

---

## Phase 4 — JPA Repositories

Repositories are named with the `Jpa` prefix to distinguish from hypothetical Redis or in-memory implementations. Placed in each module's `infrastructure/persistence/` package.

### Task 4.1 — UserJpaRepository

```java
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    // Used by RoleService to guard against deleting roles with active users
    @Query("SELECT COUNT(u) > 0 FROM UserEntity u JOIN u.roles r WHERE r.id = :roleId")
    boolean existsByRolesId(@Param("roleId") UUID roleId);
}
```

### Task 4.2 — RoleJpaRepository / PermissionJpaRepository

```java
public interface RoleJpaRepository extends JpaRepository<RoleEntity, UUID> {
    Optional<RoleEntity> findByName(String name);
    boolean existsByName(String name);
}

public interface PermissionJpaRepository extends JpaRepository<PermissionEntity, UUID> {
    Optional<PermissionEntity> findByName(String name);
    boolean existsByName(String name);
}
```

### Task 4.3 — RefreshTokenJpaRepository

```java
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
    @Modifying
    @Query("""
        UPDATE RefreshTokenEntity t SET t.revokedAt = CURRENT_TIMESTAMP
        WHERE t.userId = :userId AND t.revokedAt IS NULL
    """)
    int revokeAllByUserId(@Param("userId") UUID userId);
}
```

### Task 4.4 — TokenBlacklistJpaRepository

```java
public interface TokenBlacklistJpaRepository extends JpaRepository<TokenBlacklistEntry, UUID> {
    // Uses composite index idx_blacklist_jti_expires — fast on every request
    boolean existsByJtiAndExpiresAtAfter(String jti, Instant now);

    @Modifying
    @Query("DELETE FROM TokenBlacklistEntry t WHERE t.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}
```

---

## Phase 5 — Token Blacklist Infrastructure

### Task 5.1 — TokenBlacklist Interface

```java
/**
 * Strategy interface for JWT blacklist storage.
 * Default: RDBMS (no config needed). Swap to Redis: set app.blacklist.strategy=redis
 * All implementations must be thread-safe.
 */
public interface TokenBlacklist {
    void add(String jti, Instant expiresAt);
    boolean isBlacklisted(String jti);
}
```

### Task 5.2 — RdbmsTokenBlacklist

```java
@Component
@ConditionalOnProperty(name = "app.blacklist.strategy", havingValue = "rdbms", matchIfMissing = true)
@RequiredArgsConstructor
public class RdbmsTokenBlacklist implements TokenBlacklist {
    private final TokenBlacklistJpaRepository repo;

    @Override public void add(String jti, Instant expiresAt) {
        repo.save(TokenBlacklistEntry.of(jti, expiresAt));
    }
    @Override public boolean isBlacklisted(String jti) {
        return repo.existsByJtiAndExpiresAtAfter(jti, Instant.now());
    }
    // NO @Scheduled here — that responsibility belongs to TokenBlacklistPruner
}
```

### Task 5.3 — TokenBlacklistPruner (SRP split)

> Separated from `RdbmsTokenBlacklist`. One class = one reason to change. The pruner only activates when the RDBMS strategy is active.

```java
@Component
@ConditionalOnProperty(name = "app.blacklist.strategy", havingValue = "rdbms", matchIfMissing = true)
@RequiredArgsConstructor @Slf4j
public class TokenBlacklistPruner {
    private final TokenBlacklistJpaRepository repo;

    @Scheduled(cron = "${app.blacklist.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void pruneExpired() {
        int deleted = repo.deleteByExpiresAtBefore(Instant.now());
        if (deleted > 0) log.info("Pruned {} expired blacklist entries", deleted);
    }
}
```

### Task 5.4 — RedisTokenBlacklist (Optional)

```java
@Component
@ConditionalOnProperty(name = "app.blacklist.strategy", havingValue = "redis")
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
@RequiredArgsConstructor
public class RedisTokenBlacklist implements TokenBlacklist {
    private final RedisTemplate<String, String> redis;
    private static final String PREFIX = "bl:";

    @Override public void add(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (!ttl.isNegative()) redis.opsForValue().set(PREFIX + jti, "1", ttl);
    }
    @Override public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }
    // NO pruner needed — Redis handles TTL natively
}

// To activate: set app.blacklist.strategy=redis in your profile
// Add spring-boot-starter-data-redis to pom.xml
// Zero other changes required.
```

---

## Phase 6 — Security Infrastructure

### Task 6.1 — JwtService (auth/infrastructure/security/)

```java
@Component
public class JwtService {
    private final SecretKey signingKey;
    private final AppProperties props;

    public JwtService(AppProperties props) {
        byte[] keyBytes = Decoders.BASE64.decode(props.getJwt().getSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // subject = user UUID (not email — UUID is stable and non-PII in logs)
    // permissions embedded as claims → filter builds UserPrincipal from claims, no DB hit
    public String issueAccessToken(UUID userId, String email, Set<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("jti",   UUID.randomUUID().toString())
            .claim("permissions", new ArrayList<>(permissions))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(props.getJwt().getAccessTokenTtlSeconds())))
            .signWith(signingKey)
            .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser().verifyWith(signingKey).build()
            .parseSignedClaims(token).getPayload();
    }

    public String  extractJti(String token)    { return parseAndValidate(token).get("jti", String.class); }
    public Instant extractExpiry(String token) { return parseAndValidate(token).getExpiration().toInstant(); }
}
```

### Task 6.2 — UserPrincipal (auth/infrastructure/security/)

> **Key architectural fix.** `UserPrincipal` implements `UserDetails`. `UserEntity` does NOT. `fromClaims()` builds the principal from JWT claims — zero database access on every authenticated request.

```java
public record UserPrincipal(
    UUID userId, String email, String passwordHash,
    boolean enabled, boolean accountNonLocked,
    boolean accountNonExpired, boolean credentialsNonExpired,
    Set<GrantedAuthority> authorities
) implements UserDetails {

    /** Build from JWT claims — ZERO database access. Used by JwtAuthenticationFilter. */
    @SuppressWarnings("unchecked")
    public static UserPrincipal fromClaims(Claims claims) {
        List<String> perms = claims.get("permissions", List.class);
        Set<GrantedAuthority> authorities = (perms == null ? List.<String>of() : perms)
            .stream().map(p -> (GrantedAuthority) () -> p)
            .collect(Collectors.toUnmodifiableSet());
        return new UserPrincipal(
            UUID.fromString(claims.getSubject()),
            claims.get("email", String.class),
            "",  // password not needed after token validation
            true, true, true, true, authorities);
    }

    /** Build from UserEntity — used ONCE per login by UserDetailsServiceImpl. */
    public static UserPrincipal fromEntity(UserEntity entity) {
        Set<GrantedAuthority> authorities = entity.getRoles().stream()
            .flatMap(role -> role.getPermissions().stream())
            .map(perm -> (GrantedAuthority) perm::getName)
            .collect(Collectors.toUnmodifiableSet());
        return new UserPrincipal(
            entity.getId(), entity.getEmail(), entity.getPasswordHash(),
            entity.isEnabled(), entity.isAccountNonLocked(),
            entity.isAccountNonExpired(), entity.isCredentialsNonExpired(),
            authorities);
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword()  { return passwordHash; }
    @Override public String getUsername()  { return email; }
}
```

### Task 6.3 — UserDetailsServiceImpl

Called exactly ONCE per login by Spring Security's `DaoAuthenticationProvider`. Never called on subsequent requests — that path uses `UserPrincipal.fromClaims()` instead.

```java
@Service @RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserJpaRepository userRepo;

    @Override @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepo.findByEmail(email)
            .map(UserPrincipal::fromEntity)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
```

### Task 6.4 — JwtAuthenticationFilter

> **Performance critical.** Builds `UserPrincipal` from JWT claims — no `SELECT` on `auth_users`. A DB query only happens on login. All subsequent requests are pure in-memory token validation.

```java
@Component @RequiredArgsConstructor @Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final TokenBlacklist blacklist;
    private final AppProperties props;

    @Override
    protected void doFilterInternal(req, res, chain) {
        extractToken(req).ifPresent(token -> {
            try {
                Claims claims = jwtService.parseAndValidate(token);
                String jti = claims.get("jti", String.class);
                if (!blacklist.isBlacklisted(jti)) {
                    // Build from claims — NO database query
                    UserPrincipal principal = UserPrincipal.fromClaims(claims);
                    var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException ex) {
                log.debug("JWT validation failed: {}", ex.getMessage());
            }
        });
        chain.doFilter(req, res);
    }

    private Optional<String> extractToken(HttpServletRequest req) {
        // Priority 1: Authorization: Bearer <token>
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer "))
            return Optional.of(header.substring(7));
        // Priority 2: HttpOnly cookie
        if (props.getCookie().isEnabled() && req.getCookies() != null)
            return Arrays.stream(req.getCookies())
                .filter(c -> props.getCookie().getName().equals(c.getName()))
                .map(Cookie::getValue).findFirst();
        return Optional.empty();
    }
}
```

### Task 6.5 — StarterPermissionEvaluator

Implements wildcard RBAC. Three evaluation rules, in priority order.

```java
@Component
public class StarterPermissionEvaluator implements PermissionEvaluator {
    @Override
    public boolean hasPermission(Authentication auth, Object target, Object permission) {
        if (auth == null || !auth.isAuthenticated()) return false;
        if (!(permission instanceof String required)) return false;
        Set<String> held = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        // Rule 1: all:all wildcard (ROLE_DEVELOPER)
        if (held.contains("all:all")) return true;
        // Rule 2: resource wildcard — "role:*" grants role:create, role:read, etc.
        String resource = required.split(":")[0];
        if (held.contains(resource + ":*")) return true;
        // Rule 3: exact match
        return held.contains(required);
    }
}
```

### Task 6.6 — SecurityConfig

```java
@Configuration @EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AppProperties props;
    private final StarterPermissionEvaluator permissionEvaluator;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/login", "/api/auth/register", "/api/auth/refresh",
                    "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg)
        throws Exception { return cfg.getAuthenticationManager(); }
    @Bean public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        var handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
    // Also defines: CorsConfigurationSource from AppProperties.cors
}
```

---

## Phase 7 — Auth Application Layer

### Task 7.1 — RefreshTokenService

All operations transactional. Tokens stored as SHA-256 hash — raw token returned once to client, never persisted in plain text. Rotation is atomic.

```java
@Service @RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenJpaRepository repo;
    private final AppProperties props;

    @Transactional
    public String issue(UUID userId) {
        String raw = UUID.randomUUID().toString();
        repo.save(RefreshTokenEntity.builder()
            .tokenHash(sha256(raw)).userId(userId)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(props.getJwt().getRefreshTokenTtlSeconds()))
            .build());
        return raw;  // returned ONCE to client, never stored plain text
    }

    @Transactional
    public RefreshTokenEntity validateAndRotate(String rawToken) {
        RefreshTokenEntity entity = repo.findByTokenHash(sha256(rawToken))
            .orElseThrow(() -> new AuthException("Refresh token not found"));
        if (entity.getRevokedAt() != null)
            throw new AuthException("Token already revoked — possible replay attack");
        if (entity.getExpiresAt().isBefore(Instant.now()))
            throw new AuthException("Refresh token expired");
        entity.setRevokedAt(Instant.now());
        repo.save(entity);
        return entity;
    }

    @Transactional
    public void revokeAllForUser(UUID userId) { repo.revokeAllByUserId(userId); }

    private static String sha256(String input) { /* MessageDigest SHA-256 */ }
}
```

### Task 7.2 — AuthService

Updated to work with `UserPrincipal`. The login method casts `auth.getPrincipal()` to `UserPrincipal` (not `UserEntity`). No entity is returned from any use case method.

```java
@Service @Transactional @RequiredArgsConstructor
public class AuthService {
    // Dependencies: UserJpaRepository, RoleJpaRepository, JwtService,
    // RefreshTokenService, TokenBlacklist, PasswordEncoder,
    // AuthenticationManager, ApplicationEventPublisher, AppProperties

    public TokenResponse login(LoginRequest req) {
        Authentication auth = authManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal(); // NOT UserEntity
        Set<String> permissions = extractPermissions(principal);
        String access  = jwtService.issueAccessToken(principal.userId(), principal.email(), permissions);
        String refresh = refreshTokenService.issue(principal.userId());
        eventPublisher.publishEvent(new UserLoggedInEvent(principal.userId()));
        return TokenResponse.of(access, refresh, props.getJwt().getAccessTokenTtlSeconds());
    }

    public void register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email())) throw new EmailAlreadyExistsException(req.email());
        RoleEntity userRole = roleRepo.findByName("ROLE_USER")
            .orElseThrow(() -> new IllegalStateException("ROLE_USER not seeded"));
        UserEntity user = new UserEntity();
        user.setEmail(req.email());
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRoles(Set.of(userRole));
        UserEntity saved = userRepo.save(user);
        eventPublisher.publishEvent(new UserRegisteredEvent(saved.getId(), req.email()));
    }

    public void logout(String rawAccessToken) {
        try {
            blacklist.add(jwtService.extractJti(rawAccessToken),
                          jwtService.extractExpiry(rawAccessToken));
            eventPublisher.publishEvent(new UserLoggedOutEvent());
        } catch (JwtException e) { /* idempotent — already invalid */ }
    }

    public TokenResponse refreshTokens(String rawRefreshToken) {
        RefreshTokenEntity old = refreshTokenService.validateAndRotate(rawRefreshToken);
        UserEntity user = userRepo.findById(old.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        // Re-resolve permissions from DB on refresh (picks up permission changes)
        Set<String> permissions = resolvePermissionsFromEntity(user);
        return TokenResponse.of(
            jwtService.issueAccessToken(user.getId(), user.getEmail(), permissions),
            refreshTokenService.issue(user.getId()),
            props.getJwt().getAccessTokenTtlSeconds());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UserEntity user = userRepo.findById(principal.userId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return new UserProfileResponse(user.getId(), user.getEmail(),
            user.getRoles().stream().map(RoleEntity::getName).collect(Collectors.toSet()),
            extractPermissions(principal));
    }
}
```

---

## Phase 8 — Auth Web Layer

### Task 8.1 — Request / Response DTOs

```java
// Requests — validated Java records
public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String password) {}

public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 72) String password) {}

public record RefreshTokenRequest(@NotBlank String refreshToken) {}

// Responses — immutable records
public record TokenResponse(
    String accessToken, String refreshToken, String tokenType, long expiresIn) {
    public static TokenResponse of(String access, String refresh, long ttl) {
        return new TokenResponse(access, refresh, "Bearer", ttl);
    }
}

public record UserProfileResponse(
    UUID id, String email, Set<String> roles, Set<String> permissions) {}
```

### Task 8.2 — AuthController

```java
@RestController @RequestMapping("/api/auth")
@Tag(name = "Authentication") @RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final AppProperties props;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @RequestBody @Valid LoginRequest req, HttpServletResponse response) {
        TokenResponse tokens = authService.login(req);
        attachCookie(response, tokens.accessToken());
        return ResponseEntity.ok(ApiResponse.success(tokens));
    }

    @PostMapping("/register")  @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> register(@RequestBody @Valid RegisterRequest req) {
        authService.register(req);
        return ApiResponse.success(null);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest req, HttpServletResponse res) {
        extractBearerToken(req).ifPresent(authService::logout);
        clearCookie(res);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @RequestBody @Valid RefreshTokenRequest req, HttpServletResponse res) {
        TokenResponse tokens = authService.refreshTokens(req.refreshToken());
        attachCookie(res, tokens.accessToken());
        return ResponseEntity.ok(ApiResponse.success(tokens));
    }

    @GetMapping("/me") @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserProfileResponse> me(Authentication auth) {
        return ApiResponse.success(authService.me(auth));
    }
    // private helpers: attachCookie, clearCookie, extractBearerToken
}
```

---

## Phase 9 — RBAC Application + Web Layers

### Task 9.1 — RbacDataSeeder (rbac/infrastructure/seeding/)

> Changed from `@Service` to `@Component`. It is not a business service — it is a startup infrastructure task. Moved to `infrastructure/seeding/` to make intent obvious.

```java
@Component  // NOT @Service
@RequiredArgsConstructor @Slf4j
public class RbacDataSeeder implements ApplicationRunner {
    private final RoleJpaRepository roleRepo;
    private final PermissionJpaRepository permRepo;
    private final UserJpaRepository userRepo;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (!props.getSeeding().isEnabled()) return;
        seedPermissions();  // must run before seedRoles()
        seedRoles();
        seedDeveloperUser();
    }

    private void seedPermissions() {
        List.of("all:all",
                "role:create", "role:read", "role:update", "role:delete",
                "permission:create", "permission:read", "permission:update", "permission:delete")
            .forEach(name -> {
                if (!permRepo.existsByName(name))
                    permRepo.save(PermissionEntity.of(name));
            });
    }

    private void seedRoles() {
        seedRole("ROLE_USER",         Set.of());
        seedRole("ROLE_SYSTEM_ADMIN",  Set.of("role:create", "role:read", /* ... */));
        seedRole("ROLE_DEVELOPER",     Set.of("all:all"));
    }
    // private seedDeveloperUser(), seedRole() helpers...
}
```

### Task 9.2 — RoleService

```java
@Service @Transactional @RequiredArgsConstructor
public class RoleService {
    private final RoleJpaRepository roleRepo;
    private final PermissionJpaRepository permRepo;
    private final UserJpaRepository userRepo;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly=true) public Page<RoleResponse> list(Pageable p)  { ... }
    @Transactional(readOnly=true) public RoleResponse getById(UUID id)         { ... }

    public RoleResponse create(CreateRoleRequest req) {
        if (roleRepo.existsByName(req.name()))
            throw new IllegalArgumentException("Role already exists: " + req.name());
        RoleEntity saved = roleRepo.save(RoleEntity.of(req.name(), req.description(), Set.of()));
        eventPublisher.publishEvent(new RoleCreatedEvent(saved.getId(), saved.getName()));
        return RoleResponse.from(saved);
    }

    public void delete(UUID id) {
        if (userRepo.existsByRolesId(id))
            throw new RoleInUseException("Role assigned to users — reassign before deleting");
        roleRepo.deleteById(id);
    }

    public RoleResponse assignPermission(UUID roleId, UUID permId) {
        RoleEntity role = findOrThrow(roleId);
        PermissionEntity perm = permRepo.findById(permId)
            .orElseThrow(() -> new ResourceNotFoundException("Permission not found"));
        role.getPermissions().add(perm);
        eventPublisher.publishEvent(new PermissionAssignedEvent(roleId, permId));
        return RoleResponse.from(roleRepo.save(role));
    }
}
```

### Task 9.3 — PermissionService

```java
@Service @Transactional @RequiredArgsConstructor
public class PermissionService {
    // DB-level constraint is backup. Service validates first.
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z_]+:[a-z_*]+$");
    private final PermissionJpaRepository permRepo;

    public PermissionResponse create(CreatePermissionRequest req) {
        if (!NAME_PATTERN.matcher(req.name()).matches())
            throw new InvalidPermissionNameException(
                "Name must match 'resource:action' format. Got: " + req.name());
        if (permRepo.existsByName(req.name()))
            throw new IllegalArgumentException("Permission already exists");
        return PermissionResponse.from(
            permRepo.save(PermissionEntity.of(req.name(), req.description())));
    }
}
```

### Task 9.4 — RoleController + PermissionController

```java
@RestController @RequestMapping("/api/roles")
@Tag(name = "RBAC — Roles") @RequiredArgsConstructor
public class RoleController {
    @GetMapping              @PreAuthorize("hasPermission(null, 'role:read')")   // list()
    @GetMapping("/{id}")     @PreAuthorize("hasPermission(null, 'role:read')")   // getById()
    @PostMapping             @PreAuthorize("hasPermission(null, 'role:create')") // create()
    @PutMapping("/{id}")     @PreAuthorize("hasPermission(null, 'role:update')") // update()
    @DeleteMapping("/{id}")  @PreAuthorize("hasPermission(null, 'role:delete')") // delete()
    @PostMapping("/{rId}/permissions/{pId}")   @PreAuthorize("...role:update")   // assignPermission()
    @DeleteMapping("/{rId}/permissions/{pId}") @PreAuthorize("...role:update")   // removePermission()
}

@RestController @RequestMapping("/api/permissions")
public class PermissionController {
    @GetMapping       @PreAuthorize("hasPermission(null, 'permission:read')")   // list()
    @GetMapping("/{id}") @PreAuthorize("hasPermission(null, 'permission:read')")   // getById()
    @PostMapping      @PreAuthorize("hasPermission(null, 'permission:create')") // create()
    @DeleteMapping    @PreAuthorize("hasPermission(null, 'permission:delete')") // delete()
}
```

---

## Phase 10 — Audit Module

### Task 10.1 — Domain Events (audit/event/)

All domain events live in `audit/event/`. Feature services import from here to publish. The single-source location eliminates cross-module event imports.

```java
// All events are Java records — immutable, zero boilerplate
public record UserRegisteredEvent(UUID userId, String email) {}
public record UserLoggedInEvent(UUID userId) {}
public record UserLoggedOutEvent() {}
public record RoleCreatedEvent(UUID roleId, String roleName) {}
public record RoleUpdatedEvent(UUID roleId, String roleName) {}
public record PermissionAssignedEvent(UUID roleId, UUID permissionId) {}
```

### Task 10.2 — AuditableEvent Marker Interface

```java
// audit/application/AuditableEvent.java
/**
 * Marker interface. All domain events that should be persisted to
 * the audit log implement this. AuditEventListener only processes
 * events that implement this interface.
 */
public interface AuditableEvent {}
```

### Task 10.3 — AuditEventListener

```java
@Component @RequiredArgsConstructor @Slf4j
public class AuditEventListener {
    private final AuditLogJpaRepository auditRepo;

    @EventListener @Async
    public void on(UserRegisteredEvent event) {
        auditRepo.save(AuditLogEntity.of(
            AuditLogEntity.EventType.REGISTER,
            event.userId(), null, null,
            "{\"email\":\"" + event.email() + "\"}"));
    }

    @EventListener @Async
    public void on(UserLoggedInEvent event) {
        auditRepo.save(AuditLogEntity.of(
            AuditLogEntity.EventType.LOGIN, event.userId(), null, null, null));
    }

    @EventListener @Async
    public void on(RoleCreatedEvent event) {
        auditRepo.save(AuditLogEntity.of(
            AuditLogEntity.EventType.ROLE_CREATED, null, event.roleId(),
            null, "{\"name\":\"" + event.roleName() + "\"}"));
    }
    // ... handlers for all other event types
}
```

---

## Test Infrastructure

```java
// Base class for all integration and @DataJpaTest tests
@Testcontainers
public abstract class BaseIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```
