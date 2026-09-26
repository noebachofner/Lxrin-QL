package ch.lxrin.ql.it.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * The user of the current request, from the Spring Security context. With Keycloak and
 * {@code spring-boot-starter-oauth2-resource-server}, the authentication name is the
 * {@code sub} claim: the user's UUID.
 */
public final class CurrentUser {

    private CurrentUser() {}

    /** Returns the current user's UUID, or {@code null} without an authenticated user (e.g. a scheduled job). */
    public static UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;
        return UUID.fromString(authentication.getName());
    }
}
