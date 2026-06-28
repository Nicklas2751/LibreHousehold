package eu.wiegandt.librehousehold.auth;

import java.util.UUID;

/**
 * Named Interface for author-based authorization.
 * Implemented by modules that expose resources protected by {@link OnlyAuthor}.
 * Each implementation answers: "does the given account own this resource?"
 */
public interface ResourceOwnerQuery {

    boolean isOwner(UUID resourceId, UUID accountId);
}
