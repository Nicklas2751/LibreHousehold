package eu.wiegandt.librehousehold.core;

import java.util.UUID;

/**
 * Named Interface for resolving the currently authenticated account's id from the security context.
 * Implemented by the auth module.
 */
public interface CurrentUserIdProvider {

    UUID getCurrentUserId();
}
