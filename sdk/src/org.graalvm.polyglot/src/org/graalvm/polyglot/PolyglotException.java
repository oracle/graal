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
 * Represents an exception that originates from a guest language.
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

    @Override
    public void printStackTrace() {
        impl.printStackTrace(System.err);
    }

    @Override
    public void printStackTrace(PrintStream s) {
        impl.printStackTrace(s);
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        impl.printStackTrace(s);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // nothing to do
        return this;
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return impl.getStackTrace();
    }

    @Override
    public String getMessage() {
        return impl.getMessage();
    }

    public SourceSection getSourceLocation() {
        return impl.getSourceLocation();
    }

    @Override
    public void setStackTrace(StackTraceElement[] stackTrace) {
        // validate arguments to fullfil contract
        for (int i = 0; i < stackTrace.length; i++) {
            if (stackTrace[i] == null) {
                throw new NullPointerException("stackTrace[" + i + "]");
            }
        }
    }

    public Iterable<StackFrame> getPolyglotStackTrace() {
        return impl.getPolyglotStackTrace();
    }

    public boolean isHostException() {
        return impl.isHostException();
    }

    public boolean isGuestException() {
        return !impl.isHostException();
    }

    public Throwable asHostException() {
        return impl.asHostException();
    }

    /**
     * Returns <code>true</code> if this exception was caused by an internal implementation error.
     * These errors should be reported as bugs if observed.
     *
     * @since 1.0
     */
    public boolean isInternalError() {
        return impl.isInternalError();
    }

    /**
     * Returns <code>true</code> if the execution was cancelled. The execution can be cancelled by
     * {@link Context#close(boolean) closing} a context or if an instrument like a debugger decides
     * to cancel the current execution. The context that caused a cancel event becomes unusable ie.
     * closed.
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
     * @since 1.0
     */
    public boolean isIncompleteSource() {
        return impl.isIncompleteSource();
    }

    /**
     * Returns an additional guest language object. The value is never <code>null</code> and returns
     * a Value object where {@link Value#isNull()} returns true if it is not available.
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
     */
    public static final class StackFrame {

        final AbstractStackFrameImpl impl;

        StackFrame(AbstractStackFrameImpl impl) {
            this.impl = impl;
        }

        public boolean isHostFrame() {
            return impl.isHostFrame();
        }

        public boolean isGuestFrame() {
            return !impl.isHostFrame();
        }

        /**
         * Returns a Java stack trace element representation of the polyglot stack trace element.
         * This is supported for host stack frames as well as guest language stack frames. A
         * convertion to the host frame format can be useful for interoperability.
         *
         * @since 1.0
         */
        public StackTraceElement toHostFrame() {
            return impl.toHostFrame();
        }

        public SourceSection getSourceLocation() {
            return impl.getSourceLocation();
        }

        public String getRootName() {
            return impl.getRootName();
        }

        public Language getLanguage() {
            return impl.getLanguage();
        }

        @Override
        public String toString() {
            return impl.toStringImpl(0);
        }
    }
}
