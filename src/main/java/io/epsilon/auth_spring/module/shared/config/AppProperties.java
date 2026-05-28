package io.epsilon.auth_spring.module.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Valid @NotNull
    private Jwt jwt = new Jwt();

    @Valid @NotNull
    private Cookie cookie = new Cookie();

    @Valid @NotNull
    private Blacklist blacklist = new Blacklist();

    @Valid @NotNull
    private Seeding seeding = new Seeding();

    @Valid @NotNull
    private Cors cors = new Cors();

    @Valid @NotNull
    private Security security = new Security();

    // =========================================================
    // JWT
    // =========================================================
    @Getter
    @Setter
    public static class Jwt {

        @NotNull
        private String secret;

        private long accessTokenTtlSeconds = 900;

        private long refreshTokenTtlSeconds = 604800;
    }

    // =========================================================
    // COOKIE
    // =========================================================
    @Getter
    @Setter
    public static class Cookie {

        private boolean enabled = true;

        @NotNull
        private String name = "accessToken";

        private boolean httpOnly = true;

        private boolean secure = true;

        @NotNull
        private String sameSite = "Strict";

        @NotNull
        private String path = "/";
    }

    // =========================================================
    // BLACKLIST
    // =========================================================
    @Getter
    @Setter
    public static class Blacklist {

        @NotNull
        private String strategy = "rdbms";

        @NotNull
        private String cleanupCron = "0 0 * * * *";
    }

    // =========================================================
    // SEEDING
    // =========================================================
    @Getter
    @Setter
    public static class Seeding {

        private boolean enabled = true;

        @NotNull
        private String developerEmail;

        @NotNull
        private String developerInitialPassword;
    }

    // =========================================================
    // CORS
    // =========================================================
    @Getter
    @Setter
    public static class Cors {

        @NotNull
        private String allowedOrigins = "http://localhost:3000";

        @NotNull
        private String allowedMethods = "GET,POST,PUT,DELETE,OPTIONS";

        private long maxAgeSeconds = 3600;
    }

    // =========================================================
    // SECURITY
    // =========================================================
    @Getter
    @Setter
    public static class Security {

        private int maxFailedAttempts = 5;

        private int lockoutDurationMinutes = 30;

        private int rateLimitRequestsPerMinute = 10;
    }
}