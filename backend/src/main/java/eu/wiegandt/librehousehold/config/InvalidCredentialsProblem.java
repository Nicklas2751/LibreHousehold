package eu.wiegandt.librehousehold.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

/**
 * A failed form login with wrong or unknown credentials. Deliberately does not tell which of the two
 * was wrong (ENUM1 in Arc42 Chapter 8).
 */
class InvalidCredentialsProblem extends ErrorResponseException {

    InvalidCredentialsProblem() {
        super(HttpStatus.UNAUTHORIZED);
        setType(URI.create("/problems/invalid-credentials"));
        setDetail("Incorrect email or password.");
    }
}
