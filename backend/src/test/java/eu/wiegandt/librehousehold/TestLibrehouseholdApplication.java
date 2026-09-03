package eu.wiegandt.librehousehold;

import org.springframework.boot.SpringApplication;

import java.util.Arrays;
import java.util.stream.Stream;

public class TestLibrehouseholdApplication {

    // The frontend dev server (npm run dev) runs on a different origin than this backend, unlike
    // the production Nginx setup where both are same-origin (see application.yaml). The Vite dev
    // proxy (vite.config.ts) forwards /api, /login, /logout and /oauth2 to this backend, so the
    // browser-facing authorization-uri (and the redirect-uri it round-trips through) must point at
    // this origin instead of the backend's own. issuer itself stays at the application.yaml default
    // (http://localhost:8080): it is only ever used for this backend's own server-to-server calls
    // (token/jwks/userinfo, see SecurityConfig#clientRegistrationRepository) — routing those through
    // the dev proxy as well made the backend proxy a request back to itself and hung indefinitely.
    private static final String FRONTEND_DEV_ORIGIN = "http://localhost:5173";

    static void main(String[] args) {
        var localDevDefaults = Stream.of(
                "--librehousehold.security.oauth2-client.client-secret=test-client-secret",
                "--librehousehold.security.cors.allowed-origins=" + FRONTEND_DEV_ORIGIN,
                "--librehousehold.security.oauth2-client.authorization-uri=" + FRONTEND_DEV_ORIGIN
                        + "/oauth2/authorize",
                "--librehousehold.security.oauth2-client.redirect-uri=" + FRONTEND_DEV_ORIGIN
                        + "/login/oauth2/code/spa-backend-client")
                // Spring Boot Devtools restarts re-invoke main() with the previous call's (already
                // defaulted) args, so appending unconditionally would duplicate each --key=value on
                // every restart; SimpleCommandLinePropertySource then joins duplicates with a comma,
                // corrupting single-value properties like these URLs.
                .filter(defaultArg -> Arrays.stream(args).noneMatch(arg -> arg.startsWith(defaultArg.split("=")[0] + "=")));
        var argsWithLocalDevDefaults = Stream.concat(Arrays.stream(args), localDevDefaults).toArray(String[]::new);
        SpringApplication.from(LibrehouseholdApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(argsWithLocalDevDefaults);
    }

}
