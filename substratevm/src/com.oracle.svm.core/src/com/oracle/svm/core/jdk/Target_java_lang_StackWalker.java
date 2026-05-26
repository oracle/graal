/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.lang.invoke.MethodType;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.code.UntetheredCodeInfoAccess;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.deopt.VirtualFrame;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.interpreter.InterpreterFrameSourceInfo;
import com.oracle.svm.core.interpreter.InterpreterSupport;
import com.oracle.svm.core.interpreter.InterpreterSupport.InterpretedFrameData;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaFrame;
import com.oracle.svm.core.stack.JavaFrames;
import com.oracle.svm.core.stack.JavaStackFrameVisitor;
import com.oracle.svm.core.stack.JavaStackWalk;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.thread.ContinuationInternals;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.Target_java_lang_VirtualThread;
import com.oracle.svm.core.thread.Target_jdk_internal_vm_Continuation;
import com.oracle.svm.core.thread.Target_jdk_internal_vm_ContinuationScope;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

@TargetClass(value = java.lang.StackWalker.class)
@Platforms(InternalPlatform.NATIVE_ONLY.class)
final class Target_java_lang_StackWalker {

    /**
     * Current continuation that the stack walker is on.
     */
    @Alias //
    Target_jdk_internal_vm_Continuation continuation;

    /**
     * Target continuation scope if we're iterating a {@link Target_jdk_internal_vm_Continuation}.
     */
    @Alias //
    Target_jdk_internal_vm_ContinuationScope contScope;

    @Alias Set<Option> options;
    @Alias boolean retainClassRef;

    @Substitute
    @NeverInline("Starting a stack walk in the caller frame")
    private void forEach(Consumer<? super StackFrame> action) {
        boolean showHiddenFrames = options.contains(StackWalker.Option.SHOW_HIDDEN_FRAMES);
        boolean showReflectFrames = options.contains(StackWalker.Option.SHOW_REFLECT_FRAMES);

        JavaStackWalker.walkCurrentThread(KnownIntrinsics.readCallerStackPointer(), new JavaStackFrameVisitor() {
            @Override
            public boolean visitFrame(FrameSourceInfo frameInfo, Pointer sp) {
                if (StackTraceUtils.shouldShowFrame(frameInfo, showHiddenFrames, showReflectFrames, showHiddenFrames)) {
                    action.accept(new StackFrameImpl(frameInfo));
                }
                return true;
            }
        });
    }

    /*
     * NOTE: this implementation could be optimized by an intrinsic constant-folding operation in
     * case deep enough method inlining has happened.
     */
    @Substitute
    @NeverInline("Starting a stack walk in the caller frame")
    @SuppressWarnings("static-method")
    private Class<?> getCallerClass() {
        if (!retainClassRef) {
            throw new UnsupportedOperationException("This stack walker does not have RETAIN_CLASS_REFERENCE access");
        }

        /*
         * It is intentional that the StackWalker.options is ignored. The specification JavaDoc of
         * StackWalker.getCallerClass states:
         *
         * This method filters reflection frames, MethodHandle, and hidden frames regardless of the
         * SHOW_REFLECT_FRAMES and SHOW_HIDDEN_FRAMES options this StackWalker has been configured
         * with.
         */

        Class<?> result = StackTraceUtils.getCallerClass(KnownIntrinsics.readCallerStackPointer(), false);
        if (result == null) {
            throw new IllegalCallerException("No calling frame");
        }
        return result;
    }

    @Substitute
    @NeverInline("Starting a stack walk in the caller frame")
    private <T> T walk(Function<? super Stream<StackFrame>, ? extends T> function) {
        JavaStackWalk walk = UnsafeStackValue.get(JavaStackWalker.sizeOfJavaStackWalk());
        AbstractStackFrameSpliterator spliterator;
        if (ContinuationSupport.isSupported() && continuation != null) {
            /* Walk a yielded continuation. */
            spliterator = new ContinuationSpliterator(walk, contScope, continuation);
        } else {
            /* Walk the stack of the current thread. */
            IsolateThread isolateThread = CurrentIsolate.getCurrentThread();
            Pointer sp = KnownIntrinsics.readCallerStackPointer();
            Pointer endSP = Word.nullPointer();
            if (ContinuationSupport.isSupported() && (contScope != null || JavaThreads.isCurrentThreadVirtual())) {
                var scope = (contScope != null) ? contScope : Target_java_lang_VirtualThread.continuationScope();
                var top = Target_jdk_internal_vm_Continuation.getCurrentContinuation(scope);
                if (top != null) {
                    /* Has a delimitation scope, so we need to stop the stack walk correctly. */
                    endSP = ContinuationInternals.getBaseSP(top);
                }
            }

            spliterator = new StackFrameSpliterator(walk, isolateThread, sp, endSP);
        }

        try {
            return function.apply(StreamSupport.stream(spliterator, false));
        } finally {
            spliterator.invalidate();
        }
    }

    /**
     * The core stack-walking logic in this class must have the same behavior as
     * {@link JavaStackFrameVisitor}.
     */
    private abstract class AbstractStackFrameSpliterator implements Spliterator<StackFrame> {
        protected VirtualFrame deoptimizedVFrame;
        protected FrameInfoQueryResult vmLevelVFrame;
        protected FrameSourceInfo sourceLevelVFrame;

        @Override
        public boolean tryAdvance(Consumer<? super StackFrame> action) {
            checkState();

            boolean showHiddenFrames = options.contains(StackWalker.Option.SHOW_HIDDEN_FRAMES);
            boolean showReflectFrames = options.contains(StackWalker.Option.SHOW_REFLECT_FRAMES);

            FrameSourceInfo vFrame = null;
            while (true) {
                if (sourceLevelVFrame != null) {
                    vFrame = sourceLevelVFrame;
                    sourceLevelVFrame = sourceLevelVFrame.getCaller();
                } else if (deoptimizedVFrame != null) {
                    vFrame = translateToSourceLevelVFrames(deoptimizedVFrame.getFrameInfo(), true);
                    deoptimizedVFrame = deoptimizedVFrame.getCaller();
                } else if (vmLevelVFrame != null) {
                    vFrame = translateToSourceLevelVFrames(vmLevelVFrame, false);
                    vmLevelVFrame = vmLevelVFrame.getCaller();
                } else if (!advancePhysically()) {
                    /* No more physical frames, we are done. */
                    invalidate();
                    return false;
                }

                if (vFrame != null && shouldShowFrame(vFrame, showHiddenFrames, showReflectFrames, showHiddenFrames)) {
                    action.accept(new StackFrameImpl(vFrame));
                    return true;
                }
            }
        }

        /**
         * Translates a VM-level frame to source-level frames and remembers any remaining translated
         * caller frames in {@link #sourceLevelVFrame}. The {@code deoptimizedFrame} flag identifies
         * virtual frames from a {@link DeoptimizedFrame}; those frames do not have their own physical
         * stack pointer, so implementations must avoid using the current physical frame SP for them.
         * Regular VM-level frames pass {@code false} and may use their current physical frame SP.
         */
        private FrameSourceInfo translateToSourceLevelVFrames(FrameInfoQueryResult vFrame, boolean deoptimizedFrame) {
            if (InterpreterSupport.isEnabled()) {
                FrameSourceInfo sourceVFrame = getSourceLevelVFrames(vFrame, deoptimizedFrame);
                if (vFrame != sourceVFrame) {
                    this.sourceLevelVFrame = sourceVFrame == null ? null : sourceVFrame.getCaller();
                    return sourceVFrame;
                }
            }
            return vFrame;
        }

        @Override
        public Spliterator<StackFrame> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return Spliterator.IMMUTABLE | Spliterator.NONNULL | Spliterator.ORDERED;
        }

        @Uninterruptible(reason = "Wraps the now safe call to query frame information.", calleeMustBe = false)
        protected static CodeInfoQueryResult queryCodeInfoInterruptibly(CodeInfo info, CodePointer ip) {
            return CodeInfoTable.lookupCodeInfoQueryResult(info, ip);
        }

        private static boolean shouldShowFrame(FrameSourceInfo frameInfo, boolean showLambdaFrames, boolean showReflectFrames, boolean showHiddenFrames) {
            return StackTraceUtils.shouldShowFrame(frameInfo, showLambdaFrames, showReflectFrames, showHiddenFrames);
        }

        protected abstract void invalidate();

        protected abstract void checkState();

        protected abstract boolean advancePhysically();

        protected abstract FrameSourceInfo getSourceLevelVFrames(FrameInfoQueryResult vFrame, boolean deoptimizedFrame);
    }

    final class ContinuationSpliterator extends AbstractStackFrameSpliterator {
        private final Target_jdk_internal_vm_ContinuationScope contScope;

        private boolean initialized;
        private JavaStackWalk walk;
        private Target_jdk_internal_vm_Continuation continuation;
        private StoredContinuation stored;
        private final InterpretedFrameData interpretedFrameData = new InterpretedFrameData();

        ContinuationSpliterator(JavaStackWalk walk, Target_jdk_internal_vm_ContinuationScope contScope, Target_jdk_internal_vm_Continuation continuation) {
            assert walk.isNonNull();
            this.walk = walk;
            this.contScope = contScope;
            this.continuation = continuation;
        }

        @Override
        protected boolean advancePhysically() {
            assert continuation != null;

            do {
                if (contScope != null) {
                    /* Navigate to the continuation that matches the scope. */
                    while (continuation != null && continuation.getScope() != contScope) {
                        continuation = continuation.getParent();
                    }
                }

                if (continuation == null) {
                    return false;
                }

                /*
                 * Store the StoredContinuation object in a field to avoid problems in case that the
                 * continuation continues execution in another thread in the meanwhile.
                 */
                stored = ContinuationInternals.getStoredContinuation(continuation);
                if (stored == null) {
                    return false;
                }

                if (advancePhysically0()) {
                    return true;
                } else {
                    /*
                     * We reached the end of the current continuation. Try to continue in the
                     * parent.
                     */
                    continuation = continuation.getParent();
                    initialized = false;
                }
            } while (continuation != null);

            return false;
        }

        @Uninterruptible(reason = "Prevent GC while in this method.")
        private boolean advancePhysically0() {
            if (initialized) {
                /*
                 * Because we are interruptible in between walking frames, pointers into the stored
                 * continuation become invalid if a garbage collection moves the object. So, we need
                 * to update all cached stack pointer values before we can continue the walk.
                 */
                JavaStackWalker.updateStackPointerForContinuation(walk, stored);
            } else {
                initialized = true;
                JavaStackWalker.initializeForContinuation(walk, stored);
            }

            if (!JavaStackWalker.advanceForContinuation(walk, stored)) {
                return false;
            }

            JavaFrame frame = getCurrentFrame();
            UntetheredCodeInfo untetheredInfo = frame.getIPCodeInfo();
            VMError.guarantee(UntetheredCodeInfoAccess.isAOTImageCode(untetheredInfo));

            Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
            try {
                CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
                /* This interruptible call might trigger a GC that moves continuation objects. */
                CodeInfoQueryResult physicalFrame = queryCodeInfoInterruptibly(info, frame.getIP());
                vmLevelVFrame = physicalFrame.getFrameInfo();
            } finally {
                CodeInfoAccess.releaseTether(untetheredInfo, tether);
            }
            return true;
        }

        @Override
        protected void invalidate() {
            walk = Word.nullPointer();
            continuation = null;
            stored = null;
        }

        @Override
        protected void checkState() {
            if (walk.isNull()) {
                throw new IllegalStateException("Continuation traversal no longer valid");
            }
        }

        @Override
        protected FrameSourceInfo getSourceLevelVFrames(FrameInfoQueryResult vFrame, boolean deoptimizedFrame) {
            assert InterpreterSupport.isEnabled();

            captureInterpretedFrameData(vFrame);
            FrameSourceInfo result = JavaStackFrameVisitor.getSourceLevelVFrames(vFrame, Word.nullPointer(), interpretedFrameData);
            interpretedFrameData.clear();
            return result;
        }

        /**
         * Captures stack-walking-related data for the given VM-level virtual frame in
         * {@link #interpretedFrameData}. This needs to be done while in {@link Uninterruptible}
         * code because a raw stack pointer is used to access the {@link StoredContinuation}.
         */
        @Uninterruptible(reason = "StoredContinuation must not move.")
        private void captureInterpretedFrameData(FrameInfoQueryResult vFrame) {
            assert InterpreterSupport.isEnabled();
            if (vFrame == null) {
                return;
            }

            /* Update the SP as the stored continuation object may have moved. */
            JavaStackWalker.updateStackPointerForContinuation(walk, stored);
            Pointer sp = getCurrentFrame().getSP();

            InterpreterSupport support = InterpreterSupport.singleton();
            if (support.isInterpreterRoot(vFrame)) {
                support.captureInterpretedMethodFrameInfo(vFrame, sp, interpretedFrameData);
            }
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private JavaFrame getCurrentFrame() {
            JavaFrame frame = JavaStackWalker.getCurrentFrame(walk);
            VMError.guarantee(!JavaFrames.isEntryPoint(frame), "Entry point frames are not supported");
            VMError.guarantee(!JavaFrames.isUnknownFrame(frame), "Stack walk must not encounter unknown frame");
            VMError.guarantee(!Deoptimizer.checkIsDeoptimized(frame), "Deoptimized frames are not supported");
            return frame;
        }
    }

    final class StackFrameSpliterator extends AbstractStackFrameSpliterator {
        private final IsolateThread thread;
        private final Pointer startSP;
        private final Pointer endSP;

        private boolean initialized;
        private JavaStackWalk walk;

        StackFrameSpliterator(JavaStackWalk walk, IsolateThread thread, Pointer startSP, Pointer endSP) {
            this.initialized = false;
            this.walk = walk;
            this.thread = thread;
            this.startSP = startSP;
            this.endSP = endSP;
        }

        @Override
        protected void invalidate() {
            walk = Word.nullPointer();
        }

        @Override
        protected void checkState() {
            if (walk.isNull()) {
                throw new IllegalStateException("Stack traversal no longer valid");
            } else if (thread != CurrentIsolate.getCurrentThread()) {
                throw new IllegalStateException("Invalid thread");
            }
        }

        @Override
        @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
        protected boolean advancePhysically() {
            if (!initialized) {
                initialized = true;
                JavaStackWalker.initialize(walk, thread, startSP, endSP);
            }

            if (!JavaStackWalker.advance(walk, thread)) {
                return false;
            }

            JavaFrame frame = getCurrentFrame();
            DeoptimizedFrame deoptimizedFrame = Deoptimizer.checkEagerDeoptimized(frame);
            if (deoptimizedFrame != null) {
                this.deoptimizedVFrame = deoptimizedFrame.getTopFrame();
            } else {
                UntetheredCodeInfo untetheredInfo = frame.getIPCodeInfo();
                Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
                try {
                    CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
                    CodeInfoQueryResult physicalFrame = queryCodeInfoInterruptibly(info, frame.getIP());
                    vmLevelVFrame = physicalFrame.getFrameInfo();
                } finally {
                    CodeInfoAccess.releaseTether(untetheredInfo, tether);
                }
            }

            return true;
        }

        @Override
        protected FrameSourceInfo getSourceLevelVFrames(FrameInfoQueryResult vFrame, boolean deoptimizedFrame) {
            assert InterpreterSupport.isEnabled();

            /*
             * A deoptimized virtual frame does not have its own physical stack pointer. The
             * current physical frame SP belongs to the deopt stub frame, so pass the null sentinel
             * and fail loudly if source-frame translation tries to use it.
             */
            Pointer sp = deoptimizedFrame ? Word.nullPointer() : getCurrentFrame().getSP();
            return JavaStackFrameVisitor.getSourceLevelVFrames(vFrame, sp, null);
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private JavaFrame getCurrentFrame() {
            JavaFrame frame = JavaStackWalker.getCurrentFrame(walk);
            VMError.guarantee(!JavaFrames.isUnknownFrame(frame), "Stack walk must not encounter unknown frame");
            return frame;
        }
    }

    final class StackFrameImpl implements StackWalker.StackFrame {
        private final FrameSourceInfo frameInfo;
        private StackTraceElement ste;

        StackFrameImpl(FrameSourceInfo frameInfo) {
            this.frameInfo = frameInfo;
        }

        @Override
        public String getClassName() {
            return frameInfo.getSourceClassName();
        }

        @Override
        public String getMethodName() {
            return frameInfo.getSourceMethodName();
        }

        @Override
        public MethodType getMethodType() {
            if (!retainClassRef) {
                throw new UnsupportedOperationException("This stack walker does not have RETAIN_CLASS_REFERENCE access");
            }
            return MethodType.fromMethodDescriptorString(getDescriptor(), DynamicHub.fromClass(getDeclaringClass()).getClassLoader());
        }

        @Override
        public String getDescriptor() {
            if (frameInfo instanceof FrameInfoQueryResult frameInfoQueryResult) {
                String descriptor = frameInfoQueryResult.getSourceMethodSignature();
                if (descriptor == null) {
                    throw new UnsupportedOperationException("Method descriptor metadata is not available for this stack frame");
                }
                return descriptor;
            }
            if (InterpreterSupport.isEnabled() && frameInfo instanceof InterpreterFrameSourceInfo interpreterFrameSourceInfo) {
                return interpreterFrameSourceInfo.getInterpretedMethod().getSignature().toMethodDescriptor();
            }
            throw VMError.shouldNotReachHere("Unknown frame source info type: " + frameInfo.getClass().getName());
        }

        @Override
        public Class<?> getDeclaringClass() {
            if (!retainClassRef) {
                throw new UnsupportedOperationException("This stack walker does not have RETAIN_CLASS_REFERENCE access");
            }
            return frameInfo.getSourceClass();
        }

        @Override
        public int getByteCodeIndex() {
            return frameInfo.getBci();
        }

        @Override
        public String getFileName() {
            return frameInfo.getSourceFileName();
        }

        @Override
        public int getLineNumber() {
            return frameInfo.getSourceLineNumber();
        }

        @Override
        public boolean isNativeMethod() {
            return frameInfo.isNativeMethod();
        }

        @Override
        public StackTraceElement toStackTraceElement() {
            if (ste == null) {
                ste = frameInfo.getSourceReference();
            }
            return ste;
        }

        @Override
        public String toString() {
            return toStackTraceElement().toString();
        }
    }
}

@TargetClass(className = "java.lang.StackFrameInfo")
@Delete
final class Target_java_lang_StackFrameInfo {
}

@TargetClass(className = "java.lang.StackStreamFactory")
@Delete
final class Target_java_lang_StackStreamFactory {
}
