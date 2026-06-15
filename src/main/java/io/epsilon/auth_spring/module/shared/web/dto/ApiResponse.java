package io.epsilon.auth_spring.module.shared.web.dto;


import org.slf4j.MDC;
import java.time.Instant;

public record ApiResponse<T>(boolean success, T data, ApiError error,
                            String requestId, Instant timestamp)
{

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, MDC.get("requestId"), Instant.now());
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message),
                MDC.get("requestId"), Instant.now());
    }
}