package eu.wiegandt.librehousehold.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reads the {@code librehousehold.security.local-login.*} namespace: whether login, setup and
 * invite join with e-mail and password are offered. {@code enabled} defaults to {@code true} via
 * {@link DefaultValue}, so local login stays available unless a self-hoster explicitly turns it off.
 * <p>
 * The switch is currently only evaluated by {@code GET /auth/discover}, which tells the client
 * which login methods to render. It does not disable the form login server-side.
 */
@ConfigurationProperties(prefix = "librehousehold.security.local-login")
public record LocalLoginProperties(@DefaultValue("true") boolean enabled) {
}
