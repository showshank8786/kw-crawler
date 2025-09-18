package org.kwcrawler;


public class ParseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
