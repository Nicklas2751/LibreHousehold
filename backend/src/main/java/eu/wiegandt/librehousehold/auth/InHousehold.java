package eu.wiegandt.librehousehold.auth;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts access to members of the requested household.
 * The annotated method must have a {@code householdId} parameter.
 * Evaluates the {@code household_id} JWT claim against the path parameter.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("@householdScopeChecker.isCurrentUserInHousehold(#householdId)")
public @interface InHousehold {
}
