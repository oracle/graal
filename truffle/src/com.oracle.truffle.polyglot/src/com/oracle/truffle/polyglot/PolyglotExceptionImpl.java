/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.oracle.truffle.api.interop.HeapIsolationException;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.polyglot.PolyglotEngineImpl.CancelExecution;

final class PolyglotExceptionImpl {

    private static final String CAUSE_CAPTION = "Caused by host exception: ";

    private RuntimeException api;

    final PolyglotImpl polyglot;
    final PolyglotEngineImpl engine;
    final PolyglotContextImpl context;
    final Throwable exception;
    final boolean showInternalStackFrames;
    final boolean printInternalStrackTrace;
    final Exception creationStackTrace;

    private StackTraceElement[] javaStackTrace;
    private List<Object> materializedFrames;

    private final Object sourceLocation;
    private final boolean internal;
    private final boolean cancelled;
    private final boolean exit;
    private final boolean incompleteSource;
    private final boolean syntaxError;
    private final boolean resourceExhausted;
    private final boolean interrupted;
    private final boolean hostException;
    private final int exitStatus;
    private final Object guestObject;
    private final String qualifiedName;
    private final String message;
    private final PolyglotExceptionImpl causeImpl;

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
        /*
         * If allowInterop == false, languageContext is passed just to get languageContext.context
         * from it. It must not be used for anything else!
         */
        this.context = (languageContext != null) ? languageContext.context : null;
        this.exception = original;
        /*
         * Note: Host frames must be materialized eagerly. Otherwise, they would be materialized via
         * PolyglotException#getStackTrace, where the presence of PolyglotException frames confuses
         * the host/guest stack merging.
         */
        TruffleStackTrace.fillIn(original);
        this.showInternalStackFrames = engine != null && engine.engineOptionValues.get(PolyglotEngineOptions.ShowInternalStackFrames);
        this.printInternalStrackTrace = engine != null && engine.engineOptionValues.get(PolyglotEngineOptions.PrintInternalStackTrace);
        if (engine != null && this.printInternalStrackTrace) {
            creationStackTrace = new Exception();
        } else {
            creationStackTrace = null;
        }
        InteropLibrary interop = InteropLibrary.getUncached();
        boolean isException = interop.isException(exception);
        this.hostException = isException && interop.isHostObject(exception);
        Error resourceLimitError = getResourceLimitError(exception, hostException);
        String exceptionQualifiedName = null;
        String exceptionMessage = null;
        if (allowInterop && isException) {
            try {
                ExceptionType exceptionType = interop.getExceptionType(exception);
                this.internal = false;
                boolean cancelInducedTruffleException = polyglotContextState != null && (polyglotContextState.isCancelling() || polyglotContextState == PolyglotContextImpl.State.CLOSED_CANCELLED);
                this.cancelled = cancelInducedTruffleException;
                this.resourceExhausted = resourceLimitError != null || (cancelInducedTruffleException && polyglotContextResourceExhausted);
                this.syntaxError = exceptionType == ExceptionType.PARSE_ERROR;
                this.exit = exceptionType == ExceptionType.EXIT;
                this.exitStatus = this.exit ? interop.getExceptionExitStatus(exception) : 0;
                this.incompleteSource = this.syntaxError ? interop.isExceptionIncompleteSource(exception) : false;
                this.interrupted = (exceptionType == ExceptionType.INTERRUPT) && !this.cancelled;
                if (interop.hasExceptionMessage(exception)) {
                    exceptionMessage = interop.asString(interop.getExceptionMessage(exception));
                }
                if (interop.hasMetaObject(exception)) {
                    exceptionQualifiedName = interop.asString(interop.getMetaQualifiedName(interop.getMetaObject(exception)));
                }
                if (interop.hasSourceLocation(exception)) {
                    this.sourceLocation = newSourceSection(interop.getSourceLocation(exception));
                } else {
                    this.sourceLocation = null;
                }
                if (entered && languageContext != null && languageContext.isCreated() && !hostException) {
                    this.guestObject = languageContext.asValue(exception);
                } else {
                    this.guestObject = null;
                }
                PolyglotExceptionImpl cause = null;
                if (interop.hasExceptionCause(exception)) {
                    Object guestCause = interop.getExceptionCause(exception);
                    if (!interop.isNull(guestCause)) {
                        Throwable causeTruffleWrapper;
                        try {
                            throw interop.throwException(guestCause);
                        } catch (Throwable t) {
                            causeTruffleWrapper = t;
                        }
                        cause = new PolyglotExceptionImpl(polyglot, engine, polyglotContextState, polyglotContextResourceExhausted, 0, languageContext, causeTruffleWrapper, allowInterop, entered);
                    }
                }
                this.causeImpl = cause;
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
                            (hostException && asHostException() instanceof InterruptedException);
            boolean truffleException = exception instanceof com.oracle.truffle.api.exception.AbstractTruffleException;
            boolean cancelInducedTruffleOrInterruptException = (polyglotContextState != null &&
                            (polyglotContextState.isCancelling() || polyglotContextState == PolyglotContextImpl.State.CLOSED_CANCELLED) &&
                            (interruptException || truffleException));
            /*
             * In case the exception is not a cancel exception, but the context is in cancelling or
             * cancelled state, set the cancelled flag, but only if the original exception is a
             * truffle exception or interrupt exception.
             */
            this.cancelled = cancelInducedTruffleOrInterruptException || (exception instanceof CancelExecution);
            this.resourceExhausted = resourceLimitError != null || (cancelInducedTruffleOrInterruptException && polyglotContextResourceExhausted);
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
                this.exit = false;
                this.exitStatus = 0;
                this.guestObject = null;
            }
            this.interrupted = interruptException && !this.cancelled && !this.exit;
            this.internal = !interrupted && !cancelled && !resourceExhausted && !exit && !truffleException;
            if (exception instanceof CancelExecution) {
                location = ((CancelExecution) exception).getSourceLocation();
            }
            this.sourceLocation = location != null ? newSourceSection(location) : null;
            this.causeImpl = null;
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
        qualifiedName = exceptionQualifiedName;
    }

    private static Error getResourceLimitError(Throwable e, boolean isHostException) {
        if (e instanceof CancelExecution) {
            return ((CancelExecution) e).isResourceLimit() ? (Error) e : null;
        } else if (isHostException) {
            Error toCheck = toHostResourceError(e);
            assert toCheck == null || toCheck instanceof StackOverflowError || toCheck instanceof OutOfMemoryError;
            return toCheck;
        } else if (e instanceof StackOverflowError || e instanceof OutOfMemoryError) {
            return (Error) e;
        }
        return null;
    }

    private static Error toHostResourceError(Throwable hostException) {
        Throwable t = unboxHostException(hostException);
        if (t instanceof StackOverflowError || t instanceof OutOfMemoryError) {
            return (Error) t;
        }
        return null;
    }

    private Object newSourceSection(com.oracle.truffle.api.source.SourceSection section) {
        com.oracle.truffle.api.source.Source truffleSource = section.getSource();
        Object source = polyglot.getAPIAccess().newSource(polyglot.getSourceDispatch(), truffleSource);
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

    public Object getSourceLocation() {
        return sourceLocation;
    }

    public void onCreate(RuntimeException instance) {
        this.api = instance;
    }

    public boolean isResourceExhausted() {
        return resourceExhausted;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public boolean isHostException() {
        return hostException;
    }

    public Throwable asHostException() {
        if (!isHostException()) {
            throw PolyglotEngineException.unsupported(
                            String.format("Unsupported operation PolyglotException.asHostException(). You can ensure that the operation is supported using PolyglotException.isHostException()"));
        }
        return unboxHostException(exception);
    }

    void printStackTrace(PrintWriter s) {
        printStackTrace(new WrappedPrintWriter(s));
    }

    void printStackTrace(PrintStream s) {
        printStackTrace(new WrappedPrintStream(s));
    }

    private void printStackTrace(PrintStreamOrWriter s) {
        synchronized (s.lock()) {
            // For an internal error without guest frames print only the internal error.
            if (isInternalError() && !hasGuestFrames()) {
                s.print(api.getClass().getName() + ": ");
                s.printStackTrace(exception);
                s.println("Internal GraalVM error, please report at https://github.com/oracle/graal/issues/.");
                return;
            }
            // Print our stack trace
            s.println(toStringImpl());

            materialize();
            int languageIdLength = 0; // java
            for (Object traceElement : getPolyglotStackTrace()) {
                PolyglotExceptionFrame frame = (PolyglotExceptionFrame) polyglot.getAPIAccess().getStackFrameReceiver(traceElement);
                if (!frame.isHostFrame() && frame.languageId != null) {
                    languageIdLength = Math.max(languageIdLength, frame.languageId.length());
                }
            }

            for (Object traceElement : getPolyglotStackTrace()) {
                s.println("\tat " + polyglot.getAPIAccess().getStackFrameDispatch(traceElement).toStringImpl(languageIdLength));
            }

            // Print cause, if any
            if (isHostException()) {
                s.println(CAUSE_CAPTION + asHostException());
            }
            if (isInternalError() || printInternalStrackTrace) {
                s.println("Original " + (isInternalError() ? "Internal " : "") + "Error: ");
                s.printStackTrace(exception);
            }
            if (creationStackTrace != null) {
                s.println("Polyglot Exception Creation Stacktrace:");
                s.printStackTrace(creationStackTrace);
            }
            if (causeImpl != null) {
                s.print("Caused by: ");
                causeImpl.printStackTrace(s);
            }
        }
    }

    private boolean hasGuestFrames() {
        materialize();
        for (Object frame : materializedFrames) {
            if (!((PolyglotException.StackFrame) frame).isHostFrame()) {
                return true;
            }
        }
        return false;
    }

    String toStringImpl() {
        if (isInternalError() && !hasGuestFrames()) {
            return api.getClass().getName() + ": " + exception.toString();
        } else {
            String s = (qualifiedName != null ? qualifiedName : api.getClass().getName());
            String m = getMessage();
            return (m != null) ? (s + ": " + m) : s;
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
                Object polyglotFrame = materializedFrames.get(i);
                PolyglotExceptionFrame frame = (PolyglotExceptionFrame) polyglot.getAPIAccess().getStackFrameDispatch(polyglotFrame);
                javaStackTrace[i] = frame.toHostFrame();
            }
        }
        return javaStackTrace;
    }

    void materialize() {
        if (this.materializedFrames == null) {
            List<Object> frames = new ArrayList<>();
            for (Object frame : getPolyglotStackTrace()) {
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

    public Iterable<Object> getPolyglotStackTrace() {
        if (materializedFrames != null) {
            return materializedFrames;
        } else {
            return new Iterable<>() {
                public Iterator<Object> iterator() {
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

    public Object getGuestObject() {
        return guestObject;
    }

    static String printStackToString(PolyglotLanguageContext context, Node node) {
        StackTraceException stack = new StackTraceException(node);
        TruffleStackTrace.fillIn(stack);
        RuntimeException e = PolyglotImpl.guestToHostException(context, stack, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(out));
        return new String(out.toByteArray());
    }

    public PolyglotExceptionImpl getCause() {
        return causeImpl;
    }

    @SuppressWarnings("serial")
    static class StackTraceException extends AbstractTruffleException {

        StackTraceException(Node location) {
            super(location);
        }

    }

    Object getFileSystemContext(Language languageApi) {
        if (context == null) {
            return null;
        }
        PolyglotLanguage language = context.engine.idToLanguage.get(languageApi.getId());
        PolyglotLanguageContext languageContext;
        if (language != null) {
            languageContext = context.getContext(language);
        } else {
            languageContext = context.getHostContext();
        }
        return languageContext.getInternalFileSystemContext();
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

    static Iterator<Object> createStackFrameIterator(PolyglotExceptionImpl impl) {
        Object stackTrace = impl.polyglot.getRootImpl().getEmbedderExceptionStackTrace(impl.engine, impl.exception, impl.isInternalError() || impl.isHostException());
        return new FrameGuestObjectIterator(impl.polyglot.getAPIAccess(), impl, stackTrace);
    }

    private static Throwable unboxHostException(Throwable cause) {
        try {
            return (Throwable) InteropLibrary.getUncached(cause).asHostObject(cause);
        } catch (HeapIsolationException ex) {
            return null;
        } catch (Exception e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private static final class FrameGuestObjectIterator implements Iterator<Object> {

        private final InteropLibrary interop;
        private final APIAccess apiAccess;
        private final PolyglotExceptionImpl exception;
        private final Object stackTrace;
        private int currentIndex;
        private Object next;

        FrameGuestObjectIterator(APIAccess apiAccess, PolyglotExceptionImpl exception, Object stackTrace) {
            this.interop = InteropLibrary.getUncached();
            this.apiAccess = apiAccess;
            this.exception = exception;
            this.stackTrace = stackTrace;
            if (!interop.hasArrayElements(stackTrace)) {
                throw new IllegalArgumentException("Stack trace object " + stackTrace + " is not a valid interop list");
            }
        }

        @Override
        public boolean hasNext() {
            return fetchNext() != null;
        }

        @Override
        public Object next() {
            Object result = fetchNext();
            next = null;
            if (result != null) {
                return result;
            } else {
                throw new NoSuchElementException();
            }
        }

        private Object fetchNext() {
            if (next == null) {
                int size;
                try {
                    size = interop.asInt(interop.getArraySize(stackTrace));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                while (currentIndex < size) {
                    Object frame;
                    try {
                        frame = interop.readArrayElement(stackTrace, currentIndex);
                    } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                    currentIndex++;
                    PolyglotExceptionFrame polyglotExceptionFrame = PolyglotExceptionFrame.create(exception, frame);
                    if (polyglotExceptionFrame != null) {
                        next = apiAccess.newPolyglotStackTraceElement(polyglotExceptionFrame, exception.api);
                        break;
                    }
                }
            }
            return next;
        }
    }
}
