/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

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
 *         return ExceptionType.SYNTAX_ERROR;
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
 * The following snippet shows a typical implementation of an exit exception.
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
 *     boolean isExceptionUnwind() {
 *         return true;
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
 * The following snippet shows a typical implementation of a cancel exception.
 *
 * <pre>
 * &#64;ExportLibrary(InteropLibrary.class)
 * final class MyLanguageCancelException extends AbstractTruffleException {
 *
 *     MyLanguageCancelException(String message, Node location) {
 *         super(message, location);
 *         this.unwind = unwind;
 *     }
 *
 *     &#64;ExportMessage
 *     boolean isExceptionUnwind() {
 *         return true;
 *     }
 *
 *     &#64;ExportMessage
 *     ExceptionType getExceptionType() {
 *         return ExceptionType.CANCEL;
 *     }
 * }
 * </pre>
 *
 * @since 20.3
 */
@SuppressWarnings({"serial", "deprecation"})
public abstract class AbstractTruffleException extends RuntimeException implements TruffleObject, com.oracle.truffle.api.TruffleException {

    private final int stackTraceElementLimit;
    private final Throwable internalCause;
    private final Node location;
    private volatile Throwable lazyStackTrace;

    protected AbstractTruffleException() {
        this(null, null, -1, null);
    }

    protected AbstractTruffleException(Node location) {
        this(null, null, -1, location);
    }

    protected AbstractTruffleException(String message) {
        this(message, null, -1, null);
    }

    protected AbstractTruffleException(String message, Node location) {
        this(message, null, -1, location);
    }

    protected AbstractTruffleException(String message, Throwable internalCause, int stackTraceElementLimit, Node location) {
        super(message, checkCause(internalCause));
        this.stackTraceElementLimit = stackTraceElementLimit;
        this.internalCause = internalCause;
        this.location = location;
    }

    @Override
    @SuppressWarnings("sync-override")
    public final Throwable fillInStackTrace() {
        return this;
    }

    /**
     * Returns a node indicating the location where this exception occurred in the AST. This method
     * may return <code>null</code> to indicate that the location is not available.
     *
     */
    @Override
    public final Node getLocation() {
        return location;
    }

    /**
     * Returns a location where this exception occurred in the AST. This method may return
     * <code>null</code> to indicate that the location is not available.
     *
     * @return the {@link SourceSection} or {@code null} Deprecated, use the
     *         {@link InteropLibrary#getSourceLocation(Object)}.
     */
    @Deprecated
    @Override
    public final SourceSection getSourceLocation() {
        InteropLibrary interop = InteropLibrary.getUncached();
        if (interop.hasSourceLocation(this)) {
            try {
                return interop.getSourceLocation(this);
            } catch (UnsupportedMessageException um) {
                throw CompilerDirectives.shouldNotReachHere(um);
            }
        } else {
            return null;
        }
    }

    /**
     * Returns the number of guest language frames that should be collected for this exception.
     * Returns a negative integer by default for unlimited guest language frames. This is intended
     * to be used by guest languages to limit the number of guest language stack frames. Languages
     * might want to limit the number of frames for performance reasons. Frames that point to
     * {@link RootNode#isInternal() internal} internal root nodes are not counted when the stack
     * trace limit is computed.
     *
     */
    @Override
    public final int getStackTraceElementLimit() {
        return stackTraceElementLimit;
    }

    /**
     * Returns an additional guest language object. The return object must be an interop exception
     * type, the {@link @link com.oracle.truffle.api.interop.InteropLibrary#isException(Object)} has
     * to return {@code true}. The default implementation returns <code>null</code> to indicate that
     * no object is available for this exception.
     */
    @Deprecated
    @Override
    public final Object getExceptionObject() {
        return this;
    }

    /**
     * Returns <code>true</code> if this exception indicates a parser or syntax error. Syntax errors
     * typically occur while
     * {@link TruffleLanguage#parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest) parsing}
     * of guest language source code.
     *
     */
    @Deprecated
    @Override
    public final boolean isSyntaxError() {
        return getExceptionType() == ExceptionType.PARSE_ERROR;
    }

    /**
     * Returns <code>true</code> if this exception indicates a syntax error that is indicating that
     * the syntax is incomplete. This allows guest language programmers to find out if more code is
     * expected from a given source. For example an incomplete JavaScript program could look like
     * this:
     *
     * <pre>
     * function incompleteFunction(arg) {
     * </pre>
     *
     * A shell might react to this exception and prompt for additional source code, if this method
     * returns <code>true</code>.
     *
     */
    @Deprecated
    @Override
    public final boolean isIncompleteSource() {
        try {
            return InteropLibrary.getUncached().isExceptionIncompleteSource(this);
        } catch (UnsupportedMessageException um) {
            throw CompilerDirectives.shouldNotReachHere(um);
        }
    }

    /**
     * Returns <code>true</code> if this exception indicates an internal error. Note that all
     * exceptions thrown in a guest language implementation that are not implementing
     * {@link AbstractTruffleException} are considered internal.
     *
     * @since 0.27
     */
    @Deprecated
    @Override
    public final boolean isInternalError() {
        return false;
    }

    /**
     * Returns <code>true</code> if this exception indicates that guest language application was
     * cancelled during its execution. If {@code isCancelled} returns {@code true} languages should
     * not catch this exception, they must just rethrow it.
     *
     */
    @Deprecated
    @Override
    public final boolean isCancelled() {
        return getExceptionType() == ExceptionType.CANCEL;
    }

    /**
     * Returns <code>true</code> if the exception indicates that the application was exited within
     * the guest language program. If {@link #isExit()} returns <code>true</code> also
     * {@link #getExitStatus()} should be implemented.
     *
     * @see #getExitStatus()
     */
    @Deprecated
    @Override
    public final boolean isExit() {
        return getExceptionType() == ExceptionType.EXIT;
    }

    /**
     * Returns the exit status if this exception indicates that the application was {@link #isExit()
     * exited}. The exit status is intended to be passed to {@link System#exit(int)}.
     *
     * @see #isExit()
     */
    @Deprecated
    @Override
    public final int getExitStatus() {
        try {
            return InteropLibrary.getUncached().getExceptionExitStatus(this);
        } catch (UnsupportedMessageException um) {
            throw CompilerDirectives.shouldNotReachHere(um);
        }
    }

    /**
     * Setting a cause is not supported. Pass in the cause using the constructors instead.
     */
    @Deprecated
    @TruffleBoundary
    @Override
    @SuppressWarnings("sync-override")
    public final Throwable initCause(Throwable cause) {
        throw new UnsupportedOperationException("Not supported. Pass in the cause using the constructors instead.");
    }

    /**
     * Returns the cause of an exception.
     *
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("sync-override")
    public final Throwable getCause() {
        return internalCause;
    }

    Throwable getLazyStackTrace() {
        Throwable res = lazyStackTrace;
        if (res == null) {
            synchronized (this) {
                res = lazyStackTrace;
                if (res == null) {
                    res = ExceptionAccessor.LANGUAGE.createLazyStackTrace();
                    lazyStackTrace = res;
                }
            }
        }
        return res;
    }

    private ExceptionType getExceptionType() {
        try {
            return InteropLibrary.getUncached().getExceptionType(this);
        } catch (UnsupportedMessageException um) {
            throw CompilerDirectives.shouldNotReachHere(um);
        }
    }

    @SuppressWarnings("deprecation")
    private static Throwable checkCause(Throwable t) {
        if (t != null && !(t instanceof com.oracle.truffle.api.TruffleException)) {
            throw new IllegalArgumentException("The " + t + " must be TruffleException subclass.");
        }
        return t;
    }
}
