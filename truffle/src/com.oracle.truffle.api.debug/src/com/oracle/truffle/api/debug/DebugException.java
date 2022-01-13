/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.debug.SuspendedEvent.DebugAsyncStackFrameLists;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
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
 * @since 19.0
 */
@SuppressWarnings("serial")
public final class DebugException extends RuntimeException {

    private static final long serialVersionUID = 5017970176581546348L;
    private static final String CAUSE_CAPTION = "Caused by: ";

    private final DebuggerSession session;
    private final Throwable exception; // the exception, or null when only a message is given
    private final LanguageInfo preferredLanguage; // the preferred language, or null
    private final Node throwLocation;         // node which intercepted the exception, or null
    private final StackTraceElement[] rawStackTrace;
    private volatile boolean isCatchNodeComputed; // the catch node is computed lazily, can be given
    private volatile CatchLocation catchLocation; // the catch location, or null
    private SuspendedEvent suspendedEvent;    // the SuspendedEvent when from breakpoint, or null
    private List<DebugStackTraceElement> debugStackTrace;
    private List<List<DebugStackTraceElement>> debugAsyncStacks;
    private StackTraceElement[] javaLikeStackTrace;

    static DebugException create(DebuggerSession session, String message) {
        return new DebugException(session, message, null, null, null, true, null);
    }

    static DebugException create(DebuggerSession session, Throwable exception, LanguageInfo preferredLanguage) {
        return create(session, exception, preferredLanguage, null, true, null);
    }

    static DebugException create(DebuggerSession session, Throwable exception, LanguageInfo preferredLanguage, Node throwLocation, boolean isCatchNodeComputed, CatchLocation catchLocation) {
        return new DebugException(session, getTheMessage(exception), exception, preferredLanguage, throwLocation, isCatchNodeComputed, catchLocation);
    }

    private DebugException(DebuggerSession session, String message, Throwable exception, LanguageInfo preferredLanguage, Node throwLocation, boolean isCatchNodeComputed, CatchLocation catchLocation) {
        super(message);
        StackTraceElement[] exceptionStackTrace = null;
        if (session.isShowHostStackFrames()) {
            if (exception != null) {
                StackTraceElement[] stackTrace = exception.getStackTrace();
                if (stackTrace.length > 0) {
                    exceptionStackTrace = stackTrace;
                }
            }
            if (exceptionStackTrace == null) {
                // We take the stack trace ourselves
                Throwable t = super.fillInStackTrace();
                assert this == t;
            }
        }
        this.rawStackTrace = exceptionStackTrace;
        this.session = session;
        this.exception = exception;
        this.preferredLanguage = preferredLanguage;
        this.throwLocation = throwLocation;
        this.isCatchNodeComputed = isCatchNodeComputed;
        this.catchLocation = catchLocation != null ? catchLocation.cloneFor(session) : null;
        // we need to materialize the stack for the case that this exception is printed
        super.setStackTrace(getStackTrace());
    }

    private static String getTheMessage(Throwable exception) {
        if (isTruffleException(exception)) {
            try {
                return InteropLibrary.getUncached().asString(InteropLibrary.getUncached().getExceptionMessage(exception));
            } catch (UnsupportedMessageException ex) {
                // No interop message
            }
        }
        return exception.getLocalizedMessage();
    }

    void setSuspendedEvent(SuspendedEvent suspendedEvent) {
        assert session == suspendedEvent.getSession();
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
     * @since 19.0
     */
    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    /**
     * Unsupported, {@link DebugException} instances are not writable therefore setting the stack
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

    private StackTraceElement[] getRawStackTrace() {
        if (rawStackTrace != null) {
            return rawStackTrace;
        } else {
            return super.getStackTrace();
        }
    }

    /**
     * Gets stack trace elements of guest languages. It is recommended to use
     * {@link #getDebugStackTrace()} as the guest language stack elements do not always fit the Java
     * format for stack trace elements.
     *
     * @since 19.0
     */
    @Override
    public StackTraceElement[] getStackTrace() {
        if (javaLikeStackTrace == null) {
            if (isInternalError()) {
                return getRawStackTrace();
            } else {
                List<DebugStackTraceElement> debugStack = getDebugStackTrace();
                int size = debugStack.size();
                javaLikeStackTrace = new StackTraceElement[size];
                for (int i = 0; i < size; i++) {
                    javaLikeStackTrace[i] = debugStack.get(i).toTraceElement();
                }
            }
        }
        return javaLikeStackTrace.clone();
    }

    /**
     * Gets stack trace elements of guest languages.
     *
     * @since 19.0
     */
    public List<DebugStackTraceElement> getDebugStackTrace() {
        if (debugStackTrace == null) {
            if (exception != null) {
                List<TruffleStackTraceElement> stackTrace = TruffleStackTrace.getStackTrace(exception);
                int n = stackTrace.size();
                List<DebugStackTraceElement> debugStack = new ArrayList<>(n);
                boolean hostInfo = session.isShowHostStackFrames();
                for (int i = 0; i < n; i++) {
                    TruffleStackTraceElement tframe = stackTrace.get(i);
                    RootNode root = tframe.getTarget().getRootNode();
                    if (root.getLanguageInfo() != null) {
                        debugStack.add(new DebugStackTraceElement(session, tframe));
                    } else if (hostInfo) {
                        debugStack.add(null);
                    }
                }
                if (hostInfo) {
                    StackTraceElement[] stack = SuspendedEvent.cutToHostDepth(getRawStackTrace());
                    Iterator<DebugStackTraceElement> mergedElements = Debugger.ACCESSOR.engineSupport().mergeHostGuestFrames(session.getDebugger().getEnv(), stack, debugStack.iterator(), true,
                                    new Function<StackTraceElement, DebugStackTraceElement>() {
                                        @Override
                                        public DebugStackTraceElement apply(StackTraceElement element) {
                                            return new DebugStackTraceElement(session, element);
                                        }
                                    }, Function.identity());
                    List<DebugStackTraceElement> elementsList = new ArrayList<>();
                    while (mergedElements.hasNext()) {
                        elementsList.add(mergedElements.next());
                    }
                    debugStackTrace = Collections.unmodifiableList(elementsList);
                } else {
                    debugStackTrace = Collections.unmodifiableList(debugStack);
                }
            } else {
                debugStackTrace = Collections.emptyList();
            }
        }
        return debugStackTrace;
    }

    /**
     * Get a list of asynchronous stack traces that led to scheduling of the exception's execution.
     * Returns an empty list if no asynchronous stack is known. The first asynchronous stack is at
     * the first index in the list. A possible next asynchronous stack (that scheduled execution of
     * the previous one) is at the next index in the list.
     * <p>
     * Languages might not provide asynchronous stack traces by default for performance reasons.
     * Call {@link DebuggerSession#setAsynchronousStackDepth(int)} to request asynchronous stacks.
     * Languages may provide asynchronous stacks if it's of no performance penalty, or if requested
     * by other options.
     *
     * @see DebuggerSession#setAsynchronousStackDepth(int)
     * @since 20.1.0
     */
    public List<List<DebugStackTraceElement>> getDebugAsynchronousStacks() {
        if (debugAsyncStacks == null) {
            int size = getDebugStackTrace().size();
            if (size == 0) {
                return Collections.emptyList();
            }
            debugAsyncStacks = new DebugAsyncStackFrameLists(session, getDebugStackTrace());
        }
        return debugAsyncStacks;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public void printStackTrace() {
        printStackTrace(new PrintStream(session.getDebugger().getEnv().err()));
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public void printStackTrace(PrintStream s) {
        super.printStackTrace(s);
        if (!isTruffleException(exception)) {
            s.print(CAUSE_CAPTION);
            exception.printStackTrace(s);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public void printStackTrace(PrintWriter s) {
        super.printStackTrace(s);
        if (!isTruffleException(exception)) {
            s.print(CAUSE_CAPTION);
            exception.printStackTrace(s);
        }
    }

    /**
     * Returns <code>true</code> if this exception indicates an internal error.
     *
     * @since 19.0
     */
    @SuppressWarnings("deprecation")
    public boolean isInternalError() {
        if (!isTruffleException(exception)) {
            if (exception instanceof DebugException) {
                return ((DebugException) exception).isInternalError();
            }
            return true;
        }
        return false;
    }

    /**
     * Get a guest language object representing the exception, if any.
     *
     * @return an exception object, or <code>null</code>
     * @since 19.0
     */
    @SuppressWarnings("deprecation")
    public DebugValue getExceptionObject() {
        if (!isTruffleException(exception)) {
            return null;
        }
        LanguageInfo language = preferredLanguage;
        if (language == null && throwLocation != null) {
            RootNode throwRoot = throwLocation.getRootNode();
            if (throwRoot != null) {
                language = throwRoot.getLanguageInfo();
            }
        }
        return new DebugValue.HeapValue(session, language, null, exception);
    }

    /**
     * Get source code location where this exception was thrown from.
     *
     * @return the thrown location, or <code>null</code> when the thrown location is not known.
     * @since 19.0
     */
    public SourceSection getThrowLocation() {
        InteropLibrary interop = InteropLibrary.getUncached();
        if (interop.isException(exception) && interop.hasSourceLocation(exception)) {
            try {
                return interop.getSourceLocation(exception);
            } catch (UnsupportedMessageException ume) {
                CompilerDirectives.shouldNotReachHere(ume);
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
     * @since 19.0
     */
    public CatchLocation getCatchLocation() {
        if (!isCatchNodeComputed) {
            synchronized (this) {
                if (!isCatchNodeComputed) {
                    if (isTruffleException(exception)) {
                        catchLocation = BreakpointExceptionFilter.getCatchNode(throwLocation, exception);
                        if (catchLocation != null) {
                            catchLocation.setSuspendedEvent(suspendedEvent);
                            catchLocation = catchLocation.cloneFor(session);
                        }
                    }
                    isCatchNodeComputed = true;
                }
            }
        }
        return catchLocation;
    }

    /**
     * Returns the guest language representation of the exception, or <code>null</code> if the
     * requesting language class does not match the root node language at the throw location.
     *
     * This method is permitted only if the guest language class is available. This is the case if
     * you want to utilize the Debugger API directly from within a guest language, or if you are an
     * instrument bound/dependent on a specific language.
     *
     * @param languageClass the Truffle language class for a given guest language
     * @return the throwable guest language exception object
     *
     * @since 20.1
     */
    public Throwable getRawException(Class<? extends TruffleLanguage<?>> languageClass) {
        Objects.requireNonNull(languageClass);
        RootNode rootNode = getThrowLocationNode().getRootNode();
        if (rootNode == null) {
            return null;
        }
        // check if language class of the root node corresponds to the input language
        TruffleLanguage<?> language = Debugger.ACCESSOR.nodeSupport().getLanguage(rootNode);
        return language != null && language.getClass() == languageClass ? getRawException() : null;
    }

    Node getThrowLocationNode() {
        return throwLocation;
    }

    /**
     * Represents an exception catch location. It provides a stack frame and a source section where
     * the exception is going to be caught.
     *
     * @since 19.0
     */
    public static final class CatchLocation {

        private final DebuggerSession session;
        private final SourceSection section;
        private final FrameInstance frameInstance;
        private final int depth;
        private DebugStackFrame frame;

        CatchLocation(SourceSection section, FrameInstance frameInstance, int depth) {
            this(null, section, frameInstance, depth);
        }

        private CatchLocation(DebuggerSession session, SourceSection section, FrameInstance frameInstance, int depth) {
            this.session = session;
            this.section = section;
            this.frameInstance = frameInstance;
            this.depth = depth;
        }

        /**
         * @return a source section, or <code>null</code> if the catch source section is not known.
         * @since 19.0
         */
        public SourceSection getSourceSection() {
            return session.resolveSection(section);
        }

        /**
         * @return a frame in which the exception is going to be caught.
         * @since 19.0
         */
        public DebugStackFrame getFrame() {
            return frame;
        }

        void setSuspendedEvent(SuspendedEvent suspendedEvent) {
            assert session == null || session == suspendedEvent.getSession();
            frame = new DebugStackFrame(suspendedEvent, depth == 0 ? null : frameInstance, depth);
        }

        private CatchLocation cloneFor(DebuggerSession ds) {
            assert this.session == null;
            CatchLocation clon = new CatchLocation(ds, section, frameInstance, depth);
            if (frame != null) {
                assert ds == frame.event.getSession();
                clon.frame = frame;
            }
            return clon;
        }
    }

    private static boolean isTruffleException(Throwable t) {
        return t != null && InteropLibrary.getUncached().isException(t);
    }
}
