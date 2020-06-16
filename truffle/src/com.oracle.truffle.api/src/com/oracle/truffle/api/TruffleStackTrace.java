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
package com.oracle.truffle.api;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import com.oracle.truffle.api.nodes.RootNode;

import sun.misc.Unsafe;

/**
 * Represents a guest language stack trace.
 *
 * A TruffleStackTrace is automatically added when a {@link Throwable} passes through a
 * {@link CallTarget call target}. {@link ControlFlowException} and {@link PolyglotException} do not
 * get a TruffleStackTrace. Other {@link Throwable} are added a TruffleStackTrace, as long as there
 * is a {@code null} {@link Throwable#getCause() cause} available to insert the TruffleStackTrace.
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

    private static final long causeFieldIndex;
    private static final sun.misc.Unsafe UNSAFE;

    static {
        Unsafe unsafe;
        try {
            unsafe = Unsafe.getUnsafe();
        } catch (SecurityException e) {
            try {
                Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeInstance.setAccessible(true);
                unsafe = (Unsafe) theUnsafeInstance.get(Unsafe.class);
            } catch (Exception e2) {
                throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e2);
            }
        }
        UNSAFE = unsafe;

        try {
            Field causeField = Throwable.class.getDeclaredField("cause");
            causeFieldIndex = UNSAFE.objectFieldOffset(causeField);
        } catch (NoSuchFieldException | SecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private static Throwable getCause(Throwable t) {
        try {
            Throwable result = (Throwable) UNSAFE.getObject(t, causeFieldIndex);
            return result == t ? null : result;
        } catch (IllegalArgumentException e) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException(e);
        }
    }

    private static void initCause(Throwable t, Throwable value) {
        try {
            UNSAFE.putObject(t, causeFieldIndex, value);
        } catch (IllegalArgumentException e) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException(e);
        }
    }

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
     * trace elements that are filled in can be customized by implementing
     * {@link TruffleException#getStackTraceElementLimit()}.
     *
     * @param throwable the throwable instance to look for guest language frames
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
        assert hasContext(target);
        return LanguageAccessor.ACCESSOR.nodeSupport().findAsynchronousFrames(target, frame);
    }

    @SuppressWarnings("unchecked")
    private static boolean hasContext(CallTarget target) {
        RootNode root = ((RootCallTarget) target).getRootNode();
        Object polyglotLanguage = LanguageAccessor.ACCESSOR.nodeSupport().getPolyglotLanguage(root.getLanguageInfo());
        return LanguageAccessor.ACCESSOR.engineSupport().getCurrentContextReference(polyglotLanguage).get() != null;
    }

    static void materializeHostFrames(Throwable t) {
        TruffleStackTrace stack = fillIn(t);
        if (stack != null) {
            stack.materializeHostException();
        }
    }

    private static LazyStackTrace findImpl(Throwable t) {
        assert !(t instanceof ControlFlowException);
        Throwable cause = getCause(t);
        while (cause != null) {
            if (cause instanceof LazyStackTrace) {
                return ((LazyStackTrace) cause);
            }
            cause = getCause(cause);
        }
        return null;
    }

    private static Throwable findInsertCause(Throwable t) {
        Throwable lastException = t;
        while (lastException != null) {
            Throwable parentCause = getCause(lastException);
            if (parentCause == null) {
                break;
            }
            lastException = parentCause;
        }
        return lastException;
    }

    private static void insert(Throwable t, LazyStackTrace trace) {
        if (getCause(t) != null) {
            CompilerDirectives.transferToInterpreter();
            // if the cause is initialized to null we have no chance of attaching guest language
            // stack traces
        } else {
            initCause(t, trace);
        }
    }

    /**
     * Fills in the guest language stack frames from the current frames on the stack. If the stack
     * was already filled before then this method has no effect. The implementation attaches a
     * lightweight exception object to the last location in the {@link Throwable#getCause() cause}
     * chain of the exception. The number stack trace elements that are filled in can be customized
     * by implementing {@link TruffleException#getStackTraceElementLimit()}.
     *
     * @param throwable the Throwable to fill
     * @since 19.0
     */
    @TruffleBoundary
    public static TruffleStackTrace fillIn(Throwable throwable) {
        if (throwable instanceof ControlFlowException) {
            return EMPTY;
        }
        LazyStackTrace lazy = findImpl(throwable);
        if (lazy == null) {
            Throwable insertCause = findInsertCause(throwable);
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
        if (throwable instanceof TruffleException) {
            TruffleException te = (TruffleException) throwable;
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
        if (currentFrame != null && root.getRootNode().isCaptureFramesForTrace()) {
            callInnerAddStackFrameInfo(isTProfiled, callNode, root, t, currentFrame.materialize());
        } else {
            callInnerAddStackFrameInfo(isTProfiled, callNode, root, t, null);
        }
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
        if (!(t instanceof TruffleException) || ((TruffleException) t).isInternalError()) {
            // capture as much information as possible for host and internal errors
            fillIn(t);
            return;
        }

        int stackTraceElementLimit = ((TruffleException) t).getStackTraceElementLimit();

        Throwable cause = getCause(t);
        LazyStackTrace lazy;
        if (cause == null) {
            insert(t, lazy = new LazyStackTrace());
        } else if (cause instanceof LazyStackTrace) {
            lazy = (LazyStackTrace) cause;
        } else {
            addStackFrameInfoSlowPath(callNode, root, cause, currentFrame, stackTraceElementLimit);
            return;
        }
        appendLazyStackTrace(callNode, root, currentFrame, lazy, stackTraceElementLimit);
    }

    @TruffleBoundary
    private static void addStackFrameInfoSlowPath(Node callNode, RootCallTarget root, Throwable t, MaterializedFrame currentFrame, int stackTraceElementLimit) {
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

    private static void appendLazyStackTrace(Node callNode, RootCallTarget root, MaterializedFrame currentFrame, LazyStackTrace lazy, int stackTraceElementLimit) {
        if (lazy.stackTrace == null) {
            if (stackTraceElementLimit >= 0 && lazy.frameCount >= stackTraceElementLimit) {
                return;
            }
            lazy.current = new TracebackElement(lazy.current, callNode, root, currentFrame);
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
