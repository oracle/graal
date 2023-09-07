/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.intentionallyUnimplemented;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import com.oracle.svm.core.deopt.DeoptEntryInfopoint;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ImplicitExceptionDispatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.LoweredCallTargetNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.SuitesProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.nodes.ComputedIndirectCallTargetNode;
import com.oracle.svm.core.graal.snippets.CFunctionSnippets;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.nodes.CFunctionPrologueDataNode;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class SubstrateBackend extends Backend {
    private static final LocationIdentity JIT_VTABLE_IDENTITY = NamedLocationIdentity.mutable("DynamicHub.vtable@jit");

    public enum SubstrateMarkId implements CompilationResult.MarkId {
        /**
         * Marks the start of the prologue in case the prologue instructions are not the first
         * instructions in the compilation.
         */
        PROLOGUE_START(true),
        PROLOGUE_DECD_RSP(true),
        PROLOGUE_SAVED_REGS(true),
        PROLOGUE_END(true),
        EPILOGUE_START(false),
        EPILOGUE_INCD_RSP(true),
        EPILOGUE_END(true);

        final boolean isMarkAfter;

        SubstrateMarkId(boolean isMarkAfter) {
            this.isMarkAfter = isMarkAfter;
        }

        @Override
        public String getName() {
            return name();
        }
    }

    private RuntimeConfiguration runtimeConfiguration;

    /**
     * @see #setRuntimeToRuntimeInvokeMethod
     */
    private ResolvedJavaMethod runtimeToRuntimeInvokeMethod;

    protected SubstrateBackend(Providers providers) {
        super(providers);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setRuntimeConfiguration(RuntimeConfiguration runtimeConfiguration) {
        this.runtimeConfiguration = runtimeConfiguration;
    }

    public RuntimeConfiguration getRuntimeConfiguration() {
        assert runtimeConfiguration != null : "Access before initialization";
        return runtimeConfiguration;
    }

    /**
     * Registers the {@link Method} that is used to dispatch direct run time to run time calls.
     * Currently, there exists at most one such method at image run time. If this changes at some
     * point, {@link #runtimeToRuntimeInvokeMethod} should be replaced by a list.
     * 
     * @see #isRuntimeToRuntimeCall
     */
    public void setRuntimeToRuntimeInvokeMethod(Method method) {
        VMError.guarantee(this.runtimeToRuntimeInvokeMethod == null, "can only be set once");
        this.runtimeToRuntimeInvokeMethod = getProviders().getMetaAccess().lookupJavaMethod(method);
    }

    /**
     * Checks whether an invoke will directly call a run-time compiled method. Such calls are only
     * performed by well-known methods (which might be inlined, though). Thus, run time to run time
     * calls can be identified by inspecting the {@link LIRFrameState} at the call.
     * 
     * @param callState the frame state at the call
     */
    protected boolean isRuntimeToRuntimeCall(LIRFrameState callState) {
        if (SubstrateUtil.HOSTED) {
            /*
             * Only a run-time compiled method can directly call another run-time compiled method.
             */
            return false;
        }
        if (runtimeToRuntimeInvokeMethod != null && callState != null && callState.topFrame != null) {
            ResolvedJavaMethod m = callState.topFrame.getMethod();
            return runtimeToRuntimeInvokeMethod.equals(m);
        }
        return false;
    }

    @Override
    public SuitesProvider getSuites() {
        throw intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    public CompilationResult newCompilationResult(CompilationIdentifier compilationIdentifier, String name) {
        return new CompilationResult(compilationIdentifier, name) {
            @Override
            public void close(OptionValues options) {
                /*
                 * Do nothing, other than compress the Infopoint frame chains by merging common
                 * frames into a tree. We do not want our CompilationResult to be closed because we
                 * aggregate all data items and machine code in the native image heap.
                 */
                FrameOptimizer.compressInfoPoints(this);
            }
        };
    }

    private static class FrameOptimizer {
        /**
         * Equivalence class that can be used to compare chains of BytecodeFrame instances.
         *
         * This class makes up for some deficiencies in the current implementation of equals on
         * class BytecodeFrame.
         */

        private static void compressInfoPoints(CompilationResult result) {
            List<Infopoint> infopointsCopy = new ArrayList<>(result.getInfopoints());
            result.clearInfopoints();
            EconomicMap<BytecodeFrame, BytecodeFrame> frameMap = EconomicMap.create();
            for (Infopoint infopoint : infopointsCopy) {
                Infopoint newInfopoint = removeDuplicates(infopoint, frameMap);
                assert verifyEqual(newInfopoint, infopoint) : "new " + newInfopoint + "\n    !=\n" + infopoint;
                result.addInfopoint(newInfopoint);
                assert (infopoint instanceof Call == newInfopoint instanceof Call);
                if (newInfopoint != infopoint && infopoint instanceof Call && !result.isValidCallDeoptimizationState((Call) infopoint)) {
                    // result must discount the replacement infopoint as a deopt location
                    result.recordCallInvalidForDeoptimization((Call) newInfopoint);
                }
            }
        }

        private static Infopoint removeDuplicates(Infopoint infopoint, EconomicMap<BytecodeFrame, BytecodeFrame> frameMap) {
            DebugInfo debugInfo = infopoint.debugInfo;
            if (debugInfo != null && debugInfo.frame() != null) {
                BytecodeFrame frame = debugInfo.frame();
                Deque<BytecodeFrame> frames = new ArrayDeque<>();
                // for efficiency process the frames top down
                while (frame != null) {
                    frames.push(frame);
                    frame = frame.caller();
                }
                BytecodeFrame lastFrame = null;
                while (!frames.isEmpty()) {
                    BytecodeFrame nextFrame = frames.pop();
                    // try to insert a shallow copy that shares common parents
                    BytecodeFrame newFrame = (lastFrame == null ? nextFrame : shallowCopy(nextFrame, lastFrame));
                    BytecodeFrame existingFrame = frameMap.putIfAbsent(newFrame, newFrame);
                    if (existingFrame != null) {
                        assert verifyEqual(newFrame, existingFrame);
                        // the frame is a duplicate so reuse the one we already have
                        lastFrame = existingFrame;
                    } else {
                        lastFrame = newFrame;
                    }
                }

                DebugInfo newDebugInfo = new DebugInfo(lastFrame, debugInfo.getVirtualObjectMapping());
                newDebugInfo.setCalleeSaveInfo(debugInfo.getCalleeSaveInfo());
                newDebugInfo.setReferenceMap(debugInfo.getReferenceMap());
                if (infopoint instanceof Call) {
                    Call callInfoPoint = (Call) infopoint;
                    return new Call(callInfoPoint.target, callInfoPoint.pcOffset, callInfoPoint.size, callInfoPoint.direct, newDebugInfo);
                } else if (infopoint instanceof ImplicitExceptionDispatch) {
                    ImplicitExceptionDispatch dispatchInfoPoint = (ImplicitExceptionDispatch) infopoint;
                    return new ImplicitExceptionDispatch(dispatchInfoPoint.pcOffset, dispatchInfoPoint.dispatchOffset, newDebugInfo);
                } else if (infopoint instanceof DeoptEntryInfopoint) {
                    return new DeoptEntryInfopoint(infopoint.pcOffset, newDebugInfo);
                } else {
                    assert infopoint.getClass() == Infopoint.class : "unexpected info point : " + infopoint;
                    return new Infopoint(infopoint.pcOffset, newDebugInfo, infopoint.reason);
                }
            } else {
                return infopoint;
            }
        }

        private static final JavaKind[] EMPTY_KINDS = new JavaKind[0];

        private static BytecodeFrame shallowCopy(BytecodeFrame frame, BytecodeFrame caller) {
            int localsCount = frame.numLocals;
            int stackCount = frame.numStack;
            int valueCount = localsCount + stackCount;
            JavaKind[] valueKinds = (valueCount > 0 ? new JavaKind[valueCount] : EMPTY_KINDS);
            for (int i = 0; i < localsCount; i++) {
                valueKinds[i] = frame.getLocalValueKind(i);
            }
            for (int i = 0; i < stackCount; i++) {
                valueKinds[localsCount + i] = frame.getStackValueKind(i);
            }
            return new BytecodeFrame(caller, frame.getMethod(), frame.getBCI(), frame.rethrowException, frame.duringCall, frame.values, valueKinds, frame.numLocals, frame.numStack, frame.numLocks);
        }

        private static boolean verifyEqual(Infopoint newInfopoint, Infopoint oldInfopoint) {
            if (newInfopoint == oldInfopoint) {
                return true;
            }
            assert newInfopoint.getClass() == oldInfopoint.getClass() : "wrong class";
            assert newInfopoint.pcOffset == oldInfopoint.pcOffset : "wrong offset";
            assert newInfopoint.reason == oldInfopoint.reason : "wrong reason";
            if (newInfopoint.getClass() == Call.class) {
                Call newCall = (Call) newInfopoint;
                Call oldCall = (Call) oldInfopoint;
                assert newCall.target == oldCall.target : "wrong target";
                assert newCall.direct == oldCall.direct : "wrong direct";
                assert newCall.size == oldCall.size : "wrong size";
            }
            if (newInfopoint.getClass() == ImplicitExceptionDispatch.class) {
                ImplicitExceptionDispatch newImplicitExceptionDispatch = (ImplicitExceptionDispatch) newInfopoint;
                ImplicitExceptionDispatch oldImplicitExceptionDispatch = (ImplicitExceptionDispatch) oldInfopoint;
                assert newImplicitExceptionDispatch.dispatchOffset == oldImplicitExceptionDispatch.dispatchOffset : "wrong dispathcOffset";
            }
            if (newInfopoint.debugInfo != oldInfopoint.debugInfo && (newInfopoint.debugInfo != null)) {
                return verifyEqual(newInfopoint.debugInfo, oldInfopoint.debugInfo);
            }
            return true;
        }

        private static boolean verifyEqual(DebugInfo oldDebugInfo, DebugInfo newDebugInfo) {
            assert oldDebugInfo.getReferenceMap() == newDebugInfo.getReferenceMap() : "wrong ref map";
            assert oldDebugInfo.getVirtualObjectMapping() == newDebugInfo.getVirtualObjectMapping() : "wrong virt obj map";
            assert oldDebugInfo.getCalleeSaveInfo() == newDebugInfo.getCalleeSaveInfo() : "wrong callee save info";
            if (oldDebugInfo.frame() != newDebugInfo.frame()) {
                return verifyEqual(oldDebugInfo.frame(), newDebugInfo.frame());
            }
            return true;
        }

        private static boolean verifyEqual(BytecodeFrame oldFrame, BytecodeFrame newFrame) {
            if (oldFrame == newFrame) {
                return true;
            }
            assert oldFrame != null && newFrame != null : "odd caller chain" + oldFrame + "\n\n" + newFrame;
            assert oldFrame.getBCI() == newFrame.getBCI() : "wrong bci";
            assert Objects.equals(oldFrame.getMethod(), newFrame.getMethod());
            assert Objects.equals(oldFrame.caller(), newFrame.caller());
            assert oldFrame.duringCall == newFrame.duringCall : "wrong duringCall";
            assert oldFrame.rethrowException == newFrame.rethrowException : "wrong rethrowException";
            assert oldFrame.numLocks == newFrame.numLocks : "wrong numLocks";
            assert oldFrame.numLocals == newFrame.numLocals : "wrong numLocals";
            assert oldFrame.numStack == newFrame.numStack : "wrong numStack";
            for (int i = 0; i < oldFrame.numLocals; i++) {
                assert oldFrame.getLocalValueKind(i) == newFrame.getLocalValueKind(i) : "bad local value kind" + i;
                assert oldFrame.getLocalValue(i).equals(newFrame.getLocalValue(i)) : "bad local value" + i;
            }
            for (int i = 0; i < oldFrame.numStack; i++) {
                assert oldFrame.getStackValueKind(i) == newFrame.getStackValueKind(i) : "bad stack value kind" + i;
                assert oldFrame.getStackValue(i).equals(newFrame.getStackValue(i)) : "bad stack value" + i;
            }
            for (int i = 0; i < oldFrame.numLocks; i++) {
                assert oldFrame.getLockValue(i).equals(newFrame.getLockValue(i)) : "bad lock value" + i;
            }
            if (oldFrame.caller() != newFrame.caller()) {
                return verifyEqual(oldFrame.caller(), newFrame.caller());
            }
            return true;
        }
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new RegisterAllocationConfig(registerConfigNonNull, allocationRestrictedTo);
    }

    public static boolean hasJavaFrameAnchor(CallTargetNode callTarget) {
        return getPrologueData(callTarget) != null;
    }

    public static ValueNode getJavaFrameAnchor(CallTargetNode callTarget) {
        ValueNode frameAnchor = getPrologueData(callTarget).frameAnchor();
        assert frameAnchor != null;
        return frameAnchor;
    }

    /** For runtime compilations, emit only indirect calls to avoid additional patching. */
    public static boolean shouldEmitOnlyIndirectCalls() {
        return !SubstrateUtil.HOSTED;
    }

    /**
     * Identity for {@link com.oracle.svm.core.hub.DynamicHub} vtable accesses.
     *
     * Runtime-compiled code uses a mutable identity because it can be persisted and loaded in a
     * process where image code is located elsewhere and so the addresses in vtables are different.
     */
    public static LocationIdentity getVTableIdentity() {
        return SubstrateUtil.HOSTED ? NamedLocationIdentity.FINAL_LOCATION : JIT_VTABLE_IDENTITY;
    }

    /** See {@link #shouldEmitOnlyIndirectCalls()}, {@link #getVTableIdentity()}. */
    protected static void verifyCallTarget(LoweredCallTargetNode callTarget) {
        if (shouldEmitOnlyIndirectCalls()) {
            if (callTarget instanceof IndirectCallTargetNode) {
                VMError.guarantee(!((IndirectCallTargetNode) callTarget).computedAddress().isConstant(),
                                "Runtime-compiled code must not contain calls with absolute addresses");
            } else if (!(callTarget instanceof ComputedIndirectCallTargetNode)) {
                throw VMError.shouldNotReachHere("Call uses non-indirect target when only indirect calls are permitted: " + callTarget);
            }
        }
    }

    /**
     * We are re-using the field {InvokeNode#classInit()} to store the prologue data, see
     * {@link CFunctionSnippets#matchCallStructure}.
     */
    private static CFunctionPrologueDataNode getPrologueData(CallTargetNode callTarget) {
        return (CFunctionPrologueDataNode) callTarget.invoke().classInit();
    }

    public static int getNewThreadStatus(CallTargetNode callTarget) {
        CFunctionPrologueDataNode prologueData = getPrologueData(callTarget);
        if (prologueData != null) {
            return prologueData.getNewThreadStatus();
        }
        return StatusSupport.STATUS_ILLEGAL;
    }

    public abstract BasePhase<CoreProviders> newAddressLoweringPhase(CodeCacheProvider codeCache);

    public abstract CompilationResult createJNITrampolineMethod(ResolvedJavaMethod method, CompilationIdentifier identifier,
                    RegisterValue threadArg, int threadIsolateOffset, RegisterValue methodIdArg, int methodObjEntryPointOffset);

    /**
     * Returns whether the backend can fold the stack overflow check into the method prologue for
     * the provided method.
     * 
     * @param method The method that is compiled.
     */
    public boolean stackOverflowCheckedInPrologue(SharedMethod method) {
        return false;
    }

    /**
     * Returns whether the backend can fold the safepoint check into the method epilogue for the
     * provided method.
     *
     * @param method The method that is compiled.
     */
    public boolean safepointCheckedInEpilogue(SharedMethod method) {
        return false;
    }
}
