package eu.wiegandt.librehousehold.household;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reads the {@code librehousehold.security.local-login.*} namespace: whether login, setup and
 * invite join with e-mail and password are offered. {@code enabled} defaults to {@code true} via
 * {@link DefaultValue}, so local login stays available unless a self-hoster explicitly turns it off.
 * <p>
 * {@code GET /auth/discover} tells the client which login methods to render. Turned off, the
 * server also rejects the form login ({@code POST /login}, see {@code LocalLoginDisabledFilter}) and
 * setup or invite join with a password; registering via social login stays possible.
 * <p>
 * Part of the module's public API: the household services enforce the switch, while {@code config}
 * and {@code session} only read it, and {@code household} must not depend on them.
 */
@ConfigurationProperties(prefix = "librehousehold.security.local-login")
public record LocalLoginProperties(@DefaultValue("true") boolean enabled) {
}
