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
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;

/**
 * @see TruffleStackTraceElement To lookup the stack trace.
 */
@SuppressWarnings("serial")
final class TruffleStackTrace extends Exception {
    private static final TruffleStackTrace EMPTY = new TruffleStackTrace(Collections.emptyList(), 0);

    private List<TruffleStackTraceElement> frames;
    private final int lazyFrames;

    // contains host exception frames
    private Exception materializedHostException;

    private TruffleStackTrace(List<TruffleStackTraceElement> frames, int lazyFrames) {
        this.frames = frames;
        this.lazyFrames = lazyFrames;
    }

    /*
     * Called when an exception leaves the guest boundary and is passed to the host language. This
     * requires us to capture the host stack frames to build a polyglot stack trace. This can be
     * done lazily because if an exception stays inside a guest language (is thrown and caught in
     * the guest language) there is no need to pay the price for host frames. If the error is a non
     * TruffleException internal error then the exception (e.g. NullPointerException) has already
     * captured the host stack trace and this host exception stack trace is not used.
     */
    private void materializeHostException() {
        if (this.materializedHostException == null) {
            this.materializedHostException = new Exception();
        }
    }

    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    StackTraceElement[] getInternalStackTrace() {
        Throwable hostException = this.materializedHostException;
        if (hostException == null) {
            hostException = this;
        }
        StackTraceElement[] hostFrames = hostException.getStackTrace();
        if (lazyFrames == 0) {
            return hostFrames;
        } else {
            StackTraceElement[] extended = new StackTraceElement[hostFrames.length + lazyFrames];
            System.arraycopy(hostFrames, 0, extended, lazyFrames, hostFrames.length);
            return extended;
        }
    }

    @Override
    public String toString() {
        return "Attached Guest Language Frames (" + frames.size() + ")";
    }

    @TruffleBoundary
    static List<TruffleStackTraceElement> find(Throwable t) {
        TruffleStackTrace stack = fillIn(t);
        if (stack != null) {
            return stack.frames;
        }
        return null;
    }

    static void materializeHostFrames(Throwable t) {
        TruffleStackTrace stack = fillIn(t);
        if (stack != null) {
            stack.materializeHostException();
        }
    }

    private static LazyStackTrace findImpl(Throwable t) {
        assert !(t instanceof ControlFlowException);
        Throwable cause = t.getCause();
        while (cause != null) {
            if (cause instanceof LazyStackTrace) {
                return ((LazyStackTrace) cause);
            }
            cause = cause.getCause();
        }
        return null;
    }

    private static Throwable findInsertCause(Throwable t) {
        Throwable lastException = t;
        while (lastException != null) {
            Throwable parentCause = lastException.getCause();
            if (parentCause == null) {
                break;
            }
            lastException = parentCause;
        }
        if (lastException != null && !(lastException instanceof StackOverflowError)) {
            return lastException;
        }
        return null;
    }

    private static void insert(Throwable t, LazyStackTrace trace) {
        try {
            t.initCause(trace);
        } catch (IllegalStateException e) {
            CompilerDirectives.transferToInterpreter();
            // if the cause is initialized to null we have no chance of attaching guest language
            // stack traces
        }
    }

    @TruffleBoundary
    static TruffleStackTrace fillIn(Throwable t) {
        if (t instanceof ControlFlowException) {
            return EMPTY;
        }

        LazyStackTrace lazy = findImpl(t);
        if (lazy == null) {
            Throwable insertCause = findInsertCause(t);
            if (insertCause == null) {
                return null;
            }
            insert(insertCause, lazy = new LazyStackTrace());
        }
        if (lazy.stackTrace != null) {
            // stack trace already exists
            return lazy.stackTrace;
        }

        int stackFrameLimit;
        Node topCallSite;
        if (t instanceof TruffleException) {
            TruffleException te = (TruffleException) t;
            topCallSite = te.getLocation();
            stackFrameLimit = te.getStackTraceElementLimit();
        } else {
            topCallSite = null;
            stackFrameLimit = -1;
        }
        // add the lazily captured stack frames above the manually queried ones
        ArrayList<TracebackElement> elements = new ArrayList<>();
        TracebackElement currentElement = lazy.current;
        while (currentElement != null) {
            elements.add(currentElement);
            currentElement = currentElement.last;
        }
        Collections.reverse(elements);

        List<TruffleStackTraceElement> frames = new ArrayList<>();
        for (TracebackElement element : elements) {
            if (element.root != null) {
                frames.add(new TruffleStackTraceElement(topCallSite, element.root, element.frame));
                topCallSite = null;
            }
            if (element.callNode != null) {
                topCallSite = element.callNode;
            }
        }
        int lazyFrames = frames.size();

        // attach the remaining stack trace elements
        addStackFrames(stackFrameLimit, lazyFrames, topCallSite, frames);

        return lazy.stackTrace = new TruffleStackTrace(frames, lazyFrames);
    }

    private static final class TracebackElement {

        private final TracebackElement last;
        private final Node callNode;
        private final RootCallTarget root;
        private final MaterializedFrame frame;

        TracebackElement(TracebackElement last, Node callNode, RootCallTarget root, MaterializedFrame frame) {
            this.last = last;
            this.callNode = callNode;
            this.root = root;
            this.frame = frame;
        }
    }

    static final class LazyStackTrace extends Throwable {

        /**
         * The root of a linked list of pieces of information about the stack trace of the
         * exception. Only used, i.e., non-null, as long as the exception wasn't queried for the
         * full stack trace.
         */
        private TracebackElement current;

        /**
         * This field is initialized iff the full stack trace was queried.
         */
        private TruffleStackTrace stackTrace;

        /**
         * The number of non-internal root nodes that was traversed so far.
         */
        public int frameCount;

        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return null;
        }

        public TruffleStackTrace getInternalStackTrace() {
            return stackTrace;
        }

        @SuppressWarnings("sync-override")
        @Override
        public Throwable initCause(Throwable cause) {
            throw new IllegalAccessError("cannot change cause of TruffleException stacktrace");
        }

        @Override
        public String toString() {
            return "Attached Guest Language Frames (" + (frameCount + (stackTrace != null ? stackTrace.frames.size() : 0)) + ")";
        }
    }

    static void addStackFrameInfo(Node callNode, Throwable t, RootCallTarget root, Frame currentFrame) {
        if (t instanceof ControlFlowException) {
            // control flow exceptions should never have to get a stack trace.
            return;
        }
        if (!(t instanceof TruffleException) || ((TruffleException) t).isInternalError()) {
            // capture as much information as possible for host and internal errors
            fillIn(t);
            return;
        }

        int stackTraceElementLimit = t instanceof TruffleException ? ((TruffleException) t).getStackTraceElementLimit() : -1;

        Throwable cause = t.getCause();
        LazyStackTrace lazy;
        if (cause == null) {
            insert(t, lazy = new LazyStackTrace());
        } else if (cause instanceof LazyStackTrace) {
            lazy = (LazyStackTrace) cause;
        } else {
            addStackFrameInfoSlowPath(callNode, cause, root, currentFrame == null ? null : currentFrame.materialize(), stackTraceElementLimit);
            return;
        }
        appendLazyStackTrace(callNode, root, currentFrame, lazy, stackTraceElementLimit);
    }

    @TruffleBoundary
    private static void addStackFrameInfoSlowPath(Node callNode, Throwable t, RootCallTarget root, MaterializedFrame currentFrame, int stackTraceElementLimit) {
        LazyStackTrace lazy = findImpl(t);
        if (lazy == null) {
            Throwable insertCause = findInsertCause(t);
            if (insertCause == null) {
                // we don't have a way to store information
                return;
            }
            insert(insertCause, lazy = new LazyStackTrace());
        }
        appendLazyStackTrace(callNode, root, currentFrame, lazy, stackTraceElementLimit);
    }

    private static void appendLazyStackTrace(Node callNode, RootCallTarget root, Frame currentFrame, LazyStackTrace lazy, int stackTraceElementLimit) {
        if (lazy.stackTrace == null) {
            if (stackTraceElementLimit >= 0 && lazy.frameCount >= stackTraceElementLimit) {
                return;
            }
            boolean captureFrames = root != null && root.getRootNode().isCaptureFramesForTrace();
            lazy.current = new TracebackElement(lazy.current, callNode, root, captureFrames ? currentFrame.materialize() : null);
            if (root != null && !root.getRootNode().isInternal()) {
                lazy.frameCount++;
            }
        }
    }

    private static void addStackFrames(int stackFrameLimit, int lazyFrames, final Node topCallSite, List<TruffleStackTraceElement> frames) {
        if (stackFrameLimit >= 0 && lazyFrames >= stackFrameLimit) {
            // early exit: avoid costly iterateFrames call if enough frames have been recorded
            // lazily
            return;
        }
        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<FrameInstance>() {
            boolean first = true;
            int stackFrameIndex = lazyFrames;

            @Override
            public FrameInstance visitFrame(FrameInstance frameInstance) {
                if (stackFrameLimit >= 0 && stackFrameIndex >= stackFrameLimit) {
                    // no more frames to create
                    return frameInstance;
                }
                Node location = frameInstance.getCallNode();
                RootCallTarget target = (RootCallTarget) frameInstance.getCallTarget();
                if (first) {
                    location = topCallSite;
                    first = false;
                }
                boolean captureFrames = target != null && target.getRootNode().isCaptureFramesForTrace();
                Frame frame = captureFrames ? frameInstance.getFrame(FrameAccess.READ_ONLY) : null;
                frames.add(new TruffleStackTraceElement(location, target, frame));
                first = false;
                if (target != null && !target.getRootNode().isInternal()) {
                    stackFrameIndex++;
                }
                return null;
            }
        });
    }

}
