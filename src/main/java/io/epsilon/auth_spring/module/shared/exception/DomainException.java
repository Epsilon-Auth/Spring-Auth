package io.epsilon.auth_spring.module.shared.exception;
/**
 * Abstract root for all domain exceptions.
 * NOT sealed — each module extends this with its own sealed hierarchy.
 * GlobalExceptionHandler registers separate @ExceptionHandler methods
 * for each module's root type, using pattern-matching switch for status mapping.
 */
public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) { super(message); }
    protected DomainException(String message, Throwable cause) { super(message, cause); }
}
