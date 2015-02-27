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

import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Cmp;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Jmp;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Nop;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.sparc.SPARC.CPUFeature;
import com.oracle.graal.sparc.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.truffle.hotspot.*;

@ServiceProvider(OptimizedCallTargetInstrumentationFactory.class)
public class SPARCOptimizedCallTargetInstumentationFactory implements OptimizedCallTargetInstrumentationFactory {

    public CompilationResultBuilder createBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, FrameContext frameContext,
                    CompilationResult compilationResult) {
        return new OptimizedCallTargetInstrumentation(codeCache, foreignCalls, frameMap, asm, frameContext, compilationResult) {
            @Override
            protected void injectTailCallCode(HotSpotVMConfig config, HotSpotRegistersProvider registers) {
                @SuppressWarnings("hiding")
                SPARCMacroAssembler asm = (SPARCMacroAssembler) this.asm;
                try (SPARCScratchRegister scratch = SPARCScratchRegister.get()) {
                    Register thisRegister = codeCache.getRegisterConfig().getCallingConventionRegisters(Type.JavaCall, Kind.Object)[0];
                    Register spillRegister = scratch.getRegister();
                    Label doProlog = new Label();
                    SPARCAddress codeBlobAddress = new SPARCAddress(thisRegister, getFieldOffset("address", InstalledCode.class));
                    SPARCAddress verifiedEntryPointAddress = new SPARCAddress(spillRegister, config.nmethodEntryOffset);

                    new Ldx(codeBlobAddress, spillRegister).emit(asm);
                    if (asm.hasFeature(CPUFeature.CBCOND)) {
                        new CBcondx(ConditionFlag.Equal, spillRegister, 0, doProlog).emit(asm);
                    } else {
                        new Cmp(spillRegister, 0).emit(asm);
                        asm.bpcc(Equal, NOT_ANNUL, doProlog, Xcc, PREDICT_NOT_TAKEN);
                        new Nop().emit(asm);
                    }
                    new Ldx(verifiedEntryPointAddress, spillRegister).emit(asm); // in delay slot
                    new Jmp(spillRegister).emit(asm);
                    new Nop().emit(asm);
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
