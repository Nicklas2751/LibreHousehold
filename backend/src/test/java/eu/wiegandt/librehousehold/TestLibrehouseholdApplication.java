package eu.wiegandt.librehousehold;

import org.springframework.boot.SpringApplication;

public class TestLibrehouseholdApplication {

	public static void main(String[] args) {
		SpringApplication.from(LibrehouseholdApplication::main)
				.with(TestcontainersConfiguration.class)
				.withAdditionalProfiles("local")
				.run(args);
	}

}
