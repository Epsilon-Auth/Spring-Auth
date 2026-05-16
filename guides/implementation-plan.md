# auth-spring — Implementation Plan

*Version 3.0 · Spring Boot 4.0.6 · Java 25 · All phases in order*

**Rules:**
- Complete each phase fully before starting the next
- Each task specifies exact package and file location
- Code snippets are complete and production-ready — do not abbreviate
- Java `record` is used for all non-entity types (DTOs, events, value objects, responses)
- No Lombok on records or value objects; Lombok only on JPA entities

---

## Phase 0 — Maven Setup

### Task 0.1 — `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.6</version>
        <relativePath/>
    </parent>

    <groupId>io.epsilon</groupId>
    <artifactId>auth-spring</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>auth-spring</name>
    <description>Production-ready Auth + RBAC template</description>

    <properties>
        <java.version>25</java.version>
        <jjwt.version>0.12.6</jjwt.version>
        <!-- springdoc v3.x targets Spring Boot 4 / Spring Framework 7 -->
        <springdoc.version>3.0.0</springdoc.version>
        <testcontainers.version>1.21.0</testcontainers.version>
    </properties>

    <dependencies>
        <!-- ─── Core ───────────────────────────────────────────────── -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- ─── JWT ────────────────────────────────────────────────── -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- ─── Database ────────────────────────────────────────────── -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.liquibase</groupId>
            <artifactId>liquibase-core</artifactId>
        </dependency>

        <!-- ─── Optional Redis blacklist strategy ───────────────────── -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- ─── API Documentation ───────────────────────────────────── -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>

        <!-- ─── Lombok (entities only — not records/DTOs) ───────────── -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- ─── Test ─────────────────────────────────────────────────── -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <!-- Enable virtual threads at the JVM level -->
                    <jvmArguments>--enable-preview</jvmArguments>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>25</source>
                    <target>25</target>
                    <compilerArgs>--enable-preview</compilerArgs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### Task 0.2 — `AuthSpringApplication.java`

**File:** `src/main/java/io/epsilon/auth/AuthSpringApplication.java`

```java
package io.epsilon.auth;

import io.epsilon.auth.shared.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class AuthSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthSpringApplication.class, args);
    }
}
```

### Task 0.3 — `application.yml`

**File:** `src/main/resources/application.yml`

```yaml
server:
  port: 8080
  # Spring Boot 4 / Tomcat 11 — enable virtual threads
  tomcat:
    threads:
      virtual: true

spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/auth_db}
    username: ${DB_USER:postgres}
    password: ${DB_PASS:password}
  jpa:
    hibernate:
      ddl-auto: validate          # Liquibase owns schema — Hibernate only validates
    open-in-view: false           # Prevent lazy-loading surprises outside transactions
    properties:
      hibernate:
        default_schema: public
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml
  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false

app:
  jwt:
    secret: ${JWT_SECRET:REPLACE-THIS-WITH-A-32-BYTE-MIN-BASE64-SECRET}
    access-token-ttl-seconds: 900           # 15 minutes
    refresh-token-ttl-seconds: 604800       # 7 days
  cookie:
    enabled: true
    name: accessToken
    http-only: true
    secure: true
    same-site: Strict
    path: /
  blacklist:
    strategy: rdbms                         # swap to 'redis' with zero code changes
    cleanup-cron: '0 0 * * * *'            # hourly pruning (RDBMS only)
  seeding:
    enabled: true
    developer-email: ${DEV_EMAIL:dev@example.com}
    developer-initial-password: ${DEV_PASSWORD:ChangeMe123!}
  cors:
    allowed-origins: ${CORS_ORIGINS:http://localhost:3000}
    allowed-methods: GET,POST,PUT,DELETE,OPTIONS
    max-age-seconds: 3600
```

### Task 0.4 — `application-dev.yml`

**File:** `src/main/resources/application-dev.yml`

```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

app:
  cookie:
    secure: false            # Allow HTTP in local dev
  seeding:
    enabled: true

logging:
  level:
    io.epsilon.auth: DEBUG
    org.springframework.security: DEBUG
```

### Task 0.5 — `application-prod.yml`

**File:** `src/main/resources/application-prod.yml`

```yaml
app:
  cookie:
    secure: true
  seeding:
    enabled: false           # Disable seeding in production after first boot

spring:
  jpa:
    show-sql: false

logging:
  level:
    io.epsilon.auth: INFO
    root: WARN
```

---

## Phase 1 — Shared Layer

> Build the shared layer first. Every subsequent phase depends on it. The shared layer contains zero business logic and zero feature-specific code.

### Task 1.1 — `AppProperties.java`

**File:** `src/main/java/io/epsilon/auth/shared/config/AppProperties.java`

```java
package io.epsilon.auth.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {

    @Valid @NotNull private Jwt jwt = new Jwt();
    @Valid @NotNull private Cookie cookie = new Cookie();
    @Valid @NotNull private Blacklist blacklist = new Blacklist();
    @Valid @NotNull private Seeding seeding = new Seeding();
    @Valid @NotNull private Cors cors = new Cors();

    // ── Getters & Setters ──────────────────────────────────────────

    public Jwt getJwt()             { return jwt; }
    public void setJwt(Jwt jwt)     { this.jwt = jwt; }
    public Cookie getCookie()       { return cookie; }
    public void setCookie(Cookie c) { this.cookie = c; }
    public Blacklist getBlacklist() { return blacklist; }
    public void setBlacklist(Blacklist b) { this.blacklist = b; }
    public Seeding getSeeding()     { return seeding; }
    public void setSeeding(Seeding s) { this.seeding = s; }
    public Cors getCors()           { return cors; }
    public void setCors(Cors c)     { this.cors = c; }

    // ── Nested Configuration Classes ──────────────────────────────

    public static class Jwt {
        @NotBlank
        private String secret;

        @Min(60)
        private long accessTokenTtlSeconds = 900;

        @Min(3600)
        private long refreshTokenTtlSeconds = 604800;

        public String getSecret()                          { return secret; }
        public void setSecret(String secret)               { this.secret = secret; }
        public long getAccessTokenTtlSeconds()             { return accessTokenTtlSeconds; }
        public void setAccessTokenTtlSeconds(long v)       { this.accessTokenTtlSeconds = v; }
        public long getRefreshTokenTtlSeconds()            { return refreshTokenTtlSeconds; }
        public void setRefreshTokenTtlSeconds(long v)      { this.refreshTokenTtlSeconds = v; }
    }

    public static class Cookie {
        private boolean enabled = true;
        @NotBlank private String name = "accessToken";
        private boolean httpOnly = true;
        private boolean secure = true;
        @NotBlank private String sameSite = "Strict";
        @NotBlank private String path = "/";

        public boolean isEnabled()           { return enabled; }
        public void setEnabled(boolean v)    { this.enabled = v; }
        public String getName()              { return name; }
        public void setName(String name)     { this.name = name; }
        public boolean isHttpOnly()          { return httpOnly; }
        public void setHttpOnly(boolean v)   { this.httpOnly = v; }
        public boolean isSecure()            { return secure; }
        public void setSecure(boolean v)     { this.secure = v; }
        public String getSameSite()          { return sameSite; }
        public void setSameSite(String v)    { this.sameSite = v; }
        public String getPath()              { return path; }
        public void setPath(String v)        { this.path = v; }
    }

    public static class Blacklist {
        @NotBlank private String strategy = "rdbms";
        @NotBlank private String cleanupCron = "0 0 * * * *";

        public String getStrategy()             { return strategy; }
        public void setStrategy(String v)       { this.strategy = v; }
        public String getCleanupCron()          { return cleanupCron; }
        public void setCleanupCron(String v)    { this.cleanupCron = v; }
    }

    public static class Seeding {
        private boolean enabled = true;
        private String developerEmail;
        private String developerInitialPassword;

        public boolean isEnabled()                         { return enabled; }
        public void setEnabled(boolean v)                  { this.enabled = v; }
        public String getDeveloperEmail()                  { return developerEmail; }
        public void setDeveloperEmail(String v)            { this.developerEmail = v; }
        public String getDeveloperInitialPassword()        { return developerInitialPassword; }
        public void setDeveloperInitialPassword(String v)  { this.developerInitialPassword = v; }
    }

    public static class Cors {
        @NotBlank private String allowedOrigins = "http://localhost:3000";
        @NotBlank private String allowedMethods = "GET,POST,PUT,DELETE,OPTIONS";
        @Min(0)   private long maxAgeSeconds = 3600;

        public String getAllowedOrigins()           { return allowedOrigins; }
        public void setAllowedOrigins(String v)     { this.allowedOrigins = v; }
        public String getAllowedMethods()           { return allowedMethods; }
        public void setAllowedMethods(String v)     { this.allowedMethods = v; }
        public long getMaxAgeSeconds()             { return maxAgeSeconds; }
        public void setMaxAgeSeconds(long v)       { this.maxAgeSeconds = v; }
    }
}
```

### Task 1.2 — Infrastructure Config Beans

**File:** `src/main/java/io/epsilon/auth/shared/config/JpaConfig.java`

```java
package io.epsilon.auth.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaConfig {
    // Activates @CreatedDate and @LastModifiedDate on all entities
}
```

**File:** `src/main/java/io/epsilon/auth/shared/config/SchedulingConfig.java`

```java
package io.epsilon.auth.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Activates @Scheduled on TokenBlacklistPruner
}
```

**File:** `src/main/java/io/epsilon/auth/shared/config/JacksonConfig.java`

```java
package io.epsilon.auth.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Instant → ISO-8601 string, not epoch millis
    }
}
```

**File:** `src/main/java/io/epsilon/auth/shared/config/AsyncConfig.java`

```java
package io.epsilon.auth.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * All @Async tasks (including AuditEventListener) run on virtual threads.
     * No pool sizing needed — virtual threads are lightweight and multiplexed.
     */
    @Override
    public Executor getAsyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

**File:** `src/main/java/io/epsilon/auth/shared/config/OpenApiConfig.java`

```java
package io.epsilon.auth.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        final String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("auth-spring API")
                        .version("3.0.0")
                        .description("Authentication and RBAC template"))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
```

### Task 1.3 — Sealed Exception Hierarchy

**File:** `src/main/java/io/epsilon/auth/shared/exception/DomainException.java`

```java
package io.epsilon.auth.shared.exception;

/**
 * Sealed base class for all domain exceptions.
 * The compiler enforces exhaustive handling in GlobalExceptionHandler
 * and pattern matching switches.
 */
public sealed class DomainException extends RuntimeException
        permits ResourceNotFoundException,
                EmailAlreadyExistsException,
                AuthException,
                RoleInUseException,
                InvalidPermissionNameException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**File:** `src/main/java/io/epsilon/auth/shared/exception/ResourceNotFoundException.java`

```java
package io.epsilon.auth.shared.exception;

public final class ResourceNotFoundException extends DomainException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

**File:** `src/main/java/io/epsilon/auth/shared/exception/EmailAlreadyExistsException.java`

```java
package io.epsilon.auth.shared.exception;

public final class EmailAlreadyExistsException extends DomainException {
    public EmailAlreadyExistsException(String email) {
        super("Email already registered: " + email);
    }
}
```

**File:** `src/main/java/io/epsilon/auth/shared/exception/AuthException.java`

```java
package io.epsilon.auth.shared.exception;

public final class AuthException extends DomainException {
    public AuthException(String message) {
        super(message);
    }
}
```

**File:** `src/main/java/io/epsilon/auth/shared/exception/RoleInUseException.java`

```java
package io.epsilon.auth.shared.exception;

public final class RoleInUseException extends DomainException {
    public RoleInUseException(String message) {
        super(message);
    }
}
```

**File:** `src/main/java/io/epsilon/auth/shared/exception/InvalidPermissionNameException.java`

```java
package io.epsilon.auth.shared.exception;

public final class InvalidPermissionNameException extends DomainException {
    public InvalidPermissionNameException(String message) {
        super(message);
    }
}
```

### Task 1.4 — `ApiResponse<T>` and `ApiError`

**File:** `src/main/java/io/epsilon/auth/shared/web/ApiError.java`

```java
package io.epsilon.auth.shared.web;

public record ApiError(String code, String message) {}
```

**File:** `src/main/java/io/epsilon/auth/shared/web/ApiResponse.java`

```java
package io.epsilon.auth.shared.web;

import org.slf4j.MDC;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        String requestId,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, MDC.get("requestId"), Instant.now());
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message),
                MDC.get("requestId"), Instant.now());
    }
}
```

### Task 1.5 — `GlobalExceptionHandler`

**File:** `src/main/java/io/epsilon/auth/shared/web/GlobalExceptionHandler.java`

```java
package io.epsilon.auth.shared.web;

import io.epsilon.auth.shared.exception.*;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException ex) {
        // Java 25 pattern matching switch — sealed hierarchy is exhaustive
        HttpStatus status = switch (ex) {
            case AuthException e                  -> HttpStatus.UNAUTHORIZED;
            case ResourceNotFoundException e      -> HttpStatus.NOT_FOUND;
            case EmailAlreadyExistsException e    -> HttpStatus.CONFLICT;
            case RoleInUseException e             -> HttpStatus.CONFLICT;
            case InvalidPermissionNameException e -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(status.name(), ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("FORBIDDEN", "Access denied"));
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenExpired(ExpiredJwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("TOKEN_EXPIRED", "Access token has expired"));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenInvalid(JwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("TOKEN_INVALID", "Invalid access token"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
```

### Task 1.6 — `RequestIdFilter`

**File:** `src/main/java/io/epsilon/auth/shared/web/RequestIdFilter.java`

```java
package io.epsilon.auth.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static java.util.Optional.ofNullable;

/**
 * Runs at HIGHEST_PRECEDENCE so every log line — including those from security
 * filters — carries the requestId. Clears MDC in finally to prevent thread-pool leakage.
 * With virtual threads, each request gets its own thread, but clearing is still correct.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = ofNullable(request.getHeader(REQUEST_ID_HEADER))
                .filter(StringUtils::hasText)
                .orElseGet(() -> UUID.randomUUID().toString());

        MDC.put("requestId", id);
        response.setHeader(REQUEST_ID_HEADER, id);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear(); // Critical: prevents MDC leakage on virtual thread reuse
        }
    }
}
```

### Task 1.7 — Cross-Cutting Security (MOVED from auth/infrastructure)

**File:** `src/main/java/io/epsilon/auth/shared/security/StarterPermissionEvaluator.java`

```java
package io.epsilon.auth.shared.security;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Evaluates custom permission expressions: @PreAuthorize("hasPermission(null, 'role:read')").
 * Lives in shared/security because it enforces rules for ALL modules (auth + rbac).
 *
 * Evaluation order (first match wins):
 *   1. "all:all"       → wildcard, grants everything (ROLE_DEVELOPER)
 *   2. "resource:*"    → resource-level wildcard (e.g. "role:*" grants role:create, role:read…)
 *   3. exact match     → literal permission string
 */
@Component
public class StarterPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        if (auth == null || !auth.isAuthenticated()) return false;
        if (!(permission instanceof String required)) return false;

        Set<String> held = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());

        if (held.contains("all:all")) return true;

        String resource = required.split(":")[0];
        if (held.contains(resource + ":*")) return true;

        return held.contains(required);
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId,
                                 String targetType, Object permission) {
        return hasPermission(auth, targetId, permission);
    }
}
```

**File:** `src/main/java/io/epsilon/auth/shared/security/SecurityConfig.java`

```java
package io.epsilon.auth.shared.security;

import io.epsilon.auth.shared.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final StarterPermissionEvaluator permissionEvaluator;
    private final AppProperties props;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          StarterPermissionEvaluator permissionEvaluator,
                          AppProperties props) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.permissionEvaluator = permissionEvaluator;
        this.props = props;
    }

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
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg)
            throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        var handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(props.getCors().getAllowedOrigins().split(",")));
        config.setAllowedMethods(Arrays.asList(props.getCors().getAllowedMethods().split(",")));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(props.getCors().getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

**File:** `src/main/java/io/epsilon/auth/shared/security/JwtAuthenticationFilter.java`

```java
package io.epsilon.auth.shared.security;

import io.epsilon.auth.auth.infrastructure.security.JwtService;
import io.epsilon.auth.auth.infrastructure.security.UserPrincipal;
import io.epsilon.auth.auth.infrastructure.token.TokenBlacklist;
import io.epsilon.auth.shared.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Validates JWT on every incoming request. Lives in shared/security because it
 * applies to ALL controllers — not just auth controllers.
 *
 * Performance contract:
 *   - Valid JWT, not blacklisted → UserPrincipal.fromClaims() → ZERO DB query
 *   - Blacklist check            → 1 indexed DB read (or 1 Redis key lookup)
 *   - Login only                 → 1 DB read via UserDetailsServiceImpl
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final TokenBlacklist blacklist;
    private final AppProperties props;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   TokenBlacklist blacklist,
                                   AppProperties props) {
        this.jwtService = jwtService;
        this.blacklist = blacklist;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        extractToken(request).ifPresent(token -> {
            try {
                Claims claims = jwtService.parseAndValidate(token);
                String jti = claims.get("jti", String.class);
                if (!blacklist.isBlacklisted(jti)) {
                    UserPrincipal principal = UserPrincipal.fromClaims(claims);
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException ex) {
                log.debug("JWT validation failed: {}", ex.getMessage());
            }
        });
        chain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        // Priority 1: Authorization: Bearer <token>
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7));
        }
        // Priority 2: HttpOnly cookie
        if (props.getCookie().isEnabled() && request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(c -> props.getCookie().getName().equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst();
        }
        return Optional.empty();
    }
}
```

---

## Phase 2 — Database Schema (Liquibase)

### Task 2.1 — `db.changelog-master.yaml`

**File:** `src/main/resources/db/changelog/db.changelog-master.yaml`

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

### Task 2.2 — `0001_create_users.yaml`

**File:** `src/main/resources/db/changelog/changes/0001_create_users.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 0001_create_users
      author: auth-spring
      changes:
        - createTable:
            tableName: auth_users
            columns:
              - column:
                  name: id
                  type: UUID
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: email
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
                    unique: true
              - column:
                  name: password_hash
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
              - column:
                  name: enabled
                  type: BOOLEAN
                  defaultValueBoolean: true
                  constraints:
                    nullable: false
              - column:
                  name: account_non_locked
                  type: BOOLEAN
                  defaultValueBoolean: true
                  constraints:
                    nullable: false
              - column:
                  name: account_non_expired
                  type: BOOLEAN
                  defaultValueBoolean: true
                  constraints:
                    nullable: false
              - column:
                  name: credentials_non_expired
                  type: BOOLEAN
                  defaultValueBoolean: true
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: TIMESTAMPTZ
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: TIMESTAMPTZ
                  constraints:
                    nullable: false
        - createIndex:
            indexName: idx_users_email
            tableName: auth_users
            columns:
              - column:
                  name: email
      rollback:
        - dropTable:
            tableName: auth_users
```

### Task 2.3 — `0002_create_roles.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 0002_create_roles
      author: auth-spring
      changes:
        - createTable:
            tableName: auth_roles
            columns:
              - column: { name: id, type: UUID, constraints: { primaryKey: true, nullable: false } }
              - column: { name: name, type: VARCHAR(100), constraints: { nullable: false, unique: true } }
              - column: { name: description, type: VARCHAR(500) }
      rollback:
        - dropTable: { tableName: auth_roles }
```

### Task 2.4 — `0003_create_permissions.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 0003_create_permissions
      author: auth-spring
      changes:
        - createTable:
            tableName: auth_permissions
            columns:
              - column: { name: id, type: UUID, constraints: { primaryKey: true, nullable: false } }
              - column: { name: name, type: VARCHAR(100), constraints: { nullable: false, unique: true } }
              - column: { name: description, type: VARCHAR(500) }
        - sql:
            sql: >
              ALTER TABLE auth_permissions
              ADD CONSTRAINT chk_permission_name_format
              CHECK (name ~ '^[a-z_]+:[a-z_*]+$');
      rollback:
        - dropTable: { tableName: auth_permissions }
```

### Task 2.5 — `0004_create_user_roles.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 0004_create_user_roles
      author: auth-spring
      changes:
        - createTable:
            tableName: auth_user_roles
            columns:
              - column: { name: user_id, type: UUID, constraints: { nullable: false } }
              - column: { name: role_id, type: UUID, constraints: { nullable: false } }
        - addPrimaryKey:
            tableName: auth_user_roles
            columnNames: user_id, role_id
            constraintName: pk_user_roles
        - addForeignKeyConstraint:
            baseTableName: auth_user_roles
            baseColumnNames: user_id
            referencedTableName: auth_users
            referencedColumnNames: id
            constraintName: fk_user_roles_user
            onDelete: CASCADE
        - addForeignKeyConstraint:
            baseTableName: auth_user_roles
            baseColumnNames: role_id
            referencedTableName: auth_roles
            referencedColumnNames: id
            constraintName: fk_user_roles_role
            onDelete: RESTRICT
      rollback:
        - dropTable: { tableName: auth_user_roles }
```

### Task 2.6 — `0005_create_role_permissions.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 0005_create_role_permissions
      author: auth-spring
      changes:
        - createTable:
            tableName: auth_role_permissions
            columns:
              - column: { name: role_id, type: UUID, constraints: { nullable: false } }
              - column: { name: permission_id, type: UUID, constraints: { nullable: false } }
        - addPrimaryKey:
            tableName: auth_role_permissions
            columnNames: role_id, permission_id
            constraintName: pk_role_permissions
        - addForeignKeyConstraint:
            baseTableName: auth_role_permissions
            baseColumnNames: role_id
            referencedTableName: auth_roles
            referencedColumnNames: id
            constraintName: fk_rp_role
            onDelete: CASCADE
        - addForeignKeyConstraint:
            baseTableName: auth_role_permissions
            baseColumnNames: permission_id
            referencedTableName: auth_permissions
            referencedColumnNames: id
            constraintName: fk_rp_permission
            onDelete: RESTRICT
      rollback:
        - dropTable: { tableName: auth_role_permissions }
```

### Task 2.7 — `0006_create_refresh_tokens.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 0006_create_refresh_tokens
      author: auth-spring
      changes:
        - createTable:
            tableName: auth_refresh_tokens
            columns:
              - column: { name: id, type: UUID, constraints: { primaryKey: true, nullable: false } }
              - column: { name: token_hash, type: VARCHAR(64), constraints: { nullable: false, unique: true } }
              - column: { name: user_id, type: UUID, constraints: { nullable: false } }
              - column: { name: issued_at, type: TIMESTAMPTZ, constraints: { nullable: false } }
              - column: { name: expires_at, type: TIMESTAMPTZ, constraints: { nullable: false } }
              - column: { name: revoked_at, type: TIMESTAMPTZ }
              - column: { name: device_fingerprint, type: VARCHAR(255) }
        - addForeignKeyConstraint:
            baseTableName: auth_refresh_tokens
            baseColumnNames: user_id
            referencedTableName: auth_users
            referencedColumnNames: id
            constraintName: fk_refresh_token_user
            onDelete: CASCADE
        - createIndex:
            indexName: idx_refresh_tokens_user
            tableName: auth_refresh_tokens
            columns:
              - column: { name: user_id }
      rollback:
        - dropTable: { tableName: auth_refresh_tokens }
```

### Task 2.8 — `0007_create_token_blacklist.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 0007_create_token_blacklist
      author: auth-spring
      changes:
        - createTable:
            tableName: auth_token_blacklist
            columns:
              - column: { name: id, type: UUID, constraints: { primaryKey: true, nullable: false } }
              - column: { name: jti, type: VARCHAR(36), constraints: { nullable: false } }
              - column: { name: expires_at, type: TIMESTAMPTZ, constraints: { nullable: false } }
        - createIndex:
            indexName: idx_blacklist_jti_expires
            tableName: auth_token_blacklist
            columns:
              - column: { name: jti }
              - column: { name: expires_at }
      rollback:
        - dropTable: { tableName: auth_token_blacklist }
```

### Task 2.9 — `0008_create_audit_log.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 0008_create_audit_log
      author: auth-spring
      changes:
        - createTable:
            tableName: auth_audit_log
            columns:
              - column: { name: id, type: UUID, constraints: { primaryKey: true, nullable: false } }
              - column: { name: event_type, type: VARCHAR(50), constraints: { nullable: false } }
              - column: { name: actor_id, type: UUID }
              - column: { name: target_id, type: UUID }
              - column: { name: ip_address, type: VARCHAR(45) }
              - column: { name: metadata, type: JSONB }
              - column: { name: occurred_at, type: TIMESTAMPTZ, constraints: { nullable: false } }
        - createIndex:
            indexName: idx_audit_actor
            tableName: auth_audit_log
            columns:
              - column: { name: actor_id }
              - column: { name: occurred_at }
        - createIndex:
            indexName: idx_audit_type
            tableName: auth_audit_log
            columns:
              - column: { name: event_type }
              - column: { name: occurred_at }
      rollback:
        - dropTable: { tableName: auth_audit_log }
```

---

## Phase 3 — JPA Entities

> All entities live in their module's `infrastructure/persistence/` package.
> No entity implements `UserDetails` or any Spring Security interface.
> Use Lombok only for entities (records are not valid JPA entities).

### Task 3.1 — `UserEntity`

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/persistence/UserEntity.java`

```java
package io.epsilon.auth.auth.infrastructure.persistence;

import io.epsilon.auth.rbac.infrastructure.persistence.RoleEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA persistence model for users.
 * Does NOT implement UserDetails — that is UserPrincipal's responsibility.
 */
@Entity
@Table(name = "auth_users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class UserEntity {

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
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "auth_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<RoleEntity> roles = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserEntity u)) return false;
        return id != null && id.equals(u.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
```

### Task 3.2 — `RefreshTokenEntity`

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/persistence/RefreshTokenEntity.java`

```java
package io.epsilon.auth.auth.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Instant issuedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    /** null = active token. Non-null = revoked. */
    private Instant revokedAt;

    @Column(length = 255)
    private String deviceFingerprint;
}
```

### Task 3.3 — `TokenBlacklistEntry`

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/persistence/TokenBlacklistEntry.java`

```java
package io.epsilon.auth.auth.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_token_blacklist")
@Getter
@NoArgsConstructor
public class TokenBlacklistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 36)
    private String jti;

    @Column(nullable = false)
    private Instant expiresAt;

    public static TokenBlacklistEntry of(String jti, Instant expiresAt) {
        var entry = new TokenBlacklistEntry();
        entry.jti = jti;
        entry.expiresAt = expiresAt;
        return entry;
    }
}
```

### Task 3.4 — `RoleEntity`

**File:** `src/main/java/io/epsilon/auth/rbac/infrastructure/persistence/RoleEntity.java`

```java
package io.epsilon.auth.rbac.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "auth_roles")
@Getter
@Setter
@NoArgsConstructor
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "auth_role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<PermissionEntity> permissions = new HashSet<>();

    public static RoleEntity of(String name, String description, Set<PermissionEntity> permissions) {
        var role = new RoleEntity();
        role.name = name;
        role.description = description;
        role.permissions = permissions != null ? permissions : new HashSet<>();
        return role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoleEntity r)) return false;
        return id != null && id.equals(r.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
```

### Task 3.5 — `PermissionEntity`

**File:** `src/main/java/io/epsilon/auth/rbac/infrastructure/persistence/PermissionEntity.java`

```java
package io.epsilon.auth.rbac.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "auth_permissions")
@Getter
@NoArgsConstructor
public class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    public static PermissionEntity of(String name) {
        var p = new PermissionEntity();
        p.name = name;
        return p;
    }

    public static PermissionEntity of(String name, String description) {
        var p = new PermissionEntity();
        p.name = name;
        p.description = description;
        return p;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PermissionEntity pe)) return false;
        return id != null && id.equals(pe.id);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
```

### Task 3.6 — `AuditLogEntity`

**File:** `src/main/java/io/epsilon/auth/audit/infrastructure/AuditLogEntity.java`

```java
package io.epsilon.auth.audit.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_audit_log")
@Getter
@NoArgsConstructor
public class AuditLogEntity {

    public enum EventType {
        LOGIN, LOGOUT, REGISTER, TOKEN_REFRESH,
        ROLE_CREATED, ROLE_UPDATED, ROLE_DELETED, PERMISSION_ASSIGNED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EventType eventType;

    private UUID actorId;
    private UUID targetId;

    @Column(length = 45)
    private String ipAddress;

    /** Stored as JSONB — use ObjectMapper, never string concatenation */
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(nullable = false)
    private Instant occurredAt;

    public static AuditLogEntity of(EventType type, UUID actorId, UUID targetId,
                                     String ipAddress, String jsonMetadata) {
        var log = new AuditLogEntity();
        log.eventType = type;
        log.actorId = actorId;
        log.targetId = targetId;
        log.ipAddress = ipAddress;
        log.metadata = jsonMetadata;
        log.occurredAt = Instant.now();
        return log;
    }
}
```

---

## Phase 4 — JPA Repositories

> Named with `Jpa` prefix to distinguish from port interfaces. Placed in each module's `infrastructure/persistence/`.

### Task 4.1 — `UserJpaRepository`

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/persistence/UserJpaRepository.java`

```java
package io.epsilon.auth.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);

    /** Used by JpaUserRoleAssignmentAdapter to guard delete-role */
    @Query("SELECT COUNT(u) > 0 FROM UserEntity u JOIN u.roles r WHERE r.id = :roleId")
    boolean existsByRolesId(@Param("roleId") UUID roleId);
}
```

### Task 4.2 — `RefreshTokenJpaRepository`

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/persistence/RefreshTokenJpaRepository.java`

```java
package io.epsilon.auth.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
        UPDATE RefreshTokenEntity t
        SET t.revokedAt = CURRENT_TIMESTAMP
        WHERE t.userId = :userId AND t.revokedAt IS NULL
    """)
    int revokeAllByUserId(@Param("userId") UUID userId);
}
```

### Task 4.3 — `TokenBlacklistJpaRepository`

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/persistence/TokenBlacklistJpaRepository.java`

```java
package io.epsilon.auth.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface TokenBlacklistJpaRepository extends JpaRepository<TokenBlacklistEntry, UUID> {

    /** Hot path: uses composite index idx_blacklist_jti_expires */
    boolean existsByJtiAndExpiresAtAfter(String jti, Instant now);

    @Modifying
    @Query("DELETE FROM TokenBlacklistEntry t WHERE t.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);
}
```

### Task 4.4 — `RoleJpaRepository` and `PermissionJpaRepository`

**File:** `src/main/java/io/epsilon/auth/rbac/infrastructure/persistence/RoleJpaRepository.java`

```java
package io.epsilon.auth.rbac.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleJpaRepository extends JpaRepository<RoleEntity, UUID> {
    Optional<RoleEntity> findByName(String name);
    boolean existsByName(String name);
    Page<RoleEntity> findAll(Pageable pageable);
}
```

**File:** `src/main/java/io/epsilon/auth/rbac/infrastructure/persistence/PermissionJpaRepository.java`

```java
package io.epsilon.auth.rbac.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PermissionJpaRepository extends JpaRepository<PermissionEntity, UUID> {
    Optional<PermissionEntity> findByName(String name);
    boolean existsByName(String name);
    Page<PermissionEntity> findAll(Pageable pageable);
}
```

**File:** `src/main/java/io/epsilon/auth/audit/infrastructure/AuditLogJpaRepository.java`

```java
package io.epsilon.auth.audit.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, UUID> {}
```

---

## Phase 5 — Token Blacklist Infrastructure

### Task 5.1 — `TokenBlacklist` Interface

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/token/TokenBlacklist.java`

```java
package io.epsilon.auth.auth.infrastructure.token;

import java.time.Instant;

/**
 * Strategy interface for JWT blacklist storage.
 * Default: RDBMS (app.blacklist.strategy=rdbms, matchIfMissing=true).
 * Redis: set app.blacklist.strategy=redis.
 * All implementations must be thread-safe (virtual threads).
 */
public interface TokenBlacklist {
    void add(String jti, Instant expiresAt);
    boolean isBlacklisted(String jti);
}
```

### Task 5.2 — `RdbmsTokenBlacklist`

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/token/RdbmsTokenBlacklist.java`

```java
package io.epsilon.auth.auth.infrastructure.token;

import io.epsilon.auth.auth.infrastructure.persistence.TokenBlacklistEntry;
import io.epsilon.auth.auth.infrastructure.persistence.TokenBlacklistJpaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "app.blacklist.strategy", havingValue = "rdbms", matchIfMissing = true)
public class RdbmsTokenBlacklist implements TokenBlacklist {

    private final TokenBlacklistJpaRepository repo;

    public RdbmsTokenBlacklist(TokenBlacklistJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public void add(String jti, Instant expiresAt) {
        repo.save(TokenBlacklistEntry.of(jti, expiresAt));
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return repo.existsByJtiAndExpiresAtAfter(jti, Instant.now());
    }
}
```

### Task 5.3 — `TokenBlacklistPruner` (SRP — scheduling only)

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/token/TokenBlacklistPruner.java`

```java
package io.epsilon.auth.auth.infrastructure.token;

import io.epsilon.auth.auth.infrastructure.persistence.TokenBlacklistJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Separated from RdbmsTokenBlacklist by SRP.
 * One class = one reason to change.
 * Only activates when the RDBMS blacklist strategy is selected.
 */
@Component
@ConditionalOnProperty(name = "app.blacklist.strategy", havingValue = "rdbms", matchIfMissing = true)
public class TokenBlacklistPruner {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistPruner.class);
    private final TokenBlacklistJpaRepository repo;

    public TokenBlacklistPruner(TokenBlacklistJpaRepository repo) {
        this.repo = repo;
    }

    @Scheduled(cron = "${app.blacklist.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void pruneExpired() {
        int deleted = repo.deleteByExpiresAtBefore(Instant.now());
        if (deleted > 0) {
            log.info("Pruned {} expired token blacklist entries", deleted);
        }
    }
}
```

### Task 5.4 — `RedisTokenBlacklist` (Optional Strategy)

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/token/RedisTokenBlacklist.java`

```java
package io.epsilon.auth.auth.infrastructure.token;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis-backed blacklist. TTL is managed natively by Redis — no pruner needed.
 *
 * To activate:
 *   1. Set app.blacklist.strategy=redis in your profile
 *   2. Uncomment the spring-boot-starter-data-redis dependency in pom.xml
 *   3. Configure spring.data.redis.host/port in application.yml
 */
@Component
@ConditionalOnProperty(name = "app.blacklist.strategy", havingValue = "redis")
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
public class RedisTokenBlacklist implements TokenBlacklist {

    private static final String KEY_PREFIX = "bl:";
    private final RedisTemplate<String, String> redis;

    public RedisTokenBlacklist(RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    @Override
    public void add(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (!ttl.isNegative()) {
            redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}
```

---

## Phase 6 — Auth Security Infrastructure

### Task 6.1 — `JwtService`

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/security/JwtService.java`

```java
package io.epsilon.auth.auth.infrastructure.security;

import io.epsilon.auth.shared.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.*;

/**
 * JWT cryptographic operations only.
 * Subject = user UUID (not email — UUID is stable, non-PII in logs).
 * Permissions embedded as claims — no DB query on subsequent requests.
 */
@Component
public class JwtService {

    private final SecretKey signingKey;
    private final AppProperties props;

    public JwtService(AppProperties props) {
        this.props = props;
        byte[] keyBytes = Decoders.BASE64.decode(props.getJwt().getSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String issueAccessToken(UUID userId, String email, Set<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("jti", UUID.randomUUID().toString())
                .claim("permissions", new ArrayList<>(permissions))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(props.getJwt().getAccessTokenTtlSeconds())))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractJti(String token) {
        return parseAndValidate(token).get("jti", String.class);
    }

    public Instant extractExpiry(String token) {
        return parseAndValidate(token).getExpiration().toInstant();
    }
}
```

### Task 6.2 — `UserPrincipal`

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/security/UserPrincipal.java`

```java
package io.epsilon.auth.auth.infrastructure.security;

import io.epsilon.auth.auth.infrastructure.persistence.UserEntity;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spring Security adapter. Implements UserDetails — UserEntity does NOT.
 *
 * Two factory methods:
 *   fromClaims() — built from JWT claims, ZERO database access (all authenticated requests)
 *   fromEntity() — built from UserEntity, ONE database access (login only)
 */
public record UserPrincipal(
        UUID userId,
        String email,
        String passwordHash,
        boolean enabled,
        boolean accountNonLocked,
        boolean accountNonExpired,
        boolean credentialsNonExpired,
        Set<GrantedAuthority> authorities
) implements UserDetails {

    @SuppressWarnings("unchecked")
    public static UserPrincipal fromClaims(Claims claims) {
        List<String> perms = claims.get("permissions", List.class);
        Set<GrantedAuthority> authorities = (perms == null ? List.<String>of() : perms)
                .stream()
                .map(p -> (GrantedAuthority) () -> p)
                .collect(Collectors.toUnmodifiableSet());

        return new UserPrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get("email", String.class),
                "",      // password not needed after token validation
                true, true, true, true,
                authorities
        );
    }

    public static UserPrincipal fromEntity(UserEntity entity) {
        Set<GrantedAuthority> authorities = entity.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(perm -> (GrantedAuthority) perm::getName)
                .collect(Collectors.toUnmodifiableSet());

        return new UserPrincipal(
                entity.getId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.isEnabled(),
                entity.isAccountNonLocked(),
                entity.isAccountNonExpired(),
                entity.isCredentialsNonExpired(),
                authorities
        );
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword()  { return passwordHash; }
    @Override public String getUsername()  { return email; }
    @Override public boolean isEnabled()   { return enabled; }
    @Override public boolean isAccountNonLocked()          { return accountNonLocked; }
    @Override public boolean isAccountNonExpired()         { return accountNonExpired; }
    @Override public boolean isCredentialsNonExpired()     { return credentialsNonExpired; }
}
```

### Task 6.3 — `UserDetailsServiceImpl`

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/security/UserDetailsServiceImpl.java`

```java
package io.epsilon.auth.auth.infrastructure.security;

import io.epsilon.auth.auth.infrastructure.persistence.UserJpaRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Called exactly ONCE per login by Spring Security's DaoAuthenticationProvider.
 * Never called on subsequent requests — those use UserPrincipal.fromClaims().
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserJpaRepository userRepo;

    public UserDetailsServiceImpl(UserJpaRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepo.findByEmail(email)
                .map(UserPrincipal::fromEntity)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
```

---

## Phase 7 — Auth Port Interfaces and Adapters

### Task 7.1 — `UserPort` Interface

**File:** `src/main/java/io/epsilon/auth/auth/application/port/UserPort.java`

```java
package io.epsilon.auth.auth.application.port;

import io.epsilon.auth.auth.infrastructure.persistence.UserEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port: what the auth application layer needs from user storage.
 * Application services depend on this interface — never on UserJpaRepository.
 */
public interface UserPort {
    Optional<UserEntity> findById(UUID id);
    Optional<UserEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    UserEntity save(UserEntity user);
}
```

### Task 7.2 — `RefreshTokenPort` Interface

**File:** `src/main/java/io/epsilon/auth/auth/application/port/RefreshTokenPort.java`

```java
package io.epsilon.auth.auth.application.port;

import io.epsilon.auth.auth.infrastructure.persistence.RefreshTokenEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port for refresh token persistence operations.
 */
public interface RefreshTokenPort {
    String issue(UUID userId, long ttlSeconds);
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
    RefreshTokenEntity save(RefreshTokenEntity entity);
    void revokeAllForUser(UUID userId);
}
```

### Task 7.3 — `JpaUserAdapter`

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/persistence/JpaUserAdapter.java`

```java
package io.epsilon.auth.auth.infrastructure.persistence;

import io.epsilon.auth.auth.application.port.UserPort;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaUserAdapter implements UserPort {

    private final UserJpaRepository repo;

    public JpaUserAdapter(UserJpaRepository repo) {
        this.repo = repo;
    }

    @Override public Optional<UserEntity> findById(UUID id)        { return repo.findById(id); }
    @Override public Optional<UserEntity> findByEmail(String email) { return repo.findByEmail(email); }
    @Override public boolean existsByEmail(String email)            { return repo.existsByEmail(email); }
    @Override public UserEntity save(UserEntity user)               { return repo.save(user); }
}
```

### Task 7.4 — `JpaRefreshTokenAdapter`

**File:** `src/main/java/io/epsilon/auth/auth/infrastructure/persistence/JpaRefreshTokenAdapter.java`

```java
package io.epsilon.auth.auth.infrastructure.persistence;

import io.epsilon.auth.auth.application.port.RefreshTokenPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaRefreshTokenAdapter implements RefreshTokenPort {

    private final RefreshTokenJpaRepository repo;

    public JpaRefreshTokenAdapter(RefreshTokenJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public String issue(UUID userId, long ttlSeconds) {
        String raw = UUID.randomUUID().toString();
        repo.save(RefreshTokenEntity.builder()
                .tokenHash(sha256(raw))
                .userId(userId)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(ttlSeconds))
                .build());
        return raw; // Returned ONCE to client — never stored plain text
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshTokenEntity> findByTokenHash(String tokenHash) {
        return repo.findByTokenHash(tokenHash);
    }

    @Override
    @Transactional
    public RefreshTokenEntity save(RefreshTokenEntity entity) {
        return repo.save(entity);
    }

    @Override
    @Transactional
    public void revokeAllForUser(UUID userId) {
        repo.revokeAllByUserId(userId);
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

---

## Phase 8 — Auth Domain Events

> Events live in `auth/event/` — the publishing module owns its events. The audit module subscribes to them. Feature services never import from `audit/`.

**File:** `src/main/java/io/epsilon/auth/auth/event/UserRegisteredEvent.java`

```java
package io.epsilon.auth.auth.event;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId, String email) {}
```

**File:** `src/main/java/io/epsilon/auth/auth/event/UserLoggedInEvent.java`

```java
package io.epsilon.auth.auth.event;

import java.util.UUID;

public record UserLoggedInEvent(UUID userId) {}
```

**File:** `src/main/java/io/epsilon/auth/auth/event/UserLoggedOutEvent.java`

```java
package io.epsilon.auth.auth.event;

public record UserLoggedOutEvent() {}
```

---

## Phase 9 — Auth Application Layer (Use Cases)

> Each use case is a separate class. Injects only the dependencies it actually needs. No god class.

### Task 9.1 — `RegisterUseCase`

**File:** `src/main/java/io/epsilon/auth/auth/application/RegisterUseCase.java`

```java
package io.epsilon.auth.auth.application;

import io.epsilon.auth.auth.application.port.UserPort;
import io.epsilon.auth.auth.event.UserRegisteredEvent;
import io.epsilon.auth.auth.infrastructure.persistence.UserEntity;
import io.epsilon.auth.auth.web.dto.request.RegisterRequest;
import io.epsilon.auth.rbac.infrastructure.persistence.RoleJpaRepository;
import io.epsilon.auth.shared.exception.EmailAlreadyExistsException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class RegisterUseCase {

    private final UserPort userPort;
    private final RoleJpaRepository roleRepo;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher eventPublisher;

    public RegisterUseCase(UserPort userPort,
                           RoleJpaRepository roleRepo,
                           PasswordEncoder encoder,
                           ApplicationEventPublisher eventPublisher) {
        this.userPort = userPort;
        this.roleRepo = roleRepo;
        this.encoder = encoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void execute(RegisterRequest req) {
        if (userPort.existsByEmail(req.email())) {
            throw new EmailAlreadyExistsException(req.email());
        }
        var userRole = roleRepo.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("ROLE_USER not seeded"));

        var user = new UserEntity();
        user.setEmail(req.email());
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRoles(Set.of(userRole));
        UserEntity saved = userPort.save(user);

        eventPublisher.publishEvent(new UserRegisteredEvent(saved.getId(), req.email()));
    }
}
```

### Task 9.2 — `LoginUseCase`

**File:** `src/main/java/io/epsilon/auth/auth/application/LoginUseCase.java`

```java
package io.epsilon.auth.auth.application;

import io.epsilon.auth.auth.application.port.RefreshTokenPort;
import io.epsilon.auth.auth.event.UserLoggedInEvent;
import io.epsilon.auth.auth.infrastructure.security.JwtService;
import io.epsilon.auth.auth.infrastructure.security.UserPrincipal;
import io.epsilon.auth.auth.web.dto.request.LoginRequest;
import io.epsilon.auth.auth.web.dto.response.TokenResponse;
import io.epsilon.auth.shared.config.AppProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LoginUseCase {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final RefreshTokenPort refreshTokenPort;
    private final ApplicationEventPublisher eventPublisher;
    private final AppProperties props;

    public LoginUseCase(AuthenticationManager authManager,
                        JwtService jwtService,
                        RefreshTokenPort refreshTokenPort,
                        ApplicationEventPublisher eventPublisher,
                        AppProperties props) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.refreshTokenPort = refreshTokenPort;
        this.eventPublisher = eventPublisher;
        this.props = props;
    }

    public TokenResponse execute(LoginRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        Set<String> permissions = extractPermissions(principal);

        String accessToken = jwtService.issueAccessToken(
                principal.userId(), principal.email(), permissions);
        String refreshToken = refreshTokenPort.issue(
                principal.userId(), props.getJwt().getRefreshTokenTtlSeconds());

        eventPublisher.publishEvent(new UserLoggedInEvent(principal.userId()));

        return TokenResponse.of(accessToken, refreshToken, props.getJwt().getAccessTokenTtlSeconds());
    }

    private Set<String> extractPermissions(UserPrincipal principal) {
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }
}
```

### Task 9.3 — `LogoutUseCase`

**File:** `src/main/java/io/epsilon/auth/auth/application/LogoutUseCase.java`

```java
package io.epsilon.auth.auth.application;

import io.epsilon.auth.auth.event.UserLoggedOutEvent;
import io.epsilon.auth.auth.infrastructure.security.JwtService;
import io.epsilon.auth.auth.infrastructure.token.TokenBlacklist;
import io.jsonwebtoken.JwtException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class LogoutUseCase {

    private final JwtService jwtService;
    private final TokenBlacklist blacklist;
    private final ApplicationEventPublisher eventPublisher;

    public LogoutUseCase(JwtService jwtService,
                         TokenBlacklist blacklist,
                         ApplicationEventPublisher eventPublisher) {
        this.jwtService = jwtService;
        this.blacklist = blacklist;
        this.eventPublisher = eventPublisher;
    }

    /** Idempotent — already-invalid tokens are silently ignored */
    public void execute(String rawAccessToken) {
        try {
            String jti = jwtService.extractJti(rawAccessToken);
            Instant expiry = jwtService.extractExpiry(rawAccessToken);
            blacklist.add(jti, expiry);
            eventPublisher.publishEvent(new UserLoggedOutEvent());
        } catch (JwtException ignored) {
            // Token already invalid — logout is still idempotent
        }
    }
}
```

### Task 9.4 — `RefreshTokensUseCase`

**File:** `src/main/java/io/epsilon/auth/auth/application/RefreshTokensUseCase.java`

```java
package io.epsilon.auth.auth.application;

import io.epsilon.auth.auth.application.port.RefreshTokenPort;
import io.epsilon.auth.auth.application.port.UserPort;
import io.epsilon.auth.auth.infrastructure.persistence.RefreshTokenEntity;
import io.epsilon.auth.auth.infrastructure.security.JwtService;
import io.epsilon.auth.auth.web.dto.request.RefreshTokenRequest;
import io.epsilon.auth.auth.web.dto.response.TokenResponse;
import io.epsilon.auth.shared.config.AppProperties;
import io.epsilon.auth.shared.exception.AuthException;
import io.epsilon.auth.shared.exception.ResourceNotFoundException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import static io.epsilon.auth.auth.infrastructure.persistence.JpaRefreshTokenAdapter.sha256;

@Service
public class RefreshTokensUseCase {

    private final RefreshTokenPort refreshTokenPort;
    private final UserPort userPort;
    private final JwtService jwtService;
    private final AppProperties props;

    public RefreshTokensUseCase(RefreshTokenPort refreshTokenPort,
                                UserPort userPort,
                                JwtService jwtService,
                                AppProperties props) {
        this.refreshTokenPort = refreshTokenPort;
        this.userPort = userPort;
        this.jwtService = jwtService;
        this.props = props;
    }

    @Transactional
    public TokenResponse execute(RefreshTokenRequest req) {
        RefreshTokenEntity old = refreshTokenPort.findByTokenHash(sha256(req.refreshToken()))
                .orElseThrow(() -> new AuthException("Refresh token not found"));

        if (old.getRevokedAt() != null) {
            throw new AuthException("Token already revoked — possible replay attack");
        }
        if (old.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthException("Refresh token expired");
        }

        // Revoke old token (rotation)
        old.setRevokedAt(Instant.now());
        refreshTokenPort.save(old);

        // Re-resolve permissions from DB on refresh (picks up mid-session role changes)
        var user = userPort.findById(old.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(perm -> (GrantedAuthority) perm::getName)
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());

        String newAccess = jwtService.issueAccessToken(
                user.getId(), user.getEmail(), permissions);
        String newRefresh = refreshTokenPort.issue(
                user.getId(), props.getJwt().getRefreshTokenTtlSeconds());

        return TokenResponse.of(newAccess, newRefresh, props.getJwt().getAccessTokenTtlSeconds());
    }
}
```

### Task 9.5 — `GetProfileUseCase`

**File:** `src/main/java/io/epsilon/auth/auth/application/GetProfileUseCase.java`

```java
package io.epsilon.auth.auth.application;

import io.epsilon.auth.auth.application.port.UserPort;
import io.epsilon.auth.auth.infrastructure.security.UserPrincipal;
import io.epsilon.auth.auth.web.dto.response.UserProfileResponse;
import io.epsilon.auth.shared.exception.ResourceNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GetProfileUseCase {

    private final UserPort userPort;

    public GetProfileUseCase(UserPort userPort) {
        this.userPort = userPort;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse execute(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        var user = userPort.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Set<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toUnmodifiableSet());

        Set<String> permissions = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());

        return new UserProfileResponse(user.getId(), user.getEmail(), roles, permissions);
    }
}
```

---

## Phase 10 — Auth Web Layer

### Task 10.1 — Request / Response DTOs

**File:** `src/main/java/io/epsilon/auth/auth/web/dto/request/LoginRequest.java`

```java
package io.epsilon.auth.auth.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password
) {}
```

**File:** `src/main/java/io/epsilon/auth/auth/web/dto/request/RegisterRequest.java`

```java
package io.epsilon.auth.auth.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {}
```

**File:** `src/main/java/io/epsilon/auth/auth/web/dto/request/RefreshTokenRequest.java`

```java
package io.epsilon.auth.auth.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(@NotBlank String refreshToken) {}
```

**File:** `src/main/java/io/epsilon/auth/auth/web/dto/response/TokenResponse.java`

```java
package io.epsilon.auth.auth.web.dto.response;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static TokenResponse of(String access, String refresh, long ttlSeconds) {
        return new TokenResponse(access, refresh, "Bearer", ttlSeconds);
    }
}
```

**File:** `src/main/java/io/epsilon/auth/auth/web/dto/response/UserProfileResponse.java`

```java
package io.epsilon.auth.auth.web.dto.response;

import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        Set<String> roles,
        Set<String> permissions
) {}
```

### Task 10.2 — `AuthController`

**File:** `src/main/java/io/epsilon/auth/auth/web/AuthController.java`

```java
package io.epsilon.auth.auth.web;

import io.epsilon.auth.auth.application.*;
import io.epsilon.auth.auth.web.dto.request.*;
import io.epsilon.auth.auth.web.dto.response.*;
import io.epsilon.auth.shared.config.AppProperties;
import io.epsilon.auth.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RegisterUseCase registerUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RefreshTokensUseCase refreshTokensUseCase;
    private final GetProfileUseCase getProfileUseCase;
    private final AppProperties props;

    public AuthController(LoginUseCase loginUseCase,
                          RegisterUseCase registerUseCase,
                          LogoutUseCase logoutUseCase,
                          RefreshTokensUseCase refreshTokensUseCase,
                          GetProfileUseCase getProfileUseCase,
                          AppProperties props) {
        this.loginUseCase = loginUseCase;
        this.registerUseCase = registerUseCase;
        this.logoutUseCase = logoutUseCase;
        this.refreshTokensUseCase = refreshTokensUseCase;
        this.getProfileUseCase = getProfileUseCase;
        this.props = props;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> register(@RequestBody @Valid RegisterRequest req) {
        registerUseCase.execute(req);
        return ApiResponse.success(null);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @RequestBody @Valid LoginRequest req,
            HttpServletResponse response) {
        TokenResponse tokens = loginUseCase.execute(req);
        attachCookie(response, tokens.accessToken());
        return ResponseEntity.ok(ApiResponse.success(tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest req,
                                                     HttpServletResponse res) {
        extractBearerToken(req).ifPresent(logoutUseCase::execute);
        clearCookie(res);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @RequestBody @Valid RefreshTokenRequest req,
            HttpServletResponse res) {
        TokenResponse tokens = refreshTokensUseCase.execute(req);
        attachCookie(res, tokens.accessToken());
        return ResponseEntity.ok(ApiResponse.success(tokens));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserProfileResponse> me(Authentication auth) {
        return ApiResponse.success(getProfileUseCase.execute(auth));
    }

    // ── Private cookie helpers ────────────────────────────────────

    private void attachCookie(HttpServletResponse response, String token) {
        if (!props.getCookie().isEnabled()) return;
        Cookie cookie = new Cookie(props.getCookie().getName(), token);
        cookie.setHttpOnly(props.getCookie().isHttpOnly());
        cookie.setSecure(props.getCookie().isSecure());
        cookie.setPath(props.getCookie().getPath());
        cookie.setAttribute("SameSite", props.getCookie().getSameSite());
        cookie.setMaxAge((int) props.getJwt().getAccessTokenTtlSeconds());
        response.addCookie(cookie);
    }

    private void clearCookie(HttpServletResponse response) {
        if (!props.getCookie().isEnabled()) return;
        Cookie cookie = new Cookie(props.getCookie().getName(), "");
        cookie.setMaxAge(0);
        cookie.setPath(props.getCookie().getPath());
        response.addCookie(cookie);
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7));
        }
        if (props.getCookie().isEnabled() && request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(c -> props.getCookie().getName().equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst();
        }
        return Optional.empty();
    }
}
```

---

## Phase 11 — RBAC Domain

### Task 11.1 — `PermissionName` Value Object

**File:** `src/main/java/io/epsilon/auth/rbac/domain/PermissionName.java`

```java
package io.epsilon.auth.rbac.domain;

import io.epsilon.auth.shared.exception.InvalidPermissionNameException;

import java.util.regex.Pattern;

/**
 * Value object representing a valid permission name in "resource:action" format.
 * Self-validates on construction. Services never receive raw strings for permission names.
 *
 * Valid examples:  role:create  |  permission:*  |  user:read
 * Invalid examples: ROLE_ADMIN  |  create        |  role::create
 */
public record PermissionName(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-z_]+:[a-z_*]+$");

    public PermissionName {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new InvalidPermissionNameException(
                    "Permission name must match 'resource:action' format (lowercase). Got: " + value);
        }
    }

    public static PermissionName of(String value) {
        return new PermissionName(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
```

### Task 11.2 — RBAC Domain Events

**File:** `src/main/java/io/epsilon/auth/rbac/event/RoleCreatedEvent.java`

```java
package io.epsilon.auth.rbac.event;

import java.util.UUID;

public record RoleCreatedEvent(UUID roleId, String roleName) {}
```

**File:** `src/main/java/io/epsilon/auth/rbac/event/RoleUpdatedEvent.java`

```java
package io.epsilon.auth.rbac.event;

import java.util.UUID;

public record RoleUpdatedEvent(UUID roleId, String roleName) {}
```

**File:** `src/main/java/io/epsilon/auth/rbac/event/RoleDeletedEvent.java`

```java
package io.epsilon.auth.rbac.event;

import java.util.UUID;

public record RoleDeletedEvent(UUID roleId) {}
```

**File:** `src/main/java/io/epsilon/auth/rbac/event/PermissionAssignedEvent.java`

```java
package io.epsilon.auth.rbac.event;

import java.util.UUID;

public record PermissionAssignedEvent(UUID roleId, UUID permissionId) {}
```

---

## Phase 12 — RBAC Port Interfaces and Adapters

### Task 12.1 — `UserRoleAssignmentPort`

**File:** `src/main/java/io/epsilon/auth/rbac/application/port/UserRoleAssignmentPort.java`

```java
package io.epsilon.auth.rbac.application.port;

import java.util.UUID;

/**
 * Output port defined by the rbac module.
 * Allows rbac to query user-role assignment state without importing UserJpaRepository.
 *
 * The adapter (JpaUserRoleAssignmentAdapter) lives in rbac/infrastructure/crossmodule/
 * and uses UserJpaRepository from auth. The dependency direction is:
 *
 *   rbac/application  →  (this interface)  ←  rbac/infrastructure/crossmodule (implements)
 *                                                        ↓
 *                                          auth/infrastructure/persistence
 */
public interface UserRoleAssignmentPort {
    boolean isRoleAssignedToAnyUser(UUID roleId);
    void assignRoleToUser(UUID userId, UUID roleId);
    void removeRoleFromUser(UUID userId, UUID roleId);
}
```

### Task 12.2 — `JpaUserRoleAssignmentAdapter`

**File:** `src/main/java/io/epsilon/auth/rbac/infrastructure/crossmodule/JpaUserRoleAssignmentAdapter.java`

```java
package io.epsilon.auth.rbac.infrastructure.crossmodule;

import io.epsilon.auth.auth.infrastructure.persistence.UserJpaRepository;
import io.epsilon.auth.rbac.application.port.UserRoleAssignmentPort;
import io.epsilon.auth.rbac.infrastructure.persistence.RoleJpaRepository;
import io.epsilon.auth.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cross-module infrastructure adapter.
 * This is the ONLY class in rbac allowed to import from auth/infrastructure.
 * It is isolated in crossmodule/ to make the coupling explicit and auditable.
 */
@Repository
public class JpaUserRoleAssignmentAdapter implements UserRoleAssignmentPort {

    private final UserJpaRepository userRepo;
    private final RoleJpaRepository roleRepo;

    public JpaUserRoleAssignmentAdapter(UserJpaRepository userRepo,
                                        RoleJpaRepository roleRepo) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
    }

    @Override
    public boolean isRoleAssignedToAnyUser(UUID roleId) {
        return userRepo.existsByRolesId(roleId);
    }

    @Override
    @Transactional
    public void assignRoleToUser(UUID userId, UUID roleId) {
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        var role = roleRepo.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
        user.getRoles().add(role);
        userRepo.save(user);
    }

    @Override
    @Transactional
    public void removeRoleFromUser(UUID userId, UUID roleId) {
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.getRoles().removeIf(r -> r.getId().equals(roleId));
        userRepo.save(user);
    }
}
```

---

## Phase 13 — RBAC Application Layer

### Task 13.1 — `RoleService`

**File:** `src/main/java/io/epsilon/auth/rbac/application/RoleService.java`

```java
package io.epsilon.auth.rbac.application;

import io.epsilon.auth.rbac.application.port.UserRoleAssignmentPort;
import io.epsilon.auth.rbac.event.PermissionAssignedEvent;
import io.epsilon.auth.rbac.event.RoleCreatedEvent;
import io.epsilon.auth.rbac.event.RoleDeletedEvent;
import io.epsilon.auth.rbac.event.RoleUpdatedEvent;
import io.epsilon.auth.rbac.infrastructure.persistence.PermissionJpaRepository;
import io.epsilon.auth.rbac.infrastructure.persistence.RoleEntity;
import io.epsilon.auth.rbac.infrastructure.persistence.RoleJpaRepository;
import io.epsilon.auth.rbac.web.dto.request.CreateRoleRequest;
import io.epsilon.auth.rbac.web.dto.request.UpdateRoleRequest;
import io.epsilon.auth.rbac.web.dto.response.RoleResponse;
import io.epsilon.auth.shared.exception.ResourceNotFoundException;
import io.epsilon.auth.shared.exception.RoleInUseException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class RoleService {

    private final RoleJpaRepository roleRepo;
    private final PermissionJpaRepository permRepo;
    private final UserRoleAssignmentPort userRolePort; // FIX: no more UserJpaRepository
    private final ApplicationEventPublisher eventPublisher;

    public RoleService(RoleJpaRepository roleRepo,
                       PermissionJpaRepository permRepo,
                       UserRoleAssignmentPort userRolePort,
                       ApplicationEventPublisher eventPublisher) {
        this.roleRepo = roleRepo;
        this.permRepo = permRepo;
        this.userRolePort = userRolePort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Page<RoleResponse> list(Pageable pageable) {
        return roleRepo.findAll(pageable).map(RoleResponse::from);
    }

    @Transactional(readOnly = true)
    public RoleResponse getById(UUID id) {
        return RoleResponse.from(findOrThrow(id));
    }

    public RoleResponse create(CreateRoleRequest req) {
        if (roleRepo.existsByName(req.name())) {
            throw new IllegalArgumentException("Role already exists: " + req.name());
        }
        RoleEntity saved = roleRepo.save(RoleEntity.of(req.name(), req.description(), Set.of()));
        eventPublisher.publishEvent(new RoleCreatedEvent(saved.getId(), saved.getName()));
        return RoleResponse.from(saved);
    }

    public RoleResponse update(UUID id, UpdateRoleRequest req) {
        RoleEntity role = findOrThrow(id);
        role.setDescription(req.description());
        RoleEntity saved = roleRepo.save(role);
        eventPublisher.publishEvent(new RoleUpdatedEvent(saved.getId(), saved.getName()));
        return RoleResponse.from(saved);
    }

    public void delete(UUID id) {
        if (userRolePort.isRoleAssignedToAnyUser(id)) {
            throw new RoleInUseException("Role assigned to users — reassign before deleting");
        }
        roleRepo.deleteById(id);
        eventPublisher.publishEvent(new RoleDeletedEvent(id));
    }

    public RoleResponse assignPermission(UUID roleId, UUID permId) {
        RoleEntity role = findOrThrow(roleId);
        var perm = permRepo.findById(permId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permId));
        role.getPermissions().add(perm);
        RoleEntity saved = roleRepo.save(role);
        eventPublisher.publishEvent(new PermissionAssignedEvent(roleId, permId));
        return RoleResponse.from(saved);
    }

    public RoleResponse removePermission(UUID roleId, UUID permId) {
        RoleEntity role = findOrThrow(roleId);
        role.getPermissions().removeIf(p -> p.getId().equals(permId));
        return RoleResponse.from(roleRepo.save(role));
    }

    private RoleEntity findOrThrow(UUID id) {
        return roleRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
    }
}
```

### Task 13.2 — `PermissionService`

**File:** `src/main/java/io/epsilon/auth/rbac/application/PermissionService.java`

```java
package io.epsilon.auth.rbac.application;

import io.epsilon.auth.rbac.domain.PermissionName;
import io.epsilon.auth.rbac.infrastructure.persistence.PermissionEntity;
import io.epsilon.auth.rbac.infrastructure.persistence.PermissionJpaRepository;
import io.epsilon.auth.rbac.web.dto.request.CreatePermissionRequest;
import io.epsilon.auth.rbac.web.dto.response.PermissionResponse;
import io.epsilon.auth.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class PermissionService {

    private final PermissionJpaRepository permRepo;

    public PermissionService(PermissionJpaRepository permRepo) {
        this.permRepo = permRepo;
    }

    @Transactional(readOnly = true)
    public Page<PermissionResponse> list(Pageable pageable) {
        return permRepo.findAll(pageable).map(PermissionResponse::from);
    }

    @Transactional(readOnly = true)
    public PermissionResponse getById(UUID id) {
        return permRepo.findById(id)
                .map(PermissionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));
    }

    public PermissionResponse create(CreatePermissionRequest req) {
        // PermissionName validates format — throws InvalidPermissionNameException if invalid
        PermissionName name = PermissionName.of(req.name());

        if (permRepo.existsByName(name.value())) {
            throw new IllegalArgumentException("Permission already exists: " + name.value());
        }
        return PermissionResponse.from(
                permRepo.save(PermissionEntity.of(name.value(), req.description())));
    }

    public void delete(UUID id) {
        if (!permRepo.existsById(id)) {
            throw new ResourceNotFoundException("Permission not found: " + id);
        }
        permRepo.deleteById(id);
    }
}
```

### Task 13.3 — `UserRoleService` (NEW — was missing)

**File:** `src/main/java/io/epsilon/auth/rbac/application/UserRoleService.java`

```java
package io.epsilon.auth.rbac.application;

import io.epsilon.auth.rbac.application.port.UserRoleAssignmentPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service for user-role assignment operations.
 * Was missing from v2.0 — UserRoleController had no service to call.
 */
@Service
@Transactional
public class UserRoleService {

    private final UserRoleAssignmentPort userRolePort;

    public UserRoleService(UserRoleAssignmentPort userRolePort) {
        this.userRolePort = userRolePort;
    }

    public void assignRole(UUID userId, UUID roleId) {
        userRolePort.assignRoleToUser(userId, roleId);
    }

    public void removeRole(UUID userId, UUID roleId) {
        userRolePort.removeRoleFromUser(userId, roleId);
    }
}
```

---

## Phase 14 — RBAC Web Layer

### Task 14.1 — RBAC DTOs

**File:** `src/main/java/io/epsilon/auth/rbac/web/dto/request/CreateRoleRequest.java`

```java
package io.epsilon.auth.rbac.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
) {}
```

**File:** `src/main/java/io/epsilon/auth/rbac/web/dto/request/UpdateRoleRequest.java`

```java
package io.epsilon.auth.rbac.web.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(@Size(max = 500) String description) {}
```

**File:** `src/main/java/io/epsilon/auth/rbac/web/dto/request/CreatePermissionRequest.java`

```java
package io.epsilon.auth.rbac.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePermissionRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
) {}
```

**File:** `src/main/java/io/epsilon/auth/rbac/web/dto/response/RoleResponse.java`

```java
package io.epsilon.auth.rbac.web.dto.response;

import io.epsilon.auth.rbac.infrastructure.persistence.RoleEntity;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        Set<String> permissions
) {
    public static RoleResponse from(RoleEntity entity) {
        return new RoleResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPermissions().stream()
                        .map(p -> p.getName())
                        .collect(Collectors.toUnmodifiableSet())
        );
    }
}
```

**File:** `src/main/java/io/epsilon/auth/rbac/web/dto/response/PermissionResponse.java`

```java
package io.epsilon.auth.rbac.web.dto.response;

import io.epsilon.auth.rbac.infrastructure.persistence.PermissionEntity;

import java.util.UUID;

public record PermissionResponse(UUID id, String name, String description) {
    public static PermissionResponse from(PermissionEntity entity) {
        return new PermissionResponse(entity.getId(), entity.getName(), entity.getDescription());
    }
}
```

### Task 14.2 — `RoleController`

**File:** `src/main/java/io/epsilon/auth/rbac/web/RoleController.java`

```java
package io.epsilon.auth.rbac.web;

import io.epsilon.auth.rbac.application.RoleService;
import io.epsilon.auth.rbac.web.dto.request.CreateRoleRequest;
import io.epsilon.auth.rbac.web.dto.request.UpdateRoleRequest;
import io.epsilon.auth.rbac.web.dto.response.RoleResponse;
import io.epsilon.auth.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
@Tag(name = "RBAC — Roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'role:read')")
    public ApiResponse<Page<RoleResponse>> list(Pageable pageable) {
        return ApiResponse.success(roleService.list(pageable));
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
        return ApiResponse.success(roleService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'role:update')")
    public ApiResponse<RoleResponse> update(@PathVariable UUID id,
                                             @RequestBody @Valid UpdateRoleRequest req) {
        return ApiResponse.success(roleService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission(null, 'role:delete')")
    public void delete(@PathVariable UUID id) {
        roleService.delete(id);
    }

    @PostMapping("/{roleId}/permissions/{permId}")
    @PreAuthorize("hasPermission(null, 'role:update')")
    public ApiResponse<RoleResponse> assignPermission(@PathVariable UUID roleId,
                                                       @PathVariable UUID permId) {
        return ApiResponse.success(roleService.assignPermission(roleId, permId));
    }

    @DeleteMapping("/{roleId}/permissions/{permId}")
    @PreAuthorize("hasPermission(null, 'role:update')")
    public ApiResponse<RoleResponse> removePermission(@PathVariable UUID roleId,
                                                       @PathVariable UUID permId) {
        return ApiResponse.success(roleService.removePermission(roleId, permId));
    }
}
```

### Task 14.3 — `PermissionController`

**File:** `src/main/java/io/epsilon/auth/rbac/web/PermissionController.java`

```java
package io.epsilon.auth.rbac.web;

import io.epsilon.auth.rbac.application.PermissionService;
import io.epsilon.auth.rbac.web.dto.request.CreatePermissionRequest;
import io.epsilon.auth.rbac.web.dto.response.PermissionResponse;
import io.epsilon.auth.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/permissions")
@Tag(name = "RBAC — Permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'permission:read')")
    public ApiResponse<Page<PermissionResponse>> list(Pageable pageable) {
        return ApiResponse.success(permissionService.list(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'permission:read')")
    public ApiResponse<PermissionResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(permissionService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission(null, 'permission:create')")
    public ApiResponse<PermissionResponse> create(@RequestBody @Valid CreatePermissionRequest req) {
        return ApiResponse.success(permissionService.create(req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission(null, 'permission:delete')")
    public void delete(@PathVariable UUID id) {
        permissionService.delete(id);
    }
}
```

### Task 14.4 — `UserRoleController`

**File:** `src/main/java/io/epsilon/auth/rbac/web/UserRoleController.java`

```java
package io.epsilon.auth.rbac.web;

import io.epsilon.auth.rbac.application.UserRoleService;
import io.epsilon.auth.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@Tag(name = "RBAC — User Roles")
public class UserRoleController {

    private final UserRoleService userRoleService;

    public UserRoleController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @PostMapping("/{userId}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission(null, 'role:update')")
    public void assignRole(@PathVariable UUID userId, @PathVariable UUID roleId) {
        userRoleService.assignRole(userId, roleId);
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasPermission(null, 'role:update')")
    public void removeRole(@PathVariable UUID userId, @PathVariable UUID roleId) {
        userRoleService.removeRole(userId, roleId);
    }
}
```

---

## Phase 15 — RBAC Seeder

### Task 15.1 — `RbacDataSeeder`

**File:** `src/main/java/io/epsilon/auth/rbac/infrastructure/seeding/RbacDataSeeder.java`

```java
package io.epsilon.auth.rbac.infrastructure.seeding;

import io.epsilon.auth.auth.infrastructure.persistence.UserEntity;
import io.epsilon.auth.auth.infrastructure.persistence.UserJpaRepository;
import io.epsilon.auth.rbac.infrastructure.persistence.PermissionEntity;
import io.epsilon.auth.rbac.infrastructure.persistence.PermissionJpaRepository;
import io.epsilon.auth.rbac.infrastructure.persistence.RoleEntity;
import io.epsilon.auth.rbac.infrastructure.persistence.RoleJpaRepository;
import io.epsilon.auth.shared.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Component — NOT @Service. This is a startup infrastructure task, not a business service.
 * Implements ApplicationRunner so it runs after the application context is fully loaded.
 * All seeding is idempotent — safe to run on every startup.
 */
@Component
public class RbacDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RbacDataSeeder.class);

    private final RoleJpaRepository roleRepo;
    private final PermissionJpaRepository permRepo;
    private final UserJpaRepository userRepo;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public RbacDataSeeder(RoleJpaRepository roleRepo,
                          PermissionJpaRepository permRepo,
                          UserJpaRepository userRepo,
                          PasswordEncoder encoder,
                          AppProperties props) {
        this.roleRepo = roleRepo;
        this.permRepo = permRepo;
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.getSeeding().isEnabled()) {
            log.info("RBAC seeding disabled (app.seeding.enabled=false)");
            return;
        }
        seedPermissions(); // Must run before seedRoles
        seedRoles();
        seedDeveloperUser();
        log.info("RBAC seeding complete");
    }

    private void seedPermissions() {
        List.of(
                "all:all",
                "role:create", "role:read", "role:update", "role:delete",
                "permission:create", "permission:read", "permission:update", "permission:delete"
        ).forEach(name -> {
            if (!permRepo.existsByName(name)) {
                permRepo.save(PermissionEntity.of(name));
                log.debug("Seeded permission: {}", name);
            }
        });
    }

    private void seedRoles() {
        seedRole("ROLE_USER", Set.of());
        seedRole("ROLE_SYSTEM_ADMIN", Set.of(
                "role:create", "role:read", "role:update", "role:delete",
                "permission:create", "permission:read", "permission:update", "permission:delete"
        ));
        seedRole("ROLE_DEVELOPER", Set.of("all:all"));
    }

    private void seedRole(String roleName, Set<String> permissionNames) {
        if (roleRepo.existsByName(roleName)) return;

        Set<PermissionEntity> permissions = permissionNames.stream()
                .map(name -> permRepo.findByName(name)
                        .orElseThrow(() -> new IllegalStateException(
                                "Permission not seeded: " + name)))
                .collect(Collectors.toSet());

        roleRepo.save(RoleEntity.of(roleName, null, permissions));
        log.debug("Seeded role: {}", roleName);
    }

    private void seedDeveloperUser() {
        String email = props.getSeeding().getDeveloperEmail();
        if (email == null || userRepo.existsByEmail(email)) return;

        RoleEntity devRole = roleRepo.findByName("ROLE_DEVELOPER")
                .orElseThrow(() -> new IllegalStateException("ROLE_DEVELOPER not seeded"));

        var user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(props.getSeeding().getDeveloperInitialPassword()));
        user.setRoles(Set.of(devRole));
        userRepo.save(user);
        log.warn("Seeded developer user: {} — CHANGE THIS PASSWORD BEFORE PRODUCTION", email);
    }
}
```

---

## Phase 16 — Audit Module

### Task 16.1 — `AuditableEvent` Marker Interface

**File:** `src/main/java/io/epsilon/auth/audit/application/AuditableEvent.java`

```java
package io.epsilon.auth.audit.application;

/**
 * Marker interface. All domain events that should produce an audit log entry
 * implement this interface.
 *
 * Note: In this codebase, AuditEventListener handles each event type directly.
 * This marker exists for future use: e.g. filtering in a generic Kafka bridge.
 */
public interface AuditableEvent {}
```

### Task 16.2 — `AuditEventListener`

**File:** `src/main/java/io/epsilon/auth/audit/application/AuditEventListener.java`

```java
package io.epsilon.auth.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.epsilon.auth.audit.infrastructure.AuditLogEntity;
import io.epsilon.auth.audit.infrastructure.AuditLogJpaRepository;
import io.epsilon.auth.auth.event.UserLoggedInEvent;
import io.epsilon.auth.auth.event.UserLoggedOutEvent;
import io.epsilon.auth.auth.event.UserRegisteredEvent;
import io.epsilon.auth.rbac.event.PermissionAssignedEvent;
import io.epsilon.auth.rbac.event.RoleCreatedEvent;
import io.epsilon.auth.rbac.event.RoleDeletedEvent;
import io.epsilon.auth.rbac.event.RoleUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens to events from auth/ and rbac/ modules.
 * Runs @Async on virtual threads (configured in AsyncConfig).
 * Uses ObjectMapper for metadata — never string concatenation (injection-safe).
 *
 * Event flow: auth/event/* → this listener ← rbac/event/*
 * audit/ depends on auth/event and rbac/event. Never the reverse.
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditLogJpaRepository auditRepo;
    private final ObjectMapper objectMapper;

    public AuditEventListener(AuditLogJpaRepository auditRepo, ObjectMapper objectMapper) {
        this.auditRepo = auditRepo;
        this.objectMapper = objectMapper;
    }

    @EventListener
    @Async
    public void on(UserRegisteredEvent event) {
        save(AuditLogEntity.of(
                AuditLogEntity.EventType.REGISTER,
                event.userId(), null, null,
                toJson(Map.of("email", event.email()))
        ));
    }

    @EventListener
    @Async
    public void on(UserLoggedInEvent event) {
        save(AuditLogEntity.of(
                AuditLogEntity.EventType.LOGIN,
                event.userId(), null, null, null
        ));
    }

    @EventListener
    @Async
    public void on(UserLoggedOutEvent event) {
        save(AuditLogEntity.of(
                AuditLogEntity.EventType.LOGOUT,
                null, null, null, null
        ));
    }

    @EventListener
    @Async
    public void on(RoleCreatedEvent event) {
        save(AuditLogEntity.of(
                AuditLogEntity.EventType.ROLE_CREATED,
                null, event.roleId(), null,
                toJson(Map.of("roleName", event.roleName()))
        ));
    }

    @EventListener
    @Async
    public void on(RoleUpdatedEvent event) {
        save(AuditLogEntity.of(
                AuditLogEntity.EventType.ROLE_UPDATED,
                null, event.roleId(), null,
                toJson(Map.of("roleName", event.roleName()))
        ));
    }

    @EventListener
    @Async
    public void on(RoleDeletedEvent event) {
        save(AuditLogEntity.of(
                AuditLogEntity.EventType.ROLE_DELETED,
                null, event.roleId(), null, null
        ));
    }

    @EventListener
    @Async
    public void on(PermissionAssignedEvent event) {
        save(AuditLogEntity.of(
                AuditLogEntity.EventType.PERMISSION_ASSIGNED,
                null, event.roleId(), null,
                toJson(Map.of("permissionId", event.permissionId().toString()))
        ));
    }

    private void save(AuditLogEntity entity) {
        try {
            auditRepo.save(entity);
        } catch (Exception ex) {
            // Audit failures must never break the business operation
            log.error("Failed to persist audit log entry: {}", ex.getMessage(), ex);
        }
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize audit metadata: {}", ex.getMessage());
            return "{}";
        }
    }
}
```

---

## Phase 17 — Test Infrastructure

### Task 17.1 — `BaseIntegrationTest`

**File:** `src/test/java/io/epsilon/auth/integration/BaseIntegrationTest.java`

```java
package io.epsilon.auth.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
        registry.add("app.jwt.secret",
                () -> "dGVzdC1zZWNyZXQtdGhhdC1pcy1hdC1sZWFzdC0zMi1ieXRlcw==");
        registry.add("app.seeding.enabled", () -> "true");
    }
}
```

### Task 17.2 — Test Strategy Reference

| Test Class | Layer | What It Tests | Key Assertion |
|---|---|---|---|
| `PermissionNameTest` | `rbac/domain` | `PermissionName` value object | Valid/invalid format, null safety |
| `JwtServiceTest` | `auth/infra/security` | Token issue, parse, expiry, tampered signature | Claims round-trip, expired exception |
| `UserPrincipalTest` | `auth/infra/security` | `fromClaims` and `fromEntity` | Authorities set correctly, no DB access |
| `RdbmsTokenBlacklistTest` | `auth/infra/token` | `add` + `isBlacklisted` | Expired entry not returned |
| `TokenBlacklistPrunerTest` | `auth/infra/token` | Conditional activation, prune call | `@ConditionalOnProperty` correct |
| `LoginUseCaseTest` | `auth/application` | Login flow, bad credentials | Mock `AuthenticationManager`, event published |
| `RegisterUseCaseTest` | `auth/application` | Duplicate email guard, event published | `EmailAlreadyExistsException` thrown |
| `LogoutUseCaseTest` | `auth/application` | Blacklist call, invalid token idempotent | No exception on bad token |
| `RefreshTokensUseCaseTest` | `auth/application` | Rotation, replay attack, expiry | `AuthException` on revoked token |
| `RoleServiceTest` | `rbac/application` | CRUD, delete-in-use via port mock | `RoleInUseException`, events published |
| `PermissionServiceTest` | `rbac/application` | `PermissionName` validation delegation | `InvalidPermissionNameException` |
| `UserRoleServiceTest` | `rbac/application` | Assign/remove role delegates to port | Port methods called once |
| `RbacDataSeederTest` | `rbac/infra/seeding` | Idempotent — no duplicate on double run | All 3 roles exist after 2 runs |
| `AuthControllerTest` | `auth/web` | `@WebMvcTest` — HTTP status codes | 201/200/400/401/403/404/409 per endpoint |
| `RoleControllerTest` | `rbac/web` | Same HTTP coverage | Permission gate enforced |
| `AuthFlowIntegrationTest` | `integration` | Register → Login → /me → Logout → token rejected | Full flow, token blacklisted |
| `RbacFlowIntegrationTest` | `integration` | Login as admin → create role → assign permission → verify | Role visible, permission enforced |
| `TokenBlacklistIntegrationTest` | `integration` | Blacklist persistence, replay attack detection | Token rejected after logout |

---

## Phase 18 — `logback-spring.xml` (Structured Logging)

**File:** `src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProfile name="prod">
        <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>requestId</includeMdcKeyName>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="STDOUT"/>
        </root>
    </springProfile>

    <springProfile name="!prod">
        <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] [%X{requestId}] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="DEBUG">
            <appender-ref ref="STDOUT"/>
        </root>
    </springProfile>
</configuration>
```

---

## Completion Checklist

| Phase | Description | Status |
|---|---|---|
| 0 | Maven Setup (Spring Boot 4.0.6, Java 25, pom.xml, YAML) | ☐ |
| 1 | Shared Layer (config, exceptions, web, security) | ☐ |
| 2 | Database Schema (Liquibase — all 8 changesets) | ☐ |
| 3 | JPA Entities (UserEntity, RoleEntity, PermissionEntity, RefreshTokenEntity, etc.) | ☐ |
| 4 | JPA Repositories (all 5 repositories) | ☐ |
| 5 | Token Blacklist (interface + RDBMS + Redis + pruner) | ☐ |
| 6 | Auth Security Infrastructure (JwtService, UserPrincipal, UserDetailsServiceImpl) | ☐ |
| 7 | Auth Port Interfaces and Adapters (UserPort, RefreshTokenPort, adapters) | ☐ |
| 8 | Auth Domain Events (auth/event/) | ☐ |
| 9 | Auth Application Layer (5 use cases) | ☐ |
| 10 | Auth Web Layer (DTOs, AuthController) | ☐ |
| 11 | RBAC Domain (PermissionName value object, rbac/event/) | ☐ |
| 12 | RBAC Port Interfaces and Adapters (UserRoleAssignmentPort, cross-module adapter) | ☐ |
| 13 | RBAC Application Layer (RoleService, PermissionService, UserRoleService) | ☐ |
| 14 | RBAC Web Layer (DTOs, RoleController, PermissionController, UserRoleController) | ☐ |
| 15 | RBAC Seeder (RbacDataSeeder — idempotent, all roles + permissions + dev user) | ☐ |
| 16 | Audit Module (AuditableEvent, AuditEventListener with ObjectMapper, AuditLogEntity) | ☐ |
| 17 | Test Infrastructure (BaseIntegrationTest + full test suite) | ☐ |
| 18 | Logging Configuration (logback-spring.xml) | ☐ |
