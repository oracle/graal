/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.stack.JavaFrameAnchors.verifyTopFrameAnchor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteOrder;
import java.util.ArrayList;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoDecoder;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueInfo;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.collections.RingBuffer;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizedFrame.RelockObjectData;
import com.oracle.svm.core.deopt.DeoptimizedFrame.VirtualFrame;
import com.oracle.svm.core.graal.code.StubCallingConvention;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.SuspendSerialGCMaxHeapSize;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.log.StringBuilderLog;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.snippets.ExceptionUnwind;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaFrame;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.util.TypeConversion;
import jdk.graal.compiler.lir.asm.FrameContext;
import jdk.graal.compiler.nodes.UnreachableNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.word.BarrieredAccess;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * Performs deoptimization. The method to deoptimize (= the source method) is either a specialized
 * runtime compiled method or an AOT-compiled test method with the {@link Specialize} annotation.
 * The target method is always an AOT-compiled method.
 * <p>
 * Deoptimization is not limited to a single method. It can be done for all deoptimizable methods on
 * the call stack. A method is deoptimizable if {@link FrameInfoQueryResult deoptimization
 * information} is available.
 * <p>
 * Deoptimization can happen eagerly or lazily. For eager deoptimization, a {@link DeoptimizedFrame}
 * is constructed immediately and pinned and only installed on the stack later, whereas for lazy
 * deoptimization, the {@link DeoptimizedFrame} is not constructed until immediately before it is
 * installed. Eager deoptimization enables immediately freeing the code metadata needed for
 * constructing the {@link DeoptimizedFrame}, while lazy deoptimization requires keeping it, but
 * does not need the {@link DeoptimizedFrame} to stay allocated and pinned for potentially lengthy
 * periods of time, which is typically preferable especially when many frames might be deoptimized
 * at once. The stack slot at SP[0] of a deoptimizable method is reserved for deoptimization, and
 * can be used freely by lazy and eager deoptimization.
 * <p>
 * With both eager and lazy deoptimization, returns to a deoptimized method are intercepted to enter
 * the deopt stub. Alternatively, {@link ExceptionUnwind} can "far-return" directly to the deopt
 * stub for dispatching an exception while unwinding the stack, in which case the exception object
 * will be passed as if it was the return value from the callee.
 * <p>
 * Eager deoptimization is done in two steps:
 * <ol>
 * <li>A call to {@link #deoptimizeInRange} walks the stack, and for each method to deoptimize, it
 * builds a {@link DeoptimizedFrame}. This object contains all constants and materialized objects
 * which are needed to build the deoptimization target frames. A reference to it is stored at SP[0]
 * (directly above the callee's return address). The callee's return address (to the deoptimized
 * method) is replaced by {@link #eagerDeoptStub}. (Find a diagram of the stack further below.)
 * <p>
 * From now on, the frame of the deoptimized method is no longer valid and the GC will ignore it.
 * Instead, the GC will also visit the reference to the {@link DeoptimizedFrame}. In other words:
 * the frame of the deoptimized method is "replaced" by a single entry, a reference to
 * {@link DeoptimizedFrame}, which contains all objects which are needed by the deoptimization
 * targets.
 * <p>
 * There is one exception: outgoing primitive parameters of a deoptimized method may still be
 * accessed by the callee, even after the first step of eager deoptimization is done. Note that this
 * does not apply to outgoing object parameters as those are always copied to registers at the
 * beginning of the called method to avoid problems with the GC.</li>
 * <li>Now when a callee would return to a deoptimized method, the eager deopt stub will be entered
 * instead. It reads the {@link DeoptimizedFrame} reference and replaces the deoptimized method's
 * frame with the frame(s) of the deopt target method(s). Note that the eager deopt stub is
 * completely allocation free.</li>
 * </ol>
 *
 * <p>
 * Lazy deoptimization is also done in two steps:
 * <ol>
 * <li>During the first step, we only patch the callee's return address to point to a lazy deopt
 * stub. Depending on whether the method being deoptimized returns an object or a primitive value,
 * that stub is either {@link #lazyDeoptStubObjectReturn} or {@link #lazyDeoptStubPrimitiveReturn}.
 * The reserved stack slot that is used to store the {@link DeoptimizedFrame} in eager
 * deoptimization is instead used to store the original return address, which points somewhere into
 * the deopt source method.
 * <p>
 * Stack walks and GC will now visit this frame that is pending lazy deoptimization as if it was a
 * normal stack frame, with the only difference being that the original return address is taken from
 * the reserved stack slot.</li>
 * <li>When a callee returns to the method pending lazy deoptimization, it instead enters one of the
 * lazy deopt stubs, which in turn invokes {@link #lazyDeoptStubCore}. This method performs all the
 * necessary operations to construct a {@link DeoptimizedFrame} just like the first step of eager
 * deoptimization. The process of constructing the frame is interruptible and involves allocation,
 * therefore if {@code gpReturnValue} contains an object reference, it must be explicitly turned
 * into an object reference so that the GC is aware of said reference.
 * <p>
 * The frame is then copied onto the stack in {@link #rewriteStackStub}.</li>
 * </ol>
 * The stack at the time of entering a deopt stub looks as follows. Because while returning, the
 * stack pointer has already moved above potential callee-saved arguments and the return address,
 * they must be considered stale because they could have been overwritten by an interrupt or signal
 * handler (despite a safe zone guaranteed by the ABI which could not be large enough).
 *
 * <pre>
 *    top of stack (lowest address)
 *
 *    :                  ...                  :
 *    +---------------------------------------+-------------
 *    | ...                                   |
 *    |---------------------------------------| frame of the callee of deopt'ed method,
 *    | potential callee-saved arguments      |  stale on deopt stub entry
 *    |---------------------------------------|
 *    | return address (to deopt stub)        |
 *    +---------------------------------------+-------------  <== stack pointer on deopt stub entry
 *    | deopt reserved slot                   |
 *    |  eager deopt: DeoptimizedFrame object |
 *    |  lazy deopt:  original return address |
 *    |---------------------------------------| frame of the deopt'ed method
 *    | outgoing stack parameters             |
 *    |---------------------------------------|
 *    | ...                                   |
 *    +---------------------------------------+-------------
 *    :                  ...                  :
 * </pre>
 */
public final class Deoptimizer {
    private static final int MAX_DEOPTIMIZATION_EVENT_PRINT_LENGTH = 1000;
    private static final RingBuffer<char[]> recentDeoptimizationEvents = new RingBuffer<>(SubstrateOptions.DiagnosticBufferSize.getValue());

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
        assert decodeDeoptAction(result) == action : result;
        assert decodeDeoptReason(result) == reason : result;
        assert decodeDebugId(result) == speculationId : result;
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

        /**
         * Enables lazy deoptimization. If not enabled, then all calls to the lazy deoptimization
         * methods are handled eagerly.
         *
         * Enabling this option adds 1 byte to the CodeInfo of each infopoint for deopt target
         * methods, which is used to keep track of whether each infopoint is a call where an object
         * is returned. This determines whether {@link #lazyDeoptStubCore} treats
         * {@code gpReturnValue} as an object reference.
         */
        @Option(help = "Enables delayed deoptimization of runtime-compiled code. This slightly enlarges code metadata.")//
        public static final HostedOptionKey<Boolean> LazyDeoptimization = new HostedOptionKey<>(true);
    }

    /**
     * If true, the GC is called during deoptimization. The deoptimizer allocates some objects,
     * which requires GC to work. This is only set to true for testing.
     */
    public static boolean testGCinDeoptimizer = false;

    /**
     * If true, then we call eager deoptimization from within {@link #lazyDeoptStubCore}, which
     * triggers a fatal error. This is only set to true for testing.
     */
    public static boolean testEagerDeoptInLazyDeoptFatalError = false;

    /**
     * If true, then we call lazy deoptimization from within {@link #lazyDeoptStubCore}, which
     * should recognize that the frame is already pending deoptimization and have no effect. This is
     * only set to true for testing.
     */
    public static boolean testLazyDeoptInLazyDeopt = false;

    public static void maybeTestGC() {
        if (testGCinDeoptimizer) {
            Heap.getHeap().getGC().collect(GCCause.TestGCInDeoptimizer);
        }
    }

    private static void maybeTestDeoptDuringLazyDeopt(Deoptimizer deoptimizer, CodePointer pc) {
        VMError.guarantee(!(testEagerDeoptInLazyDeoptFatalError && testLazyDeoptInLazyDeopt), "Cannot test both eager deopt and lazy deopt");
        if (testEagerDeoptInLazyDeoptFatalError) {
            deoptimizer.deoptSourceFrameEagerly(pc, false);
        } else if (testLazyDeoptInLazyDeopt) {
            deoptimizeFrame(deoptimizer.deoptState.sourceSp, false, null);
        }
    }

    /** Returns true if the frame has been eagerly or lazily deoptimized. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean checkIsDeoptimized(JavaFrame frame) {
        return checkLazyDeoptimized(frame) || checkEagerDeoptimized(frame) != null;
    }

    /**
     * Returns the DeoptimizedFrame object installed during eager deoptimization, or null if the
     * frame was not eagerly deoptimized.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static DeoptimizedFrame checkEagerDeoptimized(JavaFrame frame) {
        if (DeoptimizationSupport.enabled()) {
            return checkEagerDeoptimized0(frame.getSP(), frame.getIP());
        }
        return null;
    }

    /**
     * Returns the DeoptimizedFrame object installed during eager deoptimization, or null if the
     * frame was not eagerly deoptimized. This method must not be called if the return address is
     * stored in a native frame, since we do not control the layout of native frames.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static DeoptimizedFrame checkEagerDeoptimized(IsolateThread thread, Pointer sp) {
        if (DeoptimizationSupport.enabled()) {
            CodePointer ip = FrameAccess.singleton().readReturnAddress(thread, sp);
            return checkEagerDeoptimized0(sp, ip);
        }
        return null;
    }

    /**
     * Checks if a physical stack frame (identified by the stack pointer) was deoptimized, and
     * returns the {@link DeoptimizedFrame} in that case.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static DeoptimizedFrame checkEagerDeoptimized0(Pointer sp, CodePointer ip) {
        /* A frame was eagerly deoptimized if the return address was patched to the deoptStub. */
        if (ip.equal(DeoptimizationSupport.getEagerDeoptStubPointer())) {
            /* The DeoptimizedFrame instance is stored above the return address, at sp[0]. */
            DeoptimizedFrame result = (DeoptimizedFrame) ReferenceAccess.singleton().readObjectAt(sp, true);
            if (result == null) {
                throw checkDeoptimizedError(sp);
            }
            return result;
        }
        return null;
    }

    /**
     * Checks whether a {@link JavaFrame} is lazily deoptimized.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean checkLazyDeoptimized(JavaFrame frame) {
        if (DeoptimizationSupport.enabled() && Options.LazyDeoptimization.getValue()) {
            return frame.getIsPendingLazyDeopt();
        }
        return false;
    }

    /**
     * Checks whether a frame identified by the stack pointer is lazily deoptimized. This must not
     * be called if the return address is stored in a native frame, since we do not control the
     * layout of native frames.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean checkLazyDeoptimized(IsolateThread thread, Pointer sp) {
        if (DeoptimizationSupport.enabled() && Options.LazyDeoptimization.getValue()) {
            CodePointer ip = FrameAccess.singleton().readReturnAddress(thread, sp);
            return isLazyDeoptStub(ip);
        }
        return false;
    }

    /**
     * Checks whether a return address is equal to one of the lazy deopt stubs.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean checkLazyDeoptimized(CodePointer ip) {
        if (DeoptimizationSupport.enabled() && Options.LazyDeoptimization.getValue()) {
            return isLazyDeoptStub(ip);
        }
        return false;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isLazyDeoptStub(CodePointer ip) {
        assert Options.LazyDeoptimization.getValue();
        return ip.equal(DeoptimizationSupport.getLazyDeoptStubPrimitiveReturnPointer()) || ip.equal(DeoptimizationSupport.getLazyDeoptStubObjectReturnPointer());
    }

    @Uninterruptible(reason = "Switch to interruptible code and report a fatal error.", calleeMustBe = false)
    private static RuntimeException checkDeoptimizedError(Pointer sp) {
        throw checkDeoptimizedError0(sp);
    }

    @NeverInline("Throws error and exits")
    private static RuntimeException checkDeoptimizedError0(Pointer sp) {
        Log.log().string("Unable to retrieve Deoptimized frame. sp: ").hex(sp.rawValue()).newline();
        throw VMError.shouldNotReachHere("Unable to retrieve Deoptimized frame");
    }

    @Uninterruptible(reason = "Prevent stack walks from seeing an inconsistent stack.")
    private void installDeoptimizedFrame(DeoptimizedFrame deoptimizedFrame) {
        /*
         * Replace the return address to the deoptimized method with the entry point of
         * eagerDeoptStub.
         */
        FrameAccess.singleton().writeReturnAddress(deoptState.targetThread, deoptState.sourceSp, DeoptimizationSupport.getEagerDeoptStubPointer());

        /*
         * GR-54888: leaveInterpreterStub uses the deoptSlot, thus an existing value should be saved
         * and restored for the deoptee frame
         */
        /*
         * Store a pointer to the deoptimizedFrame in the stack slot above the return address. From
         * this point on, the GC will ignore the original source frame content. Instead, it will
         * visit the DeoptimizedFrame object.
         */
        ReferenceAccess.singleton().writeObjectAt(deoptState.sourceSp, deoptimizedFrame, true);
    }

    /**
     * Deoptimizes all method(s) in all call stacks (= the calling methods). Only used for testing.
     */
    @NeverInline("deoptimize must have a separate stack frame")
    public static void deoptimizeAll() {
        VMOperation.guaranteeNotInProgress("With a VM Operation in progress, we cannot determine the thread requesting deoptimization.");
        DeoptimizeAllOperation vmOp = new DeoptimizeAllOperation(CurrentIsolate.getCurrentThread());
        vmOp.enqueue();
    }

    private static class DeoptimizeAllOperation extends JavaVMOperation {
        private final IsolateThread requestingThread;

        DeoptimizeAllOperation(IsolateThread requestingThread) {
            super(VMOperationInfos.get(DeoptimizeAllOperation.class, "Deoptimize all", SystemEffect.SAFEPOINT));
            this.requestingThread = requestingThread;
        }

        @Override
        protected void operate() {
            deoptimizeInRange(Word.zero(), Word.zero(), true, requestingThread);
        }
    }

    /**
     * Deoptimize a specific method.
     *
     * @param fromIp The lower address (including) of the method's code.
     * @param toIp The upper address (excluding) of the method's code.
     */
    @NeverInline("deoptimize must have a separate stack frame")
    public static void deoptimizeInRange(CodePointer fromIp, CodePointer toIp, boolean deoptAll, IsolateThread requestingThread) {
        VMOperation.guaranteeInProgressAtSafepoint("Deoptimization requires a safepoint.");
        deoptimizeInRangeOperation(fromIp, toIp, deoptAll, requestingThread);
    }

    /** Deoptimize a specific method on all thread stacks. */
    @NeverInline("Starting a stack walk in the caller frame. " +
                    "Note that we could start the stack frame also further down the stack, because VM operation frames never need deoptimization. " +
                    "But we don't store stack frame information for the first frame we would need to process.")
    private static void deoptimizeInRangeOperation(CodePointer fromIp, CodePointer toIp, boolean deoptAll, IsolateThread requestingThread) {
        VMOperation.guaranteeInProgressAtSafepoint("Deoptimizer.deoptimizeInRangeOperation, but not in VMOperation.");
        /* Handle my own thread specially, because I do not have a JavaFrameAnchor. */
        Pointer sp = KnownIntrinsics.readCallerStackPointer();

        StackFrameVisitor currentThreadDeoptVisitor = getStackFrameVisitor((Pointer) fromIp, (Pointer) toIp, deoptAll, CurrentIsolate.getCurrentThread(), requestingThread);
        JavaStackWalker.walkCurrentThread(sp, currentThreadDeoptVisitor);

        /* Deoptimize this method on all the other stacks. */
        for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
            if (vmThread == CurrentIsolate.getCurrentThread()) {
                continue;
            }
            StackFrameVisitor deoptVisitor = getStackFrameVisitor((Pointer) fromIp, (Pointer) toIp, deoptAll, vmThread, requestingThread);
            JavaStackWalker.walkThread(vmThread, deoptVisitor);
        }
        maybeTestGC();
    }

    private static StackFrameVisitor getStackFrameVisitor(Pointer fromIp, Pointer toIp, boolean deoptAll, IsolateThread targetThread, IsolateThread requestingThread) {
        return new StackFrameVisitor() {
            @Override
            public boolean visitRegularFrame(Pointer frameSp, CodePointer frameIp, CodeInfo codeInfo) {
                Pointer ip = (Pointer) frameIp;
                if ((ip.aboveOrEqual(fromIp) && ip.belowThan(toIp)) || deoptAll) {
                    CodeInfoQueryResult queryResult = CodeInfoTable.lookupCodeInfoQueryResult(codeInfo, frameIp);
                    Deoptimizer deoptimizer = new Deoptimizer(frameSp, queryResult, targetThread, requestingThread);
                    deoptimizer.deoptSourceFrameLazily(frameIp, deoptAll);
                }
                return true;
            }

            @Override
            protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptimizedFrame) {
                /* Nothing to do. */
                return true;
            }
        };
    }

    /**
     * Deoptimizes the given frame (lazily or eagerly, depending on the configuration).
     *
     * @param ignoreNonDeoptimizable if set to true, a frame that cannot be deoptimized is ignored
     *            instead of raising an error (use for deoptimization testing only).
     */
    @NeverInline("Inlining of this method would require that we have deopt targets for callees of this method (SVM internals).")
    public static void deoptimizeFrame(Pointer sp, boolean ignoreNonDeoptimizable, SpeculationReason speculation) {
        boolean deoptEagerly = !Options.LazyDeoptimization.getValue();
        deoptimizeFrame0(sp, ignoreNonDeoptimizable, speculation, deoptEagerly);
    }

    /**
     * Deoptimizes the given frame eagerly.
     *
     * @param ignoreNonDeoptimizable if set to true, a frame that cannot be deoptimized is ignored
     *            instead of raising an error (use for deoptimization testing only).
     */
    @NeverInline("Inlining of this method would require that we have deopt targets for callees of this method (SVM internals).")
    public static void deoptimizeFrameEagerly(Pointer sp, boolean ignoreNonDeoptimizable, SpeculationReason speculation) {
        deoptimizeFrame0(sp, ignoreNonDeoptimizable, speculation, true);
    }

    private static void deoptimizeFrame0(Pointer sp, boolean ignoreNonDeoptimizable, SpeculationReason speculation, boolean deoptEagerly) {
        /*
         * Note that the thread needs to be read outside of the VMOperation, since the operation can
         * run in any different thread.
         */
        IsolateThread targetThread = CurrentIsolate.getCurrentThread();

        if (checkDeoptimizedThenRegisterSpeculationFailure(deoptEagerly, targetThread, sp, speculation)) {
            /* Already deoptimized, so nothing to do. */
            return;
        }

        VMOperation.guaranteeNotInProgress("With a VM Operation in progress, we cannot determine the thread requesting deoptimization.");
        DeoptimizeFrameOperation vmOp = new DeoptimizeFrameOperation(sp, ignoreNonDeoptimizable, speculation, targetThread, deoptEagerly, CurrentIsolate.getCurrentThread());
        vmOp.enqueue();
    }

    private static boolean checkDeoptimizedThenRegisterSpeculationFailure(boolean deoptEagerly, IsolateThread targetThread, Pointer sp, SpeculationReason speculation) {
        DeoptimizedFrame deoptFrame = checkEagerDeoptimized(targetThread, sp);
        if (deoptFrame != null) {
            /*
             * Register the failed speculation even when the frame has already been deoptimized
             * because it might have originally been deoptimized for a different reason.
             */
            registerSpeculationFailure(deoptFrame.getSourceInstalledCode(), speculation);
            return true;
        }
        if (!deoptEagerly && checkLazyDeoptimized(targetThread, sp)) {
            /*
             * This cannot race with eager deoptimization because either we are in a VM operation,
             * or we are in the thread to which the frame belongs, and with lazy deoptimization
             * enabled, only that thread may deoptimize it eagerly.
             */
            CodePointer originalReturnAddress = readLazyDeoptOriginalReturnAddress(targetThread, sp);
            SubstrateInstalledCode installedCode = CodeInfoTable.lookupInstalledCode(originalReturnAddress);
            registerSpeculationFailure(installedCode, speculation);
            return true;
        }
        return false;
    }

    private static class DeoptimizeFrameOperation extends JavaVMOperation {
        private final Pointer sourceSp;
        private final boolean ignoreNonDeoptimizable;
        private final SpeculationReason speculation;
        private final IsolateThread targetThread;
        private final IsolateThread requestingThread;
        private final boolean deoptEagerly;

        DeoptimizeFrameOperation(Pointer sourceSp, boolean ignoreNonDeoptimizable, SpeculationReason speculation, IsolateThread targetThread, boolean deoptEagerly, IsolateThread requestingThread) {
            super(VMOperationInfos.get(DeoptimizeFrameOperation.class, "Deoptimize frame", SystemEffect.SAFEPOINT));
            this.sourceSp = sourceSp;
            this.ignoreNonDeoptimizable = ignoreNonDeoptimizable;
            this.speculation = speculation;
            this.targetThread = targetThread;
            this.deoptEagerly = deoptEagerly;
            this.requestingThread = requestingThread;

            if (Options.LazyDeoptimization.getValue() && deoptEagerly) {
                /*
                 * If lazy deoptimization is enabled, eager deoptimization is only used for stack
                 * introspection. We enforce that eager deoptimization cannot be applied to other
                 * threads, because we do not want an eager deoptimization operation to interrupt
                 * and interfere with a thread that is undergoing lazy deoptimization.
                 */
                VMError.guarantee(targetThread == requestingThread, "With lazy deoptimization enabled, a thread can request eager deoptimization only on itself.");
            }
        }

        @Override
        protected void operate() {
            /* Recheck if deoptimization already happened just before entering this VM operation. */
            if (checkDeoptimizedThenRegisterSpeculationFailure(deoptEagerly, targetThread, sourceSp, speculation)) {
                return;
            }
            if (checkLazyDeoptimized(targetThread, sourceSp)) {
                assert deoptEagerly;
                uninstallLazyDeoptStubReturnAddress(sourceSp, targetThread);
            }
            CodePointer ip = FrameAccess.singleton().readReturnAddress(targetThread, sourceSp);
            deoptimizeFrame(targetThread, sourceSp, ip, ignoreNonDeoptimizable, speculation, deoptEagerly, requestingThread);
        }
    }

    @Uninterruptible(reason = "Prevent the GC from freeing the CodeInfo object.")
    private static void deoptimizeFrame(IsolateThread targetThread, Pointer sp, CodePointer ip, boolean ignoreNonDeoptimizable, SpeculationReason speculation, boolean deoptEagerly,
                    IsolateThread requestingThread) {
        UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(ip);
        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try {
            CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
            deoptimize(targetThread, sp, ip, ignoreNonDeoptimizable, speculation, info, deoptEagerly, requestingThread);
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
    }

    @Uninterruptible(reason = "Pass the now protected CodeInfo object to interruptible code.", calleeMustBe = false)
    private static void deoptimize(IsolateThread targetThread, Pointer sp, CodePointer ip, boolean ignoreNonDeoptimizable, SpeculationReason speculation, CodeInfo info, boolean deoptEagerly,
                    IsolateThread requestingThread) {
        deoptimize0(targetThread, sp, ip, ignoreNonDeoptimizable, speculation, info, deoptEagerly, requestingThread);
    }

    private static void deoptimize0(IsolateThread targetThread, Pointer sp, CodePointer ip, boolean ignoreNonDeoptimizable, SpeculationReason speculation, CodeInfo info, boolean deoptEagerly,
                    IsolateThread requestingThread) {
        CodeInfoQueryResult queryResult = CodeInfoTable.lookupCodeInfoQueryResult(info, ip);
        Deoptimizer deoptimizer = new Deoptimizer(sp, queryResult, targetThread, requestingThread);
        if (deoptEagerly) {
            DeoptimizedFrame sourceFrame = deoptimizer.deoptSourceFrameEagerly(ip, ignoreNonDeoptimizable);
            if (sourceFrame != null) {
                registerSpeculationFailure(sourceFrame.getSourceInstalledCode(), speculation);
            }
        } else {
            deoptimizer.deoptSourceFrameLazily(ip, ignoreNonDeoptimizable);
            SubstrateInstalledCode installedCode = CodeInfoTable.lookupInstalledCode(ip);
            registerSpeculationFailure(installedCode, speculation);
        }
    }

    /**
     * Invalidates the {@link InstalledCode} of the method of the given frame. The method must be a
     * runtime compiled method, since there is no {@link InstalledCode} for AOT-compiled methods.
     */
    public static void invalidateMethodOfFrame(IsolateThread thread, Pointer sp, SpeculationReason speculation) {
        VMError.guarantee(thread == CurrentIsolate.getCurrentThread());

        CodePointer ip = FrameAccess.singleton().readReturnAddress(thread, sp);
        if (checkLazyDeoptimized(ip)) {
            /*
             * This cannot race with eager deoptimization because with lazy deoptimization enabled,
             * only the thread to which the frame belongs to may deoptimize it eagerly, which is the
             * current thread.
             */
            ip = readLazyDeoptOriginalReturnAddress(thread, sp);
        }
        SubstrateInstalledCode installedCode = CodeInfoTable.lookupInstalledCode(ip);
        /*
         * We look up the installedCode before checking if the frame is deoptimized to avoid race
         * conditions. We are not in a VMOperation here. When an eager deoptimization happens, e.g.,
         * at a safepoint taken at the method exit of checkDeoptimized, then the result value
         * deoptimizedFrame will be null but the return address is already patched to the deoptStub.
         * We would not be able to find the installedCode in such a case. Invalidating the same
         * installedCode multiple times in case of a race is not a problem because the actual
         * invalidation is in a VMOperation.
         */
        DeoptimizedFrame deoptimizedFrame = checkEagerDeoptimized(thread, sp);
        if (deoptimizedFrame != null) {
            installedCode = deoptimizedFrame.getSourceInstalledCode();
            if (installedCode == null) {
                /* When the method was invalidated before, all the metadata can be gone by now. */
                return;
            }
        } else if (installedCode == null) {
            throw VMError.shouldNotReachHere("Only runtime compiled methods can be invalidated. sp = " + Long.toHexString(sp.rawValue()) + ", returnAddress = " + Long.toHexString(ip.rawValue()));
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
     * Objects that need to be re-locked.
     */
    private ArrayList<RelockObjectData> relockedObjects;

    /**
     * The size of the new stack content after all stack entries are built).
     */
    protected int targetContentSize;

    private final DeoptState deoptState;
    private final IsolateThread requestingThread;

    public Deoptimizer(Pointer sourceSp, CodeInfoQueryResult sourceChunk, IsolateThread targetThread, IsolateThread requestingThread) {
        VMError.guarantee(sourceChunk != null, "Must not be null.");
        this.sourceChunk = sourceChunk;
        this.deoptState = new DeoptState(sourceSp, targetThread);
        this.requestingThread = requestingThread;
    }

    public DeoptState getDeoptState() {
        return deoptState;
    }

    /**
     * Custom method prologues and epilogues required for deoptimization. The instructions must be
     * generated by a backend-specific FrameContext class.
     */
    public enum StubType {
        NoDeoptStub,

        /**
         * Custom prologue: save all of the architecture's return registers onto the stack.
         */
        EntryStub,

        /**
         * Custom prologue: set the stack pointer to the first method parameter.
         * <p>
         * Custom epilogue: restore all of the architecture's return registers from the stack.
         */
        ExitStub,

        /**
         * Custom prologue: save all ABI registers onto the stack.
         * <p>
         * Custom epilogue: fill in ABI return registers from stack location.
         */
        InterpreterEnterStub,

        /**
         * Custom prologue: store arguments to stack and allocate variable sized frame.
         * <p>
         * Custom epilogue: prepare stack layout and ABI registers for outgoing call.
         */
        InterpreterLeaveStub;

        public boolean isInterpreterStub() {
            return equals(InterpreterEnterStub) || equals(InterpreterLeaveStub);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface DeoptStub {
        StubType stubType();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isNonNullValue(UnsignedWord pointer) {
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            /*
             * KnownIntrinsics.heapBase() can represent null objects, but we cannot convert the heap
             * base value to an object.
             */
            return pointer != Word.nullPointer() && pointer != KnownIntrinsics.heapBase();
        }
        /*
         * With spawn isolates option disabled (which is a legacy mode), the heap base may represent
         * a valid heap object.
         */
        return pointer != Word.nullPointer();
    }

    /**
     * Entry point for the lazy deopt stub. The parameters are computed with instructions generated
     * in this method's prologue by a backend-specific {@link FrameContext}. See the class-level
     * documentation for more context and a diagram of the stack.
     * <p>
     * This method uses {@link StubCallingConvention} for when the callee (the return of which is
     * intercepted) also uses stub calling convention. In that case, the callee (rather than the
     * caller) has initially saved the values of registers, and these values are required for
     * constructing the deopt frame. The values have already been restored to their registers before
     * the return to this stub, and using stub calling convention here saves them again to the same
     * expected locations.
     * <p>
     * Usually, the saved register values would still be present below the stack pointer, but could
     * also have been overwritten by an interrupt or signal handler. The ABI might guarantee a safe
     * zone below the stack pointer to prevent this, but such zones are typically also not large
     * enough to fit all saved registers, especially with vector registers.
     * <p>
     * If the callee does not use stub calling convention, this method unnecessarily saves
     * registers, but it avoids having additional stubs and selecting between them and should not
     * have significant impact.
     * <p>
     *
     * @param originalStackPointer the original stack pointer of the deoptimized method (points to
     *            the {@link DeoptimizedFrame} object).
     * @param gpReturnValue This is the value which was stored in the general purpose return
     *            register when the deopt stub was reached. It must be restored to the register
     *            before completion of the stub.
     * @param fpReturnValue This is the value which was stored in the floating point return register
     *            when the deopt stub was reached. It must be restored to the register before
     *            completion of the stub.
     */
    @StubCallingConvention
    @DeoptStub(stubType = StubType.EntryStub)
    @Uninterruptible(reason = "Rewriting stack; gpReturnValue holds object reference.")
    public static UnsignedWord lazyDeoptStubObjectReturn(Pointer originalStackPointer, UnsignedWord gpReturnValue, UnsignedWord fpReturnValue) {
        /*
         * Establish the correct return address for this stub to make the stack walkable. The return
         * address could have been overwritten by an interrupt or signal handler if the ABI doesn't
         * guarantee a safe zone below the stack pointer.
         */
        CodePointer returnAddress = DeoptimizationSupport.getLazyDeoptStubObjectReturnPointer();
        FrameAccess.singleton().writeReturnAddress(CurrentIsolate.getCurrentThread(), originalStackPointer, returnAddress);

        try {
            assert PointerUtils.isAMultiple(KnownIntrinsics.readStackPointer(), Word.unsigned(ConfigurationValues.getTarget().stackAlignment));
            assert Options.LazyDeoptimization.getValue();
            assert VMThreads.StatusSupport.isStatusJava() : "Deopt stub execution must not be visible to other threads.";

            boolean hasException = ExceptionUnwind.getLazyDeoptStubShouldReturnToExceptionHandler();
            if (hasException) {
                ExceptionUnwind.setLazyDeoptStubShouldReturnToExceptionHandler(false);
            }
            Object gpReturnValueObject = null;
            if (isNonNullValue(gpReturnValue)) {
                gpReturnValueObject = ((Pointer) gpReturnValue).toObject();
            }

            lazyDeoptStubCore(originalStackPointer, gpReturnValue, fpReturnValue, hasException, gpReturnValueObject);
            throw UnreachableNode.unreachable();

        } catch (Throwable t) {
            throw VMError.shouldNotReachHere("Exception in lazy deopt stub", t);
        }
    }

    /** See {@link #lazyDeoptStubObjectReturn}. */
    @StubCallingConvention
    @DeoptStub(stubType = StubType.EntryStub)
    @Uninterruptible(reason = "Rewriting stack.")
    public static UnsignedWord lazyDeoptStubPrimitiveReturn(Pointer originalStackPointer, UnsignedWord gpReturnValue, UnsignedWord fpReturnValue) {
        /* Establish the correct return address for this stub to make the stack walkable. */
        CodePointer returnAddress = DeoptimizationSupport.getLazyDeoptStubPrimitiveReturnPointer();
        FrameAccess.singleton().writeReturnAddress(CurrentIsolate.getCurrentThread(), originalStackPointer, returnAddress);

        /*
         * Note: when we dispatch an exception, we enter lazyDeoptStubObjectReturn instead, since
         * that involves returning an exception object.
         */
        try {
            assert PointerUtils.isAMultiple(KnownIntrinsics.readStackPointer(), Word.unsigned(ConfigurationValues.getTarget().stackAlignment));
            assert Options.LazyDeoptimization.getValue();
            assert VMThreads.StatusSupport.isStatusJava() : "Deopt stub execution must not be visible to other threads.";
            assert !ExceptionUnwind.getLazyDeoptStubShouldReturnToExceptionHandler();

            lazyDeoptStubCore(originalStackPointer, gpReturnValue, fpReturnValue, false, null);
            throw UnreachableNode.unreachable();

        } catch (Throwable t) {
            throw VMError.shouldNotReachHere("Exception in lazy deopt stub", t);
        }
    }

    /**
     * The handler for lazy deoptimization.
     * 
     * Despite being marked Uninterruptible, this contains interruptible sections when we look up
     * the code info, and construct the {@link DeoptimizedFrame}.
     */
    @Uninterruptible(reason = "Rewriting stack.")
    private static UnsignedWord lazyDeoptStubCore(Pointer originalStackPointer, UnsignedWord gpReturnValue, UnsignedWord fpReturnValue, boolean hasException, Object gpReturnValueObject) {
        CodePointer deoptStubAddress = FrameAccess.singleton().readReturnAddress(CurrentIsolate.getCurrentThread(), originalStackPointer);
        assert isLazyDeoptStub(deoptStubAddress);

        /* The original return address is at offset 0 from the stack pointer */
        CodePointer originalReturnAddress = originalStackPointer.readWord(0);
        VMError.guarantee(originalReturnAddress.isNonNull());

        DeoptimizedFrame deoptFrame;
        try {
            deoptFrame = constructLazilyDeoptimizedFrameInterruptibly(originalStackPointer, originalReturnAddress, hasException);
        } catch (OutOfMemoryError ex) {
            /*
             * If a OutOfMemoryError occurs during lazy deoptimization, we cannot let the frame
             * being deoptimized handle the exception, because it might have been invalidated due to
             * incorrect assumptions. Note that since unwindExceptionSkippingCaller does not return,
             * this try...catch must not have a finally block, as it will not be executed.
             */
            ExceptionUnwind.unwindExceptionSkippingCaller(ex, originalStackPointer);
            throw UnreachableNode.unreachable();
        }

        DeoptimizationCounters.counters().deoptCount.inc();
        VMError.guarantee(deoptFrame != null, "was not able to lazily construct a deoptimized frame");

        Pointer newSp = computeNewStackPointer(originalStackPointer, deoptFrame);

        /* Build the content of the deopt target stack frames. */
        deoptFrame.buildContent(newSp);

        /*
         * We fail fatally if eager deoptimization is invoked when the lazy deopt stub is executing,
         * because eager deoptimization should only be invoked through stack introspection, which
         * can only be called from the current thread. Thus, there is no use case for eager
         * deoptimization to happen if the current thread is executing the lazy deopt stub.
         */
        VMError.guarantee(originalStackPointer.readWord(0) == originalReturnAddress, "Eager deoptimization should not occur when lazy deoptimization is in progress");

        CodePointer returnAddressAfter = FrameAccess.singleton().readReturnAddress(CurrentIsolate.getCurrentThread(), originalStackPointer);
        VMError.guarantee(returnAddressAfter == deoptStubAddress, "Return address must remain unchanged during deoptimization");

        recentDeoptimizationEvents.append(deoptFrame.getCompletedMessage());

        // From this point on, only uninterruptible code may be executed.
        UnsignedWord updatedGpReturnValue = gpReturnValue;
        if (gpReturnValueObject != null) {
            updatedGpReturnValue = Word.objectToUntrackedPointer(gpReturnValueObject);
        }

        /* Do the stack rewriting. Return directly to the deopt target. */
        return rewriteStackStub(newSp, updatedGpReturnValue, fpReturnValue, deoptFrame);
    }

    @Uninterruptible(reason = "Wrapper to call interruptible methods", calleeMustBe = false)
    private static DeoptimizedFrame constructLazilyDeoptimizedFrameInterruptibly(Pointer sourceSp, CodePointer ip, boolean hasException) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        SuspendSerialGCMaxHeapSize.suspendInCurrentThread();

        try {
            UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(ip);
            Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
            try {
                CodeInfo info = CodeInfoAccess.convert(untetheredInfo, tether);
                return constructLazilyDeoptimizedFrameInterruptibly0(sourceSp, info, ip, hasException);
            } finally {
                CodeInfoAccess.releaseTether(untetheredInfo, tether);
            }
        } finally {
            SuspendSerialGCMaxHeapSize.resumeInCurrentThread();
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    private static DeoptimizedFrame constructLazilyDeoptimizedFrameInterruptibly0(Pointer sourceSp, CodeInfo info, CodePointer ip, boolean hasException) {
        maybeTestGC();
        CodeInfoQueryResult sourceChunk = CodeInfoTable.lookupCodeInfoQueryResult(info, ip);
        maybeTestGC();
        Deoptimizer deoptimizer = new Deoptimizer(sourceSp, sourceChunk, CurrentIsolate.getCurrentThread(), CurrentIsolate.getCurrentThread());
        maybeTestDeoptDuringLazyDeopt(deoptimizer, ip);
        DeoptimizedFrame deoptFrame = deoptimizer.doDeoptSourceFrame(ip, true, false);
        if (hasException) {
            deoptFrame.takeException();
        }
        maybeTestGC();
        return deoptFrame;
    }

    /**
     * See {@link #lazyDeoptStubObjectReturn} for context, but note that here, the deoptimized frame
     * has already been prepared and only needs to be written to the stack. For the same reason,
     * this stub is fully uninterruptible because no allocations are needed, and does not use
     * {@link StubCallingConvention}, because access to any callee-saved registers is not required.
     */
    @DeoptStub(stubType = StubType.EntryStub)
    @Uninterruptible(reason = "Frame holds Objects in unmanaged storage.")
    public static UnsignedWord eagerDeoptStub(Pointer originalStackPointer, UnsignedWord gpReturnValue, UnsignedWord fpReturnValue) {
        /* Establish the correct return address for this stub to make the stack walkable. */
        CodePointer returnAddress = DeoptimizationSupport.getEagerDeoptStubPointer();
        FrameAccess.singleton().writeReturnAddress(CurrentIsolate.getCurrentThread(), originalStackPointer, returnAddress);

        try {
            assert PointerUtils.isAMultiple(KnownIntrinsics.readStackPointer(), Word.unsigned(ConfigurationValues.getTarget().stackAlignment));
            VMError.guarantee(VMThreads.StatusSupport.isStatusJava(), "Deopt stub execution must not be visible to other threads.");

            DeoptimizedFrame frame = (DeoptimizedFrame) ReferenceAccess.singleton().readObjectAt(originalStackPointer, true);

            DeoptimizationCounters.counters().deoptCount.inc();
            if (DeoptimizationCounters.Options.ProfileDeoptimization.getValue()) {
                DeoptimizationCounters.startTime.set(System.nanoTime());
            }

            final Pointer newSp = computeNewStackPointer(originalStackPointer, frame);

            /* Build the content of the deopt target stack frames. */
            frame.buildContent(newSp);

            /*
             * The frame was pinned to keep it from moving during construction. I can unpin it now
             * that I am uninterruptible. (And I have to unpin it.)
             */
            frame.unpin();

            recentDeoptimizationEvents.append(frame.getCompletedMessage());

            /* Do the stack rewriting. Return directly to the deopt target. */
            rewriteStackStub(newSp, gpReturnValue, fpReturnValue, frame);
            throw UnreachableNode.unreachable();

        } catch (Throwable t) {
            throw VMError.shouldNotReachHere("Exception in eager deopt stub", t);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Pointer computeNewStackPointer(Pointer originalStackPointer, DeoptimizedFrame deoptimizedFrame) {
        /* Remove the size of the frame that gets deoptimized. */
        Pointer callerStackPointer = originalStackPointer.add(Word.unsigned(deoptimizedFrame.getSourceTotalFrameSize()));

        /* Verify that the top frame anchor is in a part of the stack that is not rewritten. */
        verifyTopFrameAnchor(callerStackPointer);

        /* Add the size of the deoptimization target frames. */
        return callerStackPointer.subtract(deoptimizedFrame.getTargetContent().getSize());
    }

    /**
     * Performs the actual stack rewriting. The custom prologue of this method sets the stack
     * pointer to the new value passed in as the first parameter.
     * <p>
     * The custom epilogue of this method restores the return value registers from the returned
     * frame handle.
     */
    @DeoptStub(stubType = StubType.ExitStub)
    @NeverInline("Custom prologue modifies stack pointer register")
    @Uninterruptible(reason = "Frame holds Objects in unmanaged storage.")
    private static UnsignedWord rewriteStackStub(Pointer newSp, UnsignedWord gpReturnValue, @SuppressWarnings("unused") UnsignedWord fpReturnValue, DeoptimizedFrame frame) {
        /*
         * The first word of the new stack content is already the return address into the caller of
         * deoptimizeInRange(). So when this method returns we are inside the caller of
         * deoptimizeInRange().
         */
        Pointer bottomSp = newSp.subtract(FrameAccess.returnAddressSize() + savedBasePointerSize());
        frame.getTargetContent().copyToPointer(bottomSp);

        if (DeoptimizationCounters.Options.ProfileDeoptimization.getValue()) {
            DeoptimizationCounters.counters().timeSpentInDeopt.add(System.nanoTime() - DeoptimizationCounters.startTime.get());
        }
        return gpReturnValue;
    }

    /**
     * Returns the size in bytes of the saved base pointer in the stack frame. The saved base
     * pointer must be located immediately after the return address (if this is not the case in a
     * new architecture, bigger modifications to the Deoptimizer code are required).
     */
    @Fold
    static int savedBasePointerSize() {
        if (SubstrateOptions.hasFramePointer()) {
            return FrameAccess.wordSize();
        } else {
            VMError.guarantee(Platform.includedIn(Platform.AMD64.class));
            return 0;
        }
    }

    /**
     * Deoptimizes a source frame lazily, or reverts to eager deoptimization if lazy deoptimization
     * is not enabled.
     *
     * @param pc A code address inside the source method (= the method to deoptimize)
     */
    private void deoptSourceFrameLazily(CodePointer pc, boolean ignoreNonDeoptimizable) {
        assert VMOperation.isInProgressAtSafepoint();
        if (!Options.LazyDeoptimization.getValue()) {
            deoptSourceFrameEagerly(pc, ignoreNonDeoptimizable);
            return;
        }
        if (checkLazyDeoptimized(deoptState.targetThread, deoptState.sourceSp) || checkEagerDeoptimized(deoptState.targetThread, deoptState.sourceSp) != null) {
            // already deoptimized, nothing to do
            return;
        }

        if (!canBeDeoptimized(sourceChunk.getFrameInfo())) {
            if (ignoreNonDeoptimizable) {
                return;
            } else {
                throw fatalDeoptimizationError("Deoptimization: cannot lazily deoptimize a method that has no deoptimization entry point", sourceChunk.getFrameInfo(), sourceChunk.getFrameInfo());
            }
        }

        FrameInfoQueryResult frameInfo = sourceChunk.getFrameInfo();
        CodeInfoQueryResult targetInfo = CodeInfoTable.lookupDeoptimizationEntrypoint(frameInfo.getDeoptMethodImageCodeInfo(), frameInfo.getDeoptMethodOffset(), frameInfo.getEncodedBci());
        installLazyDeoptStubReturnAddress(targetInfo.getDeoptReturnValueIsObject(), deoptState.sourceSp, deoptState.targetThread);
    }

    private DeoptimizedFrame deoptSourceFrameEagerly(CodePointer pc, boolean ignoreNonDeoptimizable) {
        if (!canBeDeoptimized(sourceChunk.getFrameInfo())) {
            if (ignoreNonDeoptimizable) {
                return null;
            } else {
                throw fatalDeoptimizationError("Deoptimization: cannot eagerly deoptimize a method that has no deoptimization entry point", sourceChunk.getFrameInfo(), sourceChunk.getFrameInfo());
            }
        }

        final EagerDeoptSourceFrameOperation operation = new EagerDeoptSourceFrameOperation(this, pc, ignoreNonDeoptimizable);
        operation.enqueue();
        return operation.getResult();
    }

    public DeoptimizedFrame deoptimizeEagerly() {
        VMError.guarantee(requestingThread == CurrentIsolate.getCurrentThread(), "This method should be called by the thread which creates the Deoptimizer.");
        return deoptSourceFrameEagerly(sourceChunk.getIP(), false);
    }

    @Uninterruptible(reason = "Prevent stack walks from seeing an inconsistent stack.")
    private static void installLazyDeoptStubReturnAddress(boolean returnValueIsObject, Pointer sourceSp, IsolateThread targetThread) {
        assert Options.LazyDeoptimization.getValue();
        assert VMOperation.isInProgressAtSafepoint();
        CodePointer originalReturnAddress = FrameAccess.singleton().readReturnAddress(targetThread, sourceSp);

        /*
         * Replace the return address to the deoptimized method with the entry point of the lazy
         * deopt stub that is appropriate for the return value.
         *
         * Stack walks recognize our stubs in return addresses and know to read the frame's original
         * return address from another slot (see below), e.g. for walking object references.
         */
        CodePointer stubAddress = returnValueIsObject ? DeoptimizationSupport.getLazyDeoptStubObjectReturnPointer() : DeoptimizationSupport.getLazyDeoptStubPrimitiveReturnPointer();
        FrameAccess.singleton().writeReturnAddress(targetThread, sourceSp, stubAddress);
        /*
         * Write the original return address into the slot where the DeoptimizedFrame would go in
         * the case of eager deoptimization.
         */
        sourceSp.writeWord(0, originalReturnAddress);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static CodePointer readLazyDeoptOriginalReturnAddress(IsolateThread thread, Pointer sp) {
        assert checkLazyDeoptimized(thread, sp);
        return sp.readWord(0);
    }

    @Uninterruptible(reason = "Prevent stack walks from seeing an inconsistent stack.")
    private static void uninstallLazyDeoptStubReturnAddress(Pointer sourceSp, IsolateThread thread) {
        assert Options.LazyDeoptimization.getValue();
        assert VMOperation.isInProgressAtSafepoint();
        CodePointer originalReturnAddress = sourceSp.readWord(0);
        assert originalReturnAddress.isNonNull();
        // Clear the original return address from the deopt slot
        sourceSp.writeWord(0, Word.nullPointer());
        // Restore the original return address on the stack
        FrameAccess.singleton().writeReturnAddress(thread, sourceSp, originalReturnAddress);
    }

    /**
     * A VMOperation to deoptimize a frame eagerly. This involves patching the return address to
     * {@link #eagerDeoptStub} and also installing a heap-allocated {@link DeoptimizedFrame} in a
     * reserved stack slot.
     */
    private static final class EagerDeoptSourceFrameOperation extends JavaVMOperation {

        private final Deoptimizer receiver;
        private final CodePointer pc;
        private final boolean ignoreNonDeoptimizable;
        private DeoptimizedFrame result;

        EagerDeoptSourceFrameOperation(Deoptimizer receiver, CodePointer pc, boolean ignoreNonDeoptimizable) {
            super(VMOperationInfos.get(EagerDeoptSourceFrameOperation.class, "Eagerly deoptimize source frame", SystemEffect.SAFEPOINT));
            this.receiver = receiver;
            this.pc = pc;
            this.ignoreNonDeoptimizable = ignoreNonDeoptimizable;
            this.result = null;
            if (Options.LazyDeoptimization.getValue()) {
                VMError.guarantee(receiver.deoptState.targetThread == receiver.requestingThread, "With lazy deoptimization enabled, a thread can request eager deoptimization only on itself.");
            }
        }

        @Override
        public void operate() {
            result = receiver.doDeoptSourceFrame(pc, ignoreNonDeoptimizable, true);
        }

        public DeoptimizedFrame getResult() {
            return result;
        }
    }

    /**
     * Checks if a frame has a deoptimization target.
     */
    private static boolean canBeDeoptimized(FrameInfoQueryResult frame) {
        if (frame == null) {
            return false;
        }
        FrameInfoQueryResult currFrame = frame;
        while (currFrame != null) {
            if (currFrame.getDeoptMethodOffset() == 0) {
                return false;
            }
            currFrame = currFrame.getCaller();
        }
        return true;
    }

    private DeoptimizedFrame doDeoptSourceFrame(CodePointer pc, boolean ignoreNonDeoptimizable, boolean isEagerDeopt) {
        assert !isEagerDeopt || VMOperation.isInProgressAtSafepoint() : "eager deopts may only happen at a safepoint";
        if (Options.LazyDeoptimization.getValue()) {
            VMError.guarantee(deoptState.targetThread == requestingThread, "With lazy deoptimization enabled, this method may only be called for the requesting thread.");
        }

        DeoptimizedFrame existing = checkEagerDeoptimized(deoptState.targetThread, deoptState.sourceSp);
        if (existing != null) {
            /* Already deoptimized, so nothing to do. */
            return existing;
        }

        if (isEagerDeopt && checkLazyDeoptimized(deoptState.targetThread, deoptState.sourceSp)) {
            // already pending lazy deoptimization. Fix return address, then deopt eagerly below.
            uninstallLazyDeoptStubReturnAddress(deoptState.sourceSp, deoptState.targetThread);
        }

        final FrameInfoQueryResult frameInfo = sourceChunk.getFrameInfo();
        if (frameInfo == null) {
            if (ignoreNonDeoptimizable) {
                return null;
            } else {
                throw VMError.shouldNotReachHere("Deoptimization: cannot deoptimize a method that was not marked as deoptimizable from address " + Long.toHexString(pc.rawValue()));
            }
        }

        assert endOfParams == 0 : endOfParams;

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
                    throw fatalDeoptimizationError("Deoptimization: cannot deoptimize a method that has no deoptimization entry point", deoptInfo, frameInfo);
                }
            }

            CodeInfoQueryResult targetInfo = CodeInfoTable.lookupDeoptimizationEntrypoint(deoptInfo.getDeoptMethodImageCodeInfo(), deoptInfo.getDeoptMethodOffset(), deoptInfo.getEncodedBci());
            if (targetInfo == null || targetInfo.getFrameInfo() == null) {
                throw fatalDeoptimizationError(
                                "Deoptimization: no matching target bytecode frame found for deopt target method", deoptInfo, frameInfo);
            } else if (!targetInfo.getFrameInfo().isDeoptEntry()) {
                throw fatalDeoptimizationError(
                                "Deoptimization: target frame information not marked as deoptimization entry point", deoptInfo, frameInfo);
            } else if (targetInfo.getFrameInfo().getDeoptMethod() != null && targetInfo.getFrameInfo().getDeoptMethod().hasCalleeSavedRegisters()) {
                /*
                 * The deoptMethod is not guaranteed to be available, but this is only a last check,
                 * to have a better error than the probable segfault.
                 */
                throw fatalDeoptimizationError("Deoptimization: target method has callee saved registers, which are not properly restored by the deoptimization runtime", deoptInfo, frameInfo);
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

        if (sourceChunk.getTotalFrameSize() < FrameAccess.wordSize()) {
            throw fatalDeoptimizationError(
                            String.format("Insufficient space in frame for pointer to DeoptimizedFrame sourceChunkSize: %s, word size: %s", sourceChunk.getTotalFrameSize(), FrameAccess.wordSize()),
                            frameInfo, frameInfo);
        }

        RelockObjectData[] relockObjectData = relockedObjects == null ? null : relockedObjects.toArray(new RelockObjectData[relockedObjects.size()]);
        boolean rethrowException = FrameInfoDecoder.decodeRethrowException(frameInfo.getEncodedBci());
        /* Allocate a buffer to hold the contents of the new target frame. */
        DeoptimizedFrame deoptimizedFrame = DeoptimizedFrame.factory(targetContentSize, sourceChunk.getEncodedFrameSize(), CodeInfoTable.lookupInstalledCode(pc), topFrame, relockObjectData, pc,
                        rethrowException, isEagerDeopt);

        if (isEagerDeopt) {
            installDeoptimizedFrame(deoptimizedFrame);
        }

        if (Options.TraceDeoptimization.getValue()) {
            printDeoptimizedFrame(Log.log(), deoptState.sourceSp, deoptimizedFrame, frameInfo, false);
        }
        logDeoptSourceFrameOperation(deoptState.sourceSp, deoptimizedFrame, frameInfo);

        return deoptimizedFrame;
    }

    private static void logDeoptSourceFrameOperation(Pointer sp, DeoptimizedFrame deoptimizedFrame, FrameInfoQueryResult frameInfo) {
        StringBuilderLog log = new StringBuilderLog();
        Pointer deoptimizedFrameAddress = Word.objectToUntrackedPointer(deoptimizedFrame);
        log.string("deoptSourceFrameOperation: DeoptimizedFrame at ").zhex(deoptimizedFrameAddress).string(": ");
        printDeoptimizedFrame(log, sp, deoptimizedFrame, frameInfo, true);
        recentDeoptimizationEvents.append(log.getResult().toCharArray());
    }

    private static final RingBuffer.Consumer<char[]> deoptEventsConsumer = (context, entry) -> {
        Log log = (Log) context;
        int length = Math.min(entry.length, MAX_DEOPTIMIZATION_EVENT_PRINT_LENGTH);
        for (int i = 0; i < length; i++) {
            char c = entry[i];
            log.character(c);
            if (c == '\n') {
                log.spaces(log.getIndentation());
            }
        }

        if (length < entry.length) {
            log.string("...").newline();
        }
    };

    public static void logRecentDeoptimizationEvents(Log log) {
        log.string("Recent deoptimization events (oldest first):").indent(true);
        recentDeoptimizationEvents.foreach(log, deoptEventsConsumer);
        log.indent(false);
    }

    /**
     * Constructs the frame entries for the deoptimization target method.
     *
     * @param targetInfo The bytecode frame (+ some other info) of the target.
     * @param sourceFrame The bytecode frame of the source.
     */
    private VirtualFrame constructTargetFrame(CodeInfoQueryResult targetInfo, FrameInfoQueryResult sourceFrame) {
        FrameInfoQueryResult targetFrame = targetInfo.getFrameInfo();
        int savedBasePointerSize = savedBasePointerSize();
        int targetFrameSize = NumUtil.safeToInt(targetInfo.getTotalFrameSize()) - FrameAccess.returnAddressSize() - savedBasePointerSize;
        VirtualFrame result = new VirtualFrame(targetFrame);

        if (savedBasePointerSize != 0) {
            result.savedBasePointer = new DeoptimizedFrame.SavedBasePointer(targetContentSize, targetContentSize + targetFrameSize);
            targetContentSize += savedBasePointerSize;
        }

        /* The first word of the new content is the return address into the target method. */
        result.returnAddress = new DeoptimizedFrame.ReturnAddress(targetContentSize, targetInfo.getIP().rawValue());
        targetContentSize += FrameAccess.returnAddressSize();

        /* The source and target bytecode frame must match (as they stem from the same BCI). */
        boolean compatibleState = sourceFrame.getNumLocals() == targetFrame.getNumLocals() &&
                        sourceFrame.getNumStack() == targetFrame.getNumStack() &&
                        sourceFrame.getNumLocks() == targetFrame.getNumLocks() &&
                        targetFrame.getVirtualObjects().length == 0 &&
                        sourceFrame.getValueInfos().length >= targetFrame.getValueInfos().length;
        if (!compatibleState) {
            String message = String.format("Deoptimization is not possible. Please report this error.%n" +
                            "Target Frame: numLocals-%s, numStack-%s, numLocks-%s, getValueInfos length-%s, virtual objects length-%s%n" +
                            "Source Frame: numLocals-%s, numStack-%s, numLocks-%s, getValueInfos length-%s%n",
                            targetFrame.getNumLocals(), targetFrame.getNumStack(), targetFrame.getNumLocks(), targetFrame.getValueInfos().length, targetFrame.getVirtualObjects().length, //
                            sourceFrame.getNumLocals(), sourceFrame.getNumStack(), sourceFrame.getNumLocks(), sourceFrame.getValueInfos().length);
            throw fatalDeoptimizationError(message, targetFrame);
        }

        int numValues = targetFrame.getValueInfos().length;

        /*
         * Create stack entries for all values of the source frame.
         */
        int newEndOfParams = endOfParams;
        for (int idx = 0; idx < numValues; idx++) {
            ValueInfo targetValue = targetFrame.getValueInfos()[idx];
            if (targetValue.getKind() == JavaKind.Illegal || targetValue.getType() == FrameInfoQueryResult.ValueType.ReservedRegister) {
                /*
                 * The target value is either optimized out, e.g. at a position after the lifetime
                 * of a local variable, or is a reserved register. In either situation, we don't
                 * care what the source value is. Optimized out values will not be restored, and for
                 * reserved registers the value will be automatically correct when execution resumes
                 * in the target frame.
                 */
            } else {
                ValueInfo sourceValue = sourceFrame.getValueInfos()[idx];
                JavaConstant con = deoptState.readValue(sourceValue, sourceFrame);
                if (con.getJavaKind() == JavaKind.Illegal) {
                    throw Deoptimizer.fatalDeoptimizationError("Found illegal kind in source frame", sourceFrame);
                }

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
                        int totalOffset = targetContentSize + targetOffset;
                        if (!(totalOffset >= endOfParams && targetOffset >= 0 && targetOffset != targetFrameSize && targetOffset != (targetFrameSize + savedBasePointerSize))) {
                            throw fatalDeoptimizationError(
                                            String.format("Bad offset values. targetOffset %s, totalOffset %s, endOfParams %s, targetFrameSize %s, savedBasePointerSize %s", targetOffset,
                                                            totalOffset, endOfParams,
                                                            targetFrameSize, savedBasePointerSize),
                                            targetFrame);
                        }
                        if (targetOffset < targetFrameSize) {
                            /*
                             * This is the most common case: a regular slot in the stack frame,
                             * which e.g. holds a variable.
                             */
                            result.values[idx] = DeoptimizedFrame.ConstantEntry.factory(totalOffset, con, sourceFrame);

                        } else if (sourceFrame.getCaller() != null) {
                            /*
                             * Handle stack parameters for inlined calls: write the value to the
                             * outgoing parameter area of the caller frame.
                             */
                            result.values[idx] = DeoptimizedFrame.ConstantEntry.factory(totalOffset, con, sourceFrame);

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
                        } else {
                            /*
                             * This parameter is passed within the caller's frame and does not need
                             * to be restored.
                             */
                        }
                        break;

                    case DefaultConstant:
                    case Constant:
                        /*
                         * The target value was constant propagated. Check that source and target
                         * performed the same constant propagation
                         */
                        verifyConstant(targetFrame, targetValue, con);
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
                        throw Deoptimizer.fatalDeoptimizationError("Unknown deopt target value type: " + targetValue, targetFrame);
                }
            }
        }
        targetContentSize += targetFrameSize;
        endOfParams = newEndOfParams;
        return result;
    }

    private void relockObject(JavaConstant valueConstant) {
        Object lockedObject = SubstrateObjectConstant.asObject(valueConstant);
        Object lockData = MonitorSupport.singleton().prepareRelockObject(lockedObject);

        if (relockedObjects == null) {
            relockedObjects = new ArrayList<>();
        }
        relockedObjects.add(new RelockObjectData(lockedObject, lockData));
    }

    private void verifyConstant(FrameInfoQueryResult targetFrame, ValueInfo targetValue, JavaConstant source) {
        boolean equal;
        JavaConstant target = deoptState.readValue(targetValue, targetFrame);
        if (source.getJavaKind() == JavaKind.Object && target.getJavaKind() == JavaKind.Object) {
            // Differences in compression are irrelevant, compare only object identities
            equal = (SubstrateObjectConstant.asObject(target) == SubstrateObjectConstant.asObject(source));
        } else {
            equal = source.equals(target);
        }
        if (!equal) {
            throw fatalDeoptimizationError(String.format("Constants do not match.%nSource: %s%nTarget: %s", source, target), targetFrame);
        }
    }

    /**
     * Writes an instance field or an array element into a materialized object.
     *
     * @param materializedObj The materialized object
     * @param offsetInObj The offset of the instance field or array element
     * @param constant The value to write
     */
    protected static void writeValueInMaterializedObj(Object materializedObj, UnsignedWord offsetInObj, JavaConstant constant, FrameInfoQueryResult frameInfo) {
        if (offsetInObj.equal(0)) {
            throw fatalDeoptimizationError("offsetInObj is 0. Materialized value would overwrite hub.", frameInfo);
        }
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
                throw fatalDeoptimizationError("Unexpected JavaKind " + constant.getJavaKind(), frameInfo);
        }
    }

    private static void printDeoptimizedFrame(Log log, Pointer sp, DeoptimizedFrame deoptimizedFrame, FrameInfoQueryResult sourceFrameInfo, boolean printOnlyTopFrames) {
        log.string("[Deoptimization of frame (").rational(Isolates.getUptimeMillis(), TimeUtils.millisPerSecond, 3).string("s)").newline();

        SubstrateInstalledCode installedCode = deoptimizedFrame.getSourceInstalledCode();
        if (installedCode != null) {
            log.string("    name: ").string(installedCode.getName()).newline();
        }
        log.string("    sp: ").zhex(sp).string("  ip: ").zhex(deoptimizedFrame.getSourcePC()).newline();

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
                    log.string("method at ").zhex(sourceFrame.getDeoptMethodAddress());
                }
                log.string(" bci ");
                FrameInfoDecoder.logReadableBci(log, sourceFrame.getEncodedBci());
                log.string("  return address ").zhex(targetFrame.returnAddress.returnAddress).newline();

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
        log.string("  deoptMethod: ").zhex(frameInfo.getDeoptMethodAddress());
        log.string("  return address: ").zhex(virtualFrame.returnAddress.returnAddress).string("  offset: ").signed(virtualFrame.returnAddress.offset);

        for (int i = 0; i < frameInfo.getValueInfos().length; i++) {
            JavaConstant con = virtualFrame.getConstant(i);
            if (con.getJavaKind() != JavaKind.Illegal) {
                log.newline().string("            slot ").signed(i);
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
        private final int arrayBaseOffset = ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Byte);

        private static final ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException = new ArrayIndexOutOfBoundsException("TargetContent.offsetCheck");

        /* Check that an offset is in range. */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private void offsetCheck(int offset, int size) {
            if (!((0 <= offset) && (offset <= (frameBuffer.length - size)))) {
                throw arrayIndexOutOfBoundsException;
            }
        }

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
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected int getSize() {
            return frameBuffer.length;
        }

        /** Copy the bytes to the memory at the given Pointer. */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected void copyToPointer(Pointer p) {
            for (int idx = 0; idx < frameBuffer.length; idx++) {
                p.writeByte(idx, frameBuffer[idx]);
            }
        }

        /** Write an int-sized constant to the frame buffer. */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected void writeInt(int offset, int value) {
            offsetCheck(offset, sizeofInt);
            addressOfFrameArray0().writeInt(offset, value);
        }

        /** Write a long-sized constant to the frame buffer. */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected void writeLong(int offset, long value) {
            offsetCheck(offset, sizeofLong);
            addressOfFrameArray0().writeLong(offset, value);
        }

        /** Write a word-sized constant to the frame buffer. */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected void writeObject(int offset, Object value, boolean compressed) {
            offsetCheck(offset, compressed ? sizeofCompressedReference : sizeofUncompressedReference);
            Word address = (Word) addressOfFrameArray0();
            address = address.add(offset);
            ReferenceAccess.singleton().writeObjectAt(address, value, compressed);
        }

        /* Return &contentArray[0] as a Pointer. */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private Pointer addressOfFrameArray0() {
            return Word.objectToUntrackedPointer(frameBuffer).add(arrayBaseOffset);
        }
    }

    static RuntimeException fatalDeoptimizationError(String originalMessage, FrameInfoQueryResult frameInfo) {
        throw fatalDeoptimizationError0(originalMessage, frameInfo, frameInfo, false);
    }

    static RuntimeException fatalDeoptimizationError(String originalMessage, FrameInfoQueryResult frameInfo, FrameInfoQueryResult topFrame) {
        throw fatalDeoptimizationError0(originalMessage, frameInfo, topFrame, true);
    }

    private static RuntimeException fatalDeoptimizationError0(String originalMessage, FrameInfoQueryResult frameInfo, FrameInfoQueryResult topFrame, boolean fullStack) {
        long encodedBci = frameInfo.getEncodedBci();
        String message = String.format("%s%nencodedBci: %s (bci %s)%nMethod info: %s", originalMessage, encodedBci, FrameInfoDecoder.readableBci(encodedBci), frameInfo.getSourceReference());
        StringBuilder sb = new StringBuilder(message);
        if (fullStack) {
            sb.append(System.lineSeparator()).append("Full Deoptimized Stack").append(System.lineSeparator());
        } else {
            sb.append(System.lineSeparator()).append("Partial Deoptimized Stack").append(System.lineSeparator());
        }
        FrameInfoQueryResult current = topFrame;
        while (current != null) {
            sb.append(current.getSourceReference()).append(System.lineSeparator());
            current = current.getCaller();
        }
        throw VMError.shouldNotReachHere(sb.toString());
    }
}
