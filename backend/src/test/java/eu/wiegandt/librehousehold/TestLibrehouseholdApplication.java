package eu.wiegandt.librehousehold;

import org.springframework.boot.SpringApplication;

import java.util.Arrays;
import java.util.stream.Stream;

public class TestLibrehouseholdApplication {

    static void main(String[] args) {
        var argsWithClientSecret = Stream
                .concat(Arrays.stream(args),
                        Stream.of("--librehousehold.security.oauth2-client.client-secret=test-client-secret"))
                .toArray(String[]::new);
        SpringApplication.from(LibrehouseholdApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(argsWithClientSecret);
    }

}
