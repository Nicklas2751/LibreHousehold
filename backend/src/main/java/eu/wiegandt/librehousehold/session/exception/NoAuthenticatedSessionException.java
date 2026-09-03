package eu.wiegandt.librehousehold.session.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class NoAuthenticatedSessionException extends ErrorResponseException {

    public NoAuthenticatedSessionException() {
        super(HttpStatus.UNAUTHORIZED);
    }
}
