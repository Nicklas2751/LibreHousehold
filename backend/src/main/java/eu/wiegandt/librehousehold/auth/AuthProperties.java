package eu.wiegandt.librehousehold.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "librehousehold.auth")
record AuthProperties(
        String clientId,
        String redirectUri,
        Duration accessTokenTimeToLive,
        Duration refreshTokenTimeToLive
) {}
