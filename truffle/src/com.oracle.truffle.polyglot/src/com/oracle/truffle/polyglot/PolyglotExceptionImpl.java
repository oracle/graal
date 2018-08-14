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
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractExceptionImpl;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleStackTraceElement;

final class PolyglotExceptionImpl extends AbstractExceptionImpl implements com.oracle.truffle.polyglot.PolyglotImpl.VMObject {

    private static final String CAUSE_CAPTION = "Caused by host exception: ";

    private static final boolean TRACE_STACK_TRACE_WALKING = false;

    private PolyglotException api;

    final PolyglotContextImpl context;
    private final PolyglotEngineImpl engine;
    final Throwable exception;
    private final List<TruffleStackTraceElement> guestFrames;

    private StackTraceElement[] javaStackTrace;
    private List<StackFrame> materializedFrames;

    private final SourceSection sourceLocation;
    private final boolean internal;
    private final boolean cancelled;
    private final boolean exit;
    private final boolean incompleteSource;
    private final boolean syntaxError;
    private final int exitStatus;
    private final Value guestObject;
    private final String message;

    // Exception coming from a language
    PolyglotExceptionImpl(PolyglotLanguageContext languageContext, Throwable original) {
        this(languageContext.getImpl(), languageContext.getEngine(), languageContext, original);
    }

    // Exception coming from an instrument
    PolyglotExceptionImpl(PolyglotEngineImpl engine, Throwable original) {
        this(engine.impl, engine, null, original);
    }

    private PolyglotExceptionImpl(AbstractPolyglotImpl impl, PolyglotEngineImpl engine, PolyglotLanguageContext languageContext, Throwable original) {
        super(impl);
        Objects.requireNonNull(engine);
        this.engine = engine;
        this.context = (languageContext != null) ? languageContext.context : null;
        this.exception = original;
        this.guestFrames = TruffleStackTraceElement.getStackTrace(original);

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
                Objects.requireNonNull(languageContext, "Source location can not be accepted without language context.");
                com.oracle.truffle.api.source.Source truffleSource = section.getSource();
                String language = truffleSource.getLanguage();
                if (language == null) {
                    PolyglotLanguage foundLanguage = languageContext.getEngine().findLanguage(language, truffleSource.getMimeType(), false);
                    if (foundLanguage != null) {
                        language = foundLanguage.getId();
                    }
                }
                Source source = getAPIAccess().newSource(language, truffleSource);
                this.sourceLocation = getAPIAccess().newSourceSection(source, section);
            } else {
                this.sourceLocation = null;
            }
            Object exceptionObject = ((TruffleException) exception).getExceptionObject();
            if (exceptionObject != null && languageContext != null) {
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
            this.internal = true;
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
        VMAccessor.LANGUAGE.materializeHostFrames(original);
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
        this.api = instance;
    }

    @Override
    public boolean isHostException() {
        return exception instanceof HostException;
    }

    @Override
    public Throwable asHostException() {
        if (!(exception instanceof HostException)) {
            throw new PolyglotUnsupportedException(
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
        // Guard against malicious overrides of Throwable.equals by
        // using a Set with identity equality semantics.
        synchronized (s.lock()) {
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
                    languageIdLength = Math.max(languageIdLength, getAPIAccess().getImpl(traceElement).getLanguage().getId().length());
                }
            }

            for (StackFrame traceElement : getPolyglotStackTrace()) {
                s.println("\tat " + getAPIAccess().getImpl(traceElement).toStringImpl(languageIdLength));
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
    public PolyglotEngineImpl getEngine() {
        return engine;
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

    /**
     * Wrapper class for PrintStream and PrintWriter to enable a single implementation of
     * printStackTrace.
     */
    private abstract static class PrintStreamOrWriter {
        /** Returns the object to be locked when using this StreamOrWriter. */
        abstract Object lock();

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
        private static final String PROXY_PACKAGE = PolyglotProxy.class.getName();
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
            this.apiAccess = impl.getAPIAccess();

            Throwable cause = impl.exception;
            while (cause.getCause() != null && cause.getStackTrace().length == 0) {
                cause = cause.getCause();
            }
            StackTraceElement[] hostStack;
            if (VMAccessor.LANGUAGE.isTruffleStackTrace(cause)) {
                hostStack = VMAccessor.LANGUAGE.getInternalStackTraceElements(cause);
            } else if (cause.getStackTrace().length == 0) {
                hostStack = impl.exception.getStackTrace();
            } else {
                hostStack = cause.getStackTrace();
            }
            this.guestFrames = impl.guestFrames == null ? Collections.<TruffleStackTraceElement> emptyList().iterator() : impl.guestFrames.iterator();
            this.hostFrames = Arrays.asList(hostStack).listIterator();
            // we always start in some host stack frame
            this.inHostLanguage = impl.isHostException() || impl.isInternalError();
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
            return apiAccess.newPolyglotStackTraceElement(impl.api, next);
        }

        PolyglotExceptionFrame fetchNext() {
            if (fetchedNext != null) {
                return fetchedNext;
            }

            while (hostFrames.hasNext()) {
                StackTraceElement element = hostFrames.next();
                // we need to flip inHostLanguage state in opposite order as the stack is top to
                // bottom.
                if (inHostLanguage) {
                    if (isGuestToHost(element)) {
                        assert !isHostToGuest(element);
                        inHostLanguage = false;
                    }
                } else {
                    if (isHostToGuest(element)) {
                        inHostLanguage = true;

                        // skip extra host-to-guest frames
                        while (hostFrames.hasNext()) {
                            StackTraceElement next = hostFrames.next();
                            if (isHostToGuest(next)) {
                                traceStackTraceElement(element);
                                element = next;
                                continue;
                            } else {
                                hostFrames.previous();
                                break;
                            }
                        }
                    }
                }

                traceStackTraceElement(element);

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
            return isLazyStackTraceElement(element) || VMAccessor.SPI.isGuestCallStackElement(element);
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

        static boolean isGuestToHost(StackTraceElement element) {
            if (isLazyStackTraceElement(element)) {
                return false;
            }
            return element.getClassName().startsWith(PROXY_PACKAGE) || element.getClassName().startsWith(HOST_INTEROP_PACKAGE);
        }

        private void traceStackTraceElement(StackTraceElement element) {
            if (TRACE_STACK_TRACE_WALKING) {
                PrintStream out = System.out;
                out.printf("host: %5s, guestToHost: %5s, hostToGuest: %5s, guestCall: %5s, -- %s %n", inHostLanguage, isGuestToHost(element), isHostToGuest(element), isGuestCall(element), element);
            }
        }
    }

}
