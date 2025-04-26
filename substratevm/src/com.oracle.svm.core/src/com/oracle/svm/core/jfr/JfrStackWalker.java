/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jfr;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.sampler.SamplerSampleWriter;
import com.oracle.svm.core.sampler.SamplerSampleWriterData;
import com.oracle.svm.core.sampler.SamplerSampleWriterDataAccess;
import com.oracle.svm.core.stack.JavaFrame;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.stack.JavaFrameAnchors;
import com.oracle.svm.core.stack.JavaFrames;
import com.oracle.svm.core.stack.JavaStackWalk;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.VMError;

/**
 * Does a stack walk and records the instruction pointers of the physical Java frames that it
 * encounters. Note that this class knows a lot of details about stack walking, so it needs to be in
 * sync with {@link JavaStackWalker}.
 *
 * The code parts that are used for the async sampler need to be implemented in a very defensive way
 * as the async sampler may encounter unexpected stack states. For this reason, this class may only
 * use the unsafe methods of {@link FrameAccess} to read the return address. Otherwise, the
 * validation in {@link FrameAccess} could fail.
 */
public final class JfrStackWalker {
    /** A stack trace was recorded. */
    public static final int NO_ERROR = 0;
    /** A stack trace was recorded, but it was truncated because we encountered too many frames. */
    public static final int TRUNCATED = 1;
    /** No stack trace was recorded because the stack was unparseable. */
    public static final int UNPARSEABLE_STACK = 2;
    /** No stack trace was recorded because it did not fit into the buffer. */
    public static final int BUFFER_SIZE_EXCEEDED = 3;

    @Platforms(Platform.HOSTED_ONLY.class)
    private JfrStackWalker() {
    }

    @Uninterruptible(reason = "The method executes during signal handling.", callerMustBe = true)
    public static void walkCurrentThread(CodePointer initialIP, Pointer initialSP, boolean isAsync) {
        SamplerSampleWriterData data = UnsafeStackValue.get(SamplerSampleWriterData.class);
        if (SamplerSampleWriterDataAccess.initialize(data, 0, false)) {
            SamplerSampleWriter.begin(data);
            int result = walkCurrentThread(data, initialIP, initialSP, isAsync);

            switch (result) {
                case NO_ERROR, TRUNCATED -> SamplerSampleWriter.end(data, SamplerSampleWriter.EXECUTION_SAMPLE_END);
                case UNPARSEABLE_STACK -> {
                    VMError.guarantee(isAsync, "Only the async sampler may encounter an unparseable stack.");
                    JfrThreadLocal.increaseUnparseableStacks();
                }
                case BUFFER_SIZE_EXCEEDED -> JfrThreadLocal.increaseMissedSamples();
                default -> throw VMError.shouldNotReachHere("Unexpected return value");
            }
        }
    }

    @Uninterruptible(reason = "The method executes during signal handling.", callerMustBe = true)
    public static int walkCurrentThread(SamplerSampleWriterData data, CodePointer topFrameIP, Pointer topFrameSP, boolean isAsync) {
        CodePointer ip = topFrameIP;
        Pointer sp = topFrameSP;
        JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor();

        if (isAsync) {
            if (VMThreads.SafepointBehavior.isCrashedThread(CurrentIsolate.getCurrentThread())) {
                return UNPARSEABLE_STACK;
            }

            CodeInfo codeInfo = CodeInfoTable.lookupImageCodeInfo(ip);
            if (codeInfo.isNonNull()) {
                /*
                 * We are in Java code, so the IP is accurate, and we can record it. However, it is
                 * possible that the IP is for a method that is usually not visible in a stack walk
                 * (e.g., if uninterruptible AOT-compiled code is somehow called
                 * via @CFunction(NO_TRANSITION)). In such a case, it is possible that we record an
                 * invalid/impossible stack trace because we use the frame anchors to continue the
                 * stack walk once we reach an entry point frame. This may result in skipped frames
                 * because there is no frame anchor for @CFunction(NO_TRANSITION) call.
                 */
                int result = recordIp(data, ip);
                if (result != NO_ERROR) {
                    return result;
                }

                /*
                 * We might be in the middle of pushing a new frame anchor. In that case, the top
                 * frame anchor will have invalid values and needs to be filtered out.
                 */
                anchor = filterTopFrameAnchorIfIncomplete(anchor);

                /* Move SP to the top of the caller frame. */
                long topFrameEncodedSize = CodeInfoAccess.lookupEncodedFrameSize(codeInfo, ip);
                boolean topFrameIsEntryPoint = CodeInfoQueryResult.isEntryPoint(topFrameEncodedSize);
                if (topFrameIsEntryPoint) {
                    /*
                     * If the top frame is for an entry point, then the caller needs to be treated
                     * like a native frame so that we are consistent with the normal stack walking
                     * code (i.e., we need to use the frame anchors). Note that the return address
                     * might point to AOT-compiled code though (e.g., if we did a @CFunction call to
                     * AOT-compiled code).
                     */
                    if (anchor.isNull()) {
                        return UNPARSEABLE_STACK;
                    }

                    sp = anchor.getLastJavaSP();
                    ip = anchor.getLastJavaIP();
                    anchor = anchor.getPreviousAnchor();
                } else {
                    /* Both the top frame and its caller are probably Java frames. */
                    if (isSPAligned(sp)) {
                        UnsignedWord topFrameSize = Word.unsigned(CodeInfoQueryResult.getTotalFrameSize(topFrameEncodedSize));
                        if (SubstrateOptions.hasFramePointer() && !hasValidCaller(sp, topFrameSize, topFrameIsEntryPoint, anchor)) {
                            /*
                             * If we have a frame pointer, then the stack pointer can be aligned
                             * while we are in the method prologue/epilogue (i.e., the frame pointer
                             * and the return address are on top of the stack, but the actual stack
                             * frame is missing). We should reach the caller if we skip the
                             * incomplete top frame (frame pointer and return address).
                             */
                            sp = sp.add(FrameAccess.wordSize() * 2);
                        } else {
                            /*
                             * Stack looks walkable - skip the top frame as we already recorded the
                             * corresponding IP.
                             */
                            sp = sp.add(topFrameSize);
                        }
                    } else {
                        /*
                         * The async sampler interrupted the thread while it was in the middle of
                         * manipulating the stack (e.g., in a method prologue/epilogue). Most
                         * likely, there is a valid return address at the top of the stack that we
                         * can just skip.
                         */
                        sp = sp.add(FrameAccess.wordSize());
                    }

                    /* Do a basic sanity check and decide if it makes sense to continue. */
                    assert isSPAligned(sp);
                    if (!isCallerSPValid(topFrameSP, sp)) {
                        /* One of the assumptions above was incorrect. */
                        return UNPARSEABLE_STACK;
                    }

                    ip = FrameAccess.singleton().unsafeReadReturnAddress(sp);
                }
            } else {
                /*
                 * We are in native code, so we need to use the frame anchors to figure out where to
                 * start the stack walk.
                 */
                if (anchor.isNull()) {
                    /*
                     * The anchor is still null if the function is interrupted during the prologue
                     * (see: com.oracle.svm.core.graal.snippets.CFunctionSnippets.prologueSnippet)
                     * or if the thread called a native method without transition and without
                     * previous anchors.
                     */
                    return UNPARSEABLE_STACK;
                }

                /*
                 * Use the values from the frame anchor. If we are in native code that was called
                 * without a transition, we accept that we accidentally use the frame anchor of an
                 * older frame, which will result in a stack trace that misses the top frames.
                 */
                ip = anchor.getLastJavaIP();
                sp = anchor.getLastJavaSP();
                anchor = anchor.getPreviousAnchor();
            }

            /*
             * Check if the SP and IP are sane enough to start a stack walk. For the async sampler,
             * it is always possible to encounter a completely unexpected stack state.
             */
            if (!isCallerValid(topFrameSP, sp, ip)) {
                return UNPARSEABLE_STACK;
            }
        }

        return walkCurrentThread0(data, sp, ip, anchor, isAsync);
    }

    /**
     * When this method is called, we know that SP points into the stack of the current thread and
     * IP points into AOT compiled code.
     *
     * If the async sampler is used, both values can still be incorrect though (i.e., they might
     * just be sane enough so that we did not detect any obvious issues). Therefore, it can happen
     * that we encounter invalid stack frames in the middle of the stack walk. We abort the stack
     * walk in such a case. Note that it is possible that the stack walk finishes successfully even
     * though it started with invalid data. We accept that we record an invalid/impossible stack
     * trace in that case.
     */
    @Uninterruptible(reason = "The method executes during signal handling.", callerMustBe = true)
    private static int walkCurrentThread0(SamplerSampleWriterData data, Pointer startSP, CodePointer startIP, JavaFrameAnchor anchor, boolean isAsync) {
        IsolateThread thread = CurrentIsolate.getCurrentThread();
        JavaStackWalk walk = UnsafeStackValue.get(JavaStackWalker.sizeOfJavaStackWalk());
        JavaStackWalker.initialize(walk, thread, startSP, startIP, anchor);
        assert JavaStackWalker.getEndSP(walk).isNull() : "not supported by the code below";

        while (JavaStackWalker.advance(walk, thread)) {
            JavaFrame frame = JavaStackWalker.getCurrentFrame(walk);
            VMError.guarantee(!Deoptimizer.checkIsDeoptimized(frame), "JIT compilation is not supported");

            if (JavaFrames.isUnknownFrame(frame) || isAsync && !hasValidCaller(walk, frame)) {
                /* Most likely, the stack walk already started with a wrong SP or IP. */
                return UNPARSEABLE_STACK;
            }

            int result = recordIp(data, frame.getIP());
            if (result != NO_ERROR) {
                return result;
            }
        }

        return NO_ERROR;
    }

    @Uninterruptible(reason = "The method executes during signal handling.", callerMustBe = true)
    private static int recordIp(SamplerSampleWriterData data, CodePointer ip) {
        assert data.isNonNull();

        /* Increment the number of seen frames. */
        data.setSeenFrames(data.getSeenFrames() + 1);

        if (shouldTruncate(data)) {
            return TRUNCATED;
        }

        boolean success = SamplerSampleWriter.putLong(data, ip.rawValue());
        if (success) {
            int newHash = computeHash(data.getHashCode(), ip.rawValue());
            data.setHashCode(newHash);
            return NO_ERROR;
        }
        /* There was not enough space in the current buffer and no new/larger buffer. */
        return BUFFER_SIZE_EXCEEDED;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean shouldTruncate(SamplerSampleWriterData data) {
        int maxFrames = data.getMaxDepth() + data.getSkipCount();
        if (data.getSeenFrames() > maxFrames) {
            /* The stack size exceeds given depth. Stop walk! */
            data.setTruncated(true);
            return true;
        }
        return false;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int computeHash(int oldHash, long ip) {
        int hash = (int) (ip ^ (ip >>> 32));
        return 31 * oldHash + hash;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static JavaFrameAnchor filterTopFrameAnchorIfIncomplete(JavaFrameAnchor anchor) {
        if (anchor.isNonNull() && (anchor.getLastJavaSP().isNull() || anchor.getLastJavaIP().isNull())) {
            /* We are probably in the middle of pushing a frame anchor, so filter the top anchor. */
            return anchor.getPreviousAnchor();
        }
        return anchor;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean hasValidCaller(JavaStackWalk walk, JavaFrame frame) {
        return hasValidCaller(frame.getSP(), JavaFrames.getTotalFrameSize(frame), JavaFrames.isEntryPoint(frame), JavaStackWalker.getFrameAnchor(walk));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean hasValidCaller(Pointer currentSP, UnsignedWord currentFrameSize, boolean currentFrameIsEntryPoint, JavaFrameAnchor anchor) {
        if (currentFrameIsEntryPoint) {
            /*
             * The caller frame should belong to native code. However, we can't validate that the
             * return address points into native code because AOT-compiled code may be called via
             * a @CFunction call as well. So, we only do a basic sanity check of the frame anchor.
             */
            return anchor.isNull() || anchor.getLastJavaSP().aboveThan(currentSP) && CodeInfoTable.isInAOTImageCode(anchor.getLastJavaIP());
        } else {
            /* The caller frame should belong to Java code. */
            Pointer callerSP = currentSP.add(currentFrameSize);
            if (!isCallerSPValid(currentSP, callerSP)) {
                return false;
            }

            /* Check if the return address points into AOT-compiled Java code. */
            CodePointer ip = FrameAccess.singleton().unsafeReadReturnAddress(callerSP);
            return CodeInfoTable.isInAOTImageCode(ip);
        }
    }

    /**
     * Check whether the given caller stack pointer (and the corresponding return address) are
     * within the currently used part of the current thread's stack.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isCallerSPValid(Pointer currentSP, Pointer callerSP) {
        UnsignedWord stackEnd = VMThreads.StackEnd.get();
        UnsignedWord stackBase = VMThreads.StackBase.get();
        if (stackEnd.equal(0) || stackBase.equal(0)) {
            /* Stack boundaries are unknown. */
            return false;
        }

        assert stackEnd.belowThan(stackBase);
        assert stackEnd.belowOrEqual(currentSP);
        assert stackBase.aboveThan(currentSP);

        if (isSPAligned(callerSP) && callerSP.aboveThan(currentSP) && callerSP.belowThan(stackBase)) {
            UnsignedWord returnAddressLocation = FrameAccess.singleton().unsafeReturnAddressLocation(callerSP);
            return returnAddressLocation.aboveOrEqual(currentSP) && returnAddressLocation.belowThan(stackBase);
        }
        return false;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isSPAligned(Pointer sp) {
        return PointerUtils.isAMultiple(sp, Word.unsigned(ConfigurationValues.getTarget().stackAlignment));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isCallerValid(Pointer currentSP, Pointer callerSP, CodePointer callerIP) {
        return CodeInfoTable.isInAOTImageCode(callerIP) && isCallerSPValid(currentSP, callerSP);
    }
}
