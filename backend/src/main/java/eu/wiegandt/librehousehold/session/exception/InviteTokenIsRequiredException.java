package eu.wiegandt.librehousehold.session.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class InviteTokenIsRequiredException extends ErrorResponseException {

    public InviteTokenIsRequiredException() {
        super(HttpStatus.BAD_REQUEST);
        this.setDetail("An invite token is required for the INVITE context!");
    }
}
