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
package com.oracle.truffle.polyglot;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractExceptionImpl;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.polyglot.PolyglotEngineImpl.CancelExecution;

final class PolyglotExceptionImpl extends AbstractExceptionImpl {

    private static final String CAUSE_CAPTION = "Caused by host exception: ";

    private static final boolean TRACE_STACK_TRACE_WALKING = false;

    private PolyglotException impl;

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
    private final int exitStatus;
    private final Value guestObject;
    private final String message;

    // Exception coming from a language
    PolyglotExceptionImpl(PolyglotLanguageContext languageContext, Throwable original) {
        this(languageContext.getImpl(), languageContext.context.engine, languageContext, original);
    }

    PolyglotExceptionImpl(PolyglotEngineImpl engine, Throwable original) {
        this(engine.impl, engine, null, original);
    }

    // Exception coming from an instrument
    PolyglotExceptionImpl(PolyglotImpl polyglot, Throwable original) {
        this(polyglot, null, null, original);
    }

    private PolyglotExceptionImpl(PolyglotImpl polyglot, PolyglotEngineImpl engine, PolyglotLanguageContext languageContext, Throwable original) {
        super(polyglot);
        this.polyglot = polyglot;
        this.engine = engine;
        this.context = (languageContext != null) ? languageContext.context : null;
        this.exception = original;
        this.guestFrames = TruffleStackTrace.getStackTrace(original);
        this.showInternalStackFrames = engine == null ? false : engine.engineOptionValues.get(PolyglotEngineOptions.ShowInternalStackFrames);
        this.resourceExhausted = isResourceLimit(exception);

        if (exception instanceof TruffleException) {
            TruffleException truffleException = (TruffleException) exception;
            this.internal = truffleException.isInternalError();
            this.cancelled = truffleException.isCancelled();
            this.syntaxError = truffleException.isSyntaxError();
            this.incompleteSource = truffleException.isIncompleteSource();
            this.exit = truffleException.isExit();
            this.exitStatus = this.exit ? truffleException.getExitStatus() : 0;

            com.oracle.truffle.api.source.SourceSection section = truffleException.getSourceLocation();
            if (section != null) {
                com.oracle.truffle.api.source.Source truffleSource = section.getSource();
                String language = truffleSource.getLanguage();
                if (language == null) {
                    Objects.requireNonNull(engine, "Source location can not be accepted without language context.");
                    PolyglotLanguage foundLanguage = engine.findLanguage(null, language, truffleSource.getMimeType(), false, true);
                    if (foundLanguage != null) {
                        language = foundLanguage.getId();
                    }
                }
                Source source = polyglot.getAPIAccess().newSource(language, truffleSource);
                this.sourceLocation = polyglot.getAPIAccess().newSourceSection(source, section);
            } else {
                this.sourceLocation = null;
            }
            Object exceptionObject;
            if (languageContext != null && !(exception instanceof HostException) && (exceptionObject = ((TruffleException) exception).getExceptionObject()) != null) {
                /*
                 * Allow proxies in guest language objects. This is for legacy support. Ideally we
                 * should get rid of this if it is no longer relied upon.
                 */
                Object receiver = exceptionObject;
                if (receiver instanceof Proxy) {
                    receiver = languageContext.toGuestValue(receiver);
                }
                this.guestObject = languageContext.asValue(receiver);
            } else {
                this.guestObject = null;
            }
        } else {
            this.cancelled = false;
            this.internal = !resourceExhausted;
            this.syntaxError = false;
            this.incompleteSource = false;
            this.exit = false;
            this.exitStatus = 0;
            this.sourceLocation = null;
            this.guestObject = null;
        }
        if (isHostException()) {
            this.message = asHostException().getMessage();
        } else {
            if (internal) {
                this.message = exception.toString();
            } else {
                this.message = exception.getMessage();
            }
        }

        // late materialization of host frames. only needed if polyglot exceptions cross the
        // host boundary.
        EngineAccessor.LANGUAGE.materializeHostFrames(original);
    }

    private static boolean isResourceLimit(Throwable e) {
        if (e instanceof CancelExecution) {
            return true;
        }
        Throwable toCheck;
        if (e instanceof HostException) {
            toCheck = ((HostException) e).getOriginal();
        } else {
            toCheck = e;
        }
        return toCheck instanceof StackOverflowError || toCheck instanceof OutOfMemoryError;
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

    @Override
    public org.graalvm.polyglot.SourceSection getSourceLocation() {
        return sourceLocation;
    }

    @Override
    public void onCreate(PolyglotException instance) {
        this.impl = instance;
    }

    @Override
    public boolean isResourceExhausted() {
        return resourceExhausted;
    }

    @Override
    public boolean isHostException() {
        return exception instanceof HostException;
    }

    @Override
    public Throwable asHostException() {
        if (!(exception instanceof HostException)) {
            throw PolyglotEngineException.unsupported(
                            String.format("Unsupported operation %s.%s. You can ensure that the operation is supported using %s.%s.",
                                            PolyglotException.class.getSimpleName(), "asHostException()",
                                            PolyglotException.class.getSimpleName(), "isHostException()"));
        }
        return ((HostException) exception).getOriginal();
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        printStackTrace(new WrappedPrintWriter(s));
    }

    @Override
    public void printStackTrace(PrintStream s) {
        printStackTrace(new WrappedPrintStream(s));
    }

    private void printStackTrace(PrintStreamOrWriter s) {
        synchronized (s.lock()) {
            // For an internal error without guest frames print only the internal error.
            if (isInternalError() && (guestFrames == null || guestFrames.isEmpty())) {
                s.print(impl.getClass().getName() + ": ");
                s.printStackTrace(exception);
                s.println("Internal GraalVM error, please report at https://github.com/oracle/graal/issues/.");
                return;
            }
            // Print our stack trace
            if (isInternalError() || getMessage() == null || getMessage().isEmpty()) {
                s.println(impl);
            } else {
                s.println(getMessage());
            }

            materialize();
            int languageIdLength = 0; // java
            for (StackFrame traceElement : getPolyglotStackTrace()) {
                if (!traceElement.isHostFrame()) {
                    languageIdLength = Math.max(languageIdLength, polyglot.getAPIAccess().getImpl(traceElement).getLanguage().getId().length());
                }
            }

            for (StackFrame traceElement : getPolyglotStackTrace()) {
                s.println("\tat " + polyglot.getAPIAccess().getImpl(traceElement).toStringImpl(languageIdLength));
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

    @Override
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

    @Override
    public StackTraceElement[] getStackTrace() {
        return getJavaStackTrace().clone();
    }

    @Override
    public boolean isInternalError() {
        return internal;
    }

    @Override
    public Iterable<StackFrame> getPolyglotStackTrace() {
        if (materializedFrames != null) {
            return materializedFrames;
        } else {
            return new Iterable<StackFrame>() {
                public Iterator<StackFrame> iterator() {
                    return new StackFrameIterator(PolyglotExceptionImpl.this);
                }
            };
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean isExit() {
        return exit;
    }

    @Override
    public boolean isIncompleteSource() {
        return incompleteSource;
    }

    @Override
    public int getExitStatus() {
        return exitStatus;
    }

    @Override
    public boolean isSyntaxError() {
        return syntaxError;
    }

    @Override
    public Value getGuestObject() {
        return guestObject;
    }

    Object getFileSystemContext(PolyglotLanguage language) {
        if (context == null) {
            return null;
        }

        PolyglotLanguageContext languageContext = context.getContext(language);
        if (!languageContext.isCreated()) {
            return null;
        }
        return languageContext.getInternalFileSystemContext();
    }

    /**
     * Wrapper class for PrintStream and PrintWriter to enable a single implementation of
     * printStackTrace.
     */
    private abstract static class PrintStreamOrWriter {
        /** Returns the object to be locked when using this StreamOrWriter. */
        abstract Object lock();

        /** Prints the specified string. */
        abstract void print(Object o);

        /** Prints the specified string as a line on this StreamOrWriter. */
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

    private static class StackFrameIterator implements Iterator<StackFrame> {

        private static final String POLYGLOT_PACKAGE = Engine.class.getName().substring(0, Engine.class.getName().lastIndexOf('.') + 1);
        private static final String HOST_INTEROP_PACKAGE = "com.oracle.truffle.polyglot.";
        private static final String[] JAVA_INTEROP_HOST_TO_GUEST = {
                        HOST_INTEROP_PACKAGE + "PolyglotMap",
                        HOST_INTEROP_PACKAGE + "PolyglotList",
                        HOST_INTEROP_PACKAGE + "PolyglotFunction",
                        HOST_INTEROP_PACKAGE + "FunctionProxyHandler",
                        HOST_INTEROP_PACKAGE + "ObjectProxyHandler"
        };

        final PolyglotExceptionImpl impl;
        final Iterator<TruffleStackTraceElement> guestFrames;
        final StackTraceElement[] hostStack;
        final ListIterator<StackTraceElement> hostFrames;
        /*
         * Initial host frames are skipped if the error is a regular non-internal guest language
         * error.
         */
        final APIAccess apiAccess;

        boolean inHostLanguage;
        boolean firstGuestFrame = true;
        PolyglotExceptionFrame fetchedNext;

        StackFrameIterator(PolyglotExceptionImpl impl) {
            this.impl = impl;
            this.apiAccess = impl.polyglot.getAPIAccess();

            Throwable cause = impl.exception;
            while (cause.getCause() != null && cause.getStackTrace().length == 0) {
                if (cause instanceof HostException) {
                    cause = ((HostException) cause).getOriginal();
                } else {
                    cause = cause.getCause();
                }
            }
            if (EngineAccessor.LANGUAGE.isTruffleStackTrace(cause)) {
                this.hostStack = EngineAccessor.LANGUAGE.getInternalStackTraceElements(cause);
            } else if (cause.getStackTrace() == null || cause.getStackTrace().length == 0) {
                this.hostStack = impl.exception.getStackTrace();
            } else {
                this.hostStack = cause.getStackTrace();
            }
            this.guestFrames = impl.guestFrames == null ? Collections.<TruffleStackTraceElement> emptyList().iterator() : impl.guestFrames.iterator();
            this.hostFrames = Arrays.asList(hostStack).listIterator();
            // we always start in some host stack frame
            this.inHostLanguage = impl.isHostException() || impl.isInternalError();

            if (TRACE_STACK_TRACE_WALKING) {
                // To mark the beginning of the stack trace and separate from the previous one
                PrintStream out = System.out;
                out.println();
            }
        }

        public boolean hasNext() {
            return fetchNext() != null;
        }

        public StackFrame next() {
            PolyglotExceptionFrame next = fetchNext();
            if (next == null) {
                throw new NoSuchElementException();
            }
            fetchedNext = null;
            return apiAccess.newPolyglotStackTraceElement(impl.impl, next);
        }

        PolyglotExceptionFrame fetchNext() {
            if (fetchedNext != null) {
                return fetchedNext;
            }

            while (hostFrames.hasNext()) {
                StackTraceElement element = hostFrames.next();
                traceStackTraceElement(element);
                // we need to flip inHostLanguage state in opposite order as the stack is top to
                // bottom.
                if (inHostLanguage) {
                    int guestToHost = isGuestToHost(element, hostStack, hostFrames.nextIndex());
                    if (guestToHost >= 0) {
                        assert !isHostToGuest(element);
                        inHostLanguage = false;

                        for (int i = 0; i < guestToHost; i++) {
                            assert isGuestToHostReflectiveCall(element);
                            element = hostFrames.next();
                            traceStackTraceElement(element);
                        }

                        assert isGuestToHostCallFromHostInterop(element);
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
                    TruffleStackTraceElement guestFrame = null;
                    if (guestFrames.hasNext()) {
                        guestFrame = guestFrames.next();
                    }
                    PolyglotExceptionFrame frame = PolyglotExceptionFrame.createGuest(impl, guestFrame, firstGuestFrame);
                    firstGuestFrame = false;
                    if (frame != null) {
                        fetchedNext = frame;
                        return fetchedNext;
                    }
                } else if (inHostLanguage) {
                    // construct host frame
                    fetchedNext = (PolyglotExceptionFrame.createHost(impl, element));
                    return fetchedNext;
                } else {
                    // skip stack frame that is part of guest language stack
                }
            }

            // consume guest frames
            if (guestFrames.hasNext()) {
                TruffleStackTraceElement guestFrame = guestFrames.next();
                PolyglotExceptionFrame frame = PolyglotExceptionFrame.createGuest(impl, guestFrame, firstGuestFrame);
                firstGuestFrame = false;
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
        static int isGuestToHost(StackTraceElement firstElement, StackTraceElement[] hostStack, int nextElementIndex) {
            if (isLazyStackTraceElement(firstElement)) {
                return -1;
            }

            StackTraceElement element = firstElement;
            int index = nextElementIndex;
            while (isGuestToHostReflectiveCall(element) && index < hostStack.length) {
                element = hostStack[index++];
            }
            if (isGuestToHostCallFromHostInterop(element)) {
                return index - nextElementIndex;
            } else {
                return -1;
            }
        }

        private static boolean isGuestToHostCallFromHostInterop(StackTraceElement element) {
            switch (element.getClassName()) {
                case "com.oracle.truffle.polyglot.HostMethodDesc$SingleMethod$MHBase":
                    return element.getMethodName().equals("invokeHandle");
                case "com.oracle.truffle.polyglot.HostMethodDesc$SingleMethod$MethodReflectImpl":
                    return element.getMethodName().equals("reflectInvoke");
                default:
                    return element.getClassName().startsWith("com.oracle.truffle.polyglot.HostToGuestCodeCache$") && element.getMethodName().equals("executeImpl");
            }
        }

        private static boolean isGuestToHostReflectiveCall(StackTraceElement element) {
            switch (element.getClassName()) {
                case "sun.reflect.NativeMethodAccessorImpl":
                case "sun.reflect.DelegatingMethodAccessorImpl":
                case "jdk.internal.reflect.NativeMethodAccessorImpl":
                case "jdk.internal.reflect.DelegatingMethodAccessorImpl":
                case "java.lang.reflect.Method":
                    return element.getMethodName().startsWith("invoke");
                default:
                    return false;
            }
        }

        private void traceStackTraceElement(StackTraceElement element) {
            if (TRACE_STACK_TRACE_WALKING) {
                PrintStream out = System.out;
                out.printf("host: %5s, guestToHost: %2s, hostToGuest: %5s, guestCall: %5s, -- %s %n", inHostLanguage,
                                isGuestToHost(element, hostStack, hostFrames.nextIndex()), isHostToGuest(element),
                                isGuestCall(element), element);
            }
        }
    }

}
