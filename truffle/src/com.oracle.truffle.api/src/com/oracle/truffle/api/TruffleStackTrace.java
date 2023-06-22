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
package com.oracle.truffle.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import org.graalvm.polyglot.PolyglotException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;

/**
 * Represents a guest language stack trace.
 *
 * A TruffleStackTrace is automatically added when a {@link Throwable} passes through a
 * {@link CallTarget call target}. {@link ControlFlowException} and {@link PolyglotException} do not
 * get a TruffleStackTrace. An internal or host {@link Throwable} is added a TruffleStackTrace, as
 * long as suppression is not disabled for this throwable, via a Throwable added to the list of
 * {@linkplain Throwable#addSuppressed(Throwable) suppressed exceptions}.
 * <p>
 * A guest language stack trace element is automatically added by the Truffle runtime every time the
 * {@link Throwable} passes through a {@link CallTarget call target}. This is incremental and
 * therefore efficient if the exception is later caught in the same compilation unit.
 * <p>
 * Note that if the Throwable is caught, its stack trace should be filled eagerly with
 * {@link #fillIn(Throwable)}, unless it can be guaranteed to be re-thrown in the same
 * {@link CallTarget call target}, or that the stack trace will not be used.
 *
 * @see #getStackTrace(Throwable) getStackTrace(Throwable) to retrieve the guest language stack
 *      trace from a {@link Throwable}.
 * @since 19.0
 */
@SuppressWarnings("serial")
public final class TruffleStackTrace extends Exception {

    private static final TruffleStackTrace EMPTY = new TruffleStackTrace(Collections.emptyList(), 0);

    private List<TruffleStackTraceElement> frames;
    private final int lazyFrames;

    // contains host exception frames
    private Throwable materializedHostException;

    private TruffleStackTrace(List<TruffleStackTraceElement> frames, int lazyFrames) {
        this.frames = frames;
        this.lazyFrames = lazyFrames;
    }

    /**
     * Called when an exception leaves the guest boundary and is passed to the host language. This
     * requires us to capture the host stack frames to build a polyglot stack trace. This can be
     * done lazily because if an exception stays inside a guest language (is thrown and caught in
     * the guest language) there is no need to pay the price for host frames. If the error is an
     * internal error then the exception (e.g. NullPointerException) has already captured the host
     * stack trace and this host exception stack trace is not used.
     */
    private void materializeHostException() {
        if (this.materializedHostException == null) {
            this.materializedHostException = new Exception();
        }
    }

    /**
     * @since 19.0
     */
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

    /**
     * @since 19.0
     */
    @Override
    public String toString() {
        return "Attached Guest Language Frames (" + frames.size() + ")";
    }

    /**
     * Returns the guest language frames that are stored in this throwable or <code>null</code> if
     * no guest language frames can ever be stored in this throwable. This method fills in the
     * stacktrace by calling {@link #fillIn(Throwable)}, so it is not necessary to call
     * {@link #fillIn(Throwable)} before. The returned list is not modifiable. The number of stack
     * trace elements that are filled in can be customized by the {@code stackTraceElementLimit}
     * parameter of the
     * {@link com.oracle.truffle.api.exception.AbstractTruffleException#AbstractTruffleException(String, Throwable, int, Node)
     * AbstractTruffleException constructor}.
     *
     * @param throwable the {@link Throwable} instance to look for guest language frames
     * @throws NullPointerException if the {@link Throwable} is <code>null</code>
     * @since 19.0
     */
    @TruffleBoundary
    public static List<TruffleStackTraceElement> getStackTrace(Throwable throwable) {
        TruffleStackTrace stack = fillIn(throwable);
        if (stack != null) {
            return stack.frames;
        }
        return null;
    }

    /**
     * Returns asynchronous guest language stack frames that led to the execution of given
     * {@link CallTarget} on the given {@link Frame}. Returns <code>null</code> if no asynchronous
     * stack is known. Call this with a context entered only.
     * <p>
     * Languages might not provide asynchronous stack frames by default for performance reasons.
     * Instruments might need to instruct languages to provide the asynchronous stacks.
     *
     * @return a list of asynchronous frames, or <code>null</code>.
     * @since 20.1.0
     */
    @TruffleBoundary
    public static List<TruffleStackTraceElement> getAsynchronousStackTrace(CallTarget target, Frame frame) {
        Objects.requireNonNull(target, "CallTarget must not be null");
        Objects.requireNonNull(frame, "Frame must not be null");
        assert LanguageAccessor.ENGINE.hasCurrentContext();
        return LanguageAccessor.ACCESSOR.nodeSupport().findAsynchronousFrames(target, frame);
    }

    private static LazyStackTrace findImpl(Throwable t) {
        assert !(t instanceof ControlFlowException);
        for (Throwable suppressed : t.getSuppressed()) {
            if (suppressed instanceof LazyStackTrace) {
                return (LazyStackTrace) suppressed;
            }
        }
        return null;
    }

    /**
     * Fills in the guest language stack frames from the current frames on the stack. If the stack
     * was already filled before then this method has no effect. The number stack trace elements
     * that are filled in can be customized by the {@code stackTraceElementLimit} parameter of the
     * {@link com.oracle.truffle.api.exception.AbstractTruffleException#AbstractTruffleException(String, Throwable, int, Node)
     * AbstractTruffleException constructor}.
     *
     * The implementation attaches a lightweight exception object as a suppressed exception to
     * internal and host (but not guest) exceptions.
     *
     * @param throwable the {@link Throwable} to fill
     * @throws NullPointerException if the {@link Throwable} is null
     * @since 19.0
     */
    @TruffleBoundary
    public static TruffleStackTrace fillIn(Throwable throwable) {
        Objects.requireNonNull(throwable);
        if (throwable instanceof ControlFlowException) {
            return EMPTY;
        }
        LazyStackTrace lazy = getOrCreateLazyStackTrace(throwable);
        if (lazy.stackTrace != null) {
            // stack trace already exists
            return lazy.stackTrace;
        }

        int stackFrameLimit;
        Node topCallSite;
        boolean isTruffleException = LanguageAccessor.EXCEPTIONS.isException(throwable);
        if (isTruffleException) {
            topCallSite = LanguageAccessor.EXCEPTIONS.getLocation(throwable);
            stackFrameLimit = LanguageAccessor.EXCEPTIONS.getStackTraceElementLimit(throwable);
        } else {
            topCallSite = null;
            stackFrameLimit = -1;
        }
        // add the lazily captured stack frames above the manually queried ones
        List<TracebackElement> elements = new ArrayList<>();
        TracebackElement currentElement = lazy.current;
        while (currentElement != null) {
            elements.add(currentElement);
            currentElement = currentElement.last;
        }

        List<TruffleStackTraceElement> frames = new ArrayList<>();
        for (ListIterator<TracebackElement> iterator = elements.listIterator(elements.size()); iterator.hasPrevious();) {
            TracebackElement element = iterator.previous();
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

        TruffleStackTrace fullStackTrace = new TruffleStackTrace(frames, lazyFrames);
        // capture host stack trace for guest language exceptions;
        // internal and host language exceptions already have a stack trace attached.
        if (isTruffleException && !isHostException(throwable)) {
            fullStackTrace.materializeHostException();
        }
        lazy.stackTrace = fullStackTrace;
        return fullStackTrace;
    }

    private static boolean isHostException(Throwable throwable) {
        Object polyglotEngine = LanguageAccessor.ENGINE.getCurrentPolyglotEngine();
        return polyglotEngine != null && LanguageAccessor.ENGINE.getHostService(polyglotEngine).isHostException(throwable);
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

        LazyStackTrace() {
            super(null, null, false, false);
        }

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

        public TruffleStackTrace getInternalStackTrace() {
            return stackTrace;
        }

        @Override
        public String toString() {
            return "Attached Guest Language Frames (" + (frameCount + (stackTrace != null ? stackTrace.frames.size() : 0)) + ")";
        }
    }

    static void addStackFrameInfo(Node callNode, RootCallTarget root, Throwable t, Frame currentFrame) {
        if (t instanceof ControlFlowException) {
            // control flow exceptions should never have to get a stack trace.
            return;
        }
        if (t instanceof PolyglotException) {
            // Normally, polyglot exceptions should not even end up here, with the exception of
            // those thrown by Value call targets. In any case, we do not want to attach a cause.
            return;
        }

        boolean isTProfiled = CompilerDirectives.isPartialEvaluationConstant(t.getClass());
        MaterializedFrame frame = null;
        if (currentFrame != null && root.getRootNode().isCaptureFramesForTrace()) {
            frame = currentFrame.materialize();
        }
        callInnerAddStackFrameInfo(isTProfiled, callNode, root, t, frame);
    }

    private static void callInnerAddStackFrameInfo(boolean isTProfiled, Node callNode, RootCallTarget root, Throwable t, MaterializedFrame currentFrame) {
        if (isTProfiled) {
            innerAddStackFrameInfo(callNode, root, t, currentFrame);
        } else {
            innerAddStackFrameInfoBoundary(callNode, root, t, currentFrame);
        }
    }

    @TruffleBoundary
    private static void innerAddStackFrameInfoBoundary(Node callNode, RootCallTarget root, Throwable t, MaterializedFrame currentFrame) {
        innerAddStackFrameInfo(callNode, root, t, currentFrame);
    }

    private static void innerAddStackFrameInfo(Node callNode, RootCallTarget root, Throwable t, MaterializedFrame currentFrame) {
        if (!LanguageAccessor.EXCEPTIONS.isException(t)) {
            /*
             * Capture as much information as possible for internal errors. This branch should not
             * be reached by host exceptions as they should have already been wrapped in a
             * HostException in the guest-to-host call root node.
             */
            fillIn(t);
            return;
        }

        int stackTraceElementLimit = LanguageAccessor.EXCEPTIONS.getStackTraceElementLimit(t);
        LazyStackTrace lazy = (LazyStackTrace) LanguageAccessor.EXCEPTIONS.getLazyStackTrace(t);
        if (lazy == null) {
            lazy = new LazyStackTrace();
            LanguageAccessor.EXCEPTIONS.setLazyStackTrace(t, lazy);
        }
        appendLazyStackTrace(callNode, root, currentFrame, lazy, stackTraceElementLimit);
    }

    @TruffleBoundary
    static LazyStackTrace getOrCreateLazyStackTrace(Throwable throwable) {
        LazyStackTrace lazy;
        if (LanguageAccessor.EXCEPTIONS.isException(throwable)) {
            lazy = (LazyStackTrace) LanguageAccessor.EXCEPTIONS.getLazyStackTrace(throwable);
            if (lazy == null) {
                lazy = new LazyStackTrace();
                LanguageAccessor.EXCEPTIONS.setLazyStackTrace(throwable, lazy);
            }
        } else {
            lazy = findImpl(throwable);
            if (lazy == null) {
                lazy = new LazyStackTrace();
                if (!tryAddSuppressed(throwable, lazy)) {
                    // Avoid attempt to capture a lazy stack trace for immutable exceptions.
                    lazy.stackTrace = EMPTY;
                }
            }
        }
        return lazy;
    }

    private static boolean tryAddSuppressed(Throwable throwable, LazyStackTrace lazy) {
        if (throwable instanceof StackOverflowError || throwable instanceof OutOfMemoryError) {
            /*
             * These VM errors are immutable if thrown by the JVM. Regardless, we treat them as
             * immutable if manually constructed, too. This is useful for singleton errors that
             * don't have suppression disabled but should have.
             */
            return false;
        }
        throwable.addSuppressed(lazy);
        if (throwable.getSuppressed().length == 0) {
            // Suppression has been disabled for this exception.
            return false;
        }
        return true;
    }

    private static void appendLazyStackTrace(Node callNode, RootCallTarget root, MaterializedFrame currentFrame, LazyStackTrace lazy, int stackTraceElementLimit) {
        if (lazy.stackTrace == null) {
            if (stackTraceElementLimit >= 0 && lazy.frameCount >= stackTraceElementLimit) {
                return;
            }
            lazy.current = new TracebackElement(lazy.current, callNode, root, currentFrame);
            if (root != null && LanguageAccessor.ACCESSOR.nodeSupport().countsTowardsStackTraceLimit(root.getRootNode())) {
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
                if (target != null && LanguageAccessor.ACCESSOR.nodeSupport().countsTowardsStackTraceLimit(target.getRootNode())) {
                    stackFrameIndex++;
                }
                return null;
            }
        });
    }

}
