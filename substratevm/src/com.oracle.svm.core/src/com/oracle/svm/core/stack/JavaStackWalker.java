/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.stack;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import com.oracle.svm.core.deopt.DeoptimizationSlotPacking;
import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
import com.oracle.svm.core.jfr.JfrStackWalker;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;

/**
 * Provides methods to iterate over the physical Java stack frames of a thread (native stack frames
 * are skipped, see {@link JavaFrameAnchor}). When starting a stack walk, it is possible to
 * explicitly pass a stack pointer (SP) and an instruction pointer (IP) to specify where the stack
 * walk should be started. Note that this first frame must always be a Java frame.
 * <p>
 * For most cases, the "walk*" methods that apply a {@link StackFrameVisitor} are the preferred way
 * to do stack walking. Use cases that are extremely performance sensitive, or cannot use a visitor
 * approach, can use the various "initialize" and "advance" methods directly.
 * <p>
 * The stack walking code must be uninterruptible so that it can be used during garbage collection
 * and in situations where we are out of Java heap memory. It also must not have any static state so
 * that multiple threads can walk their stacks concurrently. State is therefore stored in a
 * stack-allocated {@link JavaStackWalk} structure.
 * <p>
 * If this implementation changes, other code that knows a lot about stack walking (e.g.,
 * {@link JfrStackWalker}) needs to be changed as well.
 *
 * <p>
 * Note that starting a stack walk is potentially dangerous when the walked thread has C code on the
 * stack that was called without a transition (e.g., starting a stack walk in a signal handler). In
 * such a case, stack walking may skip frames of AOT or JIT-compiled code, which can result in
 * unexpected behavior. Here is one example:
 * <ul>
 * <li>We do a C call with {@link Transition#TO_NATIVE} (pushes a {@link JavaFrameAnchor}).</li>
 * <li>The C call finishes and we are back in AOT-compiled code.</li>
 * <li>The AOT-compiled code wants to do a transition back to {@link StatusSupport#STATUS_IN_JAVA}
 * but the fastpath fails.</li>
 * <li>The thread calls the slowpath, which pushes more frames of AOT-compiled code on the
 * stack.</li>
 * <li>A signal handler (such as the async sampler) interrupts execution and pushes native frames to
 * the stack.</li>
 * <li>The signal handler calls a {@link CEntryPoint} and starts executing AOT-compiled code.</li>
 * <li>The AOT-compiled code triggers a segfault and the segfault handler starts a stack walk for
 * the crash log.</li>
 * <li>The stack walk prints the top AOT-compiled frames until it reaches the {@link CEntryPoint} of
 * the signal handler. After that, the IP is outside of AOT-compiled code and the stack walk uses
 * the last frame anchor to skip all frames in between (this includes all the safepoint slowpath
 * frames, even though they belong to AOT-compiled code).</li>
 * </ul>
 */
public final class JavaStackWalker {
    @Platforms(Platform.HOSTED_ONLY.class)
    private JavaStackWalker() {
    }

    @Fold
    static int getJavaFrameOffset() {
        return NumUtil.roundUp(SizeOf.get(JavaStackWalkImpl.class), ConfigurationValues.getTarget().wordSize);
    }

    @Fold
    public static int sizeOfJavaStackWalk() {
        return getJavaFrameOffset() + SizeOf.get(JavaFrame.class);
    }

    /**
     * Returns information about the current physical Java frame. Note that this data is updated
     * in-place when {@link #continueStackWalk} is called. During a stack walk, it is therefore not
     * possible to access the data of a previous frame.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static JavaFrame getCurrentFrame(JavaStackWalk walk) {
        return (JavaFrame) ((Pointer) walk).add(getJavaFrameOffset());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static Pointer getStartSP(JavaStackWalk walk) {
        return cast(walk).getStartSP();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static Pointer getEndSP(JavaStackWalk walk) {
        return cast(walk).getEndSP();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static JavaFrameAnchor getFrameAnchor(JavaStackWalk walk) {
        return cast(walk).getFrameAnchor();
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    public static void initialize(JavaStackWalk walk, IsolateThread thread) {
        initializeFromFrameAnchor(walk, thread, Word.nullPointer());
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    public static void initialize(JavaStackWalk walk, IsolateThread thread, Pointer startSP) {
        initWalk(walk, thread, startSP, Word.nullPointer(), Word.nullPointer(), JavaFrameAnchors.getFrameAnchor(thread));
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    public static void initialize(JavaStackWalk walk, IsolateThread thread, Pointer startSP, Pointer endSP) {
        initWalk(walk, thread, startSP, endSP, Word.nullPointer(), JavaFrameAnchors.getFrameAnchor(thread));
    }

    /**
     * This method should only be used rarely as it is usually not necessary (and potentially
     * dangerous) to specify a {@code startIP} for the stack walk.
     */
    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    public static void initialize(JavaStackWalk walk, IsolateThread thread, Pointer startSP, CodePointer startIP) {
        initWalk(walk, thread, startSP, Word.nullPointer(), startIP, JavaFrameAnchors.getFrameAnchor(thread));
    }

    /**
     * This method should only be used rarely as it is usually not necessary (and potentially
     * dangerous) to specify a {@code startIP} for the stack walk.
     */
    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    public static void initialize(JavaStackWalk walk, IsolateThread thread, Pointer startSP, CodePointer startIP, JavaFrameAnchor anchor) {
        initWalk(walk, thread, startSP, Word.nullPointer(), startIP, anchor);
    }

    @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
    public static void initializeForContinuation(JavaStackWalk walk, StoredContinuation continuation) {
        assert continuation != null;

        CodePointer startIP = StoredContinuationAccess.getIP(continuation);
        if (startIP.isNull()) {
            /* StoredContinuation is uninitialized and therefore not walkable. */
            markAsNotWalkable(walk);
        } else {
            Pointer startSP = StoredContinuationAccess.getFramesStart(continuation);
            Pointer endSP = StoredContinuationAccess.getFramesEnd(continuation);
            initWalk0(walk, startSP, endSP, startIP, Word.nullPointer());
        }
    }

    @Uninterruptible(reason = "StoredContinuation must not move.", callerMustBe = true)
    public static void initializeForContinuation(JavaStackWalk walk, StoredContinuation continuation, CodePointer startIP) {
        assert continuation != null;
        assert startIP.isNonNull();

        Pointer startSP = StoredContinuationAccess.getFramesStart(continuation);
        Pointer endSP = StoredContinuationAccess.getFramesEnd(continuation);
        initWalk0(walk, startSP, endSP, startIP, Word.nullPointer());
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    private static void initializeFromFrameAnchor(JavaStackWalk walk, IsolateThread thread, Pointer endSP) {
        assert thread != CurrentIsolate.getCurrentThread() : "Walking the stack without specifying a start SP is only allowed when walking other threads";

        JavaFrameAnchor frameAnchor = JavaFrameAnchors.getFrameAnchor(thread);
        if (frameAnchor.isNull()) {
            /* Threads that do not have a frame anchor at a safepoint are not walkable. */
            markAsNotWalkable(walk);
        } else {
            initWalk(walk, thread, frameAnchor.getLastJavaSP(), endSP, frameAnchor.getLastJavaIP(), frameAnchor.getPreviousAnchor());
        }
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    private static void initWalk(JavaStackWalk walk, IsolateThread thread, Pointer startSP, Pointer endSP, CodePointer startIP, JavaFrameAnchor anchor) {
        assert thread.isNonNull();
        assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint() : "Walking the stack of another thread is only safe when that thread is stopped at a safepoint";
        assert startSP.isNonNull();

        if (SafepointBehavior.isCrashedThread(thread)) {
            /* Crashed threads may no longer have a stack. */
            markAsNotWalkable(walk);
        } else {
            initWalk0(walk, startSP, endSP, startIP, anchor);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void markAsNotWalkable(JavaStackWalk walk) {
        initWalk0(walk, Word.nullPointer(), Word.nullPointer(), Word.nullPointer(), Word.nullPointer());
    }

    @Uninterruptible(reason = "JavaStackWalk must not contain stale values when this method returns.", callerMustBe = true)
    private static void initWalk0(JavaStackWalk walk, Pointer startSP, Pointer endSP, CodePointer startIP, JavaFrameAnchor anchor) {
        JavaStackWalkImpl w = cast(walk);
        w.setStarted(false);
        w.setStartSP(startSP);
        w.setEndSP(endSP);
        w.setStartIP(startIP);
        w.setFrameAnchor(anchor);

        JavaFrame frame = getCurrentFrame(walk);
        JavaFrames.clearData(frame);
    }

    @Uninterruptible(reason = "JavaStackWalk must not contain stale values when this method returns.", callerMustBe = true)
    public static void updateStackPointerForContinuation(JavaStackWalk walk, StoredContinuation continuation) {
        JavaStackWalkImpl w = cast(walk);
        Pointer newStartSP = StoredContinuationAccess.getFramesStart(continuation);
        long delta = newStartSP.rawValue() - w.getStartSP().rawValue();
        long newEndSP = w.getEndSP().rawValue() + delta;

        w.setStartSP(newStartSP);
        w.setEndSP(Word.pointer(newEndSP));

        JavaFrame frame = getCurrentFrame(walk);
        if (frame.getSP().isNonNull()) {
            long newSP = frame.getSP().rawValue() + delta;
            frame.setSP(Word.pointer(newSP));
        }
    }

    @Uninterruptible(reason = "Prevent deoptimization and GC while in this method.", callerMustBe = true)
    public static boolean advance(JavaStackWalk walk, IsolateThread thread) {
        return advance0(walk, thread, null);
    }

    @Uninterruptible(reason = "Prevent deoptimization and GC while in this method.", callerMustBe = true)
    public static boolean advanceForContinuation(JavaStackWalk walk, StoredContinuation continuation) {
        return advance0(walk, Word.nullPointer(), continuation);
    }

    @Uninterruptible(reason = "Prevent deoptimization and GC while in this method.", callerMustBe = true)
    private static boolean advance0(JavaStackWalk walk, IsolateThread thread, StoredContinuation continuation) {
        JavaStackWalkImpl w = cast(walk);
        if (!w.getStarted()) {
            return startStackWalk(w, thread, continuation);
        }
        return continueStackWalk(w, thread, continuation);
    }

    @Uninterruptible(reason = "Prevent deoptimization and GC while in this method.", callerMustBe = true)
    private static boolean startStackWalk(JavaStackWalkImpl walk, IsolateThread thread, StoredContinuation continuation) {
        walk.setStarted(true);

        JavaFrame frame = getCurrentFrame(walk);
        Pointer startSP = walk.getStartSP();
        Pointer endSP = walk.getEndSP();
        if (startSP.isNull() || endSP.isNonNull() && endSP.belowOrEqual(startSP)) {
            /* The stack is not walkable or there are no frames to walk. */
            JavaFrames.clearData(frame);
            return false;
        }

        /* Determine the actual start IP. */
        CodePointer startIP = walk.getStartIP();
        if (startIP.isNull()) {
            JavaFrameAnchor anchor = skipUnnecessaryFrameAnchors(walk, startSP);
            if (anchor.isNonNull() && startSP == anchor.getLastJavaSP()) {
                startIP = anchor.getLastJavaIP();
            } else {
                startIP = readReturnAddress(thread, continuation, startSP);
            }
            walk.setStartIP(startIP);
        } else {
            assert CodeInfoTable.lookupCodeInfo(startIP).isNonNull();
        }

        /* It is guaranteed that we have at least one frame. */
        JavaFrames.setData(frame, startSP, startIP);
        return true;
    }

    @Uninterruptible(reason = "Prevent deoptimization and GC while in this method.", callerMustBe = true)
    private static boolean continueStackWalk(JavaStackWalkImpl walk, IsolateThread thread, StoredContinuation continuation) {
        assert thread.isNull() != (continuation == null);

        JavaFrame frame = getCurrentFrame(walk);
        Pointer sp = frame.getSP();
        if (sp.isNull()) {
            /* No more frames to walk. */
            JavaFrames.clearData(frame);
            return false;
        }

        Pointer endSP = walk.getEndSP();
        sp = sp.add(JavaFrames.getTotalFrameSize(frame));
        if (JavaFrames.isEntryPoint(frame)) {
            /* Use the frame anchor to skip the native frames. */
            JavaFrameAnchor anchor = skipUnnecessaryFrameAnchors(walk, sp);
            if (anchor.isNonNull()) {
                walk.setFrameAnchor(anchor.getPreviousAnchor());
                if (endSP.isNull() || endSP.aboveThan(anchor.getLastJavaSP())) {
                    JavaFrames.setData(frame, anchor.getLastJavaSP(), anchor.getLastJavaIP());
                    return true;
                }
            }
        } else if (JavaFrames.isInterpreterLeaveStub(frame)) {
            long totalFrameSize = JavaFrames.getTotalFrameSize(frame).rawValue();

            /*
             * Variable frame size is packed into the first stack slot used for argument passing
             * (re-use of deopt slot).
             */
            long deoptSlot = sp.readLong((int) -totalFrameSize);
            long varStackSize = DeoptimizationSlotPacking.decodeVariableFrameSizeFromDeoptSlot(deoptSlot);
            Pointer actualSp = sp.add(Word.unsigned(varStackSize));

            CodePointer ip = readReturnAddress(thread, continuation, actualSp);
            JavaFrames.setData(frame, actualSp, ip);
            return true;
        } else {
            /* Caller is a Java frame. */
            if (endSP.isNull() || endSP.aboveThan(sp)) {
                CodePointer ip = readReturnAddress(thread, continuation, sp);
                JavaFrames.setData(frame, sp, ip);
                return true;
            }
        }

        /* No more frames. */
        JavaFrames.clearData(frame);
        return false;
    }

    /**
     * Skip frame anchors that are not needed for this stack walk. This is necessary because:
     * <ul>
     * <li>When starting a stack walk, the top frame anchor(s) could be outside the stack part that
     * should be walked.</li>
     * <li>While doing a stack walk for the current thread, we may encounter outdated frame anchors.
     * For example, a thread that is in the middle of a transition from
     * {@link StatusSupport#STATUS_IN_NATIVE} to {@link StatusSupport#STATUS_IN_JAVA} may enter the
     * safepoint slowpath. The slowpath code will push Java frames to the top of the stack while the
     * outdated frame anchor is still present and also still needed (another thread might be in the
     * middle of a VM operation and must be able to walk the stack of all threads).</li>
     * </ul>
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static JavaFrameAnchor skipUnnecessaryFrameAnchors(JavaStackWalkImpl walk, Pointer sp) {
        JavaFrameAnchor anchor = walk.getFrameAnchor();
        while (anchor.isNonNull() && anchor.getLastJavaSP().belowThan(sp)) {
            anchor = anchor.getPreviousAnchor();
        }
        walk.setFrameAnchor(anchor);
        return anchor;
    }

    @Uninterruptible(reason = "Stored continuation must not move.")
    private static CodePointer readReturnAddress(IsolateThread thread, StoredContinuation continuation, Pointer startSP) {
        if (ContinuationSupport.isSupported() && continuation != null) {
            assert thread.isNull();
            return FrameAccess.singleton().readReturnAddress(continuation, startSP);
        } else {
            assert thread.isNonNull() && continuation == null;
            return FrameAccess.singleton().readReturnAddress(thread, startSP);
        }
    }

    @Uninterruptible(reason = "Not really uninterruptible, but we are about to fatally fail.", calleeMustBe = false)
    public static RuntimeException fatalErrorUnknownFrameEncountered(Pointer sp, CodePointer ip) {
        Log log = Log.log().string("Stack walk must walk only frames of known code:");
        log.string("  sp=").zhex(sp).string("  ip=").zhex(ip);
        log.newline();
        throw VMError.shouldNotReachHere("Stack walk must walk only frames of known code");
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean walkCurrentThread(Pointer startSP, StackFrameVisitor visitor) {
        return walkCurrentThread(startSP, visitor, null);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean walkCurrentThread(Pointer startSP, Pointer endSP, StackFrameVisitor visitor) {
        assert startSP.isNonNull();
        return walkCurrentThread(startSP, endSP, Word.nullPointer(), visitor, null);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean walkCurrentThread(Pointer startSP, ParameterizedStackFrameVisitor visitor, Object data) {
        return walkCurrentThread(startSP, Word.nullPointer(), Word.nullPointer(), visitor, data);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean walkCurrentThread(Pointer startSP, CodePointer startIP, ParameterizedStackFrameVisitor visitor) {
        return walkCurrentThread(startSP, Word.nullPointer(), startIP, visitor, null);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean walkCurrentThread(Pointer startSP, Pointer endSP, CodePointer startIP, ParameterizedStackFrameVisitor visitor, Object data) {
        IsolateThread thread = CurrentIsolate.getCurrentThread();
        JavaStackWalk walk = StackValue.get(JavaStackWalker.sizeOfJavaStackWalk());
        initWalk(walk, thread, startSP, endSP, startIP, JavaFrameAnchors.getFrameAnchor());
        return doWalk(walk, thread, visitor, data);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean walkThread(IsolateThread thread, StackFrameVisitor visitor) {
        return walkThread(thread, visitor, null);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean walkThread(IsolateThread thread, ParameterizedStackFrameVisitor visitor, Object data) {
        return walkThread(thread, Word.nullPointer(), visitor, data);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean walkThread(IsolateThread thread, Pointer endSP, ParameterizedStackFrameVisitor visitor, Object data) {
        JavaStackWalk walk = StackValue.get(JavaStackWalker.sizeOfJavaStackWalk());
        initializeFromFrameAnchor(walk, thread, endSP);
        return doWalk(walk, thread, visitor, data);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static void walkThread(IsolateThread thread, Pointer startSP, Pointer endSP, CodePointer startIP, StackFrameVisitor visitor) {
        JavaStackWalk walk = StackValue.get(JavaStackWalker.sizeOfJavaStackWalk());
        initWalk(walk, thread, startSP, endSP, startIP, JavaFrameAnchors.getFrameAnchor(thread));
        doWalk(walk, thread, visitor, null);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    static boolean doWalk(JavaStackWalk walk, IsolateThread thread, ParameterizedStackFrameVisitor visitor, Object data) {
        while (advance(walk, thread)) {
            JavaFrame frame = JavaStackWalker.getCurrentFrame(walk);
            Pointer sp = frame.getSP();
            CodePointer ip = frame.getIP();

            if (JavaFrames.isUnknownFrame(frame)) {
                return visitUnknownFrame(sp, ip, visitor, data);
            }

            DeoptimizedFrame deoptimizedFrame = Deoptimizer.checkEagerDeoptimized(frame);
            if (deoptimizedFrame != null) {
                if (!visitDeoptimizedFrame(sp, ip, deoptimizedFrame, visitor, data)) {
                    return false;
                }
            } else {
                // Note that this code also visits frames pending lazy deoptimization.
                UntetheredCodeInfo untetheredInfo = frame.getIPCodeInfo();
                Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
                try {
                    CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
                    if (!visitRegularFrame(sp, ip, info, visitor, data)) {
                        return false;
                    }
                } finally {
                    CodeInfoAccess.releaseTether(untetheredInfo, tether);
                }
            }
        }

        return true;
    }

    @Uninterruptible(reason = "CodeInfo in JavaStackWalk is currently null, and we are going to abort the stack walking.", calleeMustBe = false)
    private static boolean visitUnknownFrame(Pointer sp, CodePointer ip, ParameterizedStackFrameVisitor visitor, Object data) {
        return visitor.unknownFrame(sp, ip, data);
    }

    @Uninterruptible(reason = "Wraps the now safe call to the possibly interruptible visitor.", callerMustBe = true, calleeMustBe = false)
    @RestrictHeapAccess(reason = "Whitelisted because some StackFrameVisitor implementations can allocate.", access = RestrictHeapAccess.Access.UNRESTRICTED)
    private static boolean visitRegularFrame(Pointer sp, CodePointer ip, CodeInfo info, ParameterizedStackFrameVisitor visitor, Object data) {
        return visitor.visitRegularFrame(sp, ip, info, data);
    }

    @Uninterruptible(reason = "Wraps the now safe call to the possibly interruptible visitor.", callerMustBe = true, calleeMustBe = false)
    @RestrictHeapAccess(reason = "Whitelisted because some StackFrameVisitor implementations can allocate.", access = RestrictHeapAccess.Access.UNRESTRICTED)
    private static boolean visitDeoptimizedFrame(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame, ParameterizedStackFrameVisitor visitor, Object data) {
        return visitor.visitDeoptimizedFrame(sp, ip, deoptimizedFrame, data);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static JavaStackWalkImpl cast(JavaStackWalk walk) {
        return (JavaStackWalkImpl) walk;
    }
}
