/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shared;

import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.Publish;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.gc.shared.NativeGCStructs.CodeInfos;
import com.oracle.svm.core.gc.shared.NativeGCStructs.CodeInfosPerThread;
import com.oracle.svm.core.gc.shared.NativeGCStructs.StackFrame;
import com.oracle.svm.core.gc.shared.NativeGCStructs.StackFrames;
import com.oracle.svm.core.gc.shared.NativeGCStructs.StackFramesPerThread;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.heap.StoredContinuationAccess.ContinuationStackFrameVisitor;
import com.oracle.svm.core.heap.StoredContinuationAccess.ContinuationStackFrameVisitorData;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.ParameterizedStackFrameVisitor;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

/**
 * Whenever GC-related C++ code needs information about the stack frames, it calls into Native Image
 * and does a stack walk to collect that information. Relevant information is collected in a data
 * structure that is allocated in native memory. Once the C++ code doesn't need this information
 * anymore, it calls into Native Image to free the native memory.
 */
public final class NativeGCStackWalker {
    private static final ThreadStackFrameCollector THREAD_STACK_FRAME_COLLECTOR = new ThreadStackFrameCollector();
    private static final ContinuationStackFrameCollector CONTINUATION_STACK_FRAME_COLLECTOR = new ContinuationStackFrameCollector();
    private static final CodeInfoCollector CODE_INFO_COLLECTOR = new CodeInfoCollector();

    public final CEntryPointLiteral<CFunctionPointer> funcFetchThreadStackFrames;
    public final CEntryPointLiteral<CFunctionPointer> funcFreeThreadStackFrames;
    public final CEntryPointLiteral<CFunctionPointer> funcFetchContinuationStackFrames;
    public final CEntryPointLiteral<CFunctionPointer> funcFreeContinuationStackFrames;
    public final CEntryPointLiteral<CFunctionPointer> funcFetchCodeInfos;
    public final CEntryPointLiteral<CFunctionPointer> funcFreeCodeInfos;

    @Platforms(Platform.HOSTED_ONLY.class)
    public NativeGCStackWalker() {
        funcFetchThreadStackFrames = CEntryPointLiteral.create(NativeGCStackWalker.class, "fetchThreadStackFrames", Isolate.class, IsolateThread.class);
        funcFreeThreadStackFrames = CEntryPointLiteral.create(NativeGCStackWalker.class, "freeThreadStackFrames", Isolate.class, IsolateThread.class, StackFramesPerThread.class);

        if (ContinuationSupport.isSupported()) {
            funcFetchContinuationStackFrames = CEntryPointLiteral.create(NativeGCStackWalker.class, "fetchContinuationStackFrames", Isolate.class, Pointer.class);
            funcFreeContinuationStackFrames = CEntryPointLiteral.create(NativeGCStackWalker.class, "freeContinuationStackFrames", Isolate.class, StackFrames.class);
        } else {
            funcFetchContinuationStackFrames = null;
            funcFreeContinuationStackFrames = null;
        }

        if (RuntimeCompilation.isEnabled()) {
            funcFetchCodeInfos = CEntryPointLiteral.create(NativeGCStackWalker.class, "fetchCodeInfos", Isolate.class, IsolateThread.class);
            funcFreeCodeInfos = CEntryPointLiteral.create(NativeGCStackWalker.class, "freeCodeInfos", Isolate.class, IsolateThread.class, CodeInfosPerThread.class);
        } else {
            funcFetchCodeInfos = null;
            funcFreeCodeInfos = null;
        }
    }

    @Uninterruptible(reason = "GC may only call uninterruptible code.")
    @CEntryPoint(include = UseNativeGC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    public static StackFramesPerThread fetchThreadStackFrames(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread thread) {
        return walkStack(THREAD_STACK_FRAME_COLLECTOR);
    }

    @Uninterruptible(reason = "GC may only call uninterruptible code.")
    @CEntryPoint(include = UseNativeGC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    public static void freeThreadStackFrames(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread thread, StackFramesPerThread stackFramesPerThread) {
        for (int i = 0; i < stackFramesPerThread.count(); i++) {
            NullableNativeMemory.free(stackFramesPerThread.threads().addressOf(i).read());
        }
        NullableNativeMemory.free(stackFramesPerThread);
    }

    @Uninterruptible(reason = "GC may only call uninterruptible code.")
    @CEntryPoint(include = UseNativeGCAndContinuations.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForUnattachedThread.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    public static StackFrames fetchContinuationStackFrames(@SuppressWarnings("unused") Isolate isolate, Pointer storedContinuation) {
        StoredContinuation s = (StoredContinuation) storedContinuation.toObject();
        ContinuationStackFrameCollectorData data = StackValue.get(ContinuationStackFrameCollectorData.class);
        ContinuationStackFrameCollector.initialize(data);
        StoredContinuationAccess.walkFrames(s, CONTINUATION_STACK_FRAME_COLLECTOR, data);
        return data.getStack();
    }

    @Uninterruptible(reason = "May be called by an unattached thread (during or outside of a safepoint).")
    @CEntryPoint(include = UseNativeGCAndContinuations.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForUnattachedThread.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    public static void freeContinuationStackFrames(@SuppressWarnings("unused") Isolate isolate, StackFrames stackFrames) {
        NullableNativeMemory.free(stackFrames);
    }

    @Uninterruptible(reason = "GC may only call uninterruptible code.")
    @CEntryPoint(include = UseNativeGC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    public static CodeInfosPerThread fetchCodeInfos(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread thread) {
        return walkStack(CODE_INFO_COLLECTOR);
    }

    @Uninterruptible(reason = "GC may only call uninterruptible code.")
    @CEntryPoint(include = UseNativeGC.class, publishAs = Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersForPossiblyUnattachedThread.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    public static void freeCodeInfos(@SuppressWarnings("unused") Isolate isolate, @SuppressWarnings("unused") IsolateThread thread, CodeInfosPerThread codeInfos) {
        for (int i = 0; i < codeInfos.count(); i++) {
            NullableNativeMemory.free(codeInfos.threads().addressOf(i).read());
        }
        NullableNativeMemory.free(codeInfos);
    }

    @NeverInline("Stack walking.")
    @Uninterruptible(reason = "GC may only call uninterruptible code.")
    private static <T extends PointerBase, U extends PointerBase> T walkStack(Collector<T, U> collector) {
        VMOperation.guaranteeInProgressAtSafepoint("Doing a stack walk for every thread is only possible when we are at a safepoint.");
        collector.startWalking();

        /* Walk the current thread. */
        collector.newThread();
        JavaStackWalker.walkCurrentThread(KnownIntrinsics.readCallerStackPointer(), collector, null);
        collector.finishThread();

        /* Walk all other threads. */
        for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
            if (vmThread == CurrentIsolate.getCurrentThread()) {
                /* The current thread is already walked by code above. */
                continue;
            }
            collector.newThread();
            JavaStackWalker.walkThread(vmThread, collector, null);
            collector.finishThread();
        }
        return collector.finishWalking();
    }

    @Uninterruptible(reason = "GC may only call uninterruptible code.")
    private static <V extends PointerBase> V malloc(UnsignedWord size) {
        V result = NullableNativeMemory.malloc(size, NmtCategory.GC);
        if (result.isNull()) {
            throw VMError.shouldNotReachHere("malloc returned null.");
        }
        return result;
    }

    @Uninterruptible(reason = "GC may only call uninterruptible code.")
    private static <V extends PointerBase> V realloc(V ptr, UnsignedWord size) {
        V result = NullableNativeMemory.realloc(ptr, size, NmtCategory.GC);
        if (result.isNull()) {
            throw VMError.shouldNotReachHere("realloc returned null.");
        }
        return result;
    }

    @RawStructure
    private interface ContinuationStackFrameCollectorData extends ContinuationStackFrameVisitorData {
        @RawField
        int getCapacity();

        @RawField
        void setCapacity(int value);

        @RawField
        StackFrames getStack();

        @RawField
        void setStack(StackFrames frames);
    }

    private static class ContinuationStackFrameCollector extends ContinuationStackFrameVisitor {
        private static final int DEFAULT_NUM_STACK_FRAMES = 10;

        @Platforms(value = Platform.HOSTED_ONLY.class)
        ContinuationStackFrameCollector() {
        }

        @Uninterruptible(reason = "GC may only call uninterruptible code and StoredContinuation must not move.", callerMustBe = true)
        public static void initialize(ContinuationStackFrameCollectorData data) {
            UnsignedWord size = computeStackFramesSize(DEFAULT_NUM_STACK_FRAMES);
            StackFrames frames = malloc(size);
            frames.setCount(0);

            data.setStack(frames);
            data.setCapacity(DEFAULT_NUM_STACK_FRAMES);
        }

        @Override
        @Uninterruptible(reason = "GC may only call uninterruptible code and StoredContinuation must not move.", callerMustBe = true)
        public void visitFrame(ContinuationStackFrameVisitorData data, Pointer sp, NonmovableArray<Byte> referenceMapEncoding, long referenceMapIndex, ContinuationStackFrameVisitor visitor) {
            ContinuationStackFrameCollectorData d = (ContinuationStackFrameCollectorData) data;

            long index = d.getStack().count();
            if (index == d.getCapacity()) {
                int newCapacity = d.getCapacity() * 2;
                StackFrames newStackFrames = realloc(d.getStack(), computeStackFramesSize(newCapacity));
                d.setStack(newStackFrames);
                d.setCapacity(newCapacity);
            }

            StackFrames stack = d.getStack();
            StackFrame frame = stack.frames().addressOf(index);
            frame.setStackPointer(sp);
            frame.setEncodedReferenceMap(NonmovableArrays.getArrayBase(referenceMapEncoding));
            frame.setReferenceMapIndex(referenceMapIndex);
            stack.setCount(index + 1);
        }

        @Uninterruptible(reason = "GC may only execute uninterruptible code.")
        private static UnsignedWord computeStackFramesSize(int frames) {
            return SizeOf.unsigned(StackFrames.class).add(SizeOf.unsigned(StackFrame.class).multiply(frames));
        }
    }

    private abstract static class Collector<T extends PointerBase, U extends PointerBase> extends ParameterizedStackFrameVisitor {
        protected static final int INITIALLY_RESERVED_THREADS = 5;
        protected static final int INITIALLY_RESERVED_STACK_FRAMES = 10;

        protected T threads;
        protected int threadsLength;

        protected U currentStack;
        protected int currentStackLength;

        @Platforms(value = Platform.HOSTED_ONLY.class)
        Collector() {
        }

        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        public abstract void startWalking();

        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        public abstract void newThread();

        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        public abstract void finishThread();

        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        public T finishWalking() {
            assert currentStack.isNull();
            assert currentStackLength == 0;

            T result = threads;
            this.threads = Word.nullPointer();
            this.threadsLength = 0;
            return result;
        }

        @Override
        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        protected final boolean unknownFrame(Pointer sp, CodePointer ip, Object data) {
            throw JavaStackWalker.fatalErrorUnknownFrameEncountered(sp, ip);
        }
    }

    private static class ThreadStackFrameCollector extends Collector<StackFramesPerThread, StackFrames> {
        @Platforms(value = Platform.HOSTED_ONLY.class)
        ThreadStackFrameCollector() {
        }

        @Override
        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        public void startWalking() {
            assert threads.isNull() && threadsLength == 0 && currentStack.isNull() && currentStackLength == 0;

            this.threadsLength = INITIALLY_RESERVED_THREADS;
            this.threads = malloc(computeStackFramesPerThreadSize());
            this.threads.setCount(0);
        }

        @Override
        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        public void newThread() {
            assert currentStack.isNull() && currentStackLength == 0;

            this.currentStackLength = INITIALLY_RESERVED_STACK_FRAMES;
            this.currentStack = malloc(computeStackFramesSize());
            this.currentStack.setCount(0);
        }

        @Override
        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        public void finishThread() {
            long index = this.threads.count();
            if (index == threadsLength) {
                this.threadsLength *= 2;
                this.threads = realloc(threads, computeStackFramesPerThreadSize());
            }

            threads.threads().addressOf(index).write(currentStack);
            this.threads.setCount(index + 1);

            this.currentStack = Word.nullPointer();
            this.currentStackLength = 0;
        }

        @Override
        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        public boolean visitRegularFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, Object data) {
            NonmovableArray<Byte> referenceMapEncoding = CodeInfoAccess.getStackReferenceMapEncoding(codeInfo);
            long referenceMapIndex = CodeInfoAccess.lookupStackReferenceMapIndex(codeInfo, CodeInfoAccess.relativeIP(codeInfo, ip));
            append(sp, referenceMapEncoding, referenceMapIndex);
            return true;
        }

        @Override
        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptimizedFrame, Object data) {
            /* Nothing to do - the DeoptimizedFrame is an object and therefore visible to the GC. */
            return true;
        }

        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        private void append(Pointer sp, NonmovableArray<Byte> referenceMapEncoding, long referenceMapIndex) {
            long index = currentStack.count();
            if (index == currentStackLength) {
                currentStackLength *= 2;
                currentStack = realloc(currentStack, computeStackFramesSize());
            }

            StackFrame frame = currentStack.frames().addressOf(index);
            frame.setStackPointer(sp);
            frame.setEncodedReferenceMap(NonmovableArrays.getArrayBase(referenceMapEncoding));
            frame.setReferenceMapIndex(referenceMapIndex);
            currentStack.setCount(index + 1);
        }

        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        private UnsignedWord computeStackFramesPerThreadSize() {
            return SizeOf.unsigned(StackFramesPerThread.class).add(SizeOf.unsigned(StackFrames.class).multiply(threadsLength));
        }

        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        private UnsignedWord computeStackFramesSize() {
            return SizeOf.unsigned(StackFrames.class).add(SizeOf.unsigned(StackFrame.class).multiply(currentStackLength));
        }
    }

    private static class CodeInfoCollector extends Collector<CodeInfosPerThread, CodeInfos> {
        @Platforms(value = Platform.HOSTED_ONLY.class)
        CodeInfoCollector() {
        }

        @Override
        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        public void startWalking() {
            assert threads.isNull() && threadsLength == 0 && currentStack.isNull() && currentStackLength == 0;

            this.threadsLength = INITIALLY_RESERVED_THREADS;
            this.threads = malloc(computeThreadsInfoSize());
            this.threads.setCount(0);
        }

        @Override
        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        public void newThread() {
            assert currentStack.isNull() && currentStackLength == 0;

            this.currentStackLength = INITIALLY_RESERVED_STACK_FRAMES;
            this.currentStack = malloc(computeStackInfoSize());
            this.currentStack.setCount(0);
        }

        @Override
        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        public void finishThread() {
            long index = this.threads.count();
            if (index == threadsLength) {
                this.threadsLength *= 2;
                this.threads = realloc(threads, computeThreadsInfoSize());
            }

            threads.threads().addressOf(index).write(currentStack);
            this.threads.setCount(index + 1);

            this.currentStack = Word.nullPointer();
            this.currentStackLength = 0;
        }

        @Override
        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        public boolean visitRegularFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, Object data) {
            if (!CodeInfoAccess.isAOTImageCode(codeInfo)) {
                append(codeInfo);
            }
            return true;
        }

        @Override
        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptimizedFrame, Object data) {
            /* Nothing to do. */
            return true;
        }

        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        private void append(CodeInfo codeInfo) {
            long index = this.currentStack.count();
            if (index == currentStackLength) {
                currentStackLength *= 2;
                currentStack = realloc(currentStack, computeStackInfoSize());
            }

            currentStack.codeInfos().addressOf(index).write(codeInfo);
            currentStack.setCount(index + 1);
        }

        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        private UnsignedWord computeThreadsInfoSize() {
            return SizeOf.unsigned(StackFramesPerThread.class).add(SizeOf.unsigned(StackFrames.class).multiply(threadsLength));
        }

        @Uninterruptible(reason = "GC may only call uninterruptible code.")
        private UnsignedWord computeStackInfoSize() {
            return SizeOf.unsigned(StackFrames.class).add(SizeOf.unsigned(StackFrame.class).multiply(currentStackLength));
        }
    }

    private static class UseNativeGCAndContinuations implements BooleanSupplier {
        @Platforms(Platform.HOSTED_ONLY.class)
        UseNativeGCAndContinuations() {
        }

        @Override
        public boolean getAsBoolean() {
            return UseNativeGC.get() && ContinuationSupport.isSupported();
        }
    }
}
