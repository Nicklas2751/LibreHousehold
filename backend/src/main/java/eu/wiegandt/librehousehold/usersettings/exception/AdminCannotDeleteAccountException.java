package eu.wiegandt.librehousehold.usersettings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class AdminCannotDeleteAccountException extends ErrorResponseException {

    public AdminCannotDeleteAccountException() {
        super(HttpStatus.CONFLICT);
    }
}
