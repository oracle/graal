/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Information about an exception thrown from a guest language. This exception does not contain a
 * Java stack trace, use {@link #getDebugStackTrace()} to find out the guest language stack trace.
 * <p>
 * Debugger methods that run guest language code may throw this exception.
 *
 * @since 1.0
 */
public final class DebugException extends RuntimeException {

    private static final long serialVersionUID = 5017970176581546348L;
    private static final String CAUSE_CAPTION = "Caused by: ";

    private final Debugger debugger;
    private final Throwable exception; // the exception, or null when only a message is given
    private final LanguageInfo preferredLanguage; // the preferred language, or null
    private final Node throwLocation;         // node which intercepted the exception, or null
    private volatile boolean isCatchNodeComputed; // the catch node is computed lazily, can be given
    private volatile CatchLocation catchLocation; // the catch location, or null
    private SuspendedEvent suspendedEvent;    // the SuspendedEvent when from breakpoint, or null
    private List<DebugStackTraceElement> debugStackTrace;
    private StackTraceElement[] javaLikeStackTrace;

    DebugException(Debugger debugger, String message, Node throwLocation, boolean isCatchNodeComputed, CatchLocation catchLocation) {
        super(message);
        this.debugger = debugger;
        this.exception = null;
        this.preferredLanguage = null;
        this.throwLocation = throwLocation;
        this.isCatchNodeComputed = isCatchNodeComputed;
        this.catchLocation = catchLocation;
        // we need to materialize the stack for the case that this exception is printed
        super.setStackTrace(getStackTrace());
    }

    DebugException(Debugger debugger, Throwable exception, LanguageInfo preferredLanguage, Node throwLocation, boolean isCatchNodeComputed, CatchLocation catchLocation) {
        super(exception.getLocalizedMessage());
        this.debugger = debugger;
        this.exception = exception;
        this.preferredLanguage = preferredLanguage;
        this.throwLocation = throwLocation;
        this.isCatchNodeComputed = isCatchNodeComputed;
        this.catchLocation = catchLocation;
        // we need to materialize the stack for the case that this exception is printed
        super.setStackTrace(getStackTrace());
    }

    void setSuspendedEvent(SuspendedEvent suspendedEvent) {
        if (catchLocation != null) {
            catchLocation.setSuspendedEvent(suspendedEvent);
        }
        this.suspendedEvent = suspendedEvent;
    }

    Throwable getRawException() {
        return exception;
    }

    /**
     * Unsupported, {@link DebugException} instances are not writable therefore filling the stack
     * trace has no effect for them.
     *
     * @since 1.0
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    /**
     * Unsupported, {@link DebugException} instances are not writable therefore setting the stack
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
     * Gets stack trace elements of guest languages. It is recommended to use
     * {@link #getDebugStackTrace()} as the guest language stack elements do not always fit the Java
     * format for stack trace elements.
     *
     * @since 1.0
     */
    @Override
    public StackTraceElement[] getStackTrace() {
        if (javaLikeStackTrace == null) {
            List<DebugStackTraceElement> debugStack = getDebugStackTrace();
            int size = debugStack.size();
            javaLikeStackTrace = new StackTraceElement[size];
            for (int i = 0; i < size; i++) {
                javaLikeStackTrace[i] = debugStack.get(i).toTraceElement();
            }
        }
        return javaLikeStackTrace.clone();
    }

    /**
     * Gets stack trace elements of guest languages.
     *
     * @since 1.0
     */
    public List<DebugStackTraceElement> getDebugStackTrace() {
        if (debugStackTrace == null) {
            if (exception != null) {
                TruffleStackTraceElement.fillIn(exception);
                List<TruffleStackTraceElement> stackTrace = TruffleStackTraceElement.getStackTrace(exception);
                int n = stackTrace.size();
                List<DebugStackTraceElement> debugStack = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    TruffleStackTraceElement tframe = stackTrace.get(i);
                    RootNode root = tframe.getTarget().getRootNode();
                    if (root.getLanguageInfo() != null) {
                        debugStack.add(new DebugStackTraceElement(debugger, tframe));
                    }
                }
                debugStackTrace = Collections.unmodifiableList(debugStack);
            } else {
                debugStackTrace = Collections.emptyList();
            }
        }
        return debugStackTrace;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public void printStackTrace() {
        printStackTrace(new PrintStream(debugger.getEnv().err()));
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public void printStackTrace(PrintStream s) {
        super.printStackTrace(s);
        if (!(exception instanceof TruffleException)) {
            s.print(CAUSE_CAPTION);
            exception.printStackTrace(s);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public void printStackTrace(PrintWriter s) {
        super.printStackTrace(s);
        if (!(exception instanceof TruffleException)) {
            s.print(CAUSE_CAPTION);
            exception.printStackTrace(s);
        }
    }

    /**
     * Returns <code>true</code> if this exception indicates an internal error.
     *
     * @since 1.0
     */
    public boolean isInternalError() {
        return exception != null && (!(exception instanceof TruffleException) || ((TruffleException) exception).isInternalError());
    }

    /**
     * Get a guest language object representing the exception, if any.
     *
     * @return an exception object, or <code>null</code>
     * @since 1.0
     */
    public DebugValue getExceptionObject() {
        if (!(exception instanceof TruffleException)) {
            return null;
        }
        Object obj = ((TruffleException) exception).getExceptionObject();
        if (obj == null) {
            return null;
        }
        LanguageInfo language = preferredLanguage;
        if (language == null && throwLocation != null) {
            RootNode throwRoot = throwLocation.getRootNode();
            if (throwRoot != null) {
                language = throwRoot.getLanguageInfo();
            }
        }
        return new DebugValue.HeapValue(debugger, language, null, obj);
    }

    /**
     * Get source code location where this exception was thrown from.
     *
     * @return the thrown location, or <code>null</code> when the thrown location is not known.
     * @since 1.0
     */
    public SourceSection getThrowLocation() {
        if (exception instanceof TruffleException) {
            SourceSection location = ((TruffleException) exception).getSourceLocation();
            if (location != null) {
                return location;
            }
        }
        if (throwLocation != null) {
            return throwLocation.getSourceSection();
        }
        return null;
    }

    /**
     * Get source code location where this exception is to be caught. In case this exception is
     * going to be caught by guest language code, the catch location is provided. <code>null</code>
     * is returned for uncaught exceptions.
     *
     * @return the catch location, or <code>null</code> in case of uncaught exceptions.
     * @since 1.0
     */
    public CatchLocation getCatchLocation() {
        if (!isCatchNodeComputed) {
            synchronized (this) {
                if (!isCatchNodeComputed) {
                    if (exception instanceof TruffleException) {
                        catchLocation = BreakpointExceptionFilter.getCatchNode(debugger, throwLocation, exception);
                        if (catchLocation != null) {
                            catchLocation.setSuspendedEvent(suspendedEvent);
                        }
                    }
                    isCatchNodeComputed = true;
                }
            }
        }
        return catchLocation;
    }

    /**
     * Represents an exception catch location. It provides a stack frame and a source section where
     * the exception is going to be caught.
     *
     * @since 1.0
     */
    public static final class CatchLocation {

        private final SourceSection section;
        private final FrameInstance frameInstance;
        private final int depth;
        private DebugStackFrame frame;

        CatchLocation(SourceSection section, FrameInstance frameInstance, int depth) {
            this.section = section;
            this.frameInstance = frameInstance;
            this.depth = depth;
        }

        /**
         * @return a source section, or <code>null</code> if the catch source section is not known.
         * @since 1.0
         */
        public SourceSection getSourceSection() {
            return section;
        }

        /**
         * @return a frame in which the exception is going to be caught.
         * @since 1.0
         */
        public DebugStackFrame getFrame() {
            return frame;
        }

        void setSuspendedEvent(SuspendedEvent suspendedEvent) {
            frame = new DebugStackFrame(suspendedEvent, depth == 0 ? null : frameInstance, depth);
        }
    }
}
