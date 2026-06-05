package eu.wiegandt.librehousehold.tasks;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class TaskNotFoundException extends ErrorResponseException {

    TaskNotFoundException() {
        super(HttpStatus.NOT_FOUND);
    }
}
