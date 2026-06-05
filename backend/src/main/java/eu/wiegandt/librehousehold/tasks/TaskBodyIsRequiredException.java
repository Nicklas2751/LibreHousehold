package eu.wiegandt.librehousehold.tasks;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class TaskBodyIsRequiredException extends ErrorResponseException {

    TaskBodyIsRequiredException() {
        super(HttpStatus.BAD_REQUEST);
    }
}
