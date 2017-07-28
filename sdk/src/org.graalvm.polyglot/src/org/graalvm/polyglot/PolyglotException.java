/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.polyglot;

import java.io.PrintStream;
import java.io.PrintWriter;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractExceptionImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractStackFrameImpl;

/**
 * An exception caused by executing Graal guest languages. Can originate from a guest or from the
 * host language.
 *
 * @since 1.0
 */
@SuppressWarnings("serial")
public final class PolyglotException extends RuntimeException {

    final AbstractExceptionImpl impl;

    PolyglotException(String message, AbstractExceptionImpl impl) {
        super(message);
        this.impl = impl;
        impl.onCreate(this);
    }

    /**
     * Prints host and guest language stack frames to the standard {@link System#err error output}.
     *
     * @since 1.0
     */
    @Override
    public void printStackTrace() {
        impl.printStackTrace(System.err);
    }

    /**
     * Prints host and guest language stack frames to specified print stream.
     *
     * @since 1.0
     */

    @Override
    public void printStackTrace(PrintStream s) {
        impl.printStackTrace(s);
    }

    /**
     * Prints host and guest language stack frames to specified print writer.
     *
     * @since 1.0
     */
    @Override
    public void printStackTrace(PrintWriter s) {
        impl.printStackTrace(s);
    }

    /**
     * Unsupported, {@link PolyglotException} instances are not writable therefore filling the stack
     * trace has no effect for them.
     *
     * @since 1.0
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        // nothing to do
        return this;
    }

    /**
     * Gets stack trace elements for Java and guest languages. For polyglot exceptions it
     * recommended to use {@link #getPolyglotStackTrace()} as the guest language stack elements do
     * not always fit the Java format for stack trace elements.
     *
     * @since 1.0
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
     * @since 1.0
     */
    @Override
    public String getMessage() {
        return impl.getMessage();
    }

    /**
     * Gets a guest language source location of this error or <code>null</code> if no source
     * location is available for this exception.
     *
     * @since 1.0
     */
    public SourceSection getSourceLocation() {
        return impl.getSourceLocation();
    }

    /**
     * Unsupported, {@link PolyglotException} instances are not writable therefore setting the stack
     * trace has no effect for them.
     *
     * @since 1.0
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
     * @since 1.0
     */
    public Iterable<StackFrame> getPolyglotStackTrace() {
        return impl.getPolyglotStackTrace();
    }

    /**
     * Returns <code>true</code> if this exception originates from the Java host language. In such a
     * case the first {@link #getPolyglotStackTrace() stack frame} returns a
     * {@link StackFrame#isHostFrame() host frame} as zeroth element.
     *
     * @since 1.0
     */
    public boolean isHostException() {
        return impl.isHostException();
    }

    /**
     * Returns <code>true</code> if this exception originates from a Graal guest language. In such a
     * case the first {@link #getPolyglotStackTrace() stack frame} returns a
     * {@link StackFrame#isGuestFrame() guest frame} as zeroth element.
     *
     * @since 1.0
     */
    public boolean isGuestException() {
        return !impl.isHostException();
    }

    /**
     * Returns the original Java host exception that caused this exception.
     *
     * @throws UnsupportedOperationException if this exception is not a host exception. Call
     *             {@link #isHostException()} to ensure its originating from the host language.
     * @since 1.0
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
     * @since 1.0
     */
    public boolean isInternalError() {
        return impl.isInternalError();
    }

    /**
     * Returns <code>true</code> if the execution was cancelled. The execution can be cancelled by
     * {@link Context#close(boolean) closing} a context or if an instrument such as a debugger
     * decides to cancel the current execution. The context that caused a cancel event becomes
     * unusable, i.e. closed.
     *
     * @since 1.0
     */
    public boolean isCancelled() {
        return impl.isCancelled();
    }

    /**
     * Returns <code>true</code> if this exception is caused by an attempt of a guest language
     * program to exit the application using a builtin command. The provided exit code can be
     * accessed using {@link #getExitStatus()}.
     *
     * @since 1.0
     */
    public boolean isExit() {
        return impl.isExit();
    }

    /**
     * Returns <code>true</code> if this exception indicates a parser or syntax error. In such a
     * case #get
     *
     * @since 1.0
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
     * @since 1.0
     */
    public boolean isIncompleteSource() {
        return impl.isIncompleteSource();
    }

    /**
     * Returns an additional guest language object. The value is never <code>null</code> and returns
     * a Value object where {@link Value#isNull()} returns as true if it is not available.
     *
     * @since 1.0
     */
    public Value getGuestObject() {
        return impl.getGuestObject();
    }

    /**
     * Returns the exit status if this exception indicates that the application was {@link #isExit()
     * exited}. The exit status is intended to be passed to {@link System#exit(int)}.
     *
     * @see #isExit()
     * @since 1.0
     */
    public int getExitStatus() {
        return impl.getExitStatus();
    }

    /**
     * Represents a polyglot stack frame originating from a guest language or the host language
     * Java.
     *
     * @since 1.0
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
         * @since 1.0
         */
        public boolean isHostFrame() {
            return impl.isHostFrame();
        }

        /**
         * Returns true if the stack frame originates from the guest language.
         *
         * @since 1.0
         */
        public boolean isGuestFrame() {
            return !impl.isHostFrame();
        }

        /**
         * Returns a Java stack trace element representation of the polyglot stack trace element.
         * This is supported for host stack frames as well as guest language stack frames. A
         * conversion to the host frame format can be useful for interoperability.
         *
         * @since 1.0
         */
        public StackTraceElement toHostFrame() {
            return impl.toHostFrame();
        }

        /**
         * Returns the source location of the stack frame or <code>null</code> if no source location
         * is available. Host frames do never provide a source location.
         *
         * @since 1.0
         */
        public SourceSection getSourceLocation() {
            return impl.getSourceLocation();
        }

        /**
         * Returns the root name of this stack frame. In case of the host language the Java method
         * name is returned. In guest languages it returns a useful identifier for code. For
         * example, in JavaScript this can be the function name.
         *
         * @since 1.0
         */
        public String getRootName() {
            return impl.getRootName();
        }

        /**
         * Returns the language of this stack frame. In case of the host language a synthetic Java
         * language object is returned.
         *
         * @since 1.0
         */
        public Language getLanguage() {
            return impl.getLanguage();
        }

        /**
         * Returns a string representation of this stack frame. The format is inspired by the Java
         * stack frame format.
         *
         * @since 1.0
         */
        @Override
        public String toString() {
            return impl.toStringImpl(0);
        }
    }
}
