package io.epsilon.auth_spring.module.shared.exception;

/** 404 — used by all modules for missing entities. */
public final class ResourceNotFoundException extends DomainException {
    public ResourceNotFoundException(String message) { super(message); }
}

