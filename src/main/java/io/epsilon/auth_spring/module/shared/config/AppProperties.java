package io.epsilon.auth_spring.module.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app")
@Validated
@Getter @Setter
public class AppProperties {

    @Valid @NotNull private Jwt jwt = new Jwt();
    @Valid @NotNull private Cookie cookie = new Cookie();
    @Valid @NotNull private Blacklist blacklist = new Blacklist();
    @Valid @NotNull private Seeding seeding = new Seeding();
    @Valid @NotNull private Cors cors = new Cors();

    @Getter @Setter
    public static class Jwt {
        @NotBlank private String secret;
        @Min(60)  private long accessTokenTtlSeconds = 900;
        @Min(3600) private long refreshTokenTtlSeconds = 604800;
    }

    @Getter @Setter
    public static class Cookie {
        private boolean enabled = true;
        @NotBlank private String name = "accessToken";
        private boolean httpOnly = true;
        private boolean secure = true;
        @NotBlank private String sameSite = "Strict";
        @NotBlank private String path = "/";
    }

    @Getter @Setter
    public static class Blacklist {
        @NotBlank private String strategy = "rdbms";
        @NotBlank private String cleanupCron = "0 0 * * * *";
    }

    @Getter @Setter
    public static class Seeding {
        private boolean enabled = true;
        private String developerEmail;
        private String developerInitialPassword;
    }

    @Getter @Setter
    public static class Cors {
        @NotBlank private String allowedOrigins = "http://localhost:3000";
        @NotBlank private String allowedMethods = "GET,POST,PUT,DELETE,OPTIONS";
        @Min(0)   private long maxAgeSeconds = 3600;
    }
}