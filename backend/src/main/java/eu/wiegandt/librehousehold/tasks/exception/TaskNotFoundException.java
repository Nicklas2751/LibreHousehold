package eu.wiegandt.librehousehold.tasks.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class TaskNotFoundException extends ErrorResponseException {

    public TaskNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
