/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.asm.sparc.SPARCAssembler.BranchPredict.PREDICT_NOT_TAKEN;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.Xcc;
import static com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag.Equal;
import static jdk.vm.ci.code.CallingConvention.Type.JavaCall;
import static jdk.vm.ci.meta.JavaKind.Object;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompilationResult;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.service.ServiceProvider;

import com.oracle.graal.asm.Assembler;
import com.oracle.graal.asm.Label;
import com.oracle.graal.asm.sparc.SPARCAddress;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.asm.FrameContext;
import com.oracle.graal.lir.framemap.FrameMap;
import com.oracle.graal.truffle.hotspot.OptimizedCallTargetInstrumentation;
import com.oracle.graal.truffle.hotspot.OptimizedCallTargetInstrumentationFactory;

@ServiceProvider(OptimizedCallTargetInstrumentationFactory.class)
public class SPARCOptimizedCallTargetInstumentationFactory extends OptimizedCallTargetInstrumentationFactory {

    public CompilationResultBuilder createBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, FrameContext frameContext,
                    CompilationResult compilationResult) {
        return new OptimizedCallTargetInstrumentation(codeCache, foreignCalls, frameMap, asm, frameContext, compilationResult, config, registers) {
            @Override
            protected void injectTailCallCode() {
                @SuppressWarnings("hiding")
                SPARCMacroAssembler asm = (SPARCMacroAssembler) this.asm;
                try (ScratchRegister scratch = asm.getScratchRegister()) {
                    Register thisRegister = codeCache.getRegisterConfig().getCallingConventionRegisters(JavaCall, Object)[0];
                    Register spillRegister = scratch.getRegister();
                    Label doProlog = new Label();
                    SPARCAddress entryPointAddress = new SPARCAddress(thisRegister, getFieldOffset("entryPoint", InstalledCode.class));

                    asm.ldx(entryPointAddress, spillRegister);
                    asm.compareBranch(spillRegister, 0, Equal, Xcc, doProlog, PREDICT_NOT_TAKEN, null);
                    asm.jmp(spillRegister);
                    asm.nop();
                    asm.bind(doProlog);
                }
            }
        };
    }

    @Override
    public String getArchitecture() {
        return "SPARC";
    }
}
