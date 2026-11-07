package eu.wiegandt.librehousehold;

import org.springframework.boot.SpringApplication;

import java.util.Arrays;
import java.util.stream.Stream;

public class TestLibrehouseholdApplication {

    // The frontend dev server (npm run dev) runs on a different origin than this backend, unlike
    // the production Nginx setup where both are same-origin (see application.yaml). CORS and the
    // links in e-mails must therefore point at the dev server origin.
    private static final String FRONTEND_DEV_ORIGIN = "http://localhost:5173";

    static void main(String[] args) {
        var localDevDefaults = Stream.of(
                "--librehousehold.security.cors.allowed-origins=" + FRONTEND_DEV_ORIGIN,
                "--librehousehold.frontend.base-url=" + FRONTEND_DEV_ORIGIN)
                // Spring Boot Devtools restarts re-invoke main() with the previous call's (already
                // defaulted) args, so appending unconditionally would duplicate each --key=value on
                // every restart; SimpleCommandLinePropertySource then joins duplicates with a comma,
                // corrupting single-value properties like these URLs.
                .filter(defaultArg -> Arrays.stream(args).noneMatch(arg -> arg.startsWith(defaultArg.split("=")[0] + "=")));
        var argsWithLocalDevDefaults = Stream.concat(Arrays.stream(args), localDevDefaults).toArray(String[]::new);
        SpringApplication.from(LibrehouseholdApplication::main)
                .with(TestcontainersConfiguration.class, DexTestcontainersConfiguration.class,
                        LgtmTestcontainersConfiguration.class)
                .run(argsWithLocalDevDefaults);
    }

}
