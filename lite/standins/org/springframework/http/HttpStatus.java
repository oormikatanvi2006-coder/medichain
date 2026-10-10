package org.springframework.http;

public enum HttpStatus implements HttpStatusCode {
    NOT_FOUND(404), BAD_REQUEST(400), CONFLICT(409), UNAUTHORIZED(401), FORBIDDEN(403);

    private final int code;

    HttpStatus(int code) {
        this.code = code;
    }

    public int value() {
        return code;
    }
}
