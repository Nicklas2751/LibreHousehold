package eu.wiegandt.librehousehold.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatchers;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;
import java.util.Objects;

/**
 * Wires the RATE1 rate-limiting rules (Arc42 Chapter 8): a username+IP-keyed and an IP-keyed rule
 * for {@code POST /login}, and one IP-keyed rule shared by the password-reset/verification
 * endpoints, which are partly unauthenticated and therefore have no meaningful "username" to key
 * on. The IP-keyed rules slow down an attacker cycling through many email addresses, which the
 * username+IP rule alone never throttles.
 */
@Configuration
@EnableConfigurationProperties({LoginRateLimitProperties.class, LoginPerIpRateLimitProperties.class,
        SensitiveEndpointsRateLimitProperties.class})
class RateLimitingConfig {

    private static final String LOGIN_USERNAME_PARAMETER = "username";
    // RFC 5321 caps an e-mail address at 254 characters; capping the key keeps attacker-chosen
    // parameter lengths from inflating the in-memory bucket map
    private static final int MAX_EMAIL_LENGTH = 254;

    // Servlet-only, like the filter chain that uses it: the MVC handlerExceptionResolver bean only
    // exists in a servlet web application. The resolver is @Lazy because Boot creates Filter beans
    // while starting the web server, before DynamicPropertyRegistrars run; resolving it eagerly
    // would build MVC and security beans (e.g. the client registrations) from incomplete properties.
    @Bean
    @ConditionalOnWebApplication(type = Type.SERVLET)
    OncePerRequestFilter rateLimitingFilter(
            LoginRateLimitProperties loginProperties,
            LoginPerIpRateLimitProperties loginPerIpProperties,
            SensitiveEndpointsRateLimitProperties sensitiveEndpointsProperties,
            @Value("${openapi.libreHousehold.base-path:/v1}") String basePath,
            @Lazy @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        var loginMatcher = PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/login");
        var loginRule = new RateLimitRule(
                loginMatcher,
                new Bucket4jRateLimiter(loginProperties.maxAttempts(), loginProperties.windowDuration()),
                RateLimitingConfig::loginKey);
        var loginPerIpRule = new RateLimitRule(
                loginMatcher,
                new Bucket4jRateLimiter(loginPerIpProperties.maxAttempts(), loginPerIpProperties.windowDuration()),
                HttpServletRequest::getRemoteAddr);
        var sensitiveEndpointsRule = new RateLimitRule(
                sensitiveEndpointsMatcher(basePath),
                new Bucket4jRateLimiter(sensitiveEndpointsProperties.maxAttempts(), sensitiveEndpointsProperties.windowDuration()),
                HttpServletRequest::getRemoteAddr);
        return new RateLimitingFilter(List.of(loginRule, loginPerIpRule, sensitiveEndpointsRule), handlerExceptionResolver);
    }

    private static RequestMatcher sensitiveEndpointsMatcher(String basePath) {
        return RequestMatchers.anyOf(
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath + "/password-reset/request"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath + "/password-reset/confirm"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath + "/members/verification/confirm"),
                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, basePath + "/household/{householdId}/members/{memberId}/verification/resend"));
    }

    static String loginKey(HttpServletRequest request) {
        var email = Objects.requireNonNullElse(request.getParameter(LOGIN_USERNAME_PARAMETER), "");
        return email.substring(0, Math.min(email.length(), MAX_EMAIL_LENGTH)) + "|" + request.getRemoteAddr();
    }
}
