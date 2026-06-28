package eu.wiegandt.librehousehold.auth;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts access to the resource owner (author).
 * The annotated method must have a {@code householdId} parameter and a {@code resourceId} parameter.
 * Ownership is resolved via all registered {@link ResourceOwnerQuery} beans.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("@householdScopeChecker.isCurrentUserInHousehold(#householdId) and @authorChecker.isAuthor(#resourceId)")
public @interface OnlyAuthor {
}
