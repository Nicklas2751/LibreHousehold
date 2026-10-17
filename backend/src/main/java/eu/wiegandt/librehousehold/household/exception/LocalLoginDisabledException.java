package eu.wiegandt.librehousehold.household.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

public class LocalLoginDisabledException extends ErrorResponseException {

    public LocalLoginDisabledException() {
        super(HttpStatus.FORBIDDEN);
        this.setType(URI.create("/problems/local-login-disabled"));
        this.setTitle("Local login disabled");
        this.setDetail("This instance only allows registering via a social login provider.");
    }
}
