package eu.wiegandt.librehousehold.core;

import java.util.UUID;

/**
 * Named Interface for creating local (password-based) accounts from other modules,
 * e.g. the household module when a new user joins a household via invite link.
 * Implemented by the auth module.
 */
public interface AccountRegistration {

    /**
     * Creates a local account with the given id. The password must already be encoded
     * (e.g. via {@link org.springframework.security.crypto.password.PasswordEncoder}) — this method
     * persists it as-is. Throws {@link org.springframework.dao.DataIntegrityViolationException} if the
     * email is already registered.
     */
    void registerLocalAccount(UUID accountId, String email, String encodedPassword);
}
