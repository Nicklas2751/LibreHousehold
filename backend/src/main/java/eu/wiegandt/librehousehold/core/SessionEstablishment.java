package eu.wiegandt.librehousehold.core;

/**
 * Named Interface for establishing an authenticated HTTP session for a local account,
 * for use by other modules right after they created a local account via {@link AccountRegistration}.
 * Implemented by the auth module.
 */
public interface SessionEstablishment {

    /**
     * Establishes an authenticated HTTP session for the account with the given email, as if the
     * user had just completed a form login.
     */
    void establishSession(String email);
}
