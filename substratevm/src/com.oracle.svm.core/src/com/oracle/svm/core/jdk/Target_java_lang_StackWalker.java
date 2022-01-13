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

import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationImpl;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackFrameVisitor;
import com.oracle.svm.core.stack.JavaStackWalk;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.thread.JavaContinuations;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.Target_java_lang_Continuation;
import com.oracle.svm.core.thread.Target_java_lang_ContinuationScope;
import com.oracle.svm.core.thread.Target_java_lang_VirtualThread;
import com.oracle.svm.core.util.VMError;

@TargetClass(value = java.lang.StackWalker.class)
final class Target_java_lang_StackWalker {

    /**
     * Current continuation that the stack walker is on.
     */
    @Alias @TargetElement(onlyWith = LoomJDK.class)//
    Target_java_lang_Continuation continuation;

    /**
     * Target continuation scope if we're iterating a {@link Target_java_lang_Continuation}.
     */
    @Alias @TargetElement(onlyWith = LoomJDK.class)//
    Target_java_lang_ContinuationScope contScope;

    @Alias Set<Option> options;
    @Alias boolean retainClassRef;

    @Substitute
    @NeverInline("Starting a stack walk in the caller frame")
    private void forEach(Consumer<? super StackFrame> action) {
        boolean showHiddenFrames = options.contains(StackWalker.Option.SHOW_HIDDEN_FRAMES);
        boolean showReflectFrames = options.contains(StackWalker.Option.SHOW_REFLECT_FRAMES);

        JavaStackWalker.walkCurrentThread(KnownIntrinsics.readCallerStackPointer(), new JavaStackFrameVisitor() {
            @Override
            public boolean visitFrame(FrameInfoQueryResult frameInfo) {
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
        AbstractStackFrameSpliterator spliterator;

        final Thread thread = Thread.currentThread();

        if (JavaContinuations.useLoom() && this.continuation != null) {
            // walking a yielded continuation
            spliterator = new ContinuationSpliterator(this.contScope, this.continuation);
        } else {
            // walking a running continuation
            JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
            Pointer sp = KnownIntrinsics.readCallerStackPointer();

            if (JavaContinuations.useLoom() && (this.contScope != null || JavaThreads.isVirtual(thread))) {
                // has a delimitation scope
                VMError.guarantee(JavaContinuations.useLoom());
                Target_java_lang_ContinuationScope delimitationScope = this.contScope != null ? this.contScope : Target_java_lang_VirtualThread.continuationScope();
                Target_java_lang_Continuation topContinuation = Target_java_lang_Continuation.getCurrentContinuation(delimitationScope);
                if (topContinuation != null) {
                    JavaStackWalker.initWalk(walk, sp, JavaContinuations.getSP(topContinuation));
                } else {
                    // the delimitation scope is not present in current continuation chain or null
                    JavaStackWalker.initWalk(walk, sp);
                }
            } else {
                // walking a running thread
                JavaStackWalker.initWalk(walk, sp);
            }

            spliterator = new StackFrameSpliterator(walk, thread);
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
        protected FrameInfoQueryResult queryFrameInfo(CodeInfo info, CodePointer ip) {
            return CodeInfoTable.lookupCodeInfoQueryResult(info, ip).getFrameInfo();
        }

        protected DeoptimizedFrame.VirtualFrame curDeoptimizedFrame;
        protected FrameInfoQueryResult curRegularFrame;

        @Override
        public boolean tryAdvance(Consumer<? super StackFrame> action) {
            checkState();

            boolean showHiddenFrames = options.contains(StackWalker.Option.SHOW_HIDDEN_FRAMES);
            boolean showReflectFrames = options.contains(StackWalker.Option.SHOW_REFLECT_FRAMES);

            while (true) {
                /* Check if we have pending virtual frames to process. */
                if (curDeoptimizedFrame != null) {
                    FrameInfoQueryResult frameInfo = curDeoptimizedFrame.getFrameInfo();
                    curDeoptimizedFrame = curDeoptimizedFrame.getCaller();

                    if (shouldShowFrame(frameInfo, showHiddenFrames, showReflectFrames, showHiddenFrames)) {
                        action.accept(new StackFrameImpl(frameInfo));
                        return true;
                    }

                } else if (curRegularFrame != null) {
                    FrameInfoQueryResult frameInfo = curRegularFrame;
                    curRegularFrame = curRegularFrame.getCaller();

                    if (shouldShowFrame(frameInfo, showHiddenFrames, showReflectFrames, showHiddenFrames)) {
                        action.accept(new StackFrameImpl(frameInfo));
                        return true;
                    }

                } else if (haveMoreFrames()) {
                    /* No more virtual frames, but we have more physical frames. */
                    advancePhysically();
                } else {
                    /* No more physical frames, we are done. */
                    return false;
                }
            }
        }

        protected boolean shouldShowFrame(FrameInfoQueryResult frameInfo, boolean showLambdaFrames, boolean showReflectFrames, boolean showHiddenFrames) {
            return StackTraceUtils.shouldShowFrame(frameInfo, showLambdaFrames, showReflectFrames, showHiddenFrames);
        }

        protected void invalidate() {
        }

        protected void checkState() {
        }

        protected abstract boolean haveMoreFrames();

        protected abstract void advancePhysically();
    }

    final class ContinuationSpliterator extends AbstractStackFrameSpliterator {
        private final Target_java_lang_ContinuationScope contScope;
        private Target_java_lang_Continuation continuation;

        ContinuationSpliterator(Target_java_lang_ContinuationScope contScope, Target_java_lang_Continuation continuation) {
            VMError.guarantee(JavaContinuations.useLoom());
            this.contScope = contScope;
            this.continuation = continuation;
            if (this.continuation.internalContinuation != null) {
                initCurrentContinuation();
            } // else the continuation is done and no frame should be visited
        }

        private StoredContinuation curStoredContinuation;
        private Pointer sp = WordFactory.nullPointer();
        private Pointer endSp = WordFactory.nullPointer();
        private CodePointer ip = WordFactory.nullPointer();
        private int curFrameIndex;
        private int curFrameCount;

        boolean intermediaContinuation = false;

        @Override
        protected boolean shouldShowFrame(FrameInfoQueryResult frameInfo, boolean showLambdaFrames, boolean showReflectFrames, boolean showHiddenFrames) {
            // since we're walking the yielded continuation without mounting it, we'll get extra
            // frames of `yield0` in each continuation, which is not consistent with JDK
            if (frameInfo.getSourceClass() == Target_java_lang_Continuation.class && "yield0".equals(frameInfo.getSourceMethodName())) {
                if (!intermediaContinuation) {
                    intermediaContinuation = true;
                } else {
                    return false;
                }
            }
            return super.shouldShowFrame(frameInfo, showLambdaFrames, showReflectFrames, showHiddenFrames);
        }

        private void initCurrentContinuation() {
            curStoredContinuation = continuation.internalContinuation;
            VMError.guarantee(curStoredContinuation != null);
            sp = StoredContinuationImpl.payloadFrameStart(curStoredContinuation);
            endSp = sp.add(TypeConversion.asU4(StoredContinuationImpl.readAllFrameSize(curStoredContinuation)));
            ip = JavaContinuations.getIP(continuation);
            curFrameIndex = 0;
            curFrameCount = StoredContinuationImpl.readFrameCount(curStoredContinuation);
        }

        @Override
        protected boolean haveMoreFrames() {
            if (curStoredContinuation == null) {
                return false;
            } else if (curFrameIndex <= curFrameCount - 1) {
                return true;
            } else if (contScope == null || contScope != continuation.getScope()) {
                // if delimitation scope is not present in the chain, walk until the outermost
                // continuation.
                return continuation.getParent() != null;
            }
            return false;
        }

        @Override
        @Uninterruptible(reason = "Prevent GC while in this method.", calleeMustBe = false)
        protected void advancePhysically() {
            if (sp.equal(endSp)) {
                VMError.guarantee(curFrameIndex == curFrameCount);
                this.continuation = this.continuation.getParent();
                initCurrentContinuation();
            }

            DeoptimizedFrame deoptimizedFrame = Deoptimizer.checkDeoptimized(sp);
            if (deoptimizedFrame != null) {
                curDeoptimizedFrame = deoptimizedFrame.getTopFrame();
            } else {
                UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(ip);

                Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
                try {
                    CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
                    curRegularFrame = queryFrameInfo(info, ip);
                } finally {
                    CodeInfoAccess.releaseTether(untetheredInfo, tether);
                }
            }
            sp = sp.add(StoredContinuationImpl.readFrameSize(curStoredContinuation, curFrameIndex));
            ip = FrameAccess.singleton().readReturnAddress(sp);
            curFrameIndex++;
        }

        @Override
        protected void invalidate() {
            continuation = null;
            curStoredContinuation = null;
        }

        @Override
        protected void checkState() {
            if (continuation == null) {
                throw new IllegalStateException("Continuation traversal no longer valid");
            }
        }
    }

    final class StackFrameSpliterator extends AbstractStackFrameSpliterator {
        private final Thread thread;
        private JavaStackWalk walk;

        StackFrameSpliterator(JavaStackWalk walk, Thread curThread) {
            this.walk = walk;
            this.thread = curThread;
        }

        @Override
        protected void invalidate() {
            walk = WordFactory.nullPointer();
        }

        @Override
        protected void checkState() {
            if (thread != Thread.currentThread()) {
                throw new IllegalStateException("Invalid thread");
            }
            if (walk.isNull()) {
                throw new IllegalStateException("Stack traversal no longer valid");
            }
        }

        @Override
        protected boolean haveMoreFrames() {
            Pointer endSP = walk.getEndSP();
            Pointer curSP = walk.getSP();
            return curSP.isNonNull() && (endSP.isNull() || curSP.belowThan(endSP));
        }

        /**
         * Get virtual frames to process in the next loop iteration, then update the physical stack
         * walker to the next physical frame to be ready when all virtual frames are processed.
         */
        @Override
        @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
        protected void advancePhysically() {
            CodePointer ip = FrameAccess.singleton().readReturnAddress(walk.getSP());
            walk.setPossiblyStaleIP(ip);

            DeoptimizedFrame deoptimizedFrame = Deoptimizer.checkDeoptimized(walk.getSP());
            if (deoptimizedFrame != null) {
                curDeoptimizedFrame = deoptimizedFrame.getTopFrame();
                walk.setIPCodeInfo(WordFactory.nullPointer());
                JavaStackWalker.continueWalk(walk, WordFactory.nullPointer());

            } else {
                UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(ip);
                walk.setIPCodeInfo(untetheredInfo);

                Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
                try {
                    CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
                    curRegularFrame = queryFrameInfo(info, ip);
                    JavaStackWalker.continueWalk(walk, info);
                } finally {
                    CodeInfoAccess.releaseTether(untetheredInfo, tether);
                }
            }
        }
    }

    final class StackFrameImpl implements StackWalker.StackFrame {
        private final FrameInfoQueryResult frameInfo;
        private StackTraceElement ste;

        StackFrameImpl(FrameInfoQueryResult frameInfo) {
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
