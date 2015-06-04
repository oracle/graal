/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle.hotspot.sparc;

import static com.oracle.graal.asm.sparc.SPARCAssembler.Annul.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.BranchPredict.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag.*;
import static com.oracle.jvmci.code.CallingConvention.Type.*;
import static com.oracle.jvmci.meta.Kind.*;
import static com.oracle.jvmci.sparc.SPARC.CPUFeature.*;

import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.truffle.hotspot.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.hotspot.*;
import com.oracle.jvmci.meta.*;
import com.oracle.jvmci.service.*;

@ServiceProvider(OptimizedCallTargetInstrumentationFactory.class)
public class SPARCOptimizedCallTargetInstumentationFactory implements OptimizedCallTargetInstrumentationFactory {

    public CompilationResultBuilder createBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, FrameContext frameContext,
                    CompilationResult compilationResult) {
        return new OptimizedCallTargetInstrumentation(codeCache, foreignCalls, frameMap, asm, frameContext, compilationResult) {
            @Override
            protected void injectTailCallCode(HotSpotVMConfig config, HotSpotRegistersProvider registers) {
                @SuppressWarnings("hiding")
                SPARCMacroAssembler asm = (SPARCMacroAssembler) this.asm;
                try (ScratchRegister scratch = asm.getScratchRegister()) {
                    Register thisRegister = codeCache.getRegisterConfig().getCallingConventionRegisters(JavaCall, Object)[0];
                    Register spillRegister = scratch.getRegister();
                    Label doProlog = new Label();
                    SPARCAddress codeBlobAddress = new SPARCAddress(thisRegister, getFieldOffset("address", InstalledCode.class));
                    SPARCAddress verifiedEntryPointAddress = new SPARCAddress(spillRegister, config.nmethodEntryOffset);

                    asm.ldx(codeBlobAddress, spillRegister);
                    if (asm.hasFeature(CBCOND)) {
                        asm.cbcondx(Equal, spillRegister, 0, doProlog);
                    } else {
                        asm.cmp(spillRegister, 0);
                        asm.bpcc(Equal, NOT_ANNUL, doProlog, Xcc, PREDICT_NOT_TAKEN);
                        asm.nop();
                    }
                    asm.ldx(verifiedEntryPointAddress, spillRegister); // in delay slot
                    asm.jmp(spillRegister);
                    asm.nop();
                    asm.bind(doProlog);
                }
            }
        };
    }

    public void setInstrumentedMethod(ResolvedJavaMethod method) {
        HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) method;
        hsMethod.setNotInlineable();
    }

    public String getArchitecture() {
        return "SPARC";
    }
}
