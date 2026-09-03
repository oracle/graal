/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.code;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.SkipEpilogueSafepointCheck;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.snippets.StackOverflowCheckImpl;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.thread.SafepointSlowpath;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAnnotationAccess;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class SubstrateFrameContextSupport {

    public interface FrameContextWithTailCallTrampolines {
        void emitTailCallTrampolines(CompilationResultBuilder crb);
    }

    public static final class TailCallTrampolines {
        private Label stackOverflowTrampoline;
        private Label slowPathSafepointTrampoline;

        public boolean isStackOverflowTrampolinePresent() {
            return stackOverflowTrampoline != null;
        }

        public boolean isSlowPathSafepointTrampolinePresent() {
            return slowPathSafepointTrampoline != null;
        }

        public Label createOrGetStackOverflowTrampoline() {
            if (stackOverflowTrampoline == null) {
                stackOverflowTrampoline = new Label();
            }
            return stackOverflowTrampoline;
        }

        public Label createOrGetSlowPathSafepointTrampoline() {
            if (slowPathSafepointTrampoline == null) {
                slowPathSafepointTrampoline = new Label();
            }
            return slowPathSafepointTrampoline;
        }

        public void clear() {
            stackOverflowTrampoline = null;
            slowPathSafepointTrampoline = null;
        }
    }

    private final boolean scratchRegisterAvailable;

    public SubstrateFrameContextSupport(boolean scratchRegisterAvailable) {
        this.scratchRegisterAvailable = scratchRegisterAvailable;
    }

    /**
     * Returns whether using a temporary register would overwrite a callee-saved value.
     */
    private boolean tempOverwritesCalleeSavedRegister(SharedMethod method) {
        return method.hasCalleeSavedRegisters() && !scratchRegisterAvailable;
    }

    public boolean canEmitTailCalls(SharedMethod method) {
        return emitSafepointCheckInEpilogue(method) || emitStackOverflowCheckInPrologue(method);
    }

    public boolean emitStackOverflowCheckInPrologue(SharedMethod method) {
        return stackOverflowCheckedInPrologue(method) && method.needStackOverflowCheck();
    }

    public boolean stackOverflowCheckedInPrologue(SharedMethod method) {
        if (!SubstrateOptions.StackOverflowCheckInPrologue.getValue()) {
            /* The optimization is explicitly disabled. */
            return false;
        } else if (tempOverwritesCalleeSavedRegister(method)) {
            /*
             * The stack overflow check requires a temporary register. When all registers are
             * callee-saved, this would require a special runtime method that reuses the registers
             * saved by the throwing method. It would not save callee-saved registers in its
             * prologue, but it would still restore them in its epilogue. Few methods have
             * callee-saved registers, so do not optimize this case.
             */
            return false;
        } else if (!SubstrateUtil.HOSTED) {
            // GR-35438 currently tail call address calculation is incompatible with auxiliary heaps
            return false;
        } else {
            return true;
        }
    }

    public static ResolvedJavaMethod getStackOverflowCallTarget(CompilationResultBuilder crb, SharedMethod method) {
        SnippetRuntime.SubstrateForeignCallDescriptor callDescriptor = mustNotAllocate(method)
                        ? StackOverflowCheckImpl.THROW_CACHED_STACK_OVERFLOW_ERROR
                        : StackOverflowCheckImpl.THROW_NEW_STACK_OVERFLOW_ERROR;
        return ((SubstrateForeignCallLinkage) crb.getForeignCalls().lookupForeignCall(callDescriptor)).getMethod();
    }

    /**
     * Returns the addend used for the stack overflow check.
     */
    public static int getDeoptFrameSize(CompilationResultBuilder crb) {
        LIR lir = crb.getLIR();
        StructuredGraph graph = ((ControlFlowGraph) lir.getControlFlowGraph()).graph;
        return NumUtil.safeToInt(StackOverflowCheckImpl.computeDeoptFrameSize(graph));
    }

    /**
     * Returns whether the method must not allocate. This information is only available for an AOT
     * compiled method. Runtime-compiled methods are always allowed to allocate.
     */
    private static boolean mustNotAllocate(SharedMethod method) {
        if (SubstrateUtil.HOSTED) {
            return ImageSingletons.lookup(RestrictHeapAccessCallees.class).mustNotAllocate(method);
        }
        return false;
    }

    public boolean emitSafepointCheckInEpilogue(SharedMethod method) {
        return safepointCheckedInEpilogue(method) && method.needSafepointCheck() &&
                        (ImageInfo.inImageRuntimeCode() || !GuestAnnotationAccess.isAnnotationPresent(method, SkipEpilogueSafepointCheck.class));
    }

    public boolean safepointCheckedInEpilogue(SharedMethod method) {
        if (!CalleeSavedRegisters.supportedByPlatform()) {
            /*
             * Callee-saved register support ensures that a safepoint check in the epilogue does
             * not overwrite the return value.
             */
            return false;
        } else if (!SubstrateOptions.SafepointCheckInEpilogue.getValue()) {
            /* The optimization is explicitly disabled. */
            return false;
        } else if (!SubstrateUtil.HOSTED && tempOverwritesCalleeSavedRegister(method)) {
            /*
             * A runtime-compiled method needs a temporary register for the AOT target address.
             * Runtime-compiled methods may be outside the range of a direct jump to AOT code, so
             * runtime-to-AOT calls must be indirect.
             */
            return false;
        } else if (!SubstrateUtil.HOSTED) {
            // GR-35438 currently tail call address calculation is incompatible with auxiliary heaps
            return false;
        }
        return true;
    }

    public static ResolvedJavaMethod getSlowPathSafepointCallTarget(CompilationResultBuilder crb, SharedMethod method) {
        SnippetRuntime.SubstrateForeignCallDescriptor callDescriptor = ((SharedType) method.getSignature().getReturnType(null)).getStorageKind() == JavaKind.Object
                        ? SafepointSlowpath.ENTER_SLOW_PATH_SAFEPOINT_CHECK_OBJECT
                        : SafepointSlowpath.ENTER_SLOW_PATH_SAFEPOINT_CHECK_CALLEE_SAVED_CCONV;
        return ((SubstrateForeignCallLinkage) crb.getForeignCalls().lookupForeignCall(callDescriptor)).getMethod();
    }

    public static long getCallTargetAddress(ResolvedJavaMethod callTarget) {
        assert !SubstrateUtil.HOSTED;

        SharedMethod targetMethod = (SharedMethod) callTarget;
        long callTargetStart = targetMethod.getImageCodeInfo().getCodeStart().rawValue() + targetMethod.getImageCodeOffset();
        if (callTargetStart == 0) {
            throw VMError.shouldNotReachHere("target method not compiled: " + targetMethod.format("%H.%n(%p)"));
        }
        return callTargetStart;
    }
}
