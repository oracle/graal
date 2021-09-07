/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import java.util.Locale;

@ExportLibrary(InteropLibrary.class)
final class InsightException extends AbstractTruffleException {
    static final long serialVersionUID = 1L;
    private final int exitCode;

    @TruffleBoundary
    private InsightException(String msg, Throwable cause, int exitCode) {
        super("insight: " + msg, cause, UNLIMITED_STACK_TRACE, null);
        this.exitCode = exitCode;
    }

    @TruffleBoundary
    private InsightException(Throwable cause, Node node) {
        super(cause.getMessage(), node);
        this.exitCode = -1;
    }

    @ExportMessage
    ExceptionType getExceptionType() {
        return exitCode < 0 ? ExceptionType.RUNTIME_ERROR : ExceptionType.EXIT;
    }

    @ExportMessage
    int getExceptionExitStatus() throws UnsupportedMessageException {
        if (exitCode < 0) {
            throw UnsupportedMessageException.create();
        }
        return exitCode;
    }

    @TruffleBoundary
    static InsightException raise(Exception ex) throws InsightException {
        final String msg;
        if (ex.getMessage() == null) {
            msg = "Unexpected " + ex.getClass().getSimpleName();
        } else {
            msg = ex.getMessage().replace(System.lineSeparator(), ": ");
        }
        throw new InsightException(msg, ex, -1);
    }

    @TruffleBoundary
    static InsightException notFound(TruffleFile file) {
        throw new InsightException(file.getName() + ": No such file or directory", null, 1);
    }

    @TruffleBoundary
    static InsightException notRecognized(TruffleFile file) {
        throw new InsightException(file.getName() + ": No language to process the file. Try --polyglot", null, 1);
    }

    @TruffleBoundary
    static InsightException unknownAttribute(String type) {
        throw new InsightException("Unknown attribute " + type, null, 1);
    }

    @TruffleBoundary
    static InsightException unknownType(Throwable originalError, String str, AgentType[] values) {
        StringBuilder sb = new StringBuilder();
        sb.append("Unknown event type '").append(str).append("'. Known types are:");
        String sep = " ";
        for (AgentType t : values) {
            sb.append(sep).append("'").append(t.toString().toLowerCase(Locale.ENGLISH)).append("'");
            sep = ", ";
        }
        throw new InsightException(sb.toString(), originalError, 1);
    }

    @TruffleBoundary
    static InsightException alreadyClosed() {
        throw new InsightException("The script has already been closed", null, -1);
    }

    @TruffleBoundary
    static void throwWhenExecuted(Instrumenter instrumenter, Source source, Exception ex) {
        TruffleStackTrace.getStackTrace(ex);
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().sourceIs(source).build();
        EventBinding<?>[] waitForSourceBeingExecuted = {null};
        waitForSourceBeingExecuted[0] = instrumenter.attachExecutionEventListener(filter, new ExecutionEventListener() {
            @Override
            @TruffleBoundary
            public void onEnter(EventContext context, VirtualFrame frame) {
                waitForSourceBeingExecuted[0].dispose();
                InteropLibrary interopLib = InteropLibrary.getUncached();
                if (interopLib.isException(ex)) {
                    throw EventContextObject.rethrow((RuntimeException) ex, interopLib);
                }
                InsightException wrapper = new InsightException(ex, context.getInstrumentedNode());
                throw EventContextObject.rethrow(wrapper, interopLib);
            }

            @Override
            public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
            }

            @Override
            public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            }
        });
    }

}
