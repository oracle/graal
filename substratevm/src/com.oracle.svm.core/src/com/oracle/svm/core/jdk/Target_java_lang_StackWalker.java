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

import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.oracle.svm.core.code.FrameSourceInfo;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.code.UntetheredCodeInfoAccess;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.heap.StoredContinuation;
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
import com.oracle.svm.core.util.VMError;

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
            public boolean visitFrame(FrameSourceInfo frameInfo) {
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
            Pointer endSP = WordFactory.nullPointer();
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

    private abstract class AbstractStackFrameSpliterator implements Spliterator<StackFrame> {
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

        protected DeoptimizedFrame.VirtualFrame deoptimizedVFrame;
        protected FrameInfoQueryResult regularVFrame;

        @Override
        public boolean tryAdvance(Consumer<? super StackFrame> action) {
            checkState();

            boolean showHiddenFrames = options.contains(StackWalker.Option.SHOW_HIDDEN_FRAMES);
            boolean showReflectFrames = options.contains(StackWalker.Option.SHOW_REFLECT_FRAMES);

            while (true) {
                /* Check if we have pending virtual frames to process. */
                if (deoptimizedVFrame != null) {
                    FrameInfoQueryResult frameInfo = deoptimizedVFrame.getFrameInfo();
                    deoptimizedVFrame = deoptimizedVFrame.getCaller();

                    if (shouldShowFrame(frameInfo, showHiddenFrames, showReflectFrames, showHiddenFrames)) {
                        action.accept(new StackFrameImpl(frameInfo));
                        return true;
                    }

                } else if (regularVFrame != null) {
                    FrameInfoQueryResult frameInfo = regularVFrame;
                    regularVFrame = frameInfo.getCaller();

                    if (shouldShowFrame(frameInfo, showHiddenFrames, showReflectFrames, showHiddenFrames)) {
                        action.accept(new StackFrameImpl(frameInfo));
                        return true;
                    }

                } else if (!advancePhysically()) {
                    /* No more physical frames, we are done. */
                    invalidate();
                    return false;
                }
            }
        }

        private static boolean shouldShowFrame(FrameInfoQueryResult frameInfo, boolean showLambdaFrames, boolean showReflectFrames, boolean showHiddenFrames) {
            return StackTraceUtils.shouldShowFrame(frameInfo, showLambdaFrames, showReflectFrames, showHiddenFrames);
        }

        protected abstract void invalidate();

        protected abstract void checkState();

        protected abstract boolean advancePhysically();
    }

    final class ContinuationSpliterator extends AbstractStackFrameSpliterator {
        private final Target_jdk_internal_vm_ContinuationScope contScope;

        private boolean initialized;
        private JavaStackWalk walk;
        private Target_jdk_internal_vm_Continuation continuation;
        private StoredContinuation stored;

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

            JavaFrame frame = JavaStackWalker.getCurrentFrame(walk);
            VMError.guarantee(!JavaFrames.isEntryPoint(frame), "Entry point frames are not supported");
            VMError.guarantee(!JavaFrames.isUnknownFrame(frame), "Stack walk must not encounter unknown frame");
            VMError.guarantee(Deoptimizer.checkDeoptimized(frame) == null, "Deoptimized frames are not supported");

            UntetheredCodeInfo untetheredInfo = frame.getIPCodeInfo();
            VMError.guarantee(UntetheredCodeInfoAccess.isAOTImageCode(untetheredInfo));

            Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
            try {
                CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
                /* This interruptible call might trigger a GC that moves continuation objects. */
                CodeInfoQueryResult physicalFrame = queryCodeInfoInterruptibly(info, frame.getIP());
                regularVFrame = physicalFrame.getFrameInfo();
            } finally {
                CodeInfoAccess.releaseTether(untetheredInfo, tether);
            }
            return true;
        }

        @Override
        protected void invalidate() {
            walk = WordFactory.nullPointer();
            continuation = null;
            stored = null;
        }

        @Override
        protected void checkState() {
            if (walk.isNull()) {
                throw new IllegalStateException("Continuation traversal no longer valid");
            }
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
            walk = WordFactory.nullPointer();
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

            JavaFrame frame = JavaStackWalker.getCurrentFrame(walk);
            VMError.guarantee(!JavaFrames.isUnknownFrame(frame), "Stack walk must not encounter unknown frame");

            DeoptimizedFrame deoptimizedFrame = Deoptimizer.checkDeoptimized(frame);
            if (deoptimizedFrame != null) {
                this.deoptimizedVFrame = deoptimizedFrame.getTopFrame();
            } else {
                UntetheredCodeInfo untetheredInfo = frame.getIPCodeInfo();
                Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
                try {
                    CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
                    CodeInfoQueryResult physicalFrame = queryCodeInfoInterruptibly(info, frame.getIP());
                    regularVFrame = physicalFrame.getFrameInfo();
                } finally {
                    CodeInfoAccess.releaseTether(untetheredInfo, tether);
                }
            }

            return true;
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
