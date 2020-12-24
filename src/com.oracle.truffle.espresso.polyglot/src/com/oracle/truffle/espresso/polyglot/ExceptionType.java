package com.oracle.truffle.espresso.polyglot;


/**
 * Represents a type of a Truffle exception.
 *
 * @see Interop#isException(Object)
 * @see Interop#getExceptionType(Object)
 * @since 20.3
 */
public enum ExceptionType {
    /**
     * Indicates that the application was exited within the guest language program. To obtain the
     * exit status use {@link Interop#getExceptionExitStatus(Object) getExceptionExitStatus}.
     *
     * @see Interop#getExceptionExitStatus(Object)
     * @since 20.3
     */
    EXIT,

    /**
     * Indicates that the application thread was interrupted by an {@link InterruptedException}.
     *
     * @since 20.3
     */
    INTERRUPT,

    /**
     * Indicates a guest language error.
     *
     * @since 20.3
     */
    RUNTIME_ERROR,

    /**
     * Indicates a parser or syntax error. Syntax errors typically occur while
     * parsing of guest language source code. Use
     * {@link Interop#isExceptionIncompleteSource(Object) isExceptionIncompleteSource} to
     * find out if the parse error happened due to incomplete source.
     *
     * @see Interop#isExceptionIncompleteSource(Object)
     * @since 20.3
     */
    PARSE_ERROR
}

