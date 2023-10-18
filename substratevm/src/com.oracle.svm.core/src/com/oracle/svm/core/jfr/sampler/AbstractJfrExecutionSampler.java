/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr.sampler;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.SimpleCodeInfoQueryResult;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrThreadLocal;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.sampler.SamplerSampleWriter;
import com.oracle.svm.core.sampler.SamplerSampleWriterData;
import com.oracle.svm.core.sampler.SamplerSampleWriterDataAccess;
import com.oracle.svm.core.sampler.SamplerStackWalkVisitor;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.ThreadListener;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.util.PointerUtils;

import jdk.graal.compiler.api.replacements.Fold;

/*
 * Base class for different sampler implementations that emit JFR ExecutionSample events.
 *
 * The sampler does a stack walk and writes the encountered IPs into a {@link SamplerBuffer}. As it is
 * impossible to allocate buffers in the sampler, the {@link SamplerBufferPool} is used to ensure that
 * there are pre-allocated buffers available.
 *
 * Conceptually, the sampler produces {@link SamplerBuffer}s, while the {@link JfrRecorderThread} consumes
 * those buffers. The producer and the consumer are always accessing different buffers:
 * <ul>
 * <li>Sampler: pop a buffer from the pool of available buffers and write the IPs into the
 * buffer. Once the buffer is full, add it to the buffers that await processing.</li>
 * <li>{@link JfrRecorderThread}: process the full buffers and reconstruct the stack trace
 * information based on the IPs.</li>
 * </ul>
 *
 * In rare cases, profiling is impossible (e.g., no available buffers in the pool, an unknown IP is
 * encountered during the stack walk, or the thread holds the pool's lock when the signal arrives).
 * If such a situation is detected, the sample is omitted.
 */
public abstract class AbstractJfrExecutionSampler extends JfrExecutionSampler implements ThreadListener {
    private static final FastThreadLocalInt samplerState = FastThreadLocalFactory.createInt("JfrSampler.samplerState");
    private static final FastThreadLocalInt isDisabledForCurrentThread = FastThreadLocalFactory.createInt("JfrSampler.isDisabledForCurrentThread");

    private final UninterruptibleUtils.AtomicInteger isSignalHandlerDisabledGlobally = new UninterruptibleUtils.AtomicInteger(0);
    private final UninterruptibleUtils.AtomicInteger threadsInSignalHandler = new UninterruptibleUtils.AtomicInteger(0);

    private volatile boolean isSampling;
    private long curIntervalMillis;
    protected long newIntervalMillis;

    @Platforms(Platform.HOSTED_ONLY.class)
    public AbstractJfrExecutionSampler() {
    }

    @Fold
    public static AbstractJfrExecutionSampler singleton() {
        return (AbstractJfrExecutionSampler) ImageSingletons.lookup(JfrExecutionSampler.class);
    }

    @Fold
    protected static UninterruptibleUtils.AtomicInteger threadsInSignalHandler() {
        return singleton().threadsInSignalHandler;
    }

    /** Only sets the field. To apply the new value, {@link #update} needs to be called. */
    @Override
    public void setIntervalMillis(long intervalMillis) {
        newIntervalMillis = intervalMillis;
    }

    @Override
    @Uninterruptible(reason = "Prevent VM operations that modify execution sampling.", callerMustBe = true)
    public boolean isSampling() {
        return isSampling;
    }

    @Override
    public void update() {
        UpdateJfrExecutionSamplerOperation op = new UpdateJfrExecutionSamplerOperation();
        op.enqueue();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void preventSamplingInCurrentThread() {
        int newValue = isDisabledForCurrentThread.get() + 1;
        assert newValue >= 0;
        isDisabledForCurrentThread.set(newValue);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void allowSamplingInCurrentThread() {
        int newValue = isDisabledForCurrentThread.get() - 1;
        assert newValue >= -1;
        isDisabledForCurrentThread.set(newValue);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void disallowThreadsInSamplerCode() {
        /* Prevent threads from entering the sampler code. */
        int value = isSignalHandlerDisabledGlobally.incrementAndGet();
        assert value > 0;

        /* Wait until there are no more threads in the sampler code. */
        while (threadsInSignalHandler.get() > 0) {
            VMThreads.singleton().yield();
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void allowThreadsInSamplerCode() {
        int value = isSignalHandlerDisabledGlobally.decrementAndGet();
        assert value >= 0;
    }

    @Override
    @Uninterruptible(reason = "Prevent VM operations that modify execution sampler state.")
    public void afterThreadRun() {
        IsolateThread thread = CurrentIsolate.getCurrentThread();
        uninstall(thread);
        ExecutionSamplerInstallation.disallow(thread);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected static boolean isExecutionSamplingAllowedInCurrentThread() {
        boolean disallowed = singleton().isSignalHandlerDisabledGlobally.get() > 0 ||
                        isDisabledForCurrentThread.get() > 0 ||
                        SubstrateJVM.getSamplerBufferPool().isLockedByCurrentThread();

        return ExecutionSamplerInstallation.isInstalled(CurrentIsolate.getCurrentThread()) && !disallowed;
    }

    protected abstract void startSampling();

    protected abstract void stopSampling();

    protected abstract void updateInterval();

    protected abstract void uninstall(IsolateThread thread);

    @Uninterruptible(reason = "The method executes during signal handling.", callerMustBe = true)
    protected static void tryUninterruptibleStackWalk(CodePointer ip, Pointer sp) {
        /*
         * To prevent races, it is crucial that the thread count is incremented before we do any
         * other checks.
         */
        threadsInSignalHandler().incrementAndGet();
        try {
            if (isExecutionSamplingAllowedInCurrentThread()) {
                /* Prevent recursive sampler invocations during the stack walk. */
                JfrExecutionSampler.singleton().preventSamplingInCurrentThread();
                try {
                    doUninterruptibleStackWalk(ip, sp);
                } finally {
                    JfrExecutionSampler.singleton().allowSamplingInCurrentThread();
                }
            } else {
                JfrThreadLocal.increaseMissedSamples();
            }
        } finally {
            threadsInSignalHandler().decrementAndGet();
        }
    }

    @Uninterruptible(reason = "The method executes during signal handling.", callerMustBe = true)
    private static void doUninterruptibleStackWalk(CodePointer initialIp, Pointer initialSp) {
        CodePointer ip = initialIp;
        Pointer sp = initialSp;
        if (!isInAOTCompiledCode(ip)) {
            JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor();
            if (anchor.isNull()) {
                /*
                 * The anchor is still null if the function is interrupted during prologue (see:
                 * com.oracle.svm.core.graal.snippets.CFunctionSnippets.prologueSnippet) or if java
                 * calls a native method without transition and without previous anchors.
                 */
                return;
            }

            ip = anchor.getLastJavaIP();
            sp = anchor.getLastJavaSP();
            if (ip.isNull() || sp.isNull()) {
                /*
                 * It can happen that anchor is in the list of all anchors, but its IP and SP are
                 * not filled yet.
                 */
                return;
            }
        }

        /* Try to do a stack walk. */
        SamplerSampleWriterData data = StackValue.get(SamplerSampleWriterData.class);
        if (SamplerSampleWriterDataAccess.initialize(data, 0, false)) {
            JfrThreadLocal.setSamplerWriterData(data);
            try {
                doUninterruptibleStackWalk(data, sp, ip);
            } finally {
                JfrThreadLocal.setSamplerWriterData(WordFactory.nullPointer());
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isInAOTCompiledCode(CodePointer ip) {
        CodeInfo codeInfo = CodeInfoTable.getImageCodeInfo();
        return CodeInfoAccess.contains(codeInfo, ip);
    }

    /**
     * Verify whether the stack pointer (SP) lies within the limits of the thread's stack. If not,
     * attempting a stack walk might result in a segmentation fault (SEGFAULT). The stack pointer
     * might be positioned outside the stack's boundaries if a signal interrupted the execution at
     * the beginning of a method, before the SP was adjusted to its correct value.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isSPOutsideStackBoundaries(Pointer sp) {
        UnsignedWord stackBase = VMThreads.StackBase.get();
        assert stackBase.notEqual(0);
        Pointer returnAddressLocation = FrameAccess.singleton().getReturnAddressLocation(sp).add(FrameAccess.returnAddressSize());
        return returnAddressLocation.aboveThan(stackBase) || returnAddressLocation.belowOrEqual(VMThreads.StackEnd.get());
    }

    @Uninterruptible(reason = "This method must be uninterruptible since it uses untethered code info.", callerMustBe = true)
    private static Pointer getCallerSP(CodeInfo codeInfo, Pointer sp, CodePointer ip) {
        long relativeIP = CodeInfoAccess.relativeIP(codeInfo, ip);
        long totalFrameSize = CodeInfoAccess.lookupTotalFrameSize(codeInfo, relativeIP);
        return sp.add(WordFactory.unsigned(totalFrameSize));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isSPAligned(Pointer sp) {
        return PointerUtils.isAMultiple(sp, WordFactory.unsigned(ConfigurationValues.getTarget().stackAlignment));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isEntryPoint(CodeInfo codeInfo, CodePointer ip) {
        long relativeIP = CodeInfoAccess.relativeIP(codeInfo, ip);
        SimpleCodeInfoQueryResult queryResult = StackValue.get(SimpleCodeInfoQueryResult.class);
        CodeInfoAccess.lookupCodeInfo(codeInfo, relativeIP, queryResult);
        return CodeInfoQueryResult.isEntryPoint(queryResult.getEncodedFrameSize());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static JavaFrameAnchor findLastJavaFrameAnchor(Pointer callerSP) {
        JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor();
        while (anchor.isNonNull() && anchor.getLastJavaSP().belowOrEqual(callerSP)) {
            /* Skip anchors that are in parts of the stack we are not traversing. */
            anchor = anchor.getPreviousAnchor();
        }
        assert anchor.isNull() || anchor.getLastJavaSP().aboveThan(callerSP);
        return anchor;
    }

    @Uninterruptible(reason = "This method must be uninterruptible since it uses untethered code info.", callerMustBe = true)
    private static void doUninterruptibleStackWalk(SamplerSampleWriterData data, Pointer sp, CodePointer ip) {
        SamplerSampleWriter.begin(data);
        /*
         * Visit the top frame.
         *
         * No matter where in the AOT-compiled code the signal has interrupted the execution, we
         * know how to decode it.
         */
        assert isInAOTCompiledCode(ip);
        CodeInfo codeInfo = CodeInfoTable.getImageCodeInfo();
        SamplerStackWalkVisitor visitor = ImageSingletons.lookup(SamplerStackWalkVisitor.class);
        if (!visitor.visitFrame(sp, ip, codeInfo, null, null)) {
            /* The top frame is also the last one. */
            SamplerSampleWriter.end(data, SamplerSampleWriter.EXECUTION_SAMPLE_END);
            return;
        }

        Pointer callerSP;
        if (isSPAligned(sp)) {
            /* Stack is probably in a normal, walkable state. */
            callerSP = getCallerSP(codeInfo, sp, ip);
            if (SubstrateOptions.PreserveFramePointer.getValue() && (isSPOutsideStackBoundaries(callerSP) || !isInAOTCompiledCode(FrameAccess.singleton().readReturnAddress(callerSP)))) {
                /*
                 * We are in the prologue or epilogue. Frame pointer and return address are on top
                 * of the stack.
                 */
                callerSP = sp.add(FrameAccess.wordSize()).add(FrameAccess.singleton().savedBasePointerSize());
            }
        } else {
            /* We are in the prologue or epilogue. Return address is at the top of the stack. */
            callerSP = sp.add(FrameAccess.wordSize());
        }

        if (isSPOutsideStackBoundaries(callerSP)) {
            /* We made an incorrect assumption earlier, the stack is not walkable. */
            JfrThreadLocal.increaseUnparseableStacks();
            return;
        }

        CodePointer returnAddressIP = FrameAccess.singleton().readReturnAddress(callerSP);
        if (!isInAOTCompiledCode(returnAddressIP)) {
            if (isEntryPoint(codeInfo, ip)) {
                JavaFrameAnchor anchor = findLastJavaFrameAnchor(callerSP);
                if (anchor.isNonNull()) {
                    callerSP = anchor.getLastJavaSP();
                    returnAddressIP = anchor.getLastJavaIP();
                } else {
                    SamplerSampleWriter.end(data, SamplerSampleWriter.EXECUTION_SAMPLE_END);
                    return;
                }
            } else {
                /* We made an incorrect assumption earlier, the stack is not walkable. */
                JfrThreadLocal.increaseUnparseableStacks();
                return;
            }
        }

        /* Start a stack walk but from the frame after the top one. */
        if (JavaStackWalker.walkCurrentThread(callerSP, returnAddressIP, visitor) || data.getTruncated()) {
            SamplerSampleWriter.end(data, SamplerSampleWriter.EXECUTION_SAMPLE_END);
        }
    }

    /**
     * Starts/Stops execution sampling and updates the sampling interval.
     *
     * This needs to be a VM operation, because a lot of different races could happen otherwise.
     * Another thread could for example a. enable/disable JFR recording, b. start/stop execution
     * sampling, c. enable/disable the JFR ExecutionSample event. Therefore, we need to re-query all
     * the information once we are in the VM operation.
     */
    private static class UpdateJfrExecutionSamplerOperation extends JavaVMOperation {
        UpdateJfrExecutionSamplerOperation() {
            super(VMOperationInfos.get(UpdateJfrExecutionSamplerOperation.class, "Update JFR sampler", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate() {
            AbstractJfrExecutionSampler sampler = AbstractJfrExecutionSampler.singleton();
            boolean shouldSample = shouldSample();
            if (sampler.isSampling != shouldSample) {
                if (shouldSample) {
                    sampler.isSampling = true;
                    sampler.startSampling();
                } else {
                    sampler.stopSampling();
                    sampler.isSampling = false;
                }
            } else if (shouldSample && sampler.newIntervalMillis != sampler.curIntervalMillis) {
                /* We are already recording but the interval needs to be updated. */
                sampler.updateInterval();
            }
            sampler.curIntervalMillis = sampler.newIntervalMillis;
        }

        @Uninterruptible(reason = "Needed for calling JfrEvent.shouldEmit().")
        private static boolean shouldSample() {
            assert VMOperation.isInProgressAtSafepoint();
            return JfrEvent.ExecutionSample.shouldEmit() && AbstractJfrExecutionSampler.singleton().newIntervalMillis > 0;
        }
    }

    protected static class ExecutionSamplerInstallation {
        private static final int DISALLOWED = -1;
        private static final int ALLOWED = 0;
        private static final int INSTALLED = 1;

        @Uninterruptible(reason = "Prevent VM operations that modify the execution sampler.", callerMustBe = true)
        public static void disallow(IsolateThread thread) {
            assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();
            assert samplerState.get(thread) == ALLOWED;
            samplerState.set(thread, DISALLOWED);
        }

        @Uninterruptible(reason = "Prevent VM operations that modify the execution sampler.", callerMustBe = true)
        public static void installed(IsolateThread thread) {
            assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();
            assert samplerState.get(thread) == ALLOWED;
            samplerState.set(thread, INSTALLED);
        }

        @Uninterruptible(reason = "Prevent VM operations that modify the execution sampler.", callerMustBe = true)
        public static void uninstalled(IsolateThread thread) {
            assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();
            assert samplerState.get(thread) == INSTALLED;
            samplerState.set(thread, ALLOWED);
        }

        @Uninterruptible(reason = "Prevent VM operations that modify the execution sampler.", callerMustBe = true)
        public static boolean isAllowed(IsolateThread thread) {
            assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();
            return samplerState.get(thread) == ALLOWED;
        }

        @Uninterruptible(reason = "Prevent VM operations that modify the execution sampler.", callerMustBe = true)
        public static boolean isInstalled(IsolateThread thread) {
            assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();
            return samplerState.get(thread) == INSTALLED;
        }
    }
}
