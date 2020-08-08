/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import java.io.PrintStream;
import java.io.PrintWriter;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractExceptionImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractStackFrameImpl;

/**
 * A polyglot exception represents errors that contain Graal guest languages on the stack trace. In
 * addition to the Java stack trace it also returns a {@link #getPolyglotStackTrace() polyglot stack
 * trace}. Methods like {@link #printStackTrace()} are implemented such that host and guest language
 * stack traces are printed nicely.
 * <p>
 * A polyglot exception may have the following properties:
 * <ul>
 * <li>{@link #isGuestException() Guest Exception}: Is <code>true</code> if the exception was raised
 * in guest language code.
 * <li>{@link #isHostException() Host Exception}: Is <code>true</code> if this exception was raised
 * in host runtime code. This may happen if the polyglot runtime host runtime methods that throw an
 * exception. The original host exception can be accessed using {@link #asHostException()}.
 * <li>{@link #isCancelled() Cancelled}: Is <code>true</code> if the execution got cancelled. The
 * execution may be cancelled when {@link Context#close() closing} a context, by a guest language
 * intrinsic or by a tool, like the debugger.
 * <li>{@link #isExit() Exit}: Is <code>true</code> if the execution exited. The guest language
 * triggers exit events if the guest language code request to exit the VM. The exit status can be
 * accessed using {@link #getExitStatus()}.
 * <li>{@link #isSyntaxError() Syntax Error}: Is <code>true</code> if the error represents a syntax
 * error. For syntax errors a {@link #getSourceLocation() location} may be available.
 * <li>{@link #isIncompleteSource() Incomplete Source}: Is <code>true</code> if this returns a
 * {@link #isSyntaxError() syntax error} that indicates that the source is incomplete.
 * <li>{@link #isResourceExhausted() Resource exhausted}: Is <code>true</code> if a resource limit
 * e.g. the maximum memory was exhausted.
 * <li>{@link #isInternalError() Internal Error}: Is <code>true</code> if an internal implementation
 * error occurred in the polyglot runtime, the guest language or an instrument. It is not
 * recommended to show such errors to the user in production. Please consider filing issues for
 * internal implementation errors.
 * </ul>
 *
 * @see Context
 * @see Value
 * @since 19.0
 */
@SuppressWarnings("serial")
public final class PolyglotException extends RuntimeException {

    final AbstractExceptionImpl impl;

    PolyglotException(String message, AbstractExceptionImpl impl) {
        super(message);
        this.impl = impl;
        impl.onCreate(this);
        // we need to materialize the stack if this exception is printed as cause of another error.
        // unfortunately we cannot detect this easily
        super.setStackTrace(getStackTrace());
    }

    /**
     * Prints host and guest language stack frames to the standard {@link System#err error output}.
     *
     * @since 19.0
     */
    @Override
    public void printStackTrace() {
        impl.printStackTrace(System.err);
    }

    /**
     * Prints host and guest language stack frames to specified print stream.
     *
     * @since 19.0
     */

    @Override
    public void printStackTrace(PrintStream s) {
        impl.printStackTrace(s);
    }

    /**
     * Prints host and guest language stack frames to specified print writer.
     *
     * @since 19.0
     */
    @Override
    public void printStackTrace(PrintWriter s) {
        impl.printStackTrace(s);
    }

    /**
     * Unsupported, {@link PolyglotException} instances are not writable therefore filling the stack
     * trace has no effect for them.
     *
     * @since 19.0
     */
    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    /**
     * Gets stack trace elements for Java and guest languages. For polyglot exceptions it
     * recommended to use {@link #getPolyglotStackTrace()} as the guest language stack elements do
     * not always fit the Java format for stack trace elements.
     *
     * @since 19.0
     */
    @Override
    public StackTraceElement[] getStackTrace() {
        return impl.getStackTrace();
    }

    /**
     * Gets a user readable message for the polyglot exception. In case the exception is
     * {@link #isInternalError() internal} then the original java class name is included in the
     * message. The message never returns <code>null</code>.
     *
     * @since 19.0
     */
    @Override
    public String getMessage() {
        return impl.getMessage();
    }

    /**
     * Gets a guest language source location of this error or <code>null</code> if no source
     * location is available for this exception.
     *
     * @since 19.0
     */
    public SourceSection getSourceLocation() {
        return impl.getSourceLocation();
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PolyglotException) {
            return impl.equals(((PolyglotException) obj).impl);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public int hashCode() {
        return impl.hashCode();
    }

    /**
     * Unsupported, {@link PolyglotException} instances are not writable therefore setting the stack
     * trace has no effect for them.
     *
     * @since 19.0
     */
    @Override
    public void setStackTrace(StackTraceElement[] stackTrace) {
        // validate arguments to fullfil contract
        for (int i = 0; i < stackTrace.length; i++) {
            if (stackTrace[i] == null) {
                throw new NullPointerException("stackTrace[" + i + "]");
            }
        }
    }

    /**
     * Provides programmatic access to the polyglot stack trace information printed by
     * {@link #printStackTrace()}. Returns an array of stack trace elements, each representing one
     * stack frame. The zeroth element of the array (assuming the array's length is non-zero)
     * represents the top of the stack, which is the last invocation in the sequence. Typically,
     * this is the point at which this throwable was created and thrown. The last element of the
     * array (assuming the array's length is non-zero) represents the bottom of the stack, which is
     * the first method invocation in the sequence.
     *
     * @see StackFrame
     * @since 19.0
     */
    public Iterable<StackFrame> getPolyglotStackTrace() {
        return impl.getPolyglotStackTrace();
    }

    /**
     * Returns <code>true</code> if this exception originates from the Java host language. In such a
     * case the first {@link #getPolyglotStackTrace() stack frame} returns a
     * {@link StackFrame#isHostFrame() host frame} as zeroth element.
     *
     * @since 19.0
     */
    public boolean isHostException() {
        return impl.isHostException();
    }

    /**
     * Returns <code>true</code> if this exception originates from a Graal guest language. In such a
     * case the first {@link #getPolyglotStackTrace() stack frame} returns a
     * {@link StackFrame#isGuestFrame() guest frame} as zeroth element.
     *
     * @since 19.0
     */
    public boolean isGuestException() {
        return !impl.isHostException();
    }

    /**
     * Returns the original Java host exception that caused this exception. The original host
     * exception contains a stack trace that is hardly interpretable by users as it contains details
     * of the language implementation. The polyglot exception provides information for user-friendly
     * error reporting with the {@link #getPolyglotStackTrace() polyglot stack trace}.
     *
     * @throws UnsupportedOperationException if this exception is not a host exception. Call
     *             {@link #isHostException()} to ensure its originating from the host language.
     * @since 19.0
     */
    public Throwable asHostException() {
        return impl.asHostException();
    }

    /**
     * Returns <code>true</code> if this exception was caused by an internal implementation error.
     * These errors should be reported as bugs if observed. Internal error messages are typically
     * hard to understand for guest language programmers and might contain implementation specific
     * details that allows guest language implementers to debug the problem.
     *
     * @since 19.0
     */
    public boolean isInternalError() {
        return impl.isInternalError();
    }

    /**
     * Returns <code>true</code> if this exception indicates that a resource limit was exceeded,
     * else <code>false</code>. Resource limit exceeded errors may be raised for the following
     * reasons:
     * <ul>
     * <li>The host runtime run out of memory or stack space. For example if host runtime throws
     * {@link OutOfMemoryError} or {@link StackOverflowError}, then they will be translated to a
     * {@link PolyglotException} that return <code>true</code> for {@link #isResourceExhausted()}.
     * <li>A configured {@link ResourceLimits resource limit} was exceeded.
     * <li>A runtime specific per context resource limit was exceeded. Depending on the host runtime
     * implementation additional options to restrict the resource usage of a context may be
     * specified using options.
     * </ul>
     * <p>
     * Resource limit exceptions may be originating from the {@link #isHostException() host} or
     * {@link #isGuestException() guest}. Resource limit exceeded errors are never
     * {@link #isInternalError() internal}, but may have caused the context to be
     * {@link #isCancelled() cancelled} such that it is no longer usable.
     *
     * @since 20.2
     */
    public boolean isResourceExhausted() {
        return impl.isResourceExhausted();
    }

    /**
     * Returns <code>true</code> if the execution was cancelled. The execution can be cancelled by
     * {@link Context#close(boolean) closing} a context, if an instrument such as a debugger decides
     * to cancel the current execution or if a {@link ResourceLimits resource limit} was exceeded.
     * The context that caused a cancel event becomes unusable, i.e. closed.
     *
     * @since 19.0
     */
    public boolean isCancelled() {
        return impl.isCancelled();
    }

    /**
     * Returns <code>true</code> if this exception is caused by an attempt of a guest language
     * program to exit the application using a builtin command. The provided exit code can be
     * accessed using {@link #getExitStatus()}.
     *
     * @since 19.0
     */
    public boolean isExit() {
        return impl.isExit();
    }

    /**
     * Returns <code>true</code> if this exception indicates a parser or syntax error. In such a
     * case #get
     *
     * @since 19.0
     */
    public boolean isSyntaxError() {
        return impl.isSyntaxError();
    }

    /**
     * Returns <code>true</code> if this exception indicates a syntax error that is indicating that
     * the syntax is incomplete. This allows guest language programmers to find out if more code is
     * expected from a given source. For example, an incomplete JavaScript program could look like
     * this:
     *
     * <pre>
     * function incompleteFunction(arg) {
     * </pre>
     *
     * A shell might react to this exception and prompt for additional source code, if this method
     * returns <code>true</code>.
     *
     * @since 19.0
     */
    public boolean isIncompleteSource() {
        return impl.isIncompleteSource();
    }

    /**
     * Returns an additional guest language object. Returns <code>null</code> if no exception object
     * is available.
     *
     * @since 19.0
     */
    public Value getGuestObject() {
        return impl.getGuestObject();
    }

    /**
     * Returns the exit status if this exception indicates that the application was {@link #isExit()
     * exited}. The exit status is intended to be passed to {@link System#exit(int)}.
     *
     * @see #isExit()
     * @since 19.0
     */
    public int getExitStatus() {
        return impl.getExitStatus();
    }

    /**
     * Represents a polyglot stack frame originating from a guest language or the host language
     * Java.
     *
     * @since 19.0
     */
    public final class StackFrame {

        final AbstractStackFrameImpl impl;

        StackFrame(AbstractStackFrameImpl impl) {
            this.impl = impl;
        }

        /**
         * Returns true if the stack frame originates from the host language. Host frames do not
         * provide a {@link #getSourceLocation() source location}. Instead the Java stack frame can
         * be accessed using {@link #toHostFrame()}.
         *
         * @since 19.0
         */
        public boolean isHostFrame() {
            return impl.isHostFrame();
        }

        /**
         * Returns true if the stack frame originates from the guest language.
         *
         * @since 19.0
         */
        public boolean isGuestFrame() {
            return !impl.isHostFrame();
        }

        /**
         * Returns a Java stack trace element representation of the polyglot stack trace element.
         * This is supported for host stack frames as well as guest language stack frames. A
         * conversion to the host frame format can be useful for interoperability.
         *
         * @since 19.0
         */
        public StackTraceElement toHostFrame() {
            return impl.toHostFrame();
        }

        /**
         * Returns the source location of the stack frame or <code>null</code> if no source location
         * is available. Host frames do never provide a source location.
         *
         * @since 19.0
         */
        public SourceSection getSourceLocation() {
            return impl.getSourceLocation();
        }

        /**
         * Returns the root name of this stack frame. In case of the host language the Java method
         * name is returned. In guest languages it returns a useful identifier for code. For
         * example, in JavaScript this can be the function name.
         *
         * @since 19.0
         */
        public String getRootName() {
            return impl.getRootName();
        }

        /**
         * Returns the language of this stack frame. In case of the host language a synthetic Java
         * language object is returned.
         *
         * @since 19.0
         */
        public Language getLanguage() {
            return impl.getLanguage();
        }

        /**
         * Returns a string representation of this stack frame. The format is inspired by the Java
         * stack frame format.
         *
         * @since 19.0
         */
        @Override
        public String toString() {
            return impl.toStringImpl(0);
        }
    }
}
