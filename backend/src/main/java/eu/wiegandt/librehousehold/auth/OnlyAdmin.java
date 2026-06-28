package eu.wiegandt.librehousehold.auth;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts access to household admins.
 * The annotated method must have a {@code householdId} parameter.
 * Combines household scope check with {@code role == "admin"} JWT claim check.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("@householdScopeChecker.isCurrentUserInHousehold(#householdId) and @adminChecker.isAdmin()")
public @interface OnlyAdmin {
}
