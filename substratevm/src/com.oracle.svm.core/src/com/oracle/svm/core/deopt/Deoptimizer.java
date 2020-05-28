/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.deopt;

// Checkstyle: allow reflection

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.nio.ByteOrder;
import java.util.ArrayList;

import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Specialize;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoDecoder;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueInfo;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.deopt.DeoptimizedFrame.RelockObjectData;
import com.oracle.svm.core.deopt.DeoptimizedFrame.VirtualFrame;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.log.StringBuilderLog;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.RingBuffer;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;
import sun.misc.Unsafe;

/**
 * Performs deoptimization. The method to deoptimize (= the source method) is either a specialized
 * runtime compiled method or an image compiled test method with the {@link Specialize} annotation.
 * The target method is always an image compiled method.
 * <p>
 * Deoptimization is not limited to a single method. It can be done for all deoptimizable methods in
 * the call stack. A method is deoptimizable if {@link FrameInfoQueryResult deoptimization
 * information} is available.
 * <p>
 * Deoptimization is done in two steps:
 * <ol>
 * <li>A call to {@link #deoptimizeInRange} walks the stack and for each method to deoptimize it
 * builds a {@link DeoptimizedFrame}. This handle contains all constants and materialized objects
 * which are needed to build the deoptimization target frames. It is stored in the stack slot right
 * above the return address. The return address (to the deoptimized method) is replaced by a pointer
 * to {@link #deoptStub}.
 *
 * <pre>
 *    :                                :
 *    |                                |
 *    |                                |
 *    +--------------------------------+   frame of the
 *    | outgoing stack parameters      |   deopmethod
 *    +--------------------------------+
 *    | pointer to DeoptimizedFrame    |
 *    +--------------------------------+---------
 *    | pointer to deoptStub           |   return address
 *    +--------------------------------+---------
 *    |                                |
 *    |                                |   frame of {@link #deoptimizeInRange} or
 *    |                                |   a method which called it
 *    :     ...                        :
 * </pre>
 *
 * From now on, the frame of the deoptimized method is no longer valid and the GC will ignore it.
 * Instead the GC will also visit the pointer to the {@link DeoptimizedFrame}. In other words: the
 * frame of the deoptimized method is "replaced" by a single entry, a pointer to
 * {@link DeoptimizedFrame}, which contains all objects which are needed by the deoptimization
 * targets.
 * <p>
 * There is one exception: outgoing parameters. Outgoing parameters of a deoptimized method may
 * still be accessed by a called method, even after the first step of deoptimization is done.
 * Therefore the calling convention must make sure that there is a free stack slot for the
 * {@link DeoptimizedFrame} between the outgoing parameters and the return address slot.
 * <p>
 * Exception from the exception: outgoing object parameters are always copied to registers at the
 * beginning of the called method. Therefore we don't have to worry about GC these parameters.</li>
 * <p>
 * <li>Now when a called method will return to a deoptimized method, the {@link #deoptStub} will be
 * called instead. It reads the {@link DeoptimizedFrame} handle and replaces the deoptimized
 * method's frame with the frame(s) of the deopt target method(s). Note that the deopt stub is
 * completely allocation free.</li>
 * </ol>
 */
public final class Deoptimizer {

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    private static final RingBuffer<char[]> recentDeoptimizationEvents = new RingBuffer<>();

    private static final int actionShift = 0;
    private static final int actionBits = Integer.SIZE - Integer.numberOfLeadingZeros(DeoptimizationAction.values().length);
    private static final int reasonShift = actionShift + actionBits;
    private static final int reasonBits = Integer.SIZE - Integer.numberOfLeadingZeros(DeoptimizationReason.values().length);
    private static final int idShift = reasonShift + reasonBits;
    private static final int idBits = Integer.SIZE;

    public static long encodeDeoptActionAndReasonToLong(DeoptimizationAction action, DeoptimizationReason reason, int speculationId) {
        return ((long) action.ordinal() << actionShift) | ((long) reason.ordinal() << reasonShift) | ((long) speculationId << idShift);
    }

    public static JavaConstant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, int speculationId) {
        JavaConstant result = JavaConstant.forLong(encodeDeoptActionAndReasonToLong(action, reason, speculationId));
        assert decodeDeoptAction(result) == action;
        assert decodeDeoptReason(result) == reason;
        assert decodeDebugId(result) == speculationId;
        return result;
    }

    public static DeoptimizationAction decodeDeoptAction(long actionAndReason) {
        return DeoptimizationAction.values()[(int) ((actionAndReason >> actionShift) & ((1L << actionBits) - 1))];
    }

    public static DeoptimizationReason decodeDeoptReason(long actionAndReason) {
        return DeoptimizationReason.values()[(int) ((actionAndReason >> reasonShift) & ((1L << reasonBits) - 1))];
    }

    public static int decodeDebugId(long actionAndReason) {
        return (int) ((actionAndReason >> idShift) & ((1L << idBits) - 1));
    }

    public static DeoptimizationAction decodeDeoptAction(JavaConstant actionAndReason) {
        return decodeDeoptAction(actionAndReason.asLong());
    }

    public static DeoptimizationReason decodeDeoptReason(JavaConstant actionAndReason) {
        return decodeDeoptReason(actionAndReason.asLong());
    }

    public static int decodeDebugId(JavaConstant actionAndReason) {
        return decodeDebugId(actionAndReason.asLong());
    }

    private static boolean checkEncoding() {
        for (DeoptimizationAction action : DeoptimizationAction.values()) {
            for (DeoptimizationReason reason : DeoptimizationReason.values()) {
                for (int speculationId : new int[]{0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                    encodeDeoptActionAndReason(action, reason, speculationId);
                }
            }
        }
        return true;
    }

    static {
        assert checkEncoding();
    }

    public static class Options {
        @Option(help = "Print logging information for every deoptimization")//
        public static final RuntimeOptionKey<Boolean> TraceDeoptimization = new RuntimeOptionKey<>(false);

        @Option(help = "Print verbose logging information for every deoptimization")//
        public static final RuntimeOptionKey<Boolean> TraceDeoptimizationDetails = new RuntimeOptionKey<>(false);
    }

    /**
     * If true, the GC is called during deoptimization. The deoptimizer allocates some objects (in
     * the first step), so GC must work inside the deoptimizer.
     */
    public static boolean testGCinDeoptimizer = false;

    /**
     * Checks if a physical stack frame (identified by the stack pointer) was deoptimized, and
     * returns the {@link DeoptimizedFrame} in that case.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static DeoptimizedFrame checkDeoptimized(Pointer sourceSp) {
        if (DeoptimizationSupport.enabled()) {
            CodePointer returnAddress = FrameAccess.singleton().readReturnAddress(sourceSp);
            /* A frame is deoptimized when the return address was patched to the deoptStub. */
            if (returnAddress.equal(DeoptimizationSupport.getDeoptStubPointer())) {
                /* The DeoptimizedFrame instance is stored above the return address. */
                DeoptimizedFrame result = KnownIntrinsics.convertUnknownValue(sourceSp.readObject(0), DeoptimizedFrame.class);
                assert result != null;
                return result;
            }
        }
        return null;
    }

    private static void installDeoptimizedFrame(Pointer sourceSp, DeoptimizedFrame deoptimizedFrame) {
        /*
         * Replace the return address to the deoptimized method with a pointer to the deoptStub.
         */
        FrameAccess.singleton().writeReturnAddress(sourceSp, DeoptimizationSupport.getDeoptStubPointer());

        /*
         * Store a pointer to the deoptimizedFrame on stack slot above the return address. From this
         * point on, the GC will ignore the original source frame content. Instead it just collects
         * this pointer to deoptimizedFrame.
         */
        sourceSp.writeWord(0, deoptimizedFrame.getPin().addressOfObject());
    }

    /**
     * Deoptimizes all method(s) in all call stacks (= the calling methods). Only used for testing.
     */
    @NeverInline("deoptimize must have a separate stack frame")
    public static void deoptimizeAll() {
        JavaVMOperation.enqueueBlockingSafepoint("Deoptimizer.deoptimizeInRange", () -> {
            deoptimizeInRange((CodePointer) WordFactory.zero(), (CodePointer) WordFactory.zero(), true);
        });
    }

    /**
     * Deoptimize a specific method.
     *
     * @param fromIp The lower address (including) of the method's code.
     * @param toIp The upper address (excluding) of the method's code.
     */
    @NeverInline("deoptimize must have a separate stack frame")
    public static void deoptimizeInRange(CodePointer fromIp, CodePointer toIp, boolean deoptAll) {
        VMOperation.guaranteeInProgressAtSafepoint("Deoptimization requires a safepoint.");
        /* Captures "fromIp", "toIp", and "deoptAll" for the VMOperation. */
        deoptimizeInRangeOperation(fromIp, toIp, deoptAll);
    }

    /** Deoptimize a specific method on all thread stacks. */
    @NeverInline("Starting a stack walk in the caller frame. " +
                    "Note that we could start the stack frame also further down the stack, because VM operation frames never need deoptimization. " +
                    "But we don't store stack frame information for the first frame we would need to process.")
    private static void deoptimizeInRangeOperation(CodePointer fromIp, CodePointer toIp, boolean deoptAll) {
        VMOperation.guaranteeInProgress("Deoptimizer.deoptimizeInRangeOperation, but not in VMOperation.");
        /* Handle my own thread specially, because I do not have a JavaFrameAnchor. */
        Pointer sp = KnownIntrinsics.readCallerStackPointer();

        StackFrameVisitor currentThreadDeoptVisitor = getStackFrameVisitor((Pointer) fromIp, (Pointer) toIp, deoptAll);
        JavaStackWalker.walkCurrentThread(sp, currentThreadDeoptVisitor);

        /* If I am multi-threaded, deoptimize this method on all the other stacks. */
        if (SubstrateOptions.MultiThreaded.getValue()) {
            for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                if (vmThread == CurrentIsolate.getCurrentThread()) {
                    continue;
                }
                StackFrameVisitor deoptVisitor = getStackFrameVisitor((Pointer) fromIp, (Pointer) toIp, deoptAll);
                JavaStackWalker.walkThread(vmThread, deoptVisitor);
            }
        }
        if (testGCinDeoptimizer) {
            Heap.getHeap().getGC().collect(GCCause.TestGCInDeoptimizer);
        }
    }

    private static StackFrameVisitor getStackFrameVisitor(Pointer fromIp, Pointer toIp, boolean deoptAll) {
        return new StackFrameVisitor() {
            @Override
            public boolean visitFrame(Pointer frameSp, CodePointer frameIp, CodeInfo codeInfo, DeoptimizedFrame deoptFrame) {
                Pointer ip = (Pointer) frameIp;
                if (deoptFrame == null && ((ip.aboveOrEqual(fromIp) && ip.belowThan(toIp)) || deoptAll)) {
                    CodeInfoQueryResult queryResult = CodeInfoTable.lookupCodeInfoQueryResult(codeInfo, frameIp);
                    Deoptimizer deoptimizer = new Deoptimizer(frameSp, queryResult);
                    deoptimizer.deoptSourceFrame(frameIp, deoptAll);
                }
                return true;
            }
        };
    }

    /**
     * Deoptimizes the given frame.
     *
     * @param ignoreNonDeoptimizable if set to true, a frame that cannot be deoptimized is ignored
     *            instead of raising an error (use for deoptimzation testing only).
     */
    @NeverInline("Inlining of this method would require that we have deopt targets for callees of this method (SVM internals).")
    public static void deoptimizeFrame(Pointer sourceSp, boolean ignoreNonDeoptimizable, SpeculationReason speculation) {
        DeoptimizedFrame deoptFrame = Deoptimizer.checkDeoptimized(sourceSp);
        if (deoptFrame != null) {
            /* Already deoptimized, so nothing to do. */
            registerSpeculationFailure(deoptFrame.getSourceInstalledCode(), speculation);
            return;
        }
        JavaVMOperation.enqueueBlockingSafepoint("DeoptimizeFrame", () -> Deoptimizer.deoptimizeFrameOperation(sourceSp, ignoreNonDeoptimizable, speculation));
    }

    private static void deoptimizeFrameOperation(Pointer sourceSp, boolean ignoreNonDeoptimizable, SpeculationReason speculation) {
        VMOperation.guaranteeInProgress("doDeoptimizeFrame");
        CodePointer returnAddress = FrameAccess.singleton().readReturnAddress(sourceSp);
        deoptimizeFrame(sourceSp, ignoreNonDeoptimizable, speculation, returnAddress);
    }

    @Uninterruptible(reason = "Prevent the GC from freeing the CodeInfo object.")
    private static void deoptimizeFrame(Pointer sourceSp, boolean ignoreNonDeoptimizable, SpeculationReason speculation, CodePointer returnAddress) {
        UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(returnAddress);
        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try {
            CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
            deoptimize(sourceSp, ignoreNonDeoptimizable, speculation, returnAddress, info);
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
    }

    @Uninterruptible(reason = "Pass the now protected CodeInfo object to interruptible code.", calleeMustBe = false)
    private static void deoptimize(Pointer sourceSp, boolean ignoreNonDeoptimizable, SpeculationReason speculation, CodePointer returnAddress, CodeInfo info) {
        deoptimize0(sourceSp, ignoreNonDeoptimizable, speculation, returnAddress, info);
    }

    private static void deoptimize0(Pointer sourceSp, boolean ignoreNonDeoptimizable, SpeculationReason speculation, CodePointer returnAddress, CodeInfo info) {
        CodeInfoQueryResult queryResult = CodeInfoTable.lookupCodeInfoQueryResult(info, returnAddress);
        Deoptimizer deoptimizer = new Deoptimizer(sourceSp, queryResult);
        DeoptimizedFrame sourceFrame = deoptimizer.deoptSourceFrame(returnAddress, ignoreNonDeoptimizable);
        if (sourceFrame != null) {
            registerSpeculationFailure(sourceFrame.getSourceInstalledCode(), speculation);
        }
    }

    /**
     * Invalidates the {@link InstalledCode} of the method of the given frame. The method must be a
     * runtime compiled method, since there is not {@link InstalledCode} for native image methods.
     */
    public static void invalidateMethodOfFrame(Pointer sourceSp, SpeculationReason speculation) {
        CodePointer returnAddress = FrameAccess.singleton().readReturnAddress(sourceSp);
        SubstrateInstalledCode installedCode = CodeInfoTable.lookupInstalledCode(returnAddress);
        /*
         * We look up the installedCode before checking if the frame is deoptimized to avoid race
         * conditions. We are not in a VMOperation here. When a deoptimization happens, e.g., at a
         * safepoint taken at the method exit of checkDeoptimized, then the result value
         * deoptimizedFrame will be null but the return address is already patched to the deoptStub.
         * We would not be able to find the installedCode in such a case. Invalidating the same
         * installedCode multiple times in case of a race is not a problem because the actual
         * invalidation is in a VMOperation.
         */
        DeoptimizedFrame deoptimizedFrame = checkDeoptimized(sourceSp);
        if (deoptimizedFrame != null) {
            installedCode = deoptimizedFrame.getSourceInstalledCode();
            if (installedCode == null) {
                /* When the method was invalidated before, all the metadata can be gone by now. */
                return;
            }
        } else {
            if (installedCode == null) {
                CodeInfoTable.getRuntimeCodeCache().logTable();
                throw VMError.shouldNotReachHere(
                                "Only runtime compiled methods can be invalidated. sp = " + Long.toHexString(sourceSp.rawValue()) + ", returnAddress = " + Long.toHexString(returnAddress.rawValue()));
            }
        }
        registerSpeculationFailure(installedCode, speculation);
        VMOperation.guaranteeNotInProgress("invalidateMethodOfFrame: running user code that can block");
        installedCode.invalidate();
    }

    private static void registerSpeculationFailure(SubstrateInstalledCode installedCode, SpeculationReason speculation) {
        if (installedCode != null && speculation != null) {
            SubstrateSpeculationLog speculationLog = installedCode.getSpeculationLog();
            if (speculationLog != null) {
                speculationLog.addFailedSpeculation(speculation);
            }

        }
    }

    /**
     * The last offset (excluding) in the new stack content where stack parameters are written. This
     * is only used to check if no other stack content overlaps with the stack parameters.
     */
    int endOfParams;

    private final CodeInfoQueryResult sourceChunk;

    /**
     * The current position when walking over the source stack frames.
     */
    private final Pointer sourceSp;

    /**
     * All objects, which are materialized during deoptimization.
     */
    private Object[] materializedObjects;

    /** The recursive locking depth of {@link #materializedObjects} that need to be re-locked. */
    private ArrayList<RelockObjectData> relockedObjects;

    /**
     * The size of the new stack content after all stack entries are built).
     */
    protected int targetContentSize;

    /**
     * The size of frame for the deoptimization stub. Initialized to 0 in the native image and
     * updated at first use at run time. Cached because it is a constant, expensive to compute, and
     * computing it before I become uninterruptible saves making a lot of extraneous code
     * uninterruptible.
     */
    protected static long deoptStubFrameSize = 0L;

    public Deoptimizer(Pointer sourceSp, CodeInfoQueryResult sourceChunk) {
        VMError.guarantee(sourceChunk != null, "Must not be null.");
        this.sourceSp = sourceSp;
        this.sourceChunk = sourceChunk;
        /* Lazily initialize constant values I can only get at run time. */
        if (deoptStubFrameSize == 0L) {
            CodeInfo info = CodeInfoTable.getImageCodeInfo();
            deoptStubFrameSize = CodeInfoAccess.lookupTotalFrameSize(info, CodeInfoAccess.relativeIP(info, DeoptimizationSupport.getDeoptStubPointer()));
        }
    }

    /**
     * Custom method prologues and epilogues required for deoptimization. The instructions must be
     * generated by a backend-specific FrameContext class.
     */
    public enum StubType {
        NoDeoptStub,

        /**
         * Custom prologue: rescue all of the architecture's return registers into the
         * {@link DeoptimizedFrame}.
         */
        EntryStub,

        /**
         * Custom prologue: set the stack pointer to the first method parameter.
         *
         * Custom epilogue:restore all of the architecture's return registers from the
         * {@link DeoptimizedFrame}.
         */
        ExitStub
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface DeoptStub {
        StubType stubType();
    }

    /**
     * Performs the second step of deoptimization: the actual rewriting of a deoptimized method's
     * frame.
     * <p>
     * The pointer to the deopt stub code was installed in the return address slot by
     * {@link #deoptimizeInRange}. Therefore the stub is "called" when a method wants to return to a
     * deoptimized method.
     * <p>
     * When {@link #deoptStub} is "called", the stack looks like this:
     *
     * <pre>
     *    :                                :
     *    |                                |
     *    |                                |   frame of the
     *    +--------------------------------+   deoptimized method
     *    | pointer to DeoptimizedFrame    |
     *    +--------------------------------+--------- no return address between the frames!
     *    |                                |
     *    |                                |   frame of
     *    |                                |   {@link #deoptStub}
     *    :     ...                        :
     * </pre>
     *
     * @param frame This is the handle which was created in {@link #deoptimizeInRange}. It is
     *            fetched from the stack (the slot above the original return address) and passed as
     *            parameter. The instructions for fetching the frame handle must be generated in
     *            this method's prolog by a backend-specific FrameContext class. The prolog also
     *            stores the original return value registers in the {@code frame}.
     */
    @DeoptStub(stubType = StubType.EntryStub)
    @Uninterruptible(reason = "Frame holds Objects in unmanaged storage.")
    public static void deoptStub(DeoptimizedFrame frame) {
        DeoptimizationCounters.counters().deoptCount.inc();
        if (DeoptimizationCounters.Options.ProfileDeoptimization.getValue()) {
            DeoptimizationCounters.startTime.set(System.nanoTime());
        }

        /* Computation of the new stack pointer: we start with the stack pointer of this frame. */
        final Pointer newSp = KnownIntrinsics.readStackPointer()
                        /* Remove the size of this frame... */
                        .add(WordFactory.unsigned(deoptStubFrameSize))
                        /*
                         * ... but compensate that there was no call which entered this method (eg.
                         * no return address pushed).
                         */
                        .subtract(FrameAccess.singleton().stackPointerAdjustmentOnCall())
                        /* Remove the size of the frame that gets deoptimized. */
                        .add(WordFactory.unsigned(frame.getSourceTotalFrameSize()))
                        /* Add the size of the deoptimization target frames. */
                        .subtract(frame.getTargetContent().getSize());

        /* Build the content of the deopt target stack frames. */
        frame.buildContent(newSp);

        /*
         * The frame was pinned to keep it from moving during construction. I can unpin it now that
         * I am uninterruptible. (And I have to unpin it.)
         */
        frame.getPin().close();

        recentDeoptimizationEvents.append(frame.getCompletedMessage());

        /* Do the stack rewriting. Return directly to the deopt target. */
        rewriteStackStub(newSp, frame);
    }

    /**
     * Performs the actual stack rewriting. The custom prologue of this method sets the stack
     * pointer to the new value passed in as the first parameter.
     *
     * The custom epilogue of this method restores the return value registers from the returned
     * frame handle.
     */
    @DeoptStub(stubType = StubType.ExitStub)
    @NeverInline("Custom prologue modifies stack pointer register")
    @Uninterruptible(reason = "Frame holds Objects in unmanaged storage.")
    private static DeoptimizedFrame rewriteStackStub(Pointer newSp, DeoptimizedFrame frame) {

        /*
         * The first word of the new stack content is already the return address into the caller of
         * deoptimizeInRange(). So when this method returns we are inside the caller of
         * deoptimizeInRange().
         */
        Pointer bottomSp = newSp.subtract(FrameAccess.returnAddressSize() + FrameAccess.singleton().savedBasePointerSize());
        frame.getTargetContent().copyToPointer(bottomSp);

        if (DeoptimizationCounters.Options.ProfileDeoptimization.getValue()) {
            DeoptimizationCounters.counters().timeSpentInDeopt.add(System.nanoTime() - DeoptimizationCounters.startTime.get());
        }
        return frame;
    }

    /**
     * Reads the value of a local variable in the given frame. If the local variable is a virtual
     * object, the object (and all other objects reachable from it) are materialized.
     *
     * @param idx the number of the local variable.
     * @param sourceFrame the frame to access, which must be an inlined frame of the physical frame
     *            that this deoptimizer has been created for.
     */
    public JavaConstant readLocalVariable(int idx, FrameInfoQueryResult sourceFrame) {
        assert idx >= 0 && idx < sourceFrame.getNumLocals();
        if (idx < sourceFrame.getValueInfos().length) {
            return readValue(sourceFrame.getValueInfos()[idx], sourceFrame);
        } else {
            return JavaConstant.forIllegal();
        }
    }

    /**
     * Deoptimizes a source frame.
     *
     * @param pc A code address inside the source method (= the method to deoptimize)
     */
    public DeoptimizedFrame deoptSourceFrame(CodePointer pc, boolean ignoreNonDeoptimizable) {
        final DeoptSourceFrameOperation operation = new DeoptSourceFrameOperation(this, pc, ignoreNonDeoptimizable);
        operation.enqueue();
        return operation.getResult();
    }

    /** A VMOperation to encapsulate deoptSourceFrame. */
    private static final class DeoptSourceFrameOperation extends JavaVMOperation {

        private final Deoptimizer receiver;
        private final CodePointer pc;
        private final boolean ignoreNonDeoptimizable;
        private DeoptimizedFrame result;

        DeoptSourceFrameOperation(Deoptimizer receiver, CodePointer pc, boolean ignoreNonDeoptimizable) {
            super("DeoptSourceFrameOperation", SystemEffect.SAFEPOINT);
            this.receiver = receiver;
            this.pc = pc;
            this.ignoreNonDeoptimizable = ignoreNonDeoptimizable;
            this.result = null;
        }

        @Override
        public void operate() {
            result = receiver.deoptSourceFrameOperation(pc, ignoreNonDeoptimizable);
        }

        public DeoptimizedFrame getResult() {
            return result;
        }
    }

    private DeoptimizedFrame deoptSourceFrameOperation(CodePointer pc, boolean ignoreNonDeoptimizable) {
        VMOperation.guaranteeInProgress("deoptSourceFrame");
        assert DeoptimizationSupport.getDeoptStubPointer().rawValue() != 0;

        DeoptimizedFrame existing = checkDeoptimized(sourceSp);
        if (existing != null) {
            /* Already deoptimized, so nothing to do. */
            return existing;
        }

        FrameInfoQueryResult frameInfo = sourceChunk.getFrameInfo();
        if (frameInfo == null) {
            if (ignoreNonDeoptimizable) {
                return null;
            } else {
                throw VMError.shouldNotReachHere("Deoptimization: cannot deoptimize a method that was not marked as deoptimizable from address " + Long.toHexString(pc.rawValue()));
            }
        }

        assert endOfParams == 0;

        /*
         * In case deoptimization is called from an inlined method, we have to construct multiple
         * target frames (one for each inline level) for this source frame. Note that target methods
         * are never inlined.
         */
        FrameInfoQueryResult deoptInfo = frameInfo;
        VirtualFrame previousVirtualFrame = null;
        VirtualFrame topFrame = null;
        while (deoptInfo != null) {
            DeoptimizationCounters.counters().virtualFrameCount.inc();
            if (deoptInfo.getDeoptMethodOffset() == 0) {
                if (ignoreNonDeoptimizable) {
                    return null;
                } else {
                    throw VMError.shouldNotReachHere("Deoptimization: cannot deoptimize a method that has no deoptimization entry point: " + deoptInfo.getSourceReference());
                }
            }

            CodeInfoQueryResult targetInfo = CodeInfoTable.lookupDeoptimizationEntrypoint(deoptInfo.getDeoptMethodOffset(), deoptInfo.getEncodedBci());
            if (targetInfo == null || targetInfo.getFrameInfo() == null) {
                throw VMError.shouldNotReachHere("Deoptimization: no matching target bytecode frame found for bci " +
                                FrameInfoDecoder.readableBci(deoptInfo.getEncodedBci()) + " in method at address " +
                                Long.toHexString(deoptInfo.getDeoptMethodAddress().rawValue()));
            } else if (!targetInfo.getFrameInfo().isDeoptEntry()) {
                throw VMError.shouldNotReachHere("Deoptimization: target frame information not marked as deoptimization entry point for bci " +
                                FrameInfoDecoder.readableBci(deoptInfo.getEncodedBci()) + " in method at address" +
                                Long.toHexString(deoptInfo.getDeoptMethodAddress().rawValue()));
            } else if (targetInfo.getFrameInfo().getDeoptMethod() != null && targetInfo.getFrameInfo().getDeoptMethod().hasCalleeSavedRegisters()) {
                /*
                 * The deoptMethod is not guaranteed to be available, but this is only a last check,
                 * to have a better error than the probable segfault.
                 */
                throw VMError.shouldNotReachHere("Deoptimization: target method has callee saved registers, which are not properly restored by the deoptimization runtime: " +
                                targetInfo.getFrameInfo().getDeoptMethod().format("%H.%n(%r)"));
            }
            VirtualFrame virtualFrame = constructTargetFrame(targetInfo, deoptInfo);
            if (previousVirtualFrame != null) {
                previousVirtualFrame.caller = virtualFrame;
            } else {
                topFrame = virtualFrame;
            }
            previousVirtualFrame = virtualFrame;

            // Go up one inline level
            deoptInfo = deoptInfo.getCaller();
        }

        VMError.guarantee(sourceChunk.getTotalFrameSize() >= FrameAccess.wordSize(), "Insufficient space in frame for pointer to DeoptimizedFrame");

        RelockObjectData[] relockObjectData = relockedObjects == null ? null : relockedObjects.toArray(new RelockObjectData[relockedObjects.size()]);
        /* Allocate a buffer to hold the contents of the new target frame. */
        DeoptimizedFrame deoptimizedFrame = DeoptimizedFrame.factory(targetContentSize, sourceChunk.getEncodedFrameSize(), CodeInfoTable.lookupInstalledCode(pc), topFrame, relockObjectData, pc);

        installDeoptimizedFrame(sourceSp, deoptimizedFrame);

        if (Options.TraceDeoptimization.getValue()) {
            printDeoptimizedFrame(Log.log(), sourceSp, deoptimizedFrame, frameInfo, false);
        }
        logDeoptSourceFrameOperation(sourceSp, deoptimizedFrame, frameInfo);

        return deoptimizedFrame;
    }

    private static void logDeoptSourceFrameOperation(Pointer sp, DeoptimizedFrame deoptimizedFrame, FrameInfoQueryResult frameInfo) {
        StringBuilderLog log = new StringBuilderLog();
        PointerBase deoptimizedFrameAddress = deoptimizedFrame.getPin().addressOfObject();
        log.string("deoptSourceFrameOperation: DeoptimizedFrame at ").hex(deoptimizedFrameAddress).string(": ");
        printDeoptimizedFrame(log, sp, deoptimizedFrame, frameInfo, true);
        recentDeoptimizationEvents.append(log.getResult().toCharArray());
    }

    private static final RingBuffer.Consumer<char[]> deoptEventsConsumer = (context, entry) -> {
        Log l = (Log) context;
        for (char c : entry) {
            if (c == '\n') {
                l.newline();
            } else {
                l.character(c);
            }
        }
    };

    public static void logRecentDeoptimizationEvents(Log log) {
        log.string("== [Recent Deoptimizer Events: ").newline();
        recentDeoptimizationEvents.foreach(log, deoptEventsConsumer);
        log.string("]").newline();
    }

    /**
     * Constructs the frame entries for the deopimization target method.
     *
     * @param targetInfo The bytecode frame (+ some other info) of the target.
     * @param sourceFrame The bytecode frame of the source.
     */
    private VirtualFrame constructTargetFrame(CodeInfoQueryResult targetInfo, FrameInfoQueryResult sourceFrame) {
        FrameInfoQueryResult targetFrame = targetInfo.getFrameInfo();
        int savedBasePointerSize = FrameAccess.singleton().savedBasePointerSize();
        long targetFrameSize = targetInfo.getTotalFrameSize() - FrameAccess.returnAddressSize() - savedBasePointerSize;
        VirtualFrame result = new VirtualFrame(targetFrame);

        if (savedBasePointerSize != 0) {
            result.savedBasePointer = new DeoptimizedFrame.SavedBasePointer(targetContentSize, targetContentSize + targetFrameSize);
            targetContentSize += savedBasePointerSize;
        }

        /* The first word of the new content is the return address into the target method. */
        result.returnAddress = new DeoptimizedFrame.ReturnAddress(targetContentSize, targetInfo.getIP().rawValue());
        targetContentSize += FrameAccess.returnAddressSize();

        /* The source and target bytecode frame must match (as they stem from the same BCI). */
        assert sourceFrame.getNumLocals() == targetFrame.getNumLocals();
        assert sourceFrame.getNumStack() == targetFrame.getNumStack();
        assert sourceFrame.getNumLocks() == targetFrame.getNumLocks();
        assert targetFrame.getVirtualObjects().length == 0;
        assert sourceFrame.getValueInfos().length >= targetFrame.getValueInfos().length;
        int numValues = targetFrame.getValueInfos().length;

        /*
         * Create stack entries for all values of the source frame.
         */
        int newEndOfParams = endOfParams;
        for (int idx = 0; idx < numValues; idx++) {
            ValueInfo targetValue = targetFrame.getValueInfos()[idx];
            if (targetValue.getKind() == JavaKind.Illegal) {
                /*
                 * The target value is optimized out, e.g. at a position after the lifetime of a
                 * local variable. Actually we don't care what's the source value in this case, but
                 * most likely it's also "illegal".
                 */
            } else {
                ValueInfo sourceValue = sourceFrame.getValueInfos()[idx];
                JavaConstant con = readValue(sourceValue, sourceFrame);
                assert con.getJavaKind() != JavaKind.Illegal;

                if (con.getJavaKind().isObject() && SubstrateObjectConstant.isCompressed(con) != targetValue.isCompressedReference()) {
                    // rewrap in constant with the appropriate compression for the target value
                    Object obj = SubstrateObjectConstant.asObject(con);
                    con = SubstrateObjectConstant.forObject(obj, targetValue.isCompressedReference());
                }

                if (sourceValue.isEliminatedMonitor()) {
                    relockObject(con);
                }

                switch (targetValue.getType()) {
                    case StackSlot:
                        /*
                         * The target value is on the stack
                         */
                        DeoptimizationCounters.counters().stackValueCount.inc();
                        int targetOffset = TypeConversion.asS4(targetValue.getData());
                        assert targetOffset != targetFrameSize : "stack slot would overwrite return address";
                        int totalOffset = targetContentSize + targetOffset;
                        assert totalOffset >= endOfParams : "stack location overwrites param area";
                        if (targetOffset < targetFrameSize) {
                            /*
                             * This is the most common case: a regular slot in the stack frame,
                             * which e.g. holds a variable.
                             */
                            assert totalOffset >= targetContentSize;
                            result.values[idx] = DeoptimizedFrame.ConstantEntry.factory(totalOffset, con);

                        } else if (sourceFrame.getCaller() != null) {
                            /*
                             * Handle stack parameters for inlined calls: write the value to the
                             * outgoing parameter area of the caller frame.
                             */
                            assert totalOffset >= targetContentSize;
                            result.values[idx] = DeoptimizedFrame.ConstantEntry.factory(totalOffset, con);

                            int size;
                            if (targetValue.getKind().isObject() && !targetValue.isCompressedReference()) {
                                size = FrameAccess.uncompressedReferenceSize();
                            } else {
                                size = ConfigurationValues.getObjectLayout().sizeInBytes(con.getJavaKind());
                            }
                            int endOffset = totalOffset + size;
                            if (endOffset > newEndOfParams) {
                                newEndOfParams = endOffset;
                            }
                        }
                        break;

                    case DefaultConstant:
                    case Constant:
                        /*
                         * The target value was constant propagated. Check that source and target
                         * performed the same constant propagation
                         */
                        assert verifyConstant(targetFrame, targetValue, con);
                        DeoptimizationCounters.counters().constantValueCount.inc();
                        break;

                    default:
                        /*
                         * There must not be any other target value types because deoptimization
                         * target methods are only optimized in a limited way. Especially there must
                         * not be Register values because registers cannot be alive across method
                         * calls; and there must not be virtual objects because no escape analysis
                         * is performed.
                         */
                        throw VMError.shouldNotReachHere("unknown deopt target value " + targetValue);
                }
            }
        }
        targetContentSize += targetFrameSize;
        endOfParams = newEndOfParams;
        return result;
    }

    private void relockObject(JavaConstant valueConstant) {
        Object lockedObject = KnownIntrinsics.convertUnknownValue(SubstrateObjectConstant.asObject(valueConstant), Object.class);
        Object lockData = MonitorSupport.singleton().prepareRelockObject(lockedObject);

        if (relockedObjects == null) {
            relockedObjects = new ArrayList<>();
        }
        relockedObjects.add(new RelockObjectData(lockedObject, lockData));
    }

    private boolean verifyConstant(FrameInfoQueryResult targetFrame, ValueInfo targetValue, JavaConstant source) {
        boolean equal;
        JavaConstant target = readValue(targetValue, targetFrame);
        if (source.getJavaKind() == JavaKind.Object && target.getJavaKind() == JavaKind.Object) {
            // Differences in compression are irrelevant, compare only object identities
            equal = (SubstrateObjectConstant.asObject(target) == SubstrateObjectConstant.asObject(source));
        } else {
            equal = source.equals(target);
        }
        if (!equal) {
            Log.log().string("source: ").string(source.toString()).string("  target: ").string(target.toString()).newline();
        }
        return equal;
    }

    private JavaConstant readValue(ValueInfo valueInfo, FrameInfoQueryResult sourceFrame) {
        switch (valueInfo.getType()) {
            case Constant:
            case DefaultConstant:
                return valueInfo.getValue();
            case StackSlot:
            case Register:
                return readConstant(sourceSp, WordFactory.signed(valueInfo.getData()), valueInfo.getKind(), valueInfo.isCompressedReference());
            case VirtualObject:
                Object obj = materializeObject(TypeConversion.asS4(valueInfo.getData()), sourceFrame);
                return SubstrateObjectConstant.forObject(obj, valueInfo.isCompressedReference());
            case Illegal:
                return JavaConstant.forIllegal();
            default:
                throw VMError.shouldNotReachHere();
        }
    }

    /**
     * Materializes a virtual object.
     *
     * @param virtualObjectId the id of the virtual object to materialize
     * @return the materialized object
     */
    private Object materializeObject(int virtualObjectId, FrameInfoQueryResult sourceFrame) {
        if (materializedObjects == null) {
            materializedObjects = new Object[sourceFrame.getVirtualObjects().length];
        }
        assert materializedObjects.length == sourceFrame.getVirtualObjects().length;

        Object obj = materializedObjects[virtualObjectId];
        if (obj != null) {
            return obj;
        }
        DeoptimizationCounters.counters().virtualObjectsCount.inc();

        ValueInfo[] encodings = sourceFrame.getVirtualObjects()[virtualObjectId];
        DynamicHub hub = KnownIntrinsics.convertUnknownValue(SubstrateObjectConstant.asObject(readValue(encodings[0], sourceFrame)), DynamicHub.class);
        ObjectLayout objectLayout = ConfigurationValues.getObjectLayout();

        boolean materializingByteArray = false;

        int curIdx;
        UnsignedWord curOffset;
        if (LayoutEncoding.isArray(hub.getLayoutEncoding())) {
            /* For arrays, the second encoded value is the array length. */
            int length = readValue(encodings[1], sourceFrame).asInt();
            obj = Array.newInstance(DynamicHub.toClass(hub.getComponentHub()), length);
            materializingByteArray = obj.getClass() == byte[].class;
            curOffset = LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding());
            curIdx = 2;
        } else {
            try {
                obj = UNSAFE.allocateInstance(DynamicHub.toClass(hub));
            } catch (InstantiationException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
            curOffset = WordFactory.unsigned(objectLayout.getFirstFieldOffset());
            curIdx = 1;
        }

        materializedObjects[virtualObjectId] = obj;
        if (testGCinDeoptimizer) {
            Heap.getHeap().getGC().collect(GCCause.TestGCInDeoptimizer);
        }

        while (curIdx < encodings.length) {
            ValueInfo value = encodings[curIdx];
            JavaKind kind = value.getKind();
            JavaConstant con = readValue(value, sourceFrame);
            if (materializingByteArray && kind.isPrimitive()) {
                /* Escape analysis of byte arrays needs special care. */
                int byteCount = restoreEscapedByteArrayWriteByteCount(encodings, curIdx);
                con = restoreByteArrayWriteValue(con, byteCount);
                /* Realign. curIdx gets reincremented, so we compensate. */
                curIdx = curIdx + byteCount - 1;
            }
            writeValueInMaterializedObj(obj, curOffset, con);
            curOffset = curOffset.add(objectLayout.sizeInBytes(kind));
            curIdx++;
        }

        return obj;
    }

    /**
     * Virtualized byte arrays might look like:
     * <p>
     * [b1, b2, INT, ILLEGAL, ILLEGAL, ILLEGAL, b7, b8]
     * <p>
     * This indicates that an int was written over 4 slots of a byte array, and this write was
     * escape analysed.
     *
     * The written int should write over the 3 illegals, and we can then simply ignore them
     * afterwards.
     */
    private static int restoreEscapedByteArrayWriteByteCount(ValueInfo[] encodings, int curIdx) {
        int index = curIdx + 1;
        while (index < encodings.length &&
                        encodings[index].getKind() == JavaKind.Illegal) {
            index++;
        }
        return index - curIdx;
    }

    /**
     * Given a written value to an escaped byte array and a bytecount, returns a correctly-sized
     * value to write at once in the array.
     */
    private static JavaConstant restoreByteArrayWriteValue(JavaConstant con, int byteCount) {
        switch (byteCount) {
            case 1:
                return JavaConstant.forByte((byte) con.asInt());
            case 2:
                return JavaConstant.forChar((char) con.asInt());
            case 4:
                if (con.getJavaKind().isNumericFloat()) {
                    return JavaConstant.forFloat(con.asFloat());
                } else {
                    return JavaConstant.forInt(con.asInt());
                }
            case 8:
                if (con.getJavaKind().isNumericFloat()) {
                    return JavaConstant.forDouble(con.asDouble());
                } else {
                    return JavaConstant.forLong(con.asLong());
                }
            default:
                throw VMError.shouldNotReachHere();
        }
    }

    /**
     * Writes an instance field or an array element into a materialized object.
     *
     * @param materializedObj The materialized object
     * @param offsetInObj The offset of the instance field or array element
     * @param constant The value to write
     */
    private static void writeValueInMaterializedObj(Object materializedObj, UnsignedWord offsetInObj, JavaConstant constant) {
        assert offsetInObj.notEqual(0) : "materialized value would overwrite hub";
        switch (constant.getJavaKind()) {
            case Boolean:
                BarrieredAccess.writeByte(materializedObj, offsetInObj, constant.asBoolean() ? (byte) 1 : (byte) 0);
                break;
            case Byte:
                BarrieredAccess.writeByte(materializedObj, offsetInObj, (byte) constant.asInt());
                break;
            case Char:
                BarrieredAccess.writeChar(materializedObj, offsetInObj, (char) constant.asInt());
                break;
            case Short:
                BarrieredAccess.writeShort(materializedObj, offsetInObj, (short) constant.asInt());
                break;
            case Int:
                BarrieredAccess.writeInt(materializedObj, offsetInObj, constant.asInt());
                break;
            case Long:
                BarrieredAccess.writeLong(materializedObj, offsetInObj, constant.asLong());
                break;
            case Float:
                BarrieredAccess.writeFloat(materializedObj, offsetInObj, constant.asFloat());
                break;
            case Double:
                BarrieredAccess.writeDouble(materializedObj, offsetInObj, constant.asDouble());
                break;
            case Object: // like all objects, references are always compressed when available
                BarrieredAccess.writeObject(materializedObj, offsetInObj, SubstrateObjectConstant.asObject(constant));
                break;
            default:
                throw VMError.shouldNotReachHere();
        }
    }

    private static JavaConstant readConstant(Pointer addr, SignedWord offset, JavaKind kind, boolean compressed) {
        switch (kind) {
            case Boolean:
                return JavaConstant.forBoolean(addr.readByte(offset) != 0);
            case Byte:
                return JavaConstant.forByte(addr.readByte(offset));
            case Char:
                return JavaConstant.forChar(addr.readChar(offset));
            case Short:
                return JavaConstant.forShort(addr.readShort(offset));
            case Int:
                return JavaConstant.forInt(addr.readInt(offset));
            case Long:
                return JavaConstant.forLong(addr.readLong(offset));
            case Float:
                return JavaConstant.forFloat(addr.readFloat(offset));
            case Double:
                return JavaConstant.forDouble(addr.readDouble(offset));
            case Object:
                Word p = ((Word) addr).add(offset);
                Object obj = ReferenceAccess.singleton().readObjectAt(p, compressed);
                return SubstrateObjectConstant.forObject(obj, compressed);
            default:
                throw VMError.shouldNotReachHere();
        }
    }

    private static void printDeoptimizedFrame(Log log, Pointer sp, DeoptimizedFrame deoptimizedFrame, FrameInfoQueryResult sourceFrameInfo, boolean printOnlyTopFrames) {
        log.string("[Deoptimization of frame").newline();

        SubstrateInstalledCode installedCode = deoptimizedFrame.getSourceInstalledCode();
        if (installedCode != null) {
            log.string("    name: ").string(installedCode.getName()).newline();
        }
        log.string("    sp: ").hex(sp).string("  ip: ").hex(deoptimizedFrame.getSourcePC()).newline();

        if (sourceFrameInfo != null) {
            log.string("    stack trace where execution continues:").newline();
            FrameInfoQueryResult sourceFrame = sourceFrameInfo;
            VirtualFrame targetFrame = deoptimizedFrame.getTopFrame();
            int count = 0;
            while (sourceFrame != null) {
                SharedMethod deoptMethod = sourceFrame.getDeoptMethod();

                log.string("        at ");
                if (deoptMethod != null) {
                    StackTraceElement element = deoptMethod.asStackTraceElement(sourceFrame.getBci());
                    if (element.getFileName() != null && element.getLineNumber() >= 0) {
                        log.string(element.toString());
                    } else {
                        log.string(deoptMethod.format("%H.%n(%p)"));
                    }
                } else {
                    log.string("method at ").hex(sourceFrame.getDeoptMethodAddress());
                }
                log.string(" bci ");
                FrameInfoDecoder.logReadableBci(log, sourceFrame.getEncodedBci());
                log.string("  return address ").hex(targetFrame.returnAddress.returnAddress).newline();

                if (printOnlyTopFrames || Options.TraceDeoptimizationDetails.getValue()) {
                    printVirtualFrame(log, targetFrame);
                }

                count++;
                if (printOnlyTopFrames && count >= 4) {
                    break;
                }

                sourceFrame = sourceFrame.getCaller();
                targetFrame = targetFrame.getCaller();
            }
        }

        log.string("]").newline();
    }

    private static void printVirtualFrame(Log log, VirtualFrame virtualFrame) {
        FrameInfoQueryResult frameInfo = virtualFrame.getFrameInfo();
        String sourceReference = frameInfo.getSourceReference().toString();
        if (sourceReference != null) {
            log.string("            ").string(sourceReference).newline();
        }

        log.string("            bci: ");
        FrameInfoDecoder.logReadableBci(log, frameInfo.getEncodedBci());
        log.string("  deoptMethodOffset: ").signed(frameInfo.getDeoptMethodOffset());
        log.string("  deoptMethod: ").hex(frameInfo.getDeoptMethodAddress());
        log.string("  return address: ").hex(virtualFrame.returnAddress.returnAddress).string("  offset: ").signed(virtualFrame.returnAddress.offset);

        for (int i = 0; i < frameInfo.getValueInfos().length; i++) {
            JavaConstant con = virtualFrame.getConstant(i);
            if (con.getJavaKind() != JavaKind.Illegal) {
                log.newline().string("            slot ").signed(i);
                String name = frameInfo.getLocalVariableName(i);
                if (name != null) {
                    log.string(" ").string(name);
                }
                log.string("  kind: ").string(con.getJavaKind().toString());
                if (con.getJavaKind() == JavaKind.Object) {
                    Object val = SubstrateObjectConstant.asObject(con);
                    if (val == null) {
                        log.string("  null");
                    } else {
                        log.string("  value: ").object(val);
                    }
                } else {
                    log.string("  value: ").string(con.toValueString());
                }
                log.string("  offset: ").signed(virtualFrame.values[i].offset);
            }
        }
        log.newline();
    }

    /** A class to wrap a byte array as if it were a stack frame. */
    static class TargetContent {

        /** The bytes of the frame, initialized to zeroes. */
        private final byte[] frameBuffer;

        /** Some constant sizes, gathered before I become uninterruptible. */
        private static final int sizeofInt = JavaKind.Int.getByteCount();
        private static final int sizeofLong = JavaKind.Long.getByteCount();
        /** All references in deopt frames are compressed when compressed references are enabled. */
        private final int sizeofCompressedReference = ConfigurationValues.getObjectLayout().getReferenceSize();
        private final int sizeofUncompressedReference = FrameAccess.uncompressedReferenceSize();
        /**
         * The offset of the within the array object. I do not have to scale the offsets.
         */
        private static final int arrayBaseOffset = ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Byte);

        private static final ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException = new ArrayIndexOutOfBoundsException("TargetContent.offsetCheck");

        /* Check that an offset is in range. */
        @Uninterruptible(reason = "Called from uninterruptible code.")
        private void offsetCheck(int offset, int size) {
            if (!((0 <= offset) && (offset <= (frameBuffer.length - size)))) {
                throw arrayIndexOutOfBoundsException;
            }
        }

        /** A constructor. */
        protected TargetContent(int targetContentSize, ByteOrder byteOrder) {
            /* Sanity checks. */
            if (byteOrder != ByteOrder.nativeOrder()) {
                VMError.unsupportedFeature("TargetContent with non-native byte order.");
            }
            if (FrameAccess.returnAddressSize() != sizeofLong) {
                VMError.unsupportedFeature("TargetContent with returnAddressSize() != sizeof(long).");
            }
            this.frameBuffer = new byte[targetContentSize];
        }

        /** The size of the frame. */
        @Uninterruptible(reason = "Called from uninterruptible code.")
        protected int getSize() {
            return frameBuffer.length;
        }

        /** Copy the bytes to the memory at the given Pointer. */
        @Uninterruptible(reason = "Called from uninterruptible code.")
        protected void copyToPointer(Pointer p) {
            for (int idx = 0; idx < frameBuffer.length; idx++) {
                p.writeByte(idx, frameBuffer[idx]);
            }
        }

        /** Write an int-sized constant to the frame buffer. */
        @Uninterruptible(reason = "Called from uninterruptible code.")
        protected void writeInt(int offset, int value) {
            offsetCheck(offset, sizeofInt);
            addressOfFrameArray0().writeInt(offset, value);
        }

        /** Write a long-sized constant to the frame buffer. */
        @Uninterruptible(reason = "Called from uninterruptible code.")
        protected void writeLong(int offset, long value) {
            offsetCheck(offset, sizeofLong);
            addressOfFrameArray0().writeLong(offset, value);
        }

        /** Write a word-sized constant to the frame buffer. */
        @Uninterruptible(reason = "Called from uninterruptible code.")
        protected void writeWord(int offset, WordBase value) {
            if (FrameAccess.wordSize() == 8) {
                writeLong(offset, value.rawValue());
            } else if (FrameAccess.wordSize() == 4) {
                writeInt(offset, (int) value.rawValue());
            } else {
                throw VMError.shouldNotReachHere("Unexpected word size: " + FrameAccess.wordSize());
            }
        }

        /** An Object can be written to the frame buffer. */
        @Uninterruptible(reason = "Called from uninterruptible code.")
        protected void writeObject(int offset, Object value, boolean compressed) {
            offsetCheck(offset, compressed ? sizeofCompressedReference : sizeofUncompressedReference);
            Word address = (Word) addressOfFrameArray0();
            address = address.add(offset);
            ReferenceAccess.singleton().writeObjectAt(address, value, compressed);
        }

        /* Return &contentArray[0] as a Pointer. */
        @Uninterruptible(reason = "Called from uninterruptible code.")
        private Pointer addressOfFrameArray0() {
            return Word.objectToUntrackedPointer(frameBuffer).add(arrayBaseOffset);
        }
    }
}
