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
package com.oracle.truffle.api.vm;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractExceptionImpl;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.vm.PolyglotImpl.VMObject;

final class PolyglotExceptionImpl extends AbstractExceptionImpl implements VMObject {

    private static final String CAUSE_CAPTION = "Caused by host exception: ";

    private static final boolean TRACE_STACK_TRACE_WALKING = false;

    PolyglotException api;

    final PolyglotEngineImpl engine;
    final Throwable exception;
    final List<TruffleStackTraceElement> guestFrames;

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

    PolyglotExceptionImpl(PolyglotLanguageContext languageContext, Throwable original) {
        super(languageContext.getImpl());
        this.engine = languageContext.getEngine();
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

            Node location = truffleException.getLocation();
            com.oracle.truffle.api.source.SourceSection section = null;
            if (location != null) {
                section = location.getEncapsulatingSourceSection();
            }
            if (section != null) {
                com.oracle.truffle.api.source.Source truffleSource = section.getSource();
                String language = truffleSource.getLanguage();
                PolyglotLanguageContext sourceContext = languageContext.context.findLanguageContext(language, truffleSource.getMimeType(), false);
                if (language == null && sourceContext != null) {
                    language = sourceContext.language.getId();
                }
                Source source = getAPIAccess().newSource(language, truffleSource);
                this.sourceLocation = getAPIAccess().newSourceSection(source, section);
            } else {
                this.sourceLocation = null;
            }
            Object exceptionObject = ((TruffleException) exception).getExceptionObject();
            if (exceptionObject != null) {
                this.guestObject = languageContext.toHostValue(exceptionObject);
            } else {
                this.guestObject = languageContext.context.getHostContext().nullValue;
            }
        } else {
            this.cancelled = false;
            this.internal = true;
            this.syntaxError = false;
            this.incompleteSource = false;
            this.exit = false;
            this.exitStatus = 0;
            this.sourceLocation = null;
            this.guestObject = languageContext.context.getHostContext().nullValue;
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

        private static final String POLYGLOT_PACKAGE = Engine.class.getPackage().getName();
        private static final String PROXY_PACKAGE = PolyglotProxy.class.getName();

        final PolyglotExceptionImpl impl;
        final Iterator<TruffleStackTraceElement> guestFrames;
        final Iterator<StackTraceElement> hostFrames;
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
            while (cause.getCause() != null && (cause.getCause() instanceof HostException || cause.getCause().getStackTrace().length > 0)) {
                cause = cause.getCause();
            }
            this.guestFrames = impl.guestFrames == null ? Collections.<TruffleStackTraceElement> emptyList().iterator() : impl.guestFrames.iterator();
            this.hostFrames = Arrays.asList(cause.getStackTrace()).iterator();
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
                        inHostLanguage = false;
                    }
                } else {
                    if (isHostToGuest(element)) {
                        inHostLanguage = true;
                    }
                }

                if (TRACE_STACK_TRACE_WALKING) {
                    PrintStream out = System.out;
                    out.printf("host: %5s, guestToHost: %5s, hostToGuest: %5s, guestCall: %5s, -- %s %n", inHostLanguage, isGuestToHost(element), isHostToGuest(element), isGuestCall(element),
                                    element);
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

        static boolean isGuestCall(StackTraceElement element) {
            return VMAccessor.SPI.isGuestCallStackElement(element);
        }

        static boolean isHostToGuest(StackTraceElement element) {
            String polyglotPackageName = POLYGLOT_PACKAGE;
            if (element.getClassName().startsWith(polyglotPackageName)) {
                int index = element.getClassName().lastIndexOf('.');
                if (index == polyglotPackageName.length()) {
                    return true;
                }
            }
            return false;
        }

        static boolean isGuestToHost(StackTraceElement element) {
            return element.getClassName().startsWith(PROXY_PACKAGE);
        }

    }

}
