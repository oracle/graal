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
package com.oracle.truffle.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;

/**
 * @see TruffleStackTraceElement To lookup the stack trace.
 */
@SuppressWarnings("serial")
final class TruffleStackTrace extends Exception {

    private static final TruffleStackTrace EMPTY = new TruffleStackTrace(Collections.emptyList());

    private List<TruffleStackTraceElement> frames;

    private TruffleStackTrace(List<TruffleStackTraceElement> frames) {
        this.frames = frames;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    @TruffleBoundary
    static List<TruffleStackTraceElement> find(Throwable t) {
        TruffleStackTrace stack = findImpl(t);
        if (stack != null) {
            return stack.frames;
        }
        return null;
    }

    private static TruffleStackTrace findImpl(Throwable t) {
        if (t instanceof ControlFlowException) {
            // control flow exceptions should never have to get a stack trace.
            return EMPTY;
        }
        Throwable cause = t.getCause();
        while (cause != null) {
            if (cause instanceof TruffleStackTrace) {
                return ((TruffleStackTrace) cause);
            }
            cause = cause.getCause();
        }
        return null;
    }

    @TruffleBoundary
    static void fillIn(Throwable t) {
        TruffleStackTrace stack = findImpl(t);
        if (stack == null) {
            int stackFrameLimit;
            final Node topCallSite;
            if (t instanceof TruffleException) {
                TruffleException te = (TruffleException) t;
                topCallSite = te.getLocation();
                stackFrameLimit = te.getStackTraceElementLimit();
            } else {
                topCallSite = null;
                stackFrameLimit = -1;
            }

            List<TruffleStackTraceElement> frames = new ArrayList<>();
            Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<FrameInstance>() {
                boolean first = true;
                int stackFrameIndex = 0;

                @Override
                public FrameInstance visitFrame(FrameInstance frameInstance) {
                    if (stackFrameIndex == stackFrameLimit) {
                        // no more frames to create
                        return frameInstance;
                    }
                    Node location = frameInstance.getCallNode();
                    RootCallTarget target = (RootCallTarget) frameInstance.getCallTarget();
                    if (first) {
                        location = topCallSite;
                        first = false;
                    }
                    frames.add(new TruffleStackTraceElement(location, target));
                    first = false;
                    if (!target.getRootNode().isInternal()) {
                        stackFrameIndex++;
                    }
                    return null;
                }
            });
            insert(t, Collections.unmodifiableList(frames));
        }
    }

    private static void insert(Throwable t, List<TruffleStackTraceElement> frames) {
        Throwable lastException = t;
        while (lastException != null) {
            Throwable parentCause = lastException.getCause();
            if (parentCause == null) {
                break;
            }
            lastException = parentCause;
        }
        if (lastException != null) {
            lastException.initCause(new TruffleStackTrace(frames));
        }
    }

}
