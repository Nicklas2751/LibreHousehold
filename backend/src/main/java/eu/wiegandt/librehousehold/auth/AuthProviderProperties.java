package eu.wiegandt.librehousehold.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "librehousehold.auth.providers")
record AuthProviderProperties(boolean local) {}
