/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.unimplemented;

import java.lang.reflect.Method;

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

        @Override
        public boolean isMarkAfter() {
            return isMarkAfter;
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
        throw unimplemented();
    }

    public CompilationResult newCompilationResult(CompilationIdentifier compilationIdentifier, String name) {
        return new CompilationResult(compilationIdentifier, name) {
            @Override
            public void close(OptionValues options) {
                /*
                 * Do nothing, we do not want our CompilationResult to be closed because we
                 * aggregate all data items and machine code in the native image heap.
                 */
            }
        };
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
