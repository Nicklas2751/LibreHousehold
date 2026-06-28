package eu.wiegandt.librehousehold.core;

import java.util.UUID;

/**
 * Named Interface for author-based authorization.
 * Implemented by modules that expose resources protected by author-level access control.
 * Each implementation answers: "does the given account own this resource?"
 */
public interface ResourceOwnerQuery {

    boolean isOwner(UUID resourceId, UUID accountId);
}
