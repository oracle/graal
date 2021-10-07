/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.polyglot.PolyglotContextImpl.State.CLOSED_EXITED;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.polyglot.PolyglotEngineImpl.CancelExecution;

final class PolyglotExceptionImpl {

    private static final String CAUSE_CAPTION = "Caused by host exception: ";

    private static final boolean TRACE_STACK_TRACE_WALKING = false;

    private Object api;

    final PolyglotImpl polyglot;
    final PolyglotEngineImpl engine;
    final PolyglotContextImpl context;
    final Throwable exception;
    final boolean showInternalStackFrames;
    private final List<TruffleStackTraceElement> guestFrames;

    private StackTraceElement[] javaStackTrace;
    private List<StackFrame> materializedFrames;

    private final SourceSection sourceLocation;
    private final boolean internal;
    private final boolean cancelled;
    private final boolean exit;
    private final boolean incompleteSource;
    private final boolean syntaxError;
    private final boolean resourceExhausted;
    private final boolean interrupted;
    private final int exitStatus;
    private final Value guestObject;
    private final String message;

    PolyglotExceptionImpl(PolyglotEngineImpl engine, PolyglotContextImpl.State polyglotContextState, boolean polyglotContextResourceExhausted, int exitCode, Throwable original) {
        this(engine.impl, engine, polyglotContextState, polyglotContextResourceExhausted, exitCode, null, original, false, false);
    }

    // Exception coming from an instrument
    PolyglotExceptionImpl(PolyglotImpl polyglot, Throwable original) {
        this(polyglot, null, null, false, 0, null, original, true, false);
    }

    @SuppressWarnings("deprecation")
    PolyglotExceptionImpl(PolyglotImpl polyglot, PolyglotEngineImpl engine, PolyglotContextImpl.State polyglotContextState, boolean polyglotContextResourceExhausted, int exitCode,
                    PolyglotLanguageContext languageContext,
                    Throwable original,
                    boolean allowInterop,
                    boolean entered) {
        this.polyglot = polyglot;
        this.engine = engine;
        this.context = (languageContext != null) ? languageContext.context : null;
        this.exception = original;
        this.guestFrames = TruffleStackTrace.getStackTrace(original);
        this.showInternalStackFrames = engine == null ? false : engine.engineOptionValues.get(PolyglotEngineOptions.ShowInternalStackFrames);
        Error resourceLimitError = getResourceLimitError(engine, exception);
        String exceptionMessage = null;
        InteropLibrary interop;
        if (allowInterop && (interop = InteropLibrary.getUncached()).isException(exception)) {
            try {
                ExceptionType exceptionType = interop.getExceptionType(exception);
                this.internal = false;
                boolean truffleException = exception instanceof com.oracle.truffle.api.TruffleException;
                boolean cancelInducedTruffleException = (polyglotContextState != null && (polyglotContextState.isCancelling() || polyglotContextState == PolyglotContextImpl.State.CLOSED_CANCELLED) &&
                                truffleException);
                this.cancelled = cancelInducedTruffleException || isLegacyTruffleExceptionCancelled(exception);
                this.resourceExhausted = resourceLimitError != null || (cancelInducedTruffleException && polyglotContextResourceExhausted);
                this.syntaxError = exceptionType == ExceptionType.PARSE_ERROR;
                this.exit = exceptionType == ExceptionType.EXIT;
                this.exitStatus = this.exit ? interop.getExceptionExitStatus(exception) : 0;
                this.incompleteSource = this.syntaxError ? interop.isExceptionIncompleteSource(exception) : false;
                this.interrupted = (exceptionType == ExceptionType.INTERRUPT) && !this.cancelled;
                if (interop.hasExceptionMessage(exception)) {
                    exceptionMessage = interop.asString(interop.getExceptionMessage(exception));
                }
                if (interop.hasSourceLocation(exception)) {
                    this.sourceLocation = newSourceSection(interop.getSourceLocation(exception));
                } else {
                    this.sourceLocation = null;
                }
                Object exceptionObject;
                if (entered && languageContext != null && languageContext.isCreated() &&
                                !isHostException(engine, exception) && (exceptionObject = ((com.oracle.truffle.api.TruffleException) exception).getExceptionObject()) != null) {
                    /*
                     * Allow proxies in guest language objects. This is for legacy support. Ideally
                     * we should get rid of this if it is no longer relied upon.
                     */
                    Object receiver = exceptionObject;
                    if (receiver instanceof Proxy) {
                        receiver = languageContext.toGuestValue(null, receiver);
                    }
                    this.guestObject = languageContext.asValue(receiver);
                } else {
                    this.guestObject = null;
                }
            } catch (UnsupportedMessageException ume) {
                throw CompilerDirectives.shouldNotReachHere(ume);
            }
        } else {
            /*
             * When polyglot context is invalid, we cannot obtain the exception type from
             * InterruptExecution exception via interop. Please note that in this case the
             * InterruptExecution was thrown before the context was made invalid.
             */
            boolean interruptException = (exception instanceof PolyglotEngineImpl.InterruptExecution) || (exception != null && exception.getCause() instanceof InterruptedException) ||
                            (isHostException(engine, exception) && asHostException() instanceof InterruptedException);
            boolean truffleException = exception instanceof com.oracle.truffle.api.TruffleException;
            boolean cancelInducedTruffleOrInterruptException = (polyglotContextState != null &&
                            (polyglotContextState.isCancelling() || polyglotContextState == PolyglotContextImpl.State.CLOSED_CANCELLED) &&
                            (interruptException || truffleException));
            /*
             * In case the exception is not a cancel exception, but the context is in cancelling or
             * cancelled state, set the cancelled flag, but only if the original exception is a
             * truffle exception or interrupt exception.
             */
            this.cancelled = cancelInducedTruffleOrInterruptException || (exception instanceof CancelExecution) || isLegacyTruffleExceptionCancelled(exception);
            this.resourceExhausted = resourceLimitError != null || (cancelInducedTruffleOrInterruptException && polyglotContextResourceExhausted);
            this.interrupted = interruptException && !this.cancelled;
            this.syntaxError = false;
            this.incompleteSource = false;
            com.oracle.truffle.api.source.SourceSection location = null;
            boolean exitInducedTruffleOrInterruptException = (polyglotContextState != null &&
                            (polyglotContextState.isExiting() || polyglotContextState == CLOSED_EXITED) &&
                            (interruptException || truffleException));
            if (exitInducedTruffleOrInterruptException || exception instanceof PolyglotContextImpl.ExitException) {
                this.exit = true;
                this.exitStatus = exception instanceof PolyglotContextImpl.ExitException ? ((PolyglotContextImpl.ExitException) exception).getExitCode() : exitCode;
                this.guestObject = null;
                location = exception instanceof PolyglotContextImpl.ExitException ? ((PolyglotContextImpl.ExitException) exception).getSourceLocation() : null;
            } else {
                if (allowInterop) {
                    this.exit = isLegacyTruffleExceptionExit(exception);
                    this.exitStatus = exit ? getLegacyTruffleExceptionExitStatus(exception) : 0;
                    this.guestObject = getLegacyTruffleExceptionGuestObject(languageContext, exception);
                } else {
                    this.exit = false;
                    this.exitStatus = 0;
                    this.guestObject = null;
                }
            }
            this.internal = !interrupted && !cancelled && !resourceExhausted && !exit;
            if (exception instanceof CancelExecution) {
                location = ((CancelExecution) exception).getSourceLocation();
            } else if (allowInterop) {
                location = getLegacyTruffleExceptionSourceLocation(exception);
            }
            this.sourceLocation = location != null ? newSourceSection(location) : null;
        }
        if (exceptionMessage == null) {
            exceptionMessage = isHostException() ? asHostException().getMessage() : internal ? exception.toString() : exception.getMessage();
        }
        if (exceptionMessage != null) {
            this.message = exceptionMessage;
        } else if (resourceLimitError != null) {
            String resourceExhaustedMessage = "Resource exhausted";
            if (resourceLimitError instanceof StackOverflowError) {
                resourceExhaustedMessage += ": Stack overflow";
            }
            if (resourceLimitError instanceof OutOfMemoryError) {
                resourceExhaustedMessage += ": Out of memory";
            }
            this.message = resourceExhaustedMessage;
        } else {
            this.message = null;
        }

        // late materialization of host frames. only needed if polyglot exceptions cross the
        // host boundary.
        EngineAccessor.LANGUAGE.materializeHostFrames(original);
    }

    private static Error getResourceLimitError(PolyglotEngineImpl engine, Throwable e) {
        if (e instanceof CancelExecution) {
            return ((CancelExecution) e).isResourceLimit() ? (Error) e : null;
        } else if (isHostException(engine, e)) {
            Throwable toCheck = engine.host.toHostResourceError(e);
            assert toCheck == null || toCheck instanceof StackOverflowError || toCheck instanceof OutOfMemoryError;
            return (Error) toCheck;
        } else if (e instanceof StackOverflowError || e instanceof OutOfMemoryError) {
            return (Error) e;
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static boolean isLegacyTruffleExceptionCancelled(Throwable e) {
        // Legacy TruffleException
        if (e instanceof com.oracle.truffle.api.TruffleException) {
            return ((com.oracle.truffle.api.TruffleException) e).isCancelled();
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static boolean isLegacyTruffleExceptionExit(Throwable e) {
        // Legacy TruffleException
        if (e instanceof com.oracle.truffle.api.TruffleException) {
            return ((com.oracle.truffle.api.TruffleException) e).isExit();
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static int getLegacyTruffleExceptionExitStatus(Throwable e) {
        // Legacy TruffleException
        if (e instanceof com.oracle.truffle.api.TruffleException) {
            return ((com.oracle.truffle.api.TruffleException) e).getExitStatus();
        }
        return 0;
    }

    @SuppressWarnings("deprecation")
    private static com.oracle.truffle.api.source.SourceSection getLegacyTruffleExceptionSourceLocation(Throwable e) {
        // Legacy TruffleException
        if (e instanceof com.oracle.truffle.api.TruffleException) {
            return ((com.oracle.truffle.api.TruffleException) e).getSourceLocation();
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static Value getLegacyTruffleExceptionGuestObject(PolyglotLanguageContext languageContext, Throwable e) {
        // Legacy TruffleException
        if (e instanceof com.oracle.truffle.api.TruffleException && languageContext != null) {
            Object exceptionObject = ((com.oracle.truffle.api.TruffleException) e).getExceptionObject();
            if (exceptionObject != null) {
                if (exceptionObject instanceof Proxy) {
                    exceptionObject = languageContext.toGuestValue(null, exceptionObject);
                }
                return languageContext.asValue(exceptionObject);
            }
        }
        return null;
    }

    private SourceSection newSourceSection(com.oracle.truffle.api.source.SourceSection section) {
        com.oracle.truffle.api.source.Source truffleSource = section.getSource();
        Source source = polyglot.getAPIAccess().newSource(polyglot.getSourceDispatch(), truffleSource);
        return polyglot.getAPIAccess().newSourceSection(source, polyglot.getSourceSectionDispatch(), section);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PolyglotExceptionImpl) {
            return exception == ((PolyglotExceptionImpl) obj).exception;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return exception.hashCode();
    }

    public org.graalvm.polyglot.SourceSection getSourceLocation() {
        return sourceLocation;
    }

    public void onCreate(PolyglotException instance) {
        this.api = instance;
    }

    public boolean isResourceExhausted() {
        return resourceExhausted;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public boolean isHostException() {
        return isHostException(engine, exception);
    }

    public Throwable asHostException() {
        if (!isHostException()) {
            throw PolyglotEngineException.unsupported(
                            String.format("Unsupported operation %s.%s. You can ensure that the operation is supported using %s.%s.",
                                            PolyglotException.class.getSimpleName(), "asHostException()",
                                            PolyglotException.class.getSimpleName(), "isHostException()"));
        }
        return engine.host.unboxHostException(exception);
    }

    public void printStackTrace(PrintWriter s) {
        printStackTrace(new WrappedPrintWriter(s));
    }

    public void printStackTrace(PrintStream s) {
        printStackTrace(new WrappedPrintStream(s));
    }

    private void printStackTrace(PrintStreamOrWriter s) {
        synchronized (s.lock()) {
            // For an internal error without guest frames print only the internal error.
            if (isInternalError() && (guestFrames == null || guestFrames.isEmpty())) {
                s.print(api.getClass().getName() + ": ");
                s.printStackTrace(exception);
                s.println("Internal GraalVM error, please report at https://github.com/oracle/graal/issues/.");
                return;
            }
            // Print our stack trace
            if (isInternalError() || getMessage() == null || getMessage().isEmpty()) {
                s.println(api);
            } else {
                s.println(getMessage());
            }

            materialize();
            int languageIdLength = 0; // java
            for (StackFrame traceElement : getPolyglotStackTrace()) {
                if (!traceElement.isHostFrame()) {
                    languageIdLength = Math.max(languageIdLength, polyglot.getAPIAccess().getDispatch(traceElement).getLanguage().getId().length());
                }
            }

            for (StackFrame traceElement : getPolyglotStackTrace()) {
                s.println("\tat " + polyglot.getAPIAccess().getDispatch(traceElement).toStringImpl(languageIdLength));
            }

            // Print cause, if any
            if (isHostException()) {
                s.println(CAUSE_CAPTION + asHostException());
            }
            if (isInternalError()) {
                s.println("Original Internal Error: ");
                s.printStackTrace(exception);
            }
        }
    }

    public String getMessage() {
        return message;
    }

    public StackTraceElement[] getJavaStackTrace() {
        if (javaStackTrace == null) {
            materialize();
            javaStackTrace = new StackTraceElement[materializedFrames.size()];
            for (int i = 0; i < javaStackTrace.length; i++) {
                javaStackTrace[i] = materializedFrames.get(i).toHostFrame();
            }
        }
        return javaStackTrace;
    }

    private void materialize() {
        if (this.materializedFrames == null) {
            List<StackFrame> frames = new ArrayList<>();
            for (StackFrame frame : getPolyglotStackTrace()) {
                frames.add(frame);
            }
            this.materializedFrames = Collections.unmodifiableList(frames);
        }
    }

    public StackTraceElement[] getStackTrace() {
        return getJavaStackTrace().clone();
    }

    public boolean isInternalError() {
        return internal;
    }

    public Iterable<StackFrame> getPolyglotStackTrace() {
        if (materializedFrames != null) {
            return materializedFrames;
        } else {
            return new Iterable<StackFrame>() {
                public Iterator<StackFrame> iterator() {
                    return createStackFrameIterator(PolyglotExceptionImpl.this);
                }
            };
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isExit() {
        return exit;
    }

    public boolean isIncompleteSource() {
        return incompleteSource;
    }

    public int getExitStatus() {
        return exitStatus;
    }

    public boolean isSyntaxError() {
        return syntaxError;
    }

    public Value getGuestObject() {
        return guestObject;
    }

    static String printStackToString(PolyglotLanguageContext context, Node node) {
        StackTraceException stack = new StackTraceException(node);
        TruffleStackTrace.fillIn(stack);
        PolyglotException e = PolyglotImpl.guestToHostException(context, stack, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(out));
        return new String(out.toByteArray());
    }

    @SuppressWarnings("serial")
    static class StackTraceException extends AbstractTruffleException {

        StackTraceException(Node location) {
            super(location);
        }

    }

    Object getFileSystemContext(PolyglotLanguage language) {
        if (context == null) {
            return null;
        }

        synchronized (context) {
            /*
             * Synchronized on polyglot context, otherwise isCreated() can change before
             * getInternalFileSystemContext is called.
             */
            PolyglotLanguageContext languageContext = context.getContext(language);
            if (!languageContext.isCreated()) {
                return null;
            }
            return languageContext.getInternalFileSystemContext();
        }
    }

    /**
     * Wrapper class for PrintStream and PrintWriter to enable a single implementation of
     * printStackTrace.
     */
    private abstract static class PrintStreamOrWriter {
        /**
         * Returns the object to be locked when using this StreamOrWriter.
         */
        abstract Object lock();

        /**
         * Prints the specified string.
         */
        abstract void print(Object o);

        /**
         * Prints the specified string as a line on this StreamOrWriter.
         */
        abstract void println(Object o);

        abstract void printStackTrace(Throwable t);
    }

    private static class WrappedPrintStream extends PrintStreamOrWriter {
        private final PrintStream printStream;

        WrappedPrintStream(PrintStream printStream) {
            this.printStream = printStream;
        }

        @Override
        Object lock() {
            return printStream;
        }

        @Override
        void print(Object o) {
            printStream.print(o);
        }

        @Override
        void println(Object o) {
            printStream.println(o);
        }

        @Override
        void printStackTrace(Throwable t) {
            t.printStackTrace(printStream);
        }
    }

    private static class WrappedPrintWriter extends PrintStreamOrWriter {
        private final PrintWriter printWriter;

        WrappedPrintWriter(PrintWriter printWriter) {
            this.printWriter = printWriter;
        }

        @Override
        Object lock() {
            return printWriter;
        }

        @Override
        void print(Object o) {
            printWriter.print(o);
        }

        @Override
        void println(Object o) {
            printWriter.println(o);
        }

        @Override
        void printStackTrace(Throwable t) {
            t.printStackTrace(printWriter);
        }
    }

    static Iterator<StackFrame> createStackFrameIterator(PolyglotExceptionImpl impl) {
        APIAccess apiAccess = impl.polyglot.getAPIAccess();

        Throwable cause = findCause(impl.engine, impl.exception);
        StackTraceElement[] hostStack;
        if (EngineAccessor.LANGUAGE.isTruffleStackTrace(cause)) {
            hostStack = EngineAccessor.LANGUAGE.getInternalStackTraceElements(cause);
        } else if (cause.getStackTrace() == null || cause.getStackTrace().length == 0) {
            hostStack = impl.exception.getStackTrace();
        } else {
            hostStack = cause.getStackTrace();
        }
        Iterator<TruffleStackTraceElement> guestFrames = impl.guestFrames == null ? Collections.emptyIterator() : impl.guestFrames.iterator();
        // we always start in some host stack frame
        boolean inHostLanguage = impl.isHostException() || impl.isInternalError();

        if (TRACE_STACK_TRACE_WALKING) {
            // To mark the beginning of the stack trace and separate from the previous one
            PrintStream out = System.out;
            out.println();
        }
        return new MergedHostGuestIterator<>(impl.engine, hostStack, guestFrames, inHostLanguage, new Function<StackTraceElement, StackFrame>() {
            @Override
            public StackFrame apply(StackTraceElement element) {
                return apiAccess.newPolyglotStackTraceElement(PolyglotExceptionFrame.createHost(impl, element), impl.api);
            }
        }, new Function<TruffleStackTraceElement, StackFrame>() {

            private boolean firstGuestFrame = true;

            @Override
            public StackFrame apply(TruffleStackTraceElement guestFrame) {
                boolean first = this.firstGuestFrame;
                this.firstGuestFrame = false;
                PolyglotExceptionFrame guest = PolyglotExceptionFrame.createGuest(impl, guestFrame, first);
                if (guest != null) {
                    return apiAccess.newPolyglotStackTraceElement(guest, impl.api);
                } else {
                    return null;
                }
            }
        });
    }

    private static Throwable findCause(PolyglotEngineImpl engine, Throwable throwable) {
        Throwable cause = throwable;
        if (isHostException(engine, cause)) {
            return findCause(engine, engine.host.unboxHostException(cause));
        } else if (EngineAccessor.EXCEPTION.isException(cause)) {
            return EngineAccessor.EXCEPTION.getLazyStackTrace(cause);
        } else {
            while (cause.getCause() != null && cause.getStackTrace().length == 0) {
                if (isHostException(engine, cause)) {
                    cause = engine.host.unboxHostException(cause);
                } else {
                    cause = cause.getCause();
                }
            }
            return cause;
        }
    }

    private static boolean isHostException(PolyglotEngineImpl engine, Throwable cause) {
        /*
         * Note that engine.host can be null if the error happens during initialization.
         */
        return engine != null && engine.host != null && engine.host.isHostException(cause);
    }

    static class MergedHostGuestIterator<T, G> implements Iterator<T> {

        private static final String POLYGLOT_PACKAGE = Engine.class.getName().substring(0, Engine.class.getName().lastIndexOf('.') + 1);
        private static final String HOST_INTEROP_PACKAGE = "com.oracle.truffle.polyglot.";
        private static final String[] JAVA_INTEROP_HOST_TO_GUEST = {
                        HOST_INTEROP_PACKAGE + "PolyglotMap",
                        HOST_INTEROP_PACKAGE + "PolyglotList",
                        HOST_INTEROP_PACKAGE + "PolyglotFunction",
                        HOST_INTEROP_PACKAGE + "PolyglotMapAndFunction",
                        HOST_INTEROP_PACKAGE + "PolyglotFunctionProxyHandler",
                        HOST_INTEROP_PACKAGE + "PolyglotObjectProxyHandler"
        };

        private final PolyglotEngineImpl engine;
        private final Iterator<G> guestFrames;
        private final StackTraceElement[] hostStack;
        private final ListIterator<StackTraceElement> hostFrames;
        private final Function<StackTraceElement, T> hostFrameConvertor;
        private final Function<G, T> guestFrameConvertor;
        private boolean inHostLanguage;
        private T fetchedNext;

        MergedHostGuestIterator(PolyglotEngineImpl engine, StackTraceElement[] hostStack, Iterator<G> guestFrames, boolean inHostLanguage, Function<StackTraceElement, T> hostFrameConvertor,
                        Function<G, T> guestFrameConvertor) {
            this.engine = engine;
            this.hostStack = hostStack;
            this.hostFrames = Arrays.asList(hostStack).listIterator();
            this.guestFrames = guestFrames;
            this.inHostLanguage = inHostLanguage;
            this.hostFrameConvertor = hostFrameConvertor;
            this.guestFrameConvertor = guestFrameConvertor;
        }

        @Override
        public boolean hasNext() {
            return fetchNext() != null;
        }

        @Override
        public T next() {
            T next = fetchNext();
            if (next == null) {
                throw new NoSuchElementException();
            }
            fetchedNext = null;
            return next;
        }

        T fetchNext() {
            if (fetchedNext != null) {
                return fetchedNext;
            }

            while (hostFrames.hasNext()) {
                StackTraceElement element = hostFrames.next();
                traceStackTraceElement(element);
                // we need to flip inHostLanguage state in opposite order as the stack is top to
                // bottom.
                if (inHostLanguage) {
                    int guestToHost = findGuestToHostFrame(engine, element, hostStack, hostFrames.nextIndex());
                    if (guestToHost >= 0) {
                        assert !isHostToGuest(element);
                        inHostLanguage = false;

                        for (int i = 0; i < guestToHost; i++) {
                            element = hostFrames.next();
                            traceStackTraceElement(element);
                        }
                    }
                } else {
                    if (isHostToGuest(element)) {
                        inHostLanguage = true;

                        // skip extra host-to-guest frames
                        while (hostFrames.hasNext()) {
                            StackTraceElement next = hostFrames.next();
                            traceStackTraceElement(next);
                            if (isHostToGuest(next)) {
                                element = next;
                            } else {
                                hostFrames.previous();
                                break;
                            }
                        }
                    }
                }

                if (isGuestCall(element)) {
                    inHostLanguage = false;
                    // construct guest frame
                    if (guestFrames.hasNext()) {
                        G guestFrame = guestFrames.next();
                        T frame = guestFrameConvertor.apply(guestFrame);
                        if (frame != null) {
                            fetchedNext = frame;
                            return fetchedNext;
                        }
                    }
                } else if (inHostLanguage) {
                    // construct host frame
                    fetchedNext = hostFrameConvertor.apply(element);
                    return fetchedNext;
                } else {
                    // skip stack frame that is part of guest language stack
                }
            }

            // consume guest frames
            while (guestFrames.hasNext()) {
                G guestFrame = guestFrames.next();
                T frame = guestFrameConvertor.apply(guestFrame);
                if (frame != null) {
                    fetchedNext = frame;
                    return fetchedNext;
                }
            }

            return null;
        }

        static boolean isLazyStackTraceElement(StackTraceElement element) {
            return element == null;
        }

        static boolean isGuestCall(StackTraceElement element) {
            return isLazyStackTraceElement(element) || EngineAccessor.RUNTIME.isGuestCallStackFrame(element);
        }

        static boolean isHostToGuest(StackTraceElement element) {
            if (isLazyStackTraceElement(element)) {
                return false;
            }
            if (element.getClassName().startsWith(POLYGLOT_PACKAGE) && element.getClassName().indexOf('.', POLYGLOT_PACKAGE.length()) < 0) {
                return true;
            } else if (element.getClassName().startsWith(HOST_INTEROP_PACKAGE)) {
                for (String hostToGuestClassName : JAVA_INTEROP_HOST_TO_GUEST) {
                    if (element.getClassName().equals(hostToGuestClassName)) {
                        return true;
                    }
                }
            }
            return false;
        }

        // Return the number of frames with reflective calls to skip
        static int findGuestToHostFrame(PolyglotEngineImpl engine, StackTraceElement firstElement, StackTraceElement[] hostStack, int nextElementIndex) {
            if (isLazyStackTraceElement(firstElement)) {
                return -1;
            }
            if (engine == null || engine.host == null) {
                return -1;
            }
            return engine.host.findNextGuestToHostStackTraceElement(firstElement, hostStack, nextElementIndex);

        }

        private void traceStackTraceElement(StackTraceElement element) {
            if (TRACE_STACK_TRACE_WALKING) {
                PrintStream out = System.out;
                out.printf("host: %5s, guestToHost: %2s, hostToGuest: %5s, guestCall: %5s, -- %s %n", inHostLanguage,
                                findGuestToHostFrame(engine, element, hostStack, hostFrames.nextIndex()), isHostToGuest(element),
                                isGuestCall(element), element);
            }
        }
    }

}
