package org.springframework.web.server;

import org.springframework.http.HttpStatusCode;

public class ResponseStatusException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final HttpStatusCode status;

    public ResponseStatusException(HttpStatusCode status, String reason) {
        super(reason);
        this.status = status;
    }

    public HttpStatusCode getStatusCode() {
        return status;
    }
}
