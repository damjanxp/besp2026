package com.bsep.pki.exception;

public class CsrException extends RuntimeException {
    public CsrException(String message) {
        super(message);
    }

    public CsrException(String message, Throwable cause) {
        super(message, cause);
    }
}

