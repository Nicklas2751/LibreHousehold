package eu.wiegandt.librehousehold.tasks.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class TaskBodyIsRequiredException extends ErrorResponseException {

    public TaskBodyIsRequiredException() {
        super(HttpStatus.BAD_REQUEST);
    }
}
