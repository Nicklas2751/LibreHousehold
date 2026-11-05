package eu.wiegandt.librehousehold.household;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

/**
 * Part of the module's public API, because {@code session} rejects {@code POST /login} with it when
 * this instance turned local login off (see {@link LocalLoginProperties}).
 */
public class LocalLoginDisabledException extends ErrorResponseException {

    public LocalLoginDisabledException() {
        super(HttpStatus.FORBIDDEN);
        this.setType(URI.create("/problems/local-login-disabled"));
        this.setTitle("Local login disabled");
        this.setDetail("This instance only allows logging in and registering via a social login provider.");
    }
}
