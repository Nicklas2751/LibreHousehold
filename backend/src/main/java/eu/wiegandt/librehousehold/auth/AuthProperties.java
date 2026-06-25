package eu.wiegandt.librehousehold.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "librehousehold.auth")
record AuthProperties(
        String clientId,
        String redirectUri
) {}
