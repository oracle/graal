package com.oracle.truffle.api.vm;

import java.io.IOException;

/**
 * Indicates that the provided source was incomplete and requires further text to be executed.
 */
@SuppressWarnings("serial")
public class IncompleteSourceException extends IOException {

    public IncompleteSourceException() {
        super();
    }

    public IncompleteSourceException(Throwable cause) {
        super(cause);
    }

}
