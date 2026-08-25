package eu.wiegandt.librehousehold;

import org.springframework.boot.SpringApplication;

import java.util.Arrays;
import java.util.stream.Stream;

public class TestLibrehouseholdApplication {

    static void main(String[] args) {
        var localDevDefaults = Stream.of(
                "--librehousehold.security.oauth2-client.client-secret=test-client-secret",
                // The frontend dev server (npm run dev) runs on a different origin than this backend,
                // unlike the production Nginx setup where both are same-origin (see application.yaml).
                "--librehousehold.security.cors.allowed-origins=http://localhost:5173");
        var argsWithLocalDevDefaults = Stream.concat(Arrays.stream(args), localDevDefaults).toArray(String[]::new);
        SpringApplication.from(LibrehouseholdApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(argsWithLocalDevDefaults);
    }

}
