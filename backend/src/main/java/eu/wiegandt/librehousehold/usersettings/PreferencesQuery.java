package eu.wiegandt.librehousehold.usersettings;

import eu.wiegandt.librehousehold.model.UserPreferences;

import java.util.UUID;

/**
 * Named interface for cross-module synchronous queries about member preferences.
 * Implemented by the usersettings module; consumed by other modules that need
 * preferences data without depending on internal types.
 */
public interface PreferencesQuery {

    /**
     * Returns the preferences of the given member, or an empty {@link UserPreferences}
     * if the member has not set any preferences yet.
     *
     * @param memberId the ID of the member
     * @return the member's preferences, or a default (empty) instance
     */
    UserPreferences getPreferencesOrDefault(UUID memberId);
}
