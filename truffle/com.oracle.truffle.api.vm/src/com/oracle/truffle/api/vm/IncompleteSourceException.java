package com.oracle.truffle.api.vm;

import java.io.IOException;

/**
 * Indicates that the provided source was incomplete and requires further text to be executed.
 * 
 * @since 0.9
 */
@SuppressWarnings("serial")
public class IncompleteSourceException extends IOException {
    /** @since 0.9 */
    public IncompleteSourceException() {
        super();
    }

    /** @since 0.9 */
    public IncompleteSourceException(Throwable cause) {
        super(cause);
    }

}
