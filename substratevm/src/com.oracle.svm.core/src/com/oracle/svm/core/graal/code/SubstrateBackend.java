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

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.tiers.SuitesProvider;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.snippets.CFunctionSnippets;
import com.oracle.svm.core.nodes.CFunctionPrologueDataNode;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class SubstrateBackend extends Backend {

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

    @Override
    public SuitesProvider getSuites() {
        throw unimplemented();
    }

    public CompilationResult newCompilationResult(CompilationIdentifier compilationIdentifier, String name) {
        return new CompilationResult(compilationIdentifier, name) {
            @Override
            public void close() {
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

    public abstract Phase newAddressLoweringPhase(CodeCacheProvider codeCache);

    public abstract CompilationResult createJNITrampolineMethod(ResolvedJavaMethod method, CompilationIdentifier identifier,
                    RegisterValue threadArg, int threadIsolateOffset, RegisterValue methodIdArg, int methodObjEntryPointOffset);
}
