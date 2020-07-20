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
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import java.util.Locale;

final class InsightException extends RuntimeException implements TruffleException {
    static final long serialVersionUID = 1L;
    private final int exitCode;
    private final Node node;

    @TruffleBoundary
    private InsightException(String msg, Throwable cause, int exitCode) {
        super("insight: " + msg, cause);
        this.exitCode = exitCode;
        this.node = null;
    }

    @TruffleBoundary
    private InsightException(Throwable cause, Node node) {
        super(cause.getMessage());
        this.exitCode = -1;
        this.node = node;
    }

    @SuppressWarnings("all")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public Node getLocation() {
        return node;
    }

    @Override
    public boolean isExit() {
        return exitCode >= 0;
    }

    @Override
    public int getExitStatus() {
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
    static void throwWhenExecuted(Instrumenter instrumenter, Source source, Exception ex) {
        TruffleStackTrace.getStackTrace(ex);
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().sourceIs(source).build();
        EventBinding<?>[] waitForSourceBeingExecuted = {null};
        waitForSourceBeingExecuted[0] = instrumenter.attachExecutionEventListener(filter, new ExecutionEventListener() {
            @Override
            @TruffleBoundary
            public void onEnter(EventContext context, VirtualFrame frame) {
                waitForSourceBeingExecuted[0].dispose();
                EventContextObject obj = new EventContextObject(context);
                if (ex instanceof TruffleException && ex instanceof RuntimeException) {
                    throw obj.rethrow((RuntimeException) ex);
                }
                InsightException wrapper = new InsightException(ex, context.getInstrumentedNode());
                throw obj.rethrow(wrapper);
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
