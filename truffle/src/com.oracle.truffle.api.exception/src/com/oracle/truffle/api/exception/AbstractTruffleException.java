/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.exception;

import org.graalvm.polyglot.PolyglotException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * A base class for an exception thrown during the execution of a guest language program.<br>
 * The following snippet shows the guest language exception implementation.
 *
 * <pre>
 * final class MyLanguageException extends AbstractTruffleException {
 *     MyLanguageException(String message, Node location) {
 *         super(message, location);
 *     }
 * }
 * </pre>
 *
 * The following snippet shows a typical implementation of a syntax error exception supporting also
 * incomplete sources.
 *
 * <pre>
 * &#64;ExportLibrary(InteropLibrary.class)
 * final class MyLanguageParseError extends AbstractTruffleException {
 *     private final Source source;
 *     private final int line;
 *     private final int column;
 *     private final int length;
 *     private final boolean incompleteSource;
 *
 *     MyLanguageParseError(Source source, int line, int column, int length, boolean incomplete, String message) {
 *         super(message);
 *         this.source = source;
 *         this.line = line;
 *         this.column = column;
 *         this.length = length;
 *         this.incompleteSource = incomplete;
 *     }
 *
 *     &#64;ExportMessage
 *     ExceptionType getExceptionType() {
 *         return ExceptionType.PARSE_ERROR;
 *     }
 *
 *     &#64;ExportMessage
 *     boolean isExceptionIncompleteSource() {
 *         return incompleteSource;
 *     }
 *
 *     &#64;ExportMessage
 *     boolean hasSourceLocation() {
 *         return source != null;
 *     }
 *
 *     &#64;ExportMessage(name = "getSourceLocation")
 *     SourceSection getSourceSection() throws UnsupportedMessageException {
 *         if (source == null) {
 *             throw UnsupportedMessageException.create();
 *         }
 *         return source.createSection(line, column, length);
 *     }
 * }
 * </pre>
 *
 * The following snippet shows a typical implementation of an interrupt exception.
 *
 * <pre>
 * &#64;ExportLibrary(InteropLibrary.class)
 * final class MyLanguageInterruptException extends AbstractTruffleException {
 *
 *     MyLanguageInterruptException(String message, Node location) {
 *         super(message, location);
 *     }
 *
 *     &#64;ExportMessage
 *     ExceptionType getExceptionType() {
 *         return ExceptionType.INTERRUPT;
 *     }
 * }
 * </pre>
 *
 * The following snippet shows a typical implementation of an soft exit exception.
 *
 * <pre>
 * &#64;ExportLibrary(InteropLibrary.class)
 * final class MyLanguageExitException extends AbstractTruffleException {
 *
 *     private final int exitStatus;
 *
 *     MyLanguageExitException(String message, int exitStatus, Node location) {
 *         super(message, location);
 *         this.exitStatus = exitStatus;
 *     }
 *
 *     &#64;ExportMessage
 *     ExceptionType getExceptionType() {
 *         return ExceptionType.EXIT;
 *     }
 *
 *     &#64;ExportMessage
 *     int getExceptionExitStatus() {
 *         return exitStatus;
 *     }
 * }
 * </pre>
 *
 * @since 20.3
 */
@SuppressWarnings({"serial"})
public abstract class AbstractTruffleException extends RuntimeException implements TruffleObject {

    /**
     * The constant for an unlimited stack trace element limit.
     *
     * @since 20.3
     */
    public static final int UNLIMITED_STACK_TRACE = -1;

    private final int stackTraceElementLimit;
    private final Throwable cause;
    private final Node location;
    private Throwable lazyStackTrace;

    /**
     * Creates a new AbstractTruffleException.
     *
     * @since 20.3
     */
    protected AbstractTruffleException() {
        this(null, null, UNLIMITED_STACK_TRACE, null);
    }

    /**
     * Creates a new AbstractTruffleException with given location.
     *
     * @since 20.3
     */
    protected AbstractTruffleException(Node location) {
        this(null, null, UNLIMITED_STACK_TRACE, location);
    }

    /**
     * Creates a new AbstractTruffleException with given message.
     *
     * @since 20.3
     */
    protected AbstractTruffleException(String message) {
        this(message, null, UNLIMITED_STACK_TRACE, null);
    }

    /**
     * Creates a new AbstractTruffleException with given message and location.
     *
     * @since 20.3
     */
    protected AbstractTruffleException(String message, Node location) {
        this(message, null, UNLIMITED_STACK_TRACE, location);
    }

    /**
     * Creates a new AbstractTruffleException initialized from the given prototype. The exception
     * message, internal cause, stack trace limit, location, suppressed exceptions and Truffle stack
     * trace of a newly created {@link AbstractTruffleException} are inherited from the given
     * {@link AbstractTruffleException}.
     *
     * @since 20.3
     */
    @TruffleBoundary
    protected AbstractTruffleException(AbstractTruffleException prototype) {
        this(prototype.getMessage(), prototype.getCause(), prototype.getStackTraceElementLimit(), prototype.getLocation());
        for (Throwable t : prototype.getSuppressed()) {
            this.addSuppressed(t);
        }
        TruffleStackTrace.fillIn(prototype);
        assert prototype.lazyStackTrace != null : "Prototype must have a stack trace after fillIn.";
        this.lazyStackTrace = prototype.lazyStackTrace;
    }

    /**
     * Creates a new AbstractTruffleException.
     *
     * @param message the exception message or {@code null}
     * @param cause an internal or {@link AbstractTruffleException} causing this exception or
     *            {@code null}. If internal errors are passed as cause, they are not accessible by
     *            other languages or the embedder. In other words,
     *            {@link InteropLibrary#getExceptionCause(Object)} or
     *            {@link PolyglotException#getCause()} will return <code>null</code> for internal
     *            errors.
     * @param stackTraceElementLimit a stack trace limit. Use {@link #UNLIMITED_STACK_TRACE} for
     *            unlimited stack trace length.
     *
     * @since 20.3
     */
    protected AbstractTruffleException(String message, Throwable cause, int stackTraceElementLimit, Node location) {
        super(message, cause);
        this.stackTraceElementLimit = stackTraceElementLimit;
        this.cause = cause;
        this.location = location;
    }

    /**
     * {@inheritDoc}
     *
     * @since 20.3
     */
    @Override
    @SuppressWarnings("sync-override")
    public final Throwable fillInStackTrace() {
        return this;
    }

    /**
     * Returns a node indicating the location where this exception occurred in the AST. This method
     * may return {@code null} to indicate that the location is not available.
     *
     * @since 20.3
     */
    public final Node getLocation() {
        return location;
    }

    /**
     * Returns the number of guest language frames that should be collected for this exception.
     * Returns a negative integer by default for unlimited guest language frames. This is intended
     * to be used by guest languages to limit the number of guest language stack frames. Languages
     * might want to limit the number of frames for performance reasons. Only frames whose
     * {@link RootNode#countsTowardsStackTraceLimit()} method return true count towards the limit.
     *
     * @since 20.3
     */
    public final int getStackTraceElementLimit() {
        return stackTraceElementLimit;
    }

    /**
     * {@inheritDoc}
     *
     * @since 20.3
     */
    @Override
    @SuppressWarnings("sync-override")
    public final Throwable getCause() {
        return cause;
    }

    Throwable getLazyStackTrace() {
        return lazyStackTrace;
    }

    void setLazyStackTrace(Throwable stackTrace) {
        this.lazyStackTrace = stackTrace;
    }
}
