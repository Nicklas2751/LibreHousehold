package eu.wiegandt.librehousehold.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatchers;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.List;

/**
 * Wires the RATE1 rate-limiting rules (Arc42 Chapter 8): one username+IP-keyed rule for
 * {@code POST /login}, and one IP-keyed rule shared by the password-reset/verification endpoints,
 * which are partly unauthenticated and therefore have no meaningful "username" to key on - an
 * IP-only limit still meaningfully slows down an attacker cycling through many email addresses.
 */
@Configuration
@EnableConfigurationProperties({LoginRateLimitProperties.class, SensitiveEndpointsRateLimitProperties.class})
class RateLimitingConfig {

    @Bean
    OncePerRequestFilter rateLimitingFilter(
            LoginRateLimitProperties loginProperties,
            SensitiveEndpointsRateLimitProperties sensitiveEndpointsProperties,
            @Value("${openapi.libreHousehold.base-path:/v1}") String basePath) {
        var loginRule = new RateLimitRule(
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/login"),
                new Bucket4jRateLimiter(loginProperties.maxAttempts(), loginProperties.windowDuration()),
                RateLimitingConfig::loginKey);
        var sensitiveEndpointsRule = new RateLimitRule(
                sensitiveEndpointsMatcher(basePath),
                new Bucket4jRateLimiter(sensitiveEndpointsProperties.maxAttempts(), sensitiveEndpointsProperties.windowDuration()),
                HttpServletRequest::getRemoteAddr);
        return new RateLimitingFilter(List.of(loginRule, sensitiveEndpointsRule));
    }

    private static RequestMatcher sensitiveEndpointsMatcher(String basePath) {
        return RequestMatchers.anyOf(
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath + "/password-reset/request"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath + "/password-reset/confirm"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath + "/members/verification/confirm"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath + "/household/{householdId}/members/{memberId}/verification/resend"));
    }

    private static String loginKey(HttpServletRequest request) {
        return request.getParameter("username") + "|" + request.getRemoteAddr();
    }
}
